package com.cyberday1.riftfall.boss;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import java.util.Locale;
import static com.cyberday1.riftfall.boss.UnseatedEncounter.*;

/** Explicit operator controls for the summon-only prototype. */
public final class UnseatedCommands {
    private UnseatedCommands() {}
    public static void register(){NeoForge.EVENT_BUS.addListener(UnseatedCommands::commands);}
    private static void commands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("riftfall").then(Commands.literal("unseated")
            .then(Commands.literal("guide").executes(c->{
                var p=c.getSource().getPlayerOrException();
                p.sendSystemMessage(Component.translatable(UnseatedText.GUIDE_INTRO));
                for(var a:new Action[]{Action.SWEEP,Action.PULSE,Action.PULL})p.sendSystemMessage(Component.translatable(UnseatedText.TELEGRAPH,UnseatedText.attack(a),UnseatedText.response(a)));
                // F6 and F7: what comes after the trials, each attack with its answer.
                p.sendSystemMessage(Component.translatable(UnseatedText.GUIDE_LATER));
                for(var a:new Action[]{Action.CROWN,Action.THRONE_FALL,Action.RESEAT,Action.REACH_RUPTURE,Action.SWEEP_CROWN})
                    p.sendSystemMessage(Component.translatable(UnseatedText.TELEGRAPH,UnseatedText.attack(a),UnseatedText.response(a)));
                p.sendSystemMessage(Component.translatable(UnseatedText.GUIDE_BINDING));
                p.sendSystemMessage(Component.translatable(UnseatedText.GUIDE_PRACTICE));
                return 1;
            }))
            // Entry stays operator-only until the campaign's start control exists; the practice attempt follows it.
            .then(Commands.literal("practice").requires(s->s.hasPermission(2))
                .executes(c->enter(c.getSource(),java.util.List.of(c.getSource().getPlayerOrException()),true))
                .then(Commands.argument("players",EntityArgument.players())
                    .executes(c->enter(c.getSource(),EntityArgument.getPlayers(c,"players"),true))))
            .then(Commands.literal("verify").requires(s->s.hasPermission(2)).executes(c->UnseatedArena.verify(c.getSource())))
            .then(Commands.literal("enter").requires(s->s.hasPermission(2))
                .executes(c->enter(c.getSource(),java.util.List.of(c.getSource().getPlayerOrException()),false))
                .then(Commands.argument("players",EntityArgument.players())
                    .executes(c->enter(c.getSource(),EntityArgument.getPlayers(c,"players"),false))))
            .then(Commands.literal("leave").executes(c->UnseatedArena.leave(c.getSource().getPlayerOrException())?1:0))));
        var root=Commands.literal("riftfall").then(Commands.literal("unseated")
            .then(Commands.literal("animate").requires(s->s.hasPermission(2)).then(Commands.argument("targets",EntityArgument.entities())
                .then(Commands.argument("action",StringArgumentType.word()).suggests((c,b)->{
                    for(var a:Action.values())b.suggest(a.name().toLowerCase(Locale.ROOT));return b.buildFuture();
                }).executes(c->{
                    Action action;
                    try{action=Action.valueOf(StringArgumentType.getString(c,"action").toUpperCase(Locale.ROOT));}
                    catch(IllegalArgumentException e){c.getSource().sendFailure(Component.literal("Unknown Unseated animation."));return 0;}
                    int count=0;
                    for(var entity:EntityArgument.getEntities(c,"targets"))if(entity instanceof UnseatedEntity boss && !boss.engaged()){
                        boss.preview(action);count++;
                    }
                    c.getSource().sendSuccess(()->Component.literal("Animation applied to dormant Unseated entities."),false);return count;
                }))))
            .then(Commands.literal("phase").requires(s->s.hasPermission(2)).then(Commands.argument("targets",EntityArgument.entities())
                .then(Commands.argument("phase",StringArgumentType.word()).suggests((c,b)->{
                    for(var p:Phase.values())b.suggest(p.name().toLowerCase(Locale.ROOT));return b.buildFuture();
                }).executes(c->{
                    Phase phase;
                    try{phase=Phase.valueOf(StringArgumentType.getString(c,"phase").toUpperCase(Locale.ROOT));}
                    catch(IllegalArgumentException e){c.getSource().sendFailure(Component.literal("Unknown Unseated phase."));return 0;}
                    int count=0;
                    for(var entity:EntityArgument.getEntities(c,"targets"))if(entity instanceof UnseatedEntity boss){boss.developmentPhase(phase);count++;}
                    return count;
                }))))
            .then(Commands.literal("reset").requires(s->s.hasPermission(2)).then(Commands.argument("targets",EntityArgument.entities()).executes(c->{
                int count=0;for(var entity:EntityArgument.getEntities(c,"targets"))if(entity instanceof UnseatedEntity boss){boss.resetEncounter();count++;}return count;
            }))));
        event.getDispatcher().register(root);
    }
    private static int enter(net.minecraft.commands.CommandSourceStack source,java.util.Collection<net.minecraft.server.level.ServerPlayer> players,boolean practiceOnly){
        try{return UnseatedArena.enter(source.getServer(),players,practiceOnly);}
        catch(IllegalArgumentException | IllegalStateException e){source.sendFailure(Component.literal(e.getMessage()));return 0;}
    }
}
