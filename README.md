# dRandomSpawn

A simple mod that teleports new players to a random location and sets it as their spawn, asynchronously.

## Features

*   **Asynchronous Random Spawn:** Players (first-join or respawning without a set bed/spawnpoint) are teleported to a unique, safe random location. The search is asynchronous.
*   **Spectator Lock (Configurable):** Optionally places players in spectator mode and gives darkness effect during the spawn search.
*   **Intelligent Respawn Handling:** Respects player-set beds, respawn anchors, and `/spawnpoint` commands. Only utilizes the unique spawn if no other respawn point is defined.
*   **Customizable Spawn Conditions:**
    *   **Search Radius:** Define the maximum distance for random teleportation.
    *   **Biome Blacklist:** Exclude specific biomes from potential spawn locations.
    *   **Block Blacklist:** Prevent spawning on undesirable or hazardous blocks.
*   **Commands:**
    *   `/drandomspawn get_spawn [player]`: Allows players to view their saved spawn points. Requires OP to specify player.
    *   `/drandomspawn random_teleport [player]`: Initiates random teleport and saves the new location. Requires OP.
    *   `/drandomspawn set_spawn [player] [x] [y] [z]`: Sets player spawn position manually. Requires OP.
## Configuration

The `dRandomSpawn.toml` file, located in your `config` folder, allows for customization:

*   `maxDistance`: The maximum radius, in blocks, from the world spawn for random teleportation. Higher values may slightly increase search time.
*   `maxTries`: How many times the mod will try to find a safe location within the maxDistance. If all attempts fail, the player will spawn at the default world spawn.
*   `useSpectatorLock`: If true, puts a player into spectator mode on first join while finding a safe spawn. This prevents them from moving and hides world loading, providing a smoother experience.
*   `biomeBlacklist`: A list of biomes where new players are not allowed to spawn. Entries must be valid biome resource locations, e.g., 'minecraft:ocean' or 'biomesoplenty:wasteland'.
*   `blockBlacklist`: A list of blocks that players cannot spawn directly on top of. This is useful for preventing spawns on dangerous blocks. Entries must be valid block resource locations, e.g., 'minecraft:lava'.

## Credits

Original project: [rinko1231/RandomSpawn](https://github.com/rinko1231/RandomSpawn)

Maintained by: [drag0n1zed](https://github.com/drag0n1zed)

## License

This project is licensed under the MIT License.
