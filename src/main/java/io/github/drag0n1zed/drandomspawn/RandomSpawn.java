package io.github.drag0n1zed.drandomspawn;

import io.github.drag0n1zed.drandomspawn.command.ModCommands;
import io.github.drag0n1zed.drandomspawn.config.ModConfig;
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
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.Random;

@Mod("drandomspawn")
public class RandomSpawn {

    private static final String NBT_KEY_SPAWN_X = "drandomspawn:spawn_x";
    private static final String NBT_KEY_SPAWN_Y = "drandomspawn:spawn_y";
    private static final String NBT_KEY_SPAWN_Z = "drandomspawn:spawn_z";

    public RandomSpawn() {
        ModConfig.setup();
        MinecraftForge.EVENT_BUS.register(this);
        // Register the command event listener
        MinecraftForge.EVENT_BUS.addListener(this::registerCommands);
    }

    public void registerCommands(RegisterCommandsEvent event) {
        ModCommands.register(event.getDispatcher());
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            CompoundTag playerData = player.getPersistentData();
            CompoundTag data = playerData.contains(Player.PERSISTED_NBT_TAG) ?
                    playerData.getCompound(Player.PERSISTED_NBT_TAG) : new CompoundTag();

            if (!data.contains(NBT_KEY_SPAWN_X)) {
                // Call our new reusable method
                boolean success = teleportPlayerToRandomLocation(player);
                if (success) {
                    player.sendSystemMessage(Component.translatable("info.drandomspawn.system.firstsuccess"));
                } else {
                    player.sendSystemMessage(Component.translatable("info.drandomspawn.system.firstfail"));
                }
            }
        }
    }

    public static boolean teleportPlayerToRandomLocation(ServerPlayer player) {
        int RANGE = ModConfig.MaxDistance.get();
        int MAX_ATTEMPTS = ModConfig.MaxTries.get();
        Level world = player.level();

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

                // Save the new spawn coordinates to the player's data.
                CompoundTag playerData = player.getPersistentData();
                CompoundTag data = playerData.contains(Player.PERSISTED_NBT_TAG) ?
                        playerData.getCompound(Player.PERSISTED_NBT_TAG) : new CompoundTag();
                data.putInt(NBT_KEY_SPAWN_X, teleportPos.getX());
                data.putInt(NBT_KEY_SPAWN_Y, teleportPos.getY());
                data.putInt(NBT_KEY_SPAWN_Z, teleportPos.getZ());
                // Make sure to put the sub-tag back if it was new
                if (!playerData.contains(Player.PERSISTED_NBT_TAG)) {
                    playerData.put(Player.PERSISTED_NBT_TAG, data);
                }
                return true; // Indicate success
            }
        }
        return false; // Indicate failure
    }

    @SubscribeEvent
    public void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            if (!player.isRespawnForced()) {
                CompoundTag data = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
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

    private static BlockPos getSafePosition(Level world, int x, int z) {
        BlockPos.MutableBlockPos testPos = new BlockPos.MutableBlockPos(x, 0, z);
        world.getChunkAt(testPos);
        BlockPos hmPos = world.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, testPos);
        BlockPos groundPos = hmPos.below();
        BlockPos playerFeetPos = hmPos;
        BlockPos playerHeadPos = hmPos.above();
        String biomeId = world.getBiome(groundPos).unwrapKey().map(key -> key.location().toString()).orElse("");
        if (world.getWorldBorder().isWithinBounds(groundPos)
                && playerFeetPos.getY() > 63
                && !world.getBiome(groundPos).is(BiomeTags.IS_OCEAN)
                && !world.getBiome(groundPos).is(BiomeTags.IS_RIVER)
                && !ModConfig.biomeBlacklist.get().contains(biomeId))
        {
            Block groundBlock = world.getBlockState(groundPos).getBlock();
            String groundBlockId = ForgeRegistries.BLOCKS.getKey(groundBlock).toString();
            boolean isSafeGround = !ModConfig.blockBlacklist.get().contains(groundBlockId);
            if (!world.getBlockState(groundPos).isAir() && isSafeGround
                    && world.getBlockState(playerFeetPos).isAir()
                    && world.getBlockState(playerHeadPos).isAir())
            {
                return playerFeetPos;
            }
        }
        return null;
    }
}