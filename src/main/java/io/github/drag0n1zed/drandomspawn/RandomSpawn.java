package io.github.drag0n1zed.drandomspawn;

import io.github.drag0n1zed.drandomspawn.config.RandomSpawnConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.Random;


@Mod("drandomspawn")
public class RandomSpawn {

    // NBT keys for the player's saved spawn point.
    private static final String NBT_KEY_SPAWN_X = "drandomspawn:spawn_x";
    private static final String NBT_KEY_SPAWN_Y = "drandomspawn:spawn_y";
    private static final String NBT_KEY_SPAWN_Z = "drandomspawn:spawn_z";

    // Mod constructor. Registers config and event listeners.
    public RandomSpawn() {
        RandomSpawnConfig.setup();
        MinecraftForge.EVENT_BUS.register(this);
    }

    /**
     * On a player's first join, finds and teleports them to a random safe location.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {

        int RANGE = RandomSpawnConfig.MaxDistance.get();
        int MAX_ATTEMPTS = RandomSpawnConfig.MaxTries.get();

        if (event.getEntity() instanceof ServerPlayer player) {
            Level world = player.level();

            CompoundTag playerData = player.getPersistentData();
            // CORRECTED: This is the standard, reliable way to get or create the sub-tag.
            CompoundTag data;
            if (playerData.contains(Player.PERSISTED_NBT_TAG)) {
                data = playerData.getCompound(Player.PERSISTED_NBT_TAG);
            } else {
                data = new CompoundTag();
                playerData.put(Player.PERSISTED_NBT_TAG, data);
            }

            // Only run if the player has no saved spawn point from this mod.
            if (!data.contains(NBT_KEY_SPAWN_X)) {

                BlockPos centerPos = player.getRespawnPosition();
                if (centerPos == null) {
                    centerPos = world.getSharedSpawnPos();
                }

                Random random = new Random();
                for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
                    int x = centerPos.getX() + random.nextInt(RANGE * 2) - RANGE;
                    int z = centerPos.getZ() + random.nextInt(RANGE * 2) - RANGE;
                    BlockPos teleportPos = getSafePosition(world, x, z);

                    if (teleportPos != null) {
                        player.teleportTo(teleportPos.getX() + 0.5, teleportPos.getY(), teleportPos.getZ() + 0.5);
                        player.sendSystemMessage(Component.translatable("info.drandomspawn.system.firstsuccess"));

                        // Save the new spawn coordinates to the player's data.
                        data.putInt(NBT_KEY_SPAWN_X, teleportPos.getX());
                        data.putInt(NBT_KEY_SPAWN_Y, teleportPos.getY());
                        data.putInt(NBT_KEY_SPAWN_Z, teleportPos.getZ());
                        return;
                    }
                }
                player.sendSystemMessage(Component.translatable("info.drandomspawn.system.firstfail"));
            }
        }
    }

    /**
     * Overrides the respawn location for players without a bed spawn.
     */
    @SubscribeEvent
    public void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            // Only run if the player has no bed/respawn anchor.
            if (!player.isRespawnForced()) {
                CompoundTag data = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);

                // Check if a random spawn point has been saved.
                if (data.contains(NBT_KEY_SPAWN_X)) {
                    double x = data.getInt(NBT_KEY_SPAWN_X) + 0.5;
                    double y = data.getInt(NBT_KEY_SPAWN_Y);
                    double z = data.getInt(NBT_KEY_SPAWN_Z) + 0.5;

                    player.teleportTo(x, y, z);
                    player.sendSystemMessage(Component.translatable("info.drandomspawn.system.deathtpsuccess"));
                }
            }
        }
    }

    /**
     * Finds a safe surface position at the given X and Z coordinates.
     */
    private static BlockPos getSafePosition(Level world, int x, int z) {
        BlockPos.MutableBlockPos testPos = new BlockPos.MutableBlockPos(x, 0, z);

        // Pre-load the chunk to prevent issues.
        world.getChunkAt(testPos);

        // Find the highest solid block, ignoring leaves.
        BlockPos hmPos = world.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, testPos);

        BlockPos groundPos = hmPos.below();
        BlockPos playerFeetPos = hmPos;
        BlockPos playerHeadPos = hmPos.above();

        // Get the biome's registry name for a reliable check.
        String biomeId = world.getBiome(groundPos).unwrapKey().map(key -> key.location().toString()).orElse("");

        // Check for world border, valid biome, Y>63, and safe ground.
        if (world.getWorldBorder().isWithinBounds(groundPos)
                && playerFeetPos.getY() > 63
                && !world.getBiome(groundPos).is(BiomeTags.IS_OCEAN)
                && !world.getBiome(groundPos).is(BiomeTags.IS_RIVER)
                && !RandomSpawnConfig.biomeBlacklist.get().contains(biomeId))
        {
            Block groundBlock = world.getBlockState(groundPos).getBlock();
            String groundBlockId = ForgeRegistries.BLOCKS.getKey(groundBlock).toString();
            boolean isSafeGround = !RandomSpawnConfig.blockBlacklist.get().contains(groundBlockId);

            // Ensure ground is solid and there are 2 blocks of air for the player.
            if (!world.getBlockState(groundPos).isAir() && isSafeGround
                    && world.getBlockState(playerFeetPos).isAir()
                    && world.getBlockState(playerHeadPos).isAir())
            {
                return playerFeetPos;
            }
        }
        // No safe position found.
        return null;
    }
}