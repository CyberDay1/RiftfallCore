package com.cyberday1.riftfall.lore;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;

/**
 * One chat line to each player in the rift when its defender falls. Before the outpost note (point 9) it is only
 * the sky creaking; after it, the line names the core the boss was guarding, from the same names as the shard.
 */
public final class BossDeathLine {
    public static final String BEFORE = "lore.riftfall.boss_fell.before";
    public static final String AFTER = "lore.riftfall.boss_fell.after";
    /** The midpoint note: once read, a kill reads as what it is. */
    public static final String WITNESS_NOTE = "05_forsaken_outpost_1";

    private BossDeathLine() {}

    public static void fell(ServerLevel rift, Mob boss) {
        ShardOrigin.at(rift, boss).ifPresent(origin -> {
            for (ServerPlayer player : java.util.List.copyOf(rift.players())) player.sendSystemMessage(line(player, boss, origin));
        });
    }

    public static Component line(ServerPlayer player, Mob boss, ShardOrigin origin) {
        boolean knows = NoteFinds.found(player.server, player.getUUID(), WITNESS_NOTE);
        return line(knows, boss.getName(), origin);
    }

    /** The line names the defender by its theme's epithet; a theme without one keeps the real name. */
    public static Component line(boolean knows, Component realName, ShardOrigin origin) {
        return Component.translatable(knows ? AFTER : BEFORE, DefenderNames.of(origin, true).orElse(realName), origin.core())
                .withStyle(ChatFormatting.GRAY);
    }
}
