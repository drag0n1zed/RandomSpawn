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
        dispatcher.register(Commands.literal("drandomspawn")
                .then(
                        Commands.literal("random_teleport")
                                .requires(source -> source.hasPermission(2))
                                .executes(ModCommands::executeRandomTeleportForSelf)
                                .then(
                                        Commands.argument("target", EntityArgument.player())
                                                .executes(ModCommands::executeRandomTeleportForOther)
                                )
                )
                .then(
                        Commands.literal("get_spawn")
                                .executes(ModCommands::executeGetSpawnForSelf)
                                .then(
                                        Commands.argument("target", EntityArgument.player())
                                                .requires(source -> source.hasPermission(2))
                                                .executes(ModCommands::executeGetSpawnForOther)
                                )
                )
        );
    }

    private static int executeRandomTeleportForSelf(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        final ServerPlayer player = context.getSource().getPlayerOrException();
        return performRandomTeleport(context, player);
    }

    private static int executeRandomTeleportForOther(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        final ServerPlayer player = EntityArgument.getPlayer(context, "target");
        return performRandomTeleport(context, player);
    }

    /**
     * Executes the random teleport logic for a given player.
     * This method uses the asynchronous search and provides callbacks for success or failure.
     */
    private static int performRandomTeleport(CommandContext<CommandSourceStack> context, ServerPlayer player) {
        final CommandSourceStack source = context.getSource();

        source.sendSuccess(() -> Component.translatable("info.drandomspawn.random_teleport.start.for", player.getDisplayName()), false);

        Consumer<BlockPos> onSuccess = (foundPos) -> {
            player.teleportTo(foundPos.getX() + 0.5, foundPos.getY(), foundPos.getZ() + 0.5);
            RandomSpawn.savePlayerSpawn(player, foundPos);
            source.sendSuccess(() -> Component.translatable("info.drandomspawn.random_teleport.success.for", player.getDisplayName()), true);
        };

        Runnable onFail = () -> {
            source.sendFailure(Component.translatable("info.drandomspawn.random_teleport.fail.for", player.getDisplayName()));
        };

        RandomSpawn.findSafeSpawnAndTeleportAsync(player, onSuccess, onFail);

        return 1;
    }

    private static int executeGetSpawnForSelf(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        return displayPlayerSpawnPoint(context, player);
    }

    private static int executeGetSpawnForOther(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(context, "target");
        return displayPlayerSpawnPoint(context, player);
    }

    /** Displays a player's saved spawn point. */
    private static int displayPlayerSpawnPoint(CommandContext<CommandSourceStack> context, ServerPlayer player) {
        CommandSourceStack source = context.getSource();
        CompoundTag data = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        if (data.contains(RandomSpawn.NBT_KEY_SPAWN_X)) {
            int x = data.getInt(RandomSpawn.NBT_KEY_SPAWN_X);
            int y = data.getInt(RandomSpawn.NBT_KEY_SPAWN_Y);
            int z = data.getInt(RandomSpawn.NBT_KEY_SPAWN_Z);

            Component message = Component.translatable("info.drandomspawn.get_spawn.success", player.getDisplayName(), x, y, z);
            source.sendSuccess(() -> message, false);
        } else {
            Component message = Component.translatable("info.drandomspawn.get_spawn.fail", player.getDisplayName());
            source.sendFailure(message);
        }
        return 1;
    }
}
