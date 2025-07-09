package io.github.drag0n1zed.drandomspawn.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github.drag0n1zed.drandomspawn.RandomSpawn;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.function.Consumer;

public class ModCommands {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        // Main command literal
        dispatcher.register(Commands.literal("drandomspawn")
                .then(
                        // /drandomspawn rtp
                        Commands.literal("rtp")
                                .requires(source -> source.hasPermission(2)) // Requires OP for all /rtp commands
                                .executes(ModCommands::executeRtpSelf)
                                .then(
                                        Commands.argument("target", EntityArgument.player())
                                                .executes(ModCommands::executeRtpOther)
                                )
                )
                .then(
                        // /drandomspawn getSpawn
                        Commands.literal("getSpawn")
                                .executes(ModCommands::executeGetSpawnSelf) // get own spawnpoint (does not require OP)
                                .then(
                                        // get others spawnpoint (requires OP)
                                        Commands.argument("target", EntityArgument.player())
                                                .requires(source -> source.hasPermission(2))
                                                .executes(ModCommands::executeGetSpawnOther)
                                )
                )
        );
    }

    private static int executeRtpSelf(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        final ServerPlayer player = context.getSource().getPlayerOrException();
        return rtpPlayer(context, player);
    }

    private static int executeRtpOther(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        final ServerPlayer player = EntityArgument.getPlayer(context, "target");
        return rtpPlayer(context, player);
    }

    /**
     * Helper method to execute the rtp logic for a given player.
     * This method now uses the asynchronous search and provides callbacks for success or failure.
     */
    private static int rtpPlayer(CommandContext<CommandSourceStack> context, ServerPlayer player) {
        final CommandSourceStack source = context.getSource();

        // Give the user immediate feedback that the search has started.
        source.sendSuccess(() -> Component.translatable("info.drandomspawn.rtp.start.for", player.getDisplayName()), false);

        // Define what happens on a successful search.
        // This will be executed on the main server thread later.
        Consumer<BlockPos> onSuccess = (foundPos) -> {
            // Teleport the player, save their new spawn, and send a success message.
            player.teleportTo(foundPos.getX() + 0.5, foundPos.getY(), foundPos.getZ() + 0.5);
            RandomSpawn.savePlayerSpawn(player, foundPos);
            source.sendSuccess(() -> Component.translatable("info.drandomspawn.rtp.success.for", player.getDisplayName()), true);
        };

        // Define what happens on a failed search.
        Runnable onFail = () -> {
            source.sendFailure(Component.translatable("info.drandomspawn.rtp.fail.for", player.getDisplayName()));
        };

        // Start the asynchronous search. This method returns immediately.
        RandomSpawn.findAndTeleportPlayerAsync(player, onSuccess, onFail);

        // The command itself successfully started the process, so we return 1.
        // The final success/fail message is handled by the callbacks.
        return 1;
    }

    private static int executeGetSpawnSelf(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        return getAndSendSpawnPoint(context, player);
    }

    private static int executeGetSpawnOther(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(context, "target");
        return getAndSendSpawnPoint(context, player);
    }

    // This helper method is unchanged as it only reads data.
    private static int getAndSendSpawnPoint(CommandContext<CommandSourceStack> context, ServerPlayer player) {
        CommandSourceStack source = context.getSource();
        CompoundTag data = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        if (data.contains(RandomSpawn.NBT_KEY_SPAWN_X)) {
            int x = data.getInt(RandomSpawn.NBT_KEY_SPAWN_X);
            int y = data.getInt(RandomSpawn.NBT_KEY_SPAWN_Y);
            int z = data.getInt(RandomSpawn.NBT_KEY_SPAWN_Z);

            Component message = Component.translatable("info.drandomspawn.getspawn.success", player.getDisplayName(), x, y, z);
            source.sendSuccess(() -> message, false);
        } else {
            Component message = Component.translatable("info.drandomspawn.getspawn.fail", player.getDisplayName());
            source.sendFailure(message);
        }
        return 1; // Returning 1 is fine as the command itself always executes correctly.
    }
    }
