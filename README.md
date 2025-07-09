# dRandomSpawn

A Minecraft mod enhancing player spawning with unique, persistent, and performance-friendly random spawn points.

## Features

*   **Asynchronous Random Spawn:** Players (first-join or respawning without a set bed/spawnpoint) are teleported to a unique, safe random location. The search for this location runs off the main thread to prevent server lag.
*   **Spectator Lock (Configurable):** Optionally places players in spectator mode during the spawn search for a seamless, controlled transition.
*   **Intelligent Respawn Handling:** Respects player-set beds, respawn anchors, and `/spawnpoint` commands. Only applies its random spawn logic if no other respawn point is defined.
*   **Customizable Spawn Conditions:**
    *   **Search Radius:** Define the maximum distance for random teleportation.
    *   **Biome Blacklist:** Exclude specific biomes from potential spawn locations.
    *   **Block Blacklist:** Prevent spawning on undesirable or hazardous blocks.
*   **Enhanced Commands:**
    *   `/drandomspawn rtp`: Initiates an asynchronous random teleport with immediate feedback.
    *   `/drandomspawn getSpawn`: Allows players to view their saved spawn, or administrators to check others.

## Configuration

The `dRandomSpawn.toml` file, located in your `config` folder, allows for customization:

*   `MaxDistance`: Maximum search radius for a new spawn location.
*   `MaxTries`: Maximum attempts to find a safe location.
*   `useSpectatorLock`: `true` to temporarily set players to spectator mode during search.
*   `biomeBlacklist`: List of biome IDs to avoid.
*   `blockBlacklist`: List of block IDs to avoid spawning on.

## Credits

Original project: [rinko1231/RandomSpawn](https://github.com/rinko1231/RandomSpawn)
Maintained by: [drag0n1zed](https://github.com/drag0n1zed)

## License

This project is licensed under the MIT License. See the [LICENSE](LICENSE) file.