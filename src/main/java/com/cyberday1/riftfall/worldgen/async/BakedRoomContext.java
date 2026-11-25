package com.cyberday1.riftfall.worldgen.async;

import com.cyberday1.riftfall.theme.ResolvedTheme;
import com.cyberday1.riftfall.worldgen.barrel.BarrelRules;
import com.cyberday1.riftfall.worldgen.decorator.RoomFacts;
import com.cyberday1.riftfall.worldgen.decorator.RoomKind;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * Immutable snapshot of everything a {@link SectionProcessor} needs.
 *
 * <p>
 * Built once per room before any section work begins, then shared across threads. Every field
 * is either primitive, immutable, or safe to read concurrently because processors never write
 * back through it.
 */
public record BakedRoomContext(
        long riftSeed,
        long worldSeed,
        RoomKind roomKind,
        BarrelRules barrelRules,
        BoundingBox clip,
        ResolvedTheme resolvedTheme,
        boolean convertSpawners,
        boolean ignoreEntities,
        RoomFacts facts
) {
    /** Without facts: tier 1, nothing reserved. */
    public BakedRoomContext(long riftSeed, long worldSeed, RoomKind roomKind, BarrelRules barrelRules,
                            BoundingBox clip, ResolvedTheme resolvedTheme, boolean convertSpawners,
                            boolean ignoreEntities) {
        this(riftSeed, worldSeed, roomKind, barrelRules, clip, resolvedTheme, convertSpawners, ignoreEntities,
                RoomFacts.NONE);
    }
}