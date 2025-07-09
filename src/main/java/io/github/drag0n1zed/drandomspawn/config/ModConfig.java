package io.github.drag0n1zed.drandomspawn.config;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;

import java.util.List;


public class ModConfig
{
    private static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    public static ForgeConfigSpec.IntValue MaxDistance;
    public static ForgeConfigSpec.IntValue MaxTries;
    public static ForgeConfigSpec.ConfigValue<List<? extends String>> biomeBlacklist;
    public static ForgeConfigSpec.ConfigValue<List<? extends String>> blockBlacklist;
    public static ForgeConfigSpec.BooleanValue useSpectatorLock;

    static
    {
        BUILDER.push("Random Spawn Settings");

        MaxDistance = BUILDER
                .comment(
                        "The maximum radius, in blocks, from the world spawn that a new player can be randomly teleported.",
                        "This only applies to a player's very first join. Higher values may slightly increase search time."
                )
                .defineInRange("maxDistance", 500, 1, 30000);

        MaxTries = BUILDER
                .comment(
                        "How many times the mod will try to find a safe location within the maxDistance.",
                        "If all attempts fail, the player will spawn at the default world spawn."
                )
                .defineInRange("maxTries", 10, 1, 50);

        biomeBlacklist = BUILDER
                .comment(
                        "A list of biomes where new players are not allowed to spawn.",
                        "Entries must be valid biome resource locations, e.g., 'minecraft:ocean' or 'biomesoplenty:wasteland'."
                )
                .defineList("biomeBlacklist", List.of(), // Using an empty list is cleaner than placeholder examples
                        element -> element instanceof String);

        blockBlacklist = BUILDER
                .comment(
                        "A list of blocks that players cannot spawn directly on top of.",
                        "This is useful for preventing spawns on dangerous blocks. The defaults are highly recommended.",
                        "Entries must be valid block resource locations, e.g., 'minecraft:lava'."
                )
                .defineList("blockBlacklist",
                        List.of("minecraft:magma_block", "minecraft:cactus", "minecraft:lava", "minecraft:sweet_berry_bush"),
                        element -> element instanceof String);

        useSpectatorLock = BUILDER
                .comment(
                        "If true, puts a player into spectator mode on first join while finding a safe spawn.",
                        "This prevents them from moving and hides world loading, providing a smoother experience."
                )
                .define("useSpectatorLock", true);
        BUILDER.pop();
        SPEC = BUILDER.build();
    }
    public static void setup()
    {
        ModLoadingContext.get().registerConfig(net.minecraftforge.fml.config.ModConfig.Type.COMMON, SPEC, "dRandomSpawn.toml");
    }

}
