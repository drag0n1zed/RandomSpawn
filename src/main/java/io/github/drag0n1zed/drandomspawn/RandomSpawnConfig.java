package io.github.drag0n1zed.drandomspawn;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;

import java.util.List;


public class RandomSpawnConfig {
    public static final ForgeConfigSpec.Builder CONFIG_BUILDER = new ForgeConfigSpec.Builder();
    public static final ForgeConfigSpec CONFIG_SPEC;
    public static ForgeConfigSpec.IntValue maxDistance;
    public static ForgeConfigSpec.IntValue minDistance;
    public static ForgeConfigSpec.IntValue maxTries;
    public static ForgeConfigSpec.ConfigValue<List<? extends String>> biomeBlacklist;
    public static ForgeConfigSpec.ConfigValue<List<? extends String>> blockBlacklist;
    public static ForgeConfigSpec.BooleanValue useSpectatorLock;

    static {
        CONFIG_BUILDER.push("dRandomSpawn Config");

        maxDistance = CONFIG_BUILDER
                .comment(
                        "The maximum radius, in blocks, from the world spawn for random teleportation.",
                        "Must have a higher value than minDistance.",
                        "Default:",
                        "maxDistance = 5000"
                )
                .defineInRange("maxDistance", 5000, 1, 50000);

        minDistance = CONFIG_BUILDER
                .comment(
                        "The minimum radius, in blocks, from the world spawn for random teleportation.",
                        "Must have a lower value than maxDistance.",
                        "Default:",
                        "minDistance = 500"
                )
                .defineInRange("minDistance", 500, 1, 50000);

        maxTries = CONFIG_BUILDER
                .comment(
                        "How many times the mod will try to find a safe location within the maxDistance.",
                        "If all attempts fail, the player will spawn at the default world spawn.",
                        "Default:",
                        "maxTries = 50"
                )
                .defineInRange("maxTries", 50, 1, 100);

        biomeBlacklist = CONFIG_BUILDER
                .comment(
                        "A list of biomes where new players are not allowed to spawn.",
                        "Entries must be valid biome resource locations, e.g., 'minecraft:ocean' or 'biomesoplenty:wasteland'.",
                        "Default: ",
                        "biomeBlacklist = []"
                )
                .defineList("biomeBlacklist", List.of(),
                        element -> element instanceof String);

        blockBlacklist = CONFIG_BUILDER
                .comment(
                        "A list of blocks that players cannot spawn directly on top of.",
                        "This is useful for preventing spawns on dangerous blocks.",
                        "Entries must be valid block resource locations, e.g., 'minecraft:lava'.",
                        "Default:",
                        "blockBlacklist = [\"minecraft:water\", \"minecraft:lava\", \"minecraft:magma_block\", \"minecraft:cactus\", \"minecraft:sweet_berry_bush\"]"
                )
                .defineList("blockBlacklist",
                        List.of("minecraft:water", "minecraft:lava", "minecraft:magma_block", "minecraft:cactus", "minecraft:sweet_berry_bush"),
                        element -> element instanceof String);

        useSpectatorLock = CONFIG_BUILDER
                .comment(
                        "If true, puts a player into spectator mode and applies a darkness effect while finding a safe spawn.",
                        "This prevents them from moving and hides world loading, providing a smoother experience.",
                        "Default:",
                        "useSpectatorLock = true"
                )
                .define("useSpectatorLock", true);
        CONFIG_BUILDER.pop();
        CONFIG_SPEC = CONFIG_BUILDER.build();
    }

    public static void register() {
        ModLoadingContext.get().registerConfig(net.minecraftforge.fml.config.ModConfig.Type.COMMON, CONFIG_SPEC, "dRandomSpawn.toml");
    }

}
