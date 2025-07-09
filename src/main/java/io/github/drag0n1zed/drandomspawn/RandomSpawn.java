package io.github.drag0n1zed.drandomspawn;

import io.github.drag0n1zed.drandomspawn.command.ModCommands;
import io.github.drag0n1zed.drandomspawn.config.ModConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Queue;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;

@Mod("drandomspawn")
public class RandomSpawn {

    // NBT Keys for storing player spawn coordinates
    public static final String NBT_KEY_SPAWN_X = "drandomspawn:spawn_x";
    public static final String NBT_KEY_SPAWN_Y = "drandomspawn:spawn_y";
    public static final String NBT_KEY_SPAWN_Z = "drandomspawn:spawn_z";

    // A thread-safe queue to hold tasks that need to be run on the main server thread.
    private static final Queue<Runnable> mainThreadExecutionQueue = new ConcurrentLinkedQueue<>();

    // Enum to distinguish spawn reasons for message customization and logic branching.
    private enum SpawnReason {
        JOIN,
        RESPAWN_NEW_SPAWN,
        RESPAWN_EXISTING_SPAWN
    }

    public RandomSpawn() {
        ModConfig.register();
        MinecraftForge.EVENT_BUS.register(this);
        MinecraftForge.EVENT_BUS.addListener(this::registerCommands);
        MinecraftForge.EVENT_BUS.addListener(this::onServerTick);
    }

    // --- Event Handlers ---

    public void registerCommands(RegisterCommandsEvent event) {
        ModCommands.register(event.getDispatcher());
    }

    /**
     * Executes tasks from other threads on the main server thread at the end of each tick.
     * This prevents concurrent modification issues with Minecraft's game state.
     */
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            while (!mainThreadExecutionQueue.isEmpty()) {
                mainThreadExecutionQueue.poll().run();
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        CompoundTag playerPersistedData = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);

        // Only runs if the player has no saved spawn point from this mod.
        if (!playerPersistedData.contains(NBT_KEY_SPAWN_X)) {
            initiatePlayerSpawn(player, SpawnReason.JOIN);
        }
    }

    @SubscribeEvent
    public void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        // Checks if the player respawned without a specific respawn position (bed/anchor).
        if (player.getRespawnPosition() == null) {
            CompoundTag playerPersistedData = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);

            // If the player has a custom spawn point from this mod, use that.
            if (playerPersistedData.contains(NBT_KEY_SPAWN_X)) {
                initiatePlayerSpawn(player, SpawnReason.RESPAWN_EXISTING_SPAWN);
            } else {
                // Player has no bed/anchor AND no custom spawn point from this mod. Searches for a new one.
                initiatePlayerSpawn(player, SpawnReason.RESPAWN_NEW_SPAWN);
            }
        }
    }

    // --- Public Helper Methods ---

    /**
     * Asynchronously finds a safe random location and executes a callback with the result.
     * The search is performed on a separate thread to avoid freezing the server.
     * The success/fail actions are run safely on the main server thread.
     * This method also handles changing the player's gamemode to spectator and back
     * if ModConfig.useSpectatorLock is enabled.
     *
     * @param player The player to teleport.
     * @param onSuccess A Consumer to run on success, accepting the found BlockPos.
     * @param onFail A Runnable to run on failure.
     */
    public static void findSafeSpawnAndTeleportAsync(ServerPlayer player, Consumer<BlockPos> onSuccess, Runnable onFail) {
        final GameType originalGamemode = player.gameMode.getGameModeForPlayer();

        if (ModConfig.useSpectatorLock.get()) {
            mainThreadExecutionQueue.add(() -> {
                if (player.connection != null) {
                    player.setGameMode(GameType.SPECTATOR);
                }
            });
        }

        Consumer<BlockPos> wrappedOnSuccess = (foundPos) -> {
            if (ModConfig.useSpectatorLock.get() && player.connection != null) {
                player.setGameMode(originalGamemode);
            }
            onSuccess.accept(foundPos);
        };

        Runnable wrappedOnFail = () -> {
            if (ModConfig.useSpectatorLock.get() && player.connection != null) {
                player.setGameMode(originalGamemode);
            }
            onFail.run();
        };


        new Thread(() -> {
            int searchRange = ModConfig.maxDistance.get();
            int maxAttempts = ModConfig.maxTries.get();
            Level world = player.level();
            BlockPos centerPos = player.getRespawnPosition() != null ? player.getRespawnPosition() : world.getSharedSpawnPos();
            Random random = new Random();
            BlockPos foundPos = null;

            for (int attempt = 0; attempt < maxAttempts; attempt++) {
                int x = centerPos.getX() + random.nextInt(searchRange * 2) - searchRange;
                int z = centerPos.getZ() + random.nextInt(searchRange * 2) - searchRange;
                BlockPos teleportPos = findSafeSpawnLocation(world, x, z);
                if (teleportPos != null) {
                    foundPos = teleportPos;
                    break;
                }
            }

            final BlockPos finalPos = foundPos;
            if (finalPos != null) {
                mainThreadExecutionQueue.add(() -> wrappedOnSuccess.accept(finalPos));
            } else {
                mainThreadExecutionQueue.add(wrappedOnFail);
            }
        }).start();
    }

    /** Saves the player's new spawn coordinates to their NBT data. */
    public static void savePlayerSpawn(ServerPlayer player, BlockPos pos) {
        CompoundTag playerData = player.getPersistentData();
        playerData.putInt(NBT_KEY_SPAWN_X, pos.getX());
        playerData.putInt(NBT_KEY_SPAWN_Y, pos.getY());
        playerData.putInt(NBT_KEY_SPAWN_Z, pos.getZ());
    }

    // --- Private Helper Methods ---

    /**
     * Handles the logic for a player's initial spawn or respawn when no bed/anchor is set.
     * This method decides whether to use an existing custom spawn or search for a new one.
     *
     * @param player The ServerPlayer.
     * @param reason The reason for triggering this spawn logic (JOIN, RESPAWN_NEW_SPAWN, RESPAWN_EXISTING_SPAWN).
     */
    private void initiatePlayerSpawn(ServerPlayer player, SpawnReason reason) {
        CompoundTag playerPersistedData = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        boolean hasModSpawn = playerPersistedData.contains(NBT_KEY_SPAWN_X);

        if (reason == SpawnReason.RESPAWN_EXISTING_SPAWN && hasModSpawn) {
            double x = playerPersistedData.getInt(NBT_KEY_SPAWN_X) + 0.5;
            double y = playerPersistedData.getInt(NBT_KEY_SPAWN_Y);
            double z = playerPersistedData.getInt(NBT_KEY_SPAWN_Z) + 0.5;
            player.teleportTo(x, y, z);
            player.sendSystemMessage(Component.translatable("info.drandomspawn.death.success"));
        } else if (!hasModSpawn) {
            player.sendSystemMessage(Component.translatable("info.drandomspawn.random_teleport.start"));

            Consumer<BlockPos> onSuccess = (foundPos) -> {
                if (player.connection == null) return;
                player.teleportTo(foundPos.getX() + 0.5, foundPos.getY(), foundPos.getZ() + 0.5);
                savePlayerSpawn(player, foundPos);

                Component successMessage;
                if (reason == SpawnReason.JOIN) {
                    successMessage = Component.translatable("info.drandomspawn.join.success");
                } else {
                    successMessage = Component.translatable("info.drandomspawn.random_teleport.success");
                }
                player.sendSystemMessage(successMessage);
            };

            Runnable onFail = () -> {
                if (player.connection == null) return;

                Component failMessage;
                if (reason == SpawnReason.JOIN) {
                    failMessage = Component.translatable("info.drandomspawn.join.fail");
                } else {
                    failMessage = Component.translatable("info.drandomspawn.random_teleport.fail");
                }
                player.sendSystemMessage(failMessage);
            };

            findSafeSpawnAndTeleportAsync(player, onSuccess, onFail);
        }
    }

    private static BlockPos findSafeSpawnLocation(Level world, int x, int z) {
        BlockPos.MutableBlockPos testPos = new BlockPos.MutableBlockPos(x, 0, z);
        world.getChunkAt(testPos);
        BlockPos hmPos = world.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, testPos);
        BlockPos groundPos = hmPos.below();
        BlockPos playerFeetPos = hmPos;
        BlockPos playerHeadPos = hmPos.above();
        String biomeId = world.getBiome(groundPos).unwrapKey().map(key -> key.location().toString()).orElse("");

        boolean isWithinWorldBorder = world.getWorldBorder().isWithinBounds(groundPos);
        boolean isAboveGroundLevel = playerFeetPos.getY() > 63;
        boolean isBiomeAllowed = !isBiomeBlacklisted(world, groundPos, biomeId);

        if (isWithinWorldBorder && isAboveGroundLevel && isBiomeAllowed) {
            Block groundBlock = world.getBlockState(groundPos).getBlock();
            String groundBlockId = ForgeRegistries.BLOCKS.getKey(groundBlock).toString();

            boolean isGroundSolidAndAllowed = !world.getBlockState(groundPos).isAir() && !isBlockBlacklisted(groundBlockId);
            boolean isPlayerSpaceClear = world.getBlockState(playerFeetPos).isAir() && world.getBlockState(playerHeadPos).isAir();

            if (isGroundSolidAndAllowed && isPlayerSpaceClear) {
                return playerFeetPos;
            }
        }
        return null;
    }

    /** Checks if a biome is blacklisted. */
    private static boolean isBiomeBlacklisted(Level world, BlockPos pos, String biomeId) {
        return world.getBiome(pos).is(BiomeTags.IS_OCEAN)
                || world.getBiome(pos).is(BiomeTags.IS_RIVER)
                || ModConfig.biomeBlacklist.get().contains(biomeId);
    }

    /** Checks if a block is blacklisted. */
    private static boolean isBlockBlacklisted(String blockId) {
        return ModConfig.blockBlacklist.get().contains(blockId);
    }
}
