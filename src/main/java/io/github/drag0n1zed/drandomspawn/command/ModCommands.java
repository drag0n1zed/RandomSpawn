package io.github.drag0n1zed.drandomspawn.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github.drag0n1zed.drandomspawn.RandomSpawn;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

public class ModCommands {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        // Main command literal
        dispatcher.register(Commands.literal("drandomspawn")
                .then(
                        // /drandomspawn rtp
                        Commands.literal("rtp")
                                .requires(source -> source.hasPermission(2)) // Requires OP
                                .executes(ModCommands::executeRtp)
                )
                .then(
                        // /drandomspawn spawn
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

    private static int executeRtp(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        // Get the player who executed the command. Throws an error if the source is not a player (e.g., console).
        ServerPlayer player = context.getSource().getPlayerOrException();

        boolean success = RandomSpawn.teleportPlayerToRandomLocation(player);

        if (success) {
            context.getSource().sendSuccess(() -> Component.translatable("info.drandomspawn.system.rtpsuccess"), true);
        } else {
            context.getSource().sendFailure(Component.translatable("info.drandomspawn.system.rtpfail"));
        }

        // Return 1 for success, 0 for failure.
        return success ? 1 : 0;
    }

    private static int executeGetSpawnSelf(CommandContext<CommandSourceStack> context) throws CommandSyntaxException{
        ServerPlayer player = context.getSource().getPlayerOrException();
        return getAndSendSpawnPoint(context, player);
    }

    private static int executeGetSpawnOther(CommandContext<CommandSourceStack> context) throws CommandSyntaxException{
        ServerPlayer player = EntityArgument.getPlayer(context, "target");
        return getAndSendSpawnPoint(context, player);
    }

    // Helper for getting spawn point
    private static int getAndSendSpawnPoint(CommandContext<CommandSourceStack> context, ServerPlayer player) {
        CommandSourceStack source = context.getSource();
        CompoundTag data = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        if (data.contains(RandomSpawn.NBT_KEY_SPAWN_X)) {
            int x = data.getInt(RandomSpawn.NBT_KEY_SPAWN_X);
            int y = data.getInt(RandomSpawn.NBT_KEY_SPAWN_Y);
            int z = data.getInt(RandomSpawn.NBT_KEY_SPAWN_Z);
            Component message = Component.translatable("info.drandomspawn.getspawn.success", player.getDisplayName(), x, y, z);
            source.sendSuccess(() -> message, false);
            return 1;
        }
        Component message = Component.translatable("info.drandomspawn.getspawn.fail", player.getDisplayName());
        source.sendFailure(message);
        return 0;
    }
}