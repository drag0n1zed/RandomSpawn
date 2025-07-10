package io.github.drag0n1zed.drandomspawn;

import com.mojang.logging.LogUtils;
import io.github.drag0n1zed.drandomspawn.command.ModCommands;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.world.effect.MobEffects;
import org.slf4j.Logger;

import java.util.Queue;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;

@Mod(RandomSpawn.MODID)
@Mod.EventBusSubscriber(modid = RandomSpawn.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class RandomSpawn {

    // MODID + LOGGER
    public static final String MODID = "drandomspawn";
    public static final Logger LOGGER = LogUtils.getLogger();

    // NBT Keys for storing player spawn coordinates
    public static final String NBT_KEY_SPAWN_X = MODID + ":spawn_x";
    public static final String NBT_KEY_SPAWN_Y = MODID + ":spawn_y";
    public static final String NBT_KEY_SPAWN_Z = MODID + ":spawn_z";

    // A thread-safe queue to hold tasks that need to be run on the main server thread.
    private static final Queue<Runnable> mainThreadExecutionQueue = new ConcurrentLinkedQueue<>();

    // Enum to distinguish spawn reasons for message customization and logic branching.
    private enum SpawnReason {
        FIRST_JOIN,
        RESPAWN_NEW_SPAWN,
        RESPAWN_EXISTING_SPAWN
    }

    public RandomSpawn() {
        RandomSpawnConfig.register();
    }

    // --- Event Handlers ---
    @SubscribeEvent
    public static void registerCommands(RegisterCommandsEvent event) {
        ModCommands.register(event.getDispatcher());
    }

    // Checks config validity
    @Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static class ModEventBusEvents {
        @SubscribeEvent
        public static void onConfigLoad(final ModConfigEvent.Loading event) {
            if (event.getConfig().getSpec() == RandomSpawnConfig.CONFIG_SPEC) {
                LOGGER.info("Loading and validating dRandomSpawn config...");

                int min = RandomSpawnConfig.minDistance.get();
                int max = RandomSpawnConfig.maxDistance.get();

                if (min > max) {
                    // This method's only job is to throw an error if invalid.
                    String errorMessage = String.format(
                            "[dRandomSpawn] CRITICAL CONFIG ERROR: 'minDistance' (%d) cannot be greater than 'maxDistance' (%d).",
                            min, max
                    );
                    throw new IllegalStateException(errorMessage);
                }

                LOGGER.info("dRandomSpawn config loaded successfully.");
            }
        }
    }

    /**
     * Executes tasks from other threads on the main server thread at the end of each tick.
     * This prevents concurrent modification issues with Minecraft's game state.
     */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            while (!mainThreadExecutionQueue.isEmpty()) {
                mainThreadExecutionQueue.poll().run();
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        CompoundTag playerPersistedData = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);

        // Only runs if the player has no saved spawn point from this mod.
        if (!playerPersistedData.contains(NBT_KEY_SPAWN_X)) {
            initiatePlayerSpawn(player, SpawnReason.FIRST_JOIN);
        }
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
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
     * This method also handles changing the player's gamemode to spectator,
     * gives the player a darkness effect, and eventually revert back
     * if ModConfig.useSpectatorLock is enabled.
     *
     * @param player    The player to teleport.
     * @param onSuccess A Consumer to run on success, accepting the found BlockPos.
     * @param onFail    A Runnable to run on failure.
     */
    public static void findSafeSpawnAndTeleportAsync(ServerPlayer player, Consumer<BlockPos> onSuccess, Runnable onFail) {
        final GameType originalGamemode = player.gameMode.getGameModeForPlayer();

        if (RandomSpawnConfig.useSpectatorLock.get()) {
            mainThreadExecutionQueue.add(() -> {
                player.setGameMode(GameType.SPECTATOR);
                player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 1000000, 0,
                        false, false, false)
                );
            });
        }

        Consumer<BlockPos> wrappedOnSuccess = (foundPos) -> {
            if (RandomSpawnConfig.useSpectatorLock.get()) {
                player.removeEffect(MobEffects.DARKNESS);
                player.setGameMode(originalGamemode);
            }
            onSuccess.accept(foundPos);
        };

        Runnable wrappedOnFail = () -> {
            if (RandomSpawnConfig.useSpectatorLock.get()) {
                player.removeEffect(MobEffects.DARKNESS);
                player.setGameMode(originalGamemode);
            }
            onFail.run();
        };


        new Thread(() -> {
            int minDistance = RandomSpawnConfig.minDistance.get();
            int maxDistance = RandomSpawnConfig.maxDistance.get();
            int maxAttempts = RandomSpawnConfig.maxTries.get();

            Level world = player.level();
            BlockPos centerPos = world.getSharedSpawnPos();
            Random random = new Random();
            BlockPos foundPos = null;

            for (int attempt = 0; attempt < maxAttempts; attempt++) {
                int dx;
                int dz;

                // Loop: Finds a GEOMETRICALLY valid point in the "donut".
                do {
                    // Generate a random offset within the outer square.
                    dx = random.nextInt(-maxDistance, maxDistance + 1);
                    dz = random.nextInt(-maxDistance, maxDistance + 1);

                    // If the point is outside the inner "forbidden" square, it's a valid candidate.
                    // If either axis is outside the minDistance, the point cannot be in the central forbidden square.
                    // Otherwise, the point was too close to the center; the while loop runs again.
                } while (Math.abs(dx) < minDistance && Math.abs(dz) < minDistance);

                // See if it's safe in the world
                BlockPos finalCoords = centerPos.offset(dx, 0, dz);
                BlockPos teleportPos = findSafeSpawnLocation(world, finalCoords.getX(), finalCoords.getZ());

                if (teleportPos != null) {
                    foundPos = teleportPos;
                    break; // Success
                }
                // If failure, reset outer loop
            }

            final BlockPos finalPos = foundPos;
            if (finalPos != null) {
                mainThreadExecutionQueue.add(() -> wrappedOnSuccess.accept(finalPos));
            } else {
                mainThreadExecutionQueue.add(wrappedOnFail);
            }
        }).start();
    }

    /**
     * Saves the player's new spawn coordinates to their NBT data.
     */
    public static void savePlayerSpawn(ServerPlayer player, BlockPos pos) {
        CompoundTag playerData = player.getPersistentData();
        CompoundTag data = playerData.contains(Player.PERSISTED_NBT_TAG) ?
                playerData.getCompound(Player.PERSISTED_NBT_TAG) : new CompoundTag();
        data.putInt(NBT_KEY_SPAWN_X, pos.getX());
        data.putInt(NBT_KEY_SPAWN_Y, pos.getY());
        data.putInt(NBT_KEY_SPAWN_Z, pos.getZ());
        playerData.put(Player.PERSISTED_NBT_TAG, data);
    }

    // --- Private Helper Methods ---

    /**
     * Handles the logic for a player's initial spawn or respawn when no bed/anchor is set.
     * This method decides whether to use an existing custom spawn or search for a new one.
     *
     * @param player The ServerPlayer.
     * @param reason The reason for triggering this spawn logic (FIRST_JOIN, RESPAWN_NEW_SPAWN, RESPAWN_EXISTING_SPAWN).
     */
    private static void initiatePlayerSpawn(ServerPlayer player, SpawnReason reason) {
        CompoundTag playerPersistedData = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);

        if (reason == SpawnReason.RESPAWN_EXISTING_SPAWN) {
            double x = playerPersistedData.getInt(NBT_KEY_SPAWN_X) + 0.5;
            double y = playerPersistedData.getInt(NBT_KEY_SPAWN_Y);
            double z = playerPersistedData.getInt(NBT_KEY_SPAWN_Z) + 0.5;
            player.teleportTo(x, y, z);
            player.sendSystemMessage(Component.translatable("info.drandomspawn.death.success"));
        } else {
            player.sendSystemMessage(Component.translatable("info.drandomspawn.random_teleport.start"));

            Consumer<BlockPos> onSuccess = (foundPos) -> {
                player.teleportTo(foundPos.getX() + 0.5, foundPos.getY(), foundPos.getZ() + 0.5);
                savePlayerSpawn(player, foundPos);

                Component successMessage;
                if (reason == SpawnReason.FIRST_JOIN) {
                    successMessage = Component.translatable("info.drandomspawn.join.success");
                } else {
                    successMessage = Component.translatable("info.drandomspawn.random_teleport.success");
                }
                player.sendSystemMessage(successMessage);
            };

            Runnable onFail = () -> {

                Component failMessage;
                if (reason == SpawnReason.FIRST_JOIN) {
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
        boolean isBiomeAllowed = !isBiomeBlacklisted(biomeId);

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

    private static boolean isBiomeBlacklisted(String biomeId) {
        return RandomSpawnConfig.biomeBlacklist.get().contains(biomeId);
    }

    private static boolean isBlockBlacklisted(String blockId) {
        return RandomSpawnConfig.blockBlacklist.get().contains(blockId);
    }
}
