package com.cyberday1.riftfall.boss;

import com.cyberday1.riftfall.init.RiftfallTags;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

/**
 * The half of a shut boss door that a door cannot do: a portal entity does not walk through a
 * doorway.
 *
 * <p>
 * The arena is a closed box once the gate is up &mdash; one aperture, covered cell for cell &mdash;
 * and the blink spells clip short on the bars. What crosses it is a portal entity, which teleports on
 * a bounding-box overlap with no geometric test and fires nothing anyone can cancel. So the answer is
 * the source rather than the crossing: the ones standing in the room go when the door shuts, and no
 * more are admitted while it is shut.
 *
 * <p>
 * ⚠ It does not reach a {@code randomTeleport} effect, which tests only where you land.
 */
public final class ArenaSeal {

    private ArenaSeal() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(ArenaSeal::refuseToAdmit);
    }

    /**
     * Discards every banished entity standing in the arena, and answers how many went.
     *
     * <p>
     * Called by {@link BossGate} on the tick the last row goes in, which is the one moment that is
     * both "the room is now sealed" and "the level is already in hand". Nothing is asked for that the
     * gate has not already paid for: the room's extent comes off the plan rather than off blocks.
     */
    public static int sweep(ServerLevel rift) {
        BoundingBox room = RiftBossArena.room(rift);
        if (room == null) {
            return 0;
        }
        int gone = 0;
        for (Entity entity : rift.getEntities((Entity) null, box(room), ArenaSeal::banished)) {
            entity.discard();
            gone++;
        }
        return gone;
    }

    /** Whether this entity is one a sealed arena will not hold. */
    static boolean banished(Entity entity) {
        return entity.getType().is(RiftfallTags.BANISHED_FROM_A_SEALED_ARENA);
    }

    /**
     * Turns a room's block extent into the volume an entity is looked for in. Inclusive of the far
     * corner, which {@code BoundingBox} is and {@code AABB} is not.
     */
    static AABB box(BoundingBox room) {
        return new AABB(room.minX(), room.minY(), room.minZ(),
                room.maxX() + 1.0, room.maxY() + 1.0, room.maxZ() + 1.0);
    }

    /**
     * Refuses to let one into a room whose door has already shut.
     *
     * <p>
     * The sweep alone would only hold until somebody cast a second portal through the bars. Ordered
     * cheapest first: almost every entity that joins a level in this game is answered by one hash
     * lookup on a set that is empty until a fight starts.
     */
    private static void refuseToAdmit(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel rift)
                || !BossGate.sealed(rift)
                || !banished(event.getEntity())) {
            return;
        }
        BoundingBox room = RiftBossArena.room(rift);
        if (room != null && room.isInside(event.getEntity().blockPosition())) {
            event.setCanceled(true);
        }
    }
}
