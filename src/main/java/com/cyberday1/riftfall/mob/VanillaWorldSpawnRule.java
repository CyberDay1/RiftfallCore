package com.cyberday1.riftfall.mob;

import net.minecraft.world.entity.MobSpawnType;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.Set;

/** Which spawns {@link VanillaWorldSpawns} refuses: the decision alone, no world state. */
public final class VanillaWorldSpawnRule {

    /** Spawns the world hands out on its own: biomes, structures, spawner blocks, patrols, events, riders. */
    public static final Set<MobSpawnType> REFUSED_TYPES = EnumSet.of(MobSpawnType.NATURAL,
            MobSpawnType.CHUNK_GENERATION, MobSpawnType.STRUCTURE, MobSpawnType.PATROL, MobSpawnType.SPAWNER,
            MobSpawnType.TRIAL_SPAWNER, MobSpawnType.EVENT, MobSpawnType.JOCKEY, MobSpawnType.REINFORCEMENT);

    /** Namespaces whose mobs are the pack's own and never refused: vanilla, and Riftfall itself. */
    private static final Set<String> OWN = Set.of("minecraft", "riftfall");

    private VanillaWorldSpawnRule() {
    }

    /** On unless the switch reads {@code off}. */
    public static boolean enforced(@Nullable String property) {
        return !"off".equalsIgnoreCase(property);
    }

    /** No spawn type is a mob added without finalization: a skill summon, a projectile-like body. */
    public static boolean refusedType(@Nullable MobSpawnType type) {
        return type != null && REFUSED_TYPES.contains(type);
    }

    /** {@code allowed} beats {@code suppressed}; otherwise a modded monster-category or {@code Enemy} mob. */
    public static boolean hostile(String namespace, boolean monster, boolean enemy, boolean allowed,
            boolean suppressed) {
        if (allowed) return false;
        if (suppressed) return true;
        return !OWN.contains(namespace) && (monster || enemy);
    }

    /** {@code vanillaWorld}: the overworld, the Nether or the End. */
    public static boolean refuses(boolean vanillaWorld, @Nullable MobSpawnType type, String namespace, boolean monster,
            boolean enemy, boolean allowed, boolean suppressed) {
        return vanillaWorld && refusedType(type) && hostile(namespace, monster, enemy, allowed, suppressed);
    }
}
