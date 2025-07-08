package io.github.drag0n1zed.drandomspawn.config;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;

import java.util.List;


public class RandomSpawnConfig
{
    private static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    public static ForgeConfigSpec.IntValue MaxDistance;
    public static ForgeConfigSpec.IntValue MaxTries;
    public static ForgeConfigSpec.ConfigValue<List<? extends String>> biomeBlacklist;
    public static ForgeConfigSpec.ConfigValue<List<? extends String>> blockBlacklist;

    static
    {
        BUILDER.push("Random Spawn Config");
        MaxDistance = BUILDER
                .comment("Max RTP Distance")
                .defineInRange("MaxDistance", 500, 1, 10000);
        MaxTries = BUILDER
                .comment("Max RTP Tries")
                .defineInRange("MaxTries", 5, 1, 20);
        biomeBlacklist = BUILDER
                .comment("Biome Blacklist")
                .defineList("biome Blacklist", List.of("mod1:biome2","mod3:biome4"),
                        element -> element instanceof String);
        blockBlacklist = BUILDER
                .comment("Block Blacklist")
                .defineList("block Blacklist", List.of("minecraft:magma_block","minecraft:cactus","minecraft:lava"),
                        element -> element instanceof String);
        SPEC = BUILDER.build();
    }
    public static void setup()
    {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, SPEC, "dRandomSpawn.toml");
    }

}
