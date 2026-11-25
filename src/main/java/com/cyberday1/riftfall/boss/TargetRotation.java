package com.cyberday1.riftfall.boss;

import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;
import java.util.function.IntUnaryOperator;

/**
 * Who a boss swings at next: every swing goes to one player, and after it lands the boss draws its
 * next target at random from everybody it could fight. Damage and threat do not enter the draw, so
 * over a fight every body in the room is hit about equally often, one swing at a time. A taunt pins
 * the draw to one player for {@link #TAUNT_TICKS}.
 *
 * <p>
 * Touches no Minecraft class, so the rule is tested with nothing but names and a counter.
 */
public final class TargetRotation {

    /**
     * The shortest time a boss keeps a target, in ticks, however many hits land. Stops a multi-hit
     * attack re-rolling on every sub-hit and a boss that restarts its pathing twenty times a second.
     */
    static final int MIN_DWELL_TICKS = 20;

    /**
     * The longest time a boss keeps a target without landing a hit, in ticks. Without it a kited or
     * shielded target would hold the boss for the whole fight and sharing would stop.
     */
    static final int MAX_DWELL_TICKS = 100;

    /**
     * How long a taunt pins the boss, in ticks. Equal to the rift flare's cooldown, so one player can
     * hold a boss without a break at the price of one flare every eight seconds.
     */
    static final int TAUNT_TICKS = 160;

    @Nullable
    private UUID holder;
    private int pickedAt;
    private boolean swung;
    @Nullable
    private UUID taunter;
    private int tauntUntil;
    private int draws;

    /**
     * The target for this tick.
     *
     * @param now        a clock that only moves forward, in ticks
     * @param candidates everybody the boss could fight right now; empty means nobody
     * @param draw       given a bound, returns an index in {@code [0, bound)}
     */
    @Nullable
    public UUID next(int now, List<UUID> candidates, IntUnaryOperator draw) {
        if (candidates.isEmpty()) {
            holder = null;
            return null;
        }
        if (taunter != null) {
            if (now < tauntUntil) {
                if (candidates.contains(taunter)) {
                    holder = taunter;
                    return holder;
                }
            } else {
                // Expired: the taunter gets no extra dwell out of it, the boss draws again at once.
                taunter = null;
                holder = null;
            }
        }
        if (holder != null && candidates.contains(holder)) {
            int dwell = now - pickedAt;
            if (dwell < MIN_DWELL_TICKS || (!swung && dwell < MAX_DWELL_TICKS)) {
                return holder;
            }
        }
        // With replacement: the same player can be drawn twice running. The long-run shares still
        // come out equal; the repeats are what keeps deaths staggered.
        holder = candidates.get(draw.applyAsInt(candidates.size()));
        pickedAt = now;
        swung = false;
        draws++;
        return holder;
    }

    /** A hit from this boss landed on somebody, which ends the swing. */
    public void swung() {
        swung = true;
    }

    /** Pins the boss to this player for {@link #TAUNT_TICKS}, from {@code now}. */
    public void taunt(UUID player, int now) {
        taunter = player;
        tauntUntil = now + TAUNT_TICKS;
        holder = player;
        pickedAt = now;
        swung = false;
    }

    /** Lets go of the current target. A taunt still running is kept. */
    public void release() {
        holder = null;
    }

    /** Whoever was last chosen, or null. */
    @Nullable
    public UUID holder() {
        return holder;
    }

    /** The taunting player, while a taunt is running at {@code now}. */
    @Nullable
    public UUID taunter(int now) {
        return taunter != null && now < tauntUntil ? taunter : null;
    }

    /** How many random draws this boss has made. For the probe. */
    public int draws() {
        return draws;
    }
}
