package com.cyberday1.riftfall.rift;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.GameProfileArgument;

/**
 * {@code /riftfall party}: the command tree only. Every verb is a {@link PartyActions} method, which the
 * Party bookmark calls too. Everything here is permission 0.
 */
public final class PartyCommand {

    private PartyCommand() {
    }

    /**
     * The subtree, for {@link RiftCommand} to hang under the one {@code /riftfall} root.
     *
     * <p>
     * Registered from there rather than as a root of its own: Brigadier merges two identically named
     * roots by keeping the first and dropping the second one's {@code requires}.
     */
    static LiteralArgumentBuilder<CommandSourceStack> subtree() {
        return Commands.literal("party")
                .executes(context -> PartyActions.show(context.getSource()))
                .then(Commands.literal("create")
                        .executes(context -> PartyActions.create(context.getSource())))
                .then(Commands.literal("invite")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> PartyActions.invite(context.getSource(),
                                        EntityArgument.getPlayer(context, "player")))))
                .then(Commands.literal("accept")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> PartyActions.answer(context.getSource(),
                                        EntityArgument.getPlayer(context, "player"), true))))
                .then(Commands.literal("decline")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> PartyActions.answer(context.getSource(),
                                        EntityArgument.getPlayer(context, "player"), false))))
                .then(Commands.literal("invites")
                        .executes(context -> PartyActions.invites(context.getSource())))
                .then(Commands.literal("leave")
                        .executes(context -> PartyActions.leave(context.getSource())))
                .then(Commands.literal("remove")
                        .then(Commands.argument("player", GameProfileArgument.gameProfile())
                                .executes(context -> PartyActions.remove(context.getSource(),
                                        GameProfileArgument.getGameProfiles(context, "player")))))
                .then(Commands.literal("disband")
                        .executes(context -> PartyActions.disband(context.getSource())));
    }
}
