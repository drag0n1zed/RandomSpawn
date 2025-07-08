# Random Spawn Mod

This mod enhances player spawning in Minecraft.

## Features

*   **Unique Player Spawn:** When a player joins for the first time, they are randomly teleported to a unique, safe location. This spot becomes their personal spawn point for future deaths, overriding the default world spawn if no bed is set.
*   **Configurable Radius:** Adjust the maximum distance for random teleportation from the world spawn.
*   **Biome Blacklist:** Exclude specific biomes where players should not spawn.
*   **Block Blacklist:** Prevent players from spawning on hazardous blocks.

## Configuration

A `RandomSpawn.toml` file is generated in your `config` folder. You can set:
*   `MaxDistance`
*   `MaxTries`
*   `biomeBlacklist` (list of biome IDs)
*   `blockBlacklist` (list of block IDs)

## Credits

Original project: [rinko1231/RandomSpawn](https://github.com/rinko1231/RandomSpawn)
Maintained by: [drag0n1zed](https://github.com/drag0n1zed)

## License

This project is licensed under the MIT License. See the [LICENSE](LICENSE) file.