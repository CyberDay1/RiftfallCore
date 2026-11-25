package com.cyberday1.riftfall.boss;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * One boss's record of what everybody fighting it has contributed. It says whether the fight has
 * started and feeds {@link ThreatRing}; it does not choose targets, {@link TargetRotation} does.
 *
 * <p>
 * Arithmetic over a map, touching no Minecraft class. What leaves it is {@link #shareOfTop}, a 0..1
 * fraction, not the raw total.
 */
public final class ThreatTable {

    /**
     * The fraction of every player's threat that one {@link #decay} call removes. The other half of
     * the rate is {@link RiftBosses#DECAY_INTERVAL}; tune this one.
     */
    static final double DECAY_PER_STEP = 0.02;

    /**
     * The threat below which a player is dropped from the table entirely. Decay is proportional, so
     * without a floor it approaches zero and never arrives. Keep it well under one hit's worth.
     */
    static final double FLOOR = 1.0;

    /**
     * How far above the current top a taunt puts the taunting player, so the ring shows who pulled.
     * The hold itself is {@link TargetRotation#taunt}.
     */
    static final double TAUNT_HEADROOM = 1.25;

    /** Insertion-ordered, so a tie for the top goes to whoever landed on the table first. */
    private final Map<UUID, Double> threat = new LinkedHashMap<>();

    /** Adds threat. Non-positive amounts are ignored rather than allowed to subtract. */
    public void add(UUID player, double amount) {
        if (amount > 0.0) {
            threat.merge(player, amount, Double::sum);
        }
    }

    /** This player's threat, or zero for anybody not on the table. */
    public double of(UUID player) {
        return threat.getOrDefault(player, 0.0);
    }

    /** Whoever has the most threat, or null if nobody has any. */
    @Nullable
    public UUID top() {
        UUID best = null;
        double bestThreat = 0.0;
        for (Map.Entry<UUID, Double> entry : threat.entrySet()) {
            if (best == null || entry.getValue() > bestThreat) {
                best = entry.getKey();
                bestThreat = entry.getValue();
            }
        }
        return best;
    }

    /**
     * This player's threat as a fraction of the highest on the table, 0 to 1. An empty table answers
     * 0.0 for everyone rather than dividing by nothing, and that 0.0 is load-bearing: it is what
     * {@link ThreatRing} tests to give a player no ring at all.
     */
    public double shareOfTop(UUID player) {
        UUID best = top();
        if (best == null) {
            return 0.0;
        }
        double ceiling = of(best);
        return ceiling <= 0.0 ? 0.0 : of(player) / ceiling;
    }

    /**
     * Ages every entry by one step, and drops anybody who has faded past {@link #FLOOR}. Proportional
     * rather than a flat subtraction, so decay is a half-life instead of a stopwatch: a flat rate
     * empties the bottom of the table in seconds while barely touching the top.
     */
    public void decay() {
        Iterator<Map.Entry<UUID, Double>> entries = threat.entrySet().iterator();
        while (entries.hasNext()) {
            Map.Entry<UUID, Double> entry = entries.next();
            double left = entry.getValue() * (1.0 - DECAY_PER_STEP);
            if (left < FLOOR) {
                entries.remove();
            } else {
                entry.setValue(left);
            }
        }
    }

    /**
     * Takes a player off the table completely. One method for dead, gone home and disconnected: from
     * the boss's side they are the same fact. Not decay, which is for a player still in the fight.
     */
    public void forget(UUID player) {
        threat.remove(player);
    }

    /** Whether anybody is on the table at all. */
    public boolean isEmpty() {
        return threat.isEmpty();
    }

    /**
     * Everybody on the table, highest first. A copy: callers draw particles and query the level while
     * walking it, and either can reach back into the table through an event listener.
     */
    public List<UUID> ranked() {
        List<UUID> players = new ArrayList<>(threat.keySet());
        players.sort((a, b) -> Double.compare(of(b), of(a)));
        return players;
    }

    /**
     * Puts a player clear at the top of the table. Relative to the current leader rather than a fixed
     * number, so it still reads as the top in the tenth minute of a fight. See {@link RiftBosses#taunt}.
     */
    public void taunt(UUID player) {
        UUID best = top();
        double ceiling = best == null ? 0.0 : of(best);
        threat.put(player, Math.max(FLOOR, Math.max(of(player), ceiling * TAUNT_HEADROOM)));
    }
}
