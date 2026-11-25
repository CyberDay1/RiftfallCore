package com.cyberday1.riftfall.skill;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The enemies one cast struck, counted for a node whose pool is sized by them ({@link SkillNode.PerEnemy}). A cast
 * opens its caster's count, the {@code riftfall:enemy_struck} impact adds each enemy it lands on, and the cast closes
 * it once the engine returns. Spell Engine resolves an instant cast's targets inside that call, so the count is whole
 * by then. Each enemy counts once however many impacts land on it. Names no engine type.
 */
public final class EnemyHits {

    private static final Map<UUID, Set<UUID>> OPEN = new HashMap<>();
    private static long counted;

    private EnemyHits() {
    }

    /** Starts {@code caster}'s count, dropping any left over. */
    static void open(UUID caster) {
        OPEN.put(caster, new HashSet<>());
    }

    /** Ends {@code caster}'s count and answers it: 0 when none was open. */
    static int close(UUID caster) {
        Set<UUID> struck = OPEN.remove(caster);
        return struck == null ? 0 : struck.size();
    }

    /** {@code target} was struck by {@code caster}'s cast. False when no count is open or it was already counted. */
    static boolean struck(UUID caster, UUID target) {
        if (TravelCast.struck(caster, target)) { counted++; return true; }
        Set<UUID> struck = OPEN.get(caster);
        if (struck == null || caster.equals(target) || !struck.add(target)) {
            return false;
        }
        counted++;
        return true;
    }

    /** Enemies counted since boot. Probe reads. */
    public static long counted() {
        return counted;
    }
}
