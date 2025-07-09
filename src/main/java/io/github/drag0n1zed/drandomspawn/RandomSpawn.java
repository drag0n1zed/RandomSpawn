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

    public static final String NBT_KEY_SPAWN_X = "drandomspawn:spawn_x";
    public static final String NBT_KEY_SPAWN_Y = "drandomspawn:spawn_y";
    public static final String NBT_KEY_SPAWN_Z = "drandomspawn:spawn_z";

    // A thread-safe queue to hold tasks that need to be run on the main server thread.
    private static final Queue<Runnable> mainThreadTasks = new ConcurrentLinkedQueue<>();

    // Enum to distinguish spawn reasons for message customization and logic branching.
    private enum SpawnReason {
        JOIN,
        RESPAWN_NEW_SPAWN,
        RESPAWN_EXISTING_SPAWN
    }

    public RandomSpawn() {
        ModConfig.setup();
        MinecraftForge.EVENT_BUS.register(this);
        // Register listeners for commands and the server tick event.
        MinecraftForge.EVENT_BUS.addListener(this::registerCommands);
        MinecraftForge.EVENT_BUS.addListener(this::onServerTick);
    }

    public void registerCommands(RegisterCommandsEvent event) {
        ModCommands.register(event.getDispatcher());
    }

    /**
     * Safely executes tasks from other threads on the main server thread at the end of each tick.
     * This prevents concurrent modification issues with Minecraft's game state.
     */
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            while (!mainThreadTasks.isEmpty()) {
                mainThreadTasks.poll().run();
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        CompoundTag data = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);

        // Only run if the player has no saved spawn point from this mod.
        if (!data.contains(NBT_KEY_SPAWN_X)) {
            handlePlayerSpawnLogic(player, SpawnReason.JOIN);
        }
    }

    @SubscribeEvent
    public void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        // Check if the player respawned without a specific respawn position (bed/anchor).
        if (player.getRespawnPosition() == null) {
            CompoundTag data = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);

            // If the player has a custom spawn point from this mod, use that.
            if (data.contains(NBT_KEY_SPAWN_X)) {
                handlePlayerSpawnLogic(player, SpawnReason.RESPAWN_EXISTING_SPAWN);
            } else {
                // Player has no bed/anchor AND no custom spawn point from this mod. Search for a new one.
                handlePlayerSpawnLogic(player, SpawnReason.RESPAWN_NEW_SPAWN);
            }
        }
    }

    /**
     * Handles the logic for a player's initial spawn or respawn when no bed/anchor is set.
     * This method decides whether to use an existing custom spawn or search for a new one.
     *
     * @param player The ServerPlayer.
     * @param reason The reason for triggering this spawn logic (JOIN, RESPAWN_NEW_SPAWN, RESPAWN_EXISTING_SPAWN).
     */
    private void handlePlayerSpawnLogic(ServerPlayer player, SpawnReason reason) {
        CompoundTag data = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        boolean hasModSpawn = data.contains(NBT_KEY_SPAWN_X);

        if (reason == SpawnReason.RESPAWN_EXISTING_SPAWN && hasModSpawn) {
            // Teleport to an already saved mod spawn point.
            double x = data.getInt(NBT_KEY_SPAWN_X) + 0.5;
            double y = data.getInt(NBT_KEY_SPAWN_Y);
            double z = data.getInt(NBT_KEY_SPAWN_Z) + 0.5;
            player.teleportTo(x, y, z);
            player.sendSystemMessage(Component.translatable("info.drandomspawn.death.success"));
        } else if (!hasModSpawn) {
            // Player needs a new random spawn (either first join or respawn without mod spawn).
            // Use the "RTP start" message for both scenarios when a new search begins.
            player.sendSystemMessage(Component.translatable("info.drandomspawn.rtp.start"));

            Consumer<BlockPos> onSuccess = (foundPos) -> {
                if (player.connection == null) return; // Player might have disconnected during search.
                player.teleportTo(foundPos.getX() + 0.5, foundPos.getY(), foundPos.getZ() + 0.5);
                savePlayerSpawn(player, foundPos); // Save the newly found spawn point.

                Component successMessage;
                if (reason == SpawnReason.JOIN) {
                    successMessage = Component.translatable("info.drandomspawn.join.success");
                } else { // RESPAWN_NEW_SPAWN, use RTP success message
                    successMessage = Component.translatable("info.drandomspawn.rtp.success");
                }
                player.sendSystemMessage(successMessage);
            };

            Runnable onFail = () -> {
                if (player.connection == null) return; // Player might have disconnected during search.

                Component failMessage;
                if (reason == SpawnReason.JOIN) {
                    failMessage = Component.translatable("info.drandomspawn.join.fail");
                } else { // RESPAWN_NEW_SPAWN, use RTP fail message
                    failMessage = Component.translatable("info.drandomspawn.rtp.fail");
                }
                player.sendSystemMessage(failMessage);
                // If the search failed, the player will remain at the default world spawn point.
            };

            // Start the asynchronous search for a new spawn point.
            findAndTeleportPlayerAsync(player, onSuccess, onFail);
        }
    }


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
    public static void findAndTeleportPlayerAsync(ServerPlayer player, Consumer<BlockPos> onSuccess, Runnable onFail) {
        // Capture original gamemode before starting the async operation.
        final GameType originalGamemode = player.gameMode.getGameModeForPlayer();

        if (ModConfig.useSpectatorLock.get()) {
            // Queue the gamemode change to spectator on the main thread for thread safety.
            mainThreadTasks.add(() -> {
                if (player.connection != null) { // Ensure player is still online.
                    player.setGameMode(GameType.SPECTATOR);
                }
            });
        }

        // Wrap the callbacks to include gamemode restoration on the main thread.
        Consumer<BlockPos> wrappedOnSuccess = (foundPos) -> {
            if (ModConfig.useSpectatorLock.get() && player.connection != null) {
                player.setGameMode(originalGamemode); // Restore original gamemode.
            }
            onSuccess.accept(foundPos);
        };

        Runnable wrappedOnFail = () -> {
            if (ModConfig.useSpectatorLock.get() && player.connection != null) {
                player.setGameMode(originalGamemode); // Restore original gamemode.
            }
            onFail.run();
        };


        new Thread(() -> {
            int RANGE = ModConfig.maxDistance.get();
            int MAX_ATTEMPTS = ModConfig.maxTries.get();
            Level world = player.level();
            // Use player's current respawn position (if set) or world's shared spawn as the center for the search.
            BlockPos centerPos = player.getRespawnPosition() != null ? player.getRespawnPosition() : world.getSharedSpawnPos();
            Random random = new Random();
            BlockPos foundPos = null;

            for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
                int x = centerPos.getX() + random.nextInt(RANGE * 2) - RANGE;
                int z = centerPos.getZ() + random.nextInt(RANGE * 2) - RANGE;
                BlockPos teleportPos = getSafePosition(world, x, z);
                if (teleportPos != null) {
                    foundPos = teleportPos;
                    break;
                }
            }

            // Queue the final actions (teleport, message, gamemode restore) to be run on the main server thread.
            final BlockPos finalPos = foundPos; // Required for lambda capture.
            if (finalPos != null) {
                mainThreadTasks.add(() -> wrappedOnSuccess.accept(finalPos));
            } else {
                mainThreadTasks.add(wrappedOnFail);
            }
        }).start();
    }

    /** Helper method to save the player's new spawn coordinates to their NBT data. */
    public static void savePlayerSpawn(ServerPlayer player, BlockPos pos) {
        CompoundTag playerData = player.getPersistentData();
        CompoundTag data = playerData.contains(Player.PERSISTED_NBT_TAG) ?
                playerData.getCompound(Player.PERSISTED_NBT_TAG) : new CompoundTag();
        data.putInt(NBT_KEY_SPAWN_X, pos.getX());
        data.putInt(NBT_KEY_SPAWN_Y, pos.getY());
        data.putInt(NBT_KEY_SPAWN_Z, pos.getZ());
        playerData.put(Player.PERSISTED_NBT_TAG, data);
    }

    private static BlockPos getSafePosition(Level world, int x, int z) {
        BlockPos.MutableBlockPos testPos = new BlockPos.MutableBlockPos(x, 0, z);
        // Note: world.getChunkAt is a blocking call and accessing world data off-thread
        // can be problematic for very distant or unloaded chunks.
        world.getChunkAt(testPos);
        BlockPos hmPos = world.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, testPos);
        BlockPos groundPos = hmPos.below();
        BlockPos playerFeetPos = hmPos;
        BlockPos playerHeadPos = hmPos.above();
        String biomeId = world.getBiome(groundPos).unwrapKey().map(key -> key.location().toString()).orElse("");

        // Check various safety criteria for the potential spawn location.
        if (world.getWorldBorder().isWithinBounds(groundPos)
                && playerFeetPos.getY() > 63 // Ensures not too deep underground or in the void.
                && !world.getBiome(groundPos).is(BiomeTags.IS_OCEAN) // Avoids ocean biomes.
                && !world.getBiome(groundPos).is(BiomeTags.IS_RIVER) // Avoids river biomes.
                && !ModConfig.biomeBlacklist.get().contains(biomeId)) // Avoids custom blacklisted biomes.
        {
            Block groundBlock = world.getBlockState(groundPos).getBlock();
            String groundBlockId = ForgeRegistries.BLOCKS.getKey(groundBlock).toString();
            boolean isSafeGround = !ModConfig.blockBlacklist.get().contains(groundBlockId);
            if (!world.getBlockState(groundPos).isAir() && isSafeGround // Ground is solid and not blacklisted.
                    && world.getBlockState(playerFeetPos).isAir() // Player's feet level is clear.
                    && world.getBlockState(playerHeadPos).isAir()) // Player's head level is clear.
            {
                return playerFeetPos;
            }
        }
        return null;
    }
}