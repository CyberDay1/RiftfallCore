package com.cyberday1.riftfall.skill;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * The only timer in the skill system, keyed by player and by <em>node</em>.
 *
 * <p>Keying by node rather than by the ability it casts is the ruling that two nodes may never
 * share a cooldown, held structurally: two nodes naming one ability get two timers because they
 * are two keys. Spell Engine's free cast path imposes no cooldown of its own, so there is no
 * second timer to reconcile with.
 *
 * <p>A timer is the world game time the node is usable again. While a server runs the map is
 * {@link SkillCooldownStore}'s, so a relog or a restart keeps every timer (CD1); without one it is
 * in memory, which is what the unit tests use.
 */
public final class SkillCooldowns {

    public static boolean bypasses(net.minecraft.world.entity.LivingEntity caster) {
        return caster instanceof net.minecraft.world.entity.player.Player player && player.isCreative();
    }

    /** Creative ignores existing timers without deleting a survival timer on a mode change. */
    public static long readyAt(net.minecraft.world.entity.LivingEntity caster, String nodeId) {
        return bypasses(caster) ? 0L : readyAt(caster.getUUID(), nodeId);
    }

    private static final Runnable NOTHING = () -> {
    };

    /** player -> node id -> the game time at which that node is usable again. */
    private static volatile Map<UUID, Map<String, Long>> readyAt = new ConcurrentHashMap<>();
    private static volatile Runnable changed = NOTHING;

    private SkillCooldowns() {
    }

    /** Timers now live in {@code store}, and {@code changed} runs after every write. Server start. */
    static void use(Map<UUID, Map<String, Long>> store, Runnable onChange) {
        readyAt = store;
        changed = onChange;
    }

    /** Back to a fresh in-memory map. Server stop, so one world's timers never reach the next. */
    static void forget() {
        use(new ConcurrentHashMap<>(), NOTHING);
    }

    /** Whether the timers are {@code store}'s map, which the rig reads to prove the boot attached it. */
    static boolean backedBy(Map<UUID, Map<String, Long>> store) {
        return readyAt == store;
    }

    public static boolean ready(UUID player, String nodeId, long gameTime) {
        return remaining(player, nodeId, gameTime) <= 0L;
    }

    /** Ticks left before the node is usable, or 0 once it is. */
    public static long remaining(UUID player, String nodeId, long gameTime) {
        long at = readyAt(player, nodeId);
        return Math.max(0L, at - gameTime);
    }

    /** The game time the node is usable again, or 0 if it has never been cast. */
    public static long readyAt(UUID player, String nodeId) {
        Map<String, Long> mine = readyAt.get(player);
        if (mine == null) {
            return 0L;
        }
        Long at = mine.get(nodeId);
        return at == null ? 0L : at;
    }

    public static void start(UUID player, String nodeId, long gameTime, int ticks) {
        if (ticks <= 0) {
            return;
        }
        readyAt.computeIfAbsent(player, p -> new HashMap<>()).put(nodeId, gameTime + ticks);
        changed.run();
    }

    /**
     * Takes {@code ticks} off every running timer of one player that {@code which} accepts, never past
     * {@code gameTime}. Returns the node ids it moved, in no order. A passive's hasten impact (Rhythm).
     */
    public static List<String> shorten(UUID player, long gameTime, int ticks, Predicate<String> which) {
        Map<String, Long> mine = readyAt.get(player);
        if (mine == null || ticks <= 0) {
            return List.of();
        }
        List<String> moved = new ArrayList<>();
        for (Map.Entry<String, Long> timer : mine.entrySet()) {
            long at = timer.getValue();
            if (at > gameTime && which.test(timer.getKey())) {
                timer.setValue(Math.max(gameTime, at - ticks));
                moved.add(timer.getKey());
            }
        }
        if (!moved.isEmpty()) {
            changed.run();
        }
        return moved;
    }

    /** Drops every timer for one player, and nobody else's. */
    public static void clear(UUID player) {
        if (readyAt.remove(player) != null) {
            changed.run();
        }
    }

    /** Drops everything. Test and probe use; a running server has no reason to call it. */
    public static void clearAll() {
        readyAt.clear();
        changed.run();
    }
}
