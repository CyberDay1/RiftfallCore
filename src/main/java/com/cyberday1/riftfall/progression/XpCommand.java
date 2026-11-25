package com.cyberday1.riftfall.progression;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.arguments.LongArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;

/** FTB command rewards run as the claimant with an explicitly elevated permission level. */
public final class XpCommand {
    private XpCommand() {}

    public static LiteralArgumentBuilder<CommandSourceStack> subtree() {
        return Commands.literal("xp")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("add")
                        .then(Commands.argument("amount", LongArgumentType.longArg(1))
                                .executes(context -> {
                                    ServerPlayer player = context.getSource().getPlayerOrException();
                                    XpAwards.award(context.getSource().getServer(), player.getUUID(),
                                            LongArgumentType.getLong(context, "amount"));
                                    return 1;
                                })));
    }
}
