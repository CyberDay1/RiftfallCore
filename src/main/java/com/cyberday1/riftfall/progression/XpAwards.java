package com.cyberday1.riftfall.progression;

import com.cyberday1.riftfall.network.RiftSheetPayload;
import com.cyberday1.riftfall.stat.RiftStats;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.UUID;

/**
 * What a rift pays, and where the XP is put.
 *
 * <p>
 * The clear and the kill both scale with the rift's tier through {@link #clearXp}, the one tier
 * term (ruled 2026-10-11). No ceiling: a total is bounded by the curve when it is read, not by
 * what may be banked. An award is a change to {@link RiftStats}, keyed by UUID, so the offline
 * case needs no machinery.
 */
public final class XpAwards {

    /** The clear's floor, and what a tier-1 clear pays. */
    public static final long PER_CLEAR = 1000L;

    /** What one kill is worth at tier 1; it scales with {@link #clearXp} / {@link #PER_CLEAR}. */
    public static final long PER_KILL = 5L;

    /** How much of their own kill XP a player who died still banks, as a percentage. */
    public static final long DEATH_PERCENT = 25L;

    private XpAwards() {
    }

    /**
     * What beating a tier-{@code tier} rift and bringing it home is worth. The baseline is half
     * the entry-level step, with a floor of {@link #PER_CLEAR}. Compact-dungeon pacing raises
     * tier 2 to 2500 and adds 50% from tier 8 onward, where the level curve becomes exponential.
     * Kill XP derives from this same award so combat keeps its share of progression.
     */
    public static long clearXp(int tier) {
        int entry = Math.min(RiftXp.CEILING, RiftTier.first(Math.max(1, tier)));
        if (tier == 2) {
            return 2500L;
        }
        long baseline = Math.max(PER_CLEAR, RiftXp.required(entry) / 2L);
        return tier >= 8 ? baseline + baseline / 2L : baseline;
    }

    /** How many clears a containing bind of the Unseated pays (ruled 2026-11-08). */
    public static final long UNSEATED_CLEARS = 3L;

    /** The Unseated's one-time award at the fight's tier. */
    public static long unseatedXp(int tier) {
        return UNSEATED_CLEARS * clearXp(tier);
    }

    /** What one kill in a tier-{@code tier} rift is worth, floored. */
    public static long perKill(int tier) {
        return PER_KILL * clearXp(tier) / PER_CLEAR;
    }

    /** What a number of tier-1 kills is worth before anything is taken off it. */
    public static long forKills(int kills) {
        return forKills(kills, 1);
    }

    /** What a number of kills in a tier-{@code tier} rift is worth, saturating. */
    public static long forKills(int kills, int tier) {
        if (kills <= 0) {
            return 0L;
        }
        long each = perKill(tier);
        return each > Long.MAX_VALUE / kills ? Long.MAX_VALUE : kills * each;
    }

    /**
     * What is left of that for a player who died: {@value #DEATH_PERCENT}% of it, floored.
     * A percentage rather than a fraction keeps the arithmetic in {@code long}, so the rounding
     * is a rule that can be stated rather than one that has to be discovered.
     */
    public static long afterDeath(long earned) {
        // Split so a saturated total cannot overflow; equal to earned * DEATH_PERCENT / 100.
        return earned <= 0L ? 0L : earned / 100L * DEATH_PERCENT + earned % 100L * DEATH_PERCENT / 100L;
    }

    /**
     * A total raised by an award, saturating rather than wrapping.
     *
     * <p>
     * ⛔ Arithmetic, not a design cap: a wrapped total is negative and {@link RiftStats#setXp}
     * throws on a negative. The level cap ({@link RiftXp#LEVEL_CAP}) bounds the level, not the
     * total: XP past it is banked as surplus.
     */
    public static long earned(long total, long gain) {
        if (gain <= 0L) {
            return total;
        }
        long raised = total + gain;
        return raised < total ? Long.MAX_VALUE : raised;
    }

    /**
     * Credits a player and answers the total they now hold. The player does not have to be there;
     * a record is only written when the total actually moves, and one who is online is told.
     */
    public static long award(MinecraftServer server, UUID player, long xp) {
        RiftStats stats = RiftStats.of(server);
        long before = stats.xpFor(player);
        long after = earned(before, xp);
        if (after != before) {
            stats.setXp(player, after);
            PlayerList online = server.getPlayerList();
            ServerPlayer there = online == null ? null : online.getPlayer(player);
            if (there != null) {
                PacketDistributor.sendToPlayer(there, new RiftSheetPayload(after, RiftXp.LEVEL_CAP));
                TabLevel.sync(there);
            }
        }
        return after;
    }
}
