package com.cyberday1.riftfall.boss;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.joml.Vector3f;

import java.util.UUID;

/**
 * The threat table, drawn on the floor as a ring of dust at each threatened player's feet. In the
 * world rather than on a HUD so the whole party reads each other's standing at a glance, which is the
 * only way four people trade aggro deliberately.
 *
 * <p>
 * All of it is {@code ServerLevel#sendParticles}, so there is no client class, no payload and nothing
 * to keep in step, and the readout works on a vanilla client. The holder gets a hue off the ramp
 * entirely plus a second inner ring, because a pure brightness ramp goes blind at 100 threat against
 * 99 -- exactly the moment the subsystem exists for.
 */
public final class ThreatRing {

    /**
     * How often the rings are re-emitted, in ticks. ⚠ Dust particles live about a second, so much
     * above 20 and the ring is gone between emissions.
     */
    static final int INTERVAL = 10;

    /** The radius of the outer ring, in blocks. Raise it and rings start overlapping in melee. */
    static final double RADIUS = 1.0;

    /** The radius of the holder's second, inner ring. Kept well inside {@link #RADIUS} to read as separate. */
    static final double INNER_RADIUS = 0.45;

    /** How far above the player's feet the rings sit, in blocks. Enough to clear a slab seam, not enough to float. */
    static final double HEIGHT = 0.08;

    /** Particles in the ring of a player who has only just made the table. Lower it and low threat costs less. */
    static final int MIN_POINTS = 5;

    /** Particles in the ring of the player at the top. Raising it makes the top of the table louder and dearer. */
    static final int MAX_POINTS = 18;

    /** Particle size at the bottom of the table. */
    static final float MIN_SCALE = 0.7F;

    /** Particle size at the top. Raising the gap between the two makes share readable from further off. */
    static final float MAX_SCALE = 1.3F;

    /** The colour of a ring at the bottom of the table: cold, dim, easy to ignore. */
    private static final Vector3f COLD = new Vector3f(0.26F, 0.30F, 0.38F);

    /** The colour of a ring approaching the top: an ember, still short of the holder's. */
    private static final Vector3f HOT = new Vector3f(0.85F, 0.36F, 0.14F);

    /** The holder's colour. Off the {@link #COLD}-to-{@link #HOT} ramp entirely, so it cannot be mistaken for a step along it. */
    private static final Vector3f HELD = new Vector3f(0.98F, 0.86F, 0.45F);

    private ThreatRing() {
    }

    /**
     * Draws one boss's whole table. The holder is read off the mob, not the table, since the rotation
     * picks targets the table knows nothing about; a holder with no threat at all still gets the
     * holder's ring. Anybody else at zero share is not drawn.
     */
    static void draw(ServerLevel level, Mob boss, ThreatTable threat) {
        LivingEntity target = boss.getTarget();
        UUID held = target == null ? null : target.getUUID();
        boolean heldDrawn = false;
        for (UUID id : threat.ranked()) {
            double share = threat.shareOfTop(id);
            if (share > 0.0 && level.getEntity(id) instanceof ServerPlayer player) {
                draw(level, player, share, id.equals(held));
                heldDrawn |= id.equals(held);
            }
        }
        if (!heldDrawn && target instanceof ServerPlayer player) {
            draw(level, player, 0.0, true);
        }
    }

    /**
     * Draws one player's ring. Share drives colour, particle size and particle count together, so the
     * cue survives being seen badly: a ring glimpsed across a dark room still reads as dense or sparse
     * once its colour is lost.
     */
    private static void draw(ServerLevel level, ServerPlayer player, double share, boolean holder) {
        float t = (float) Mth.clamp(share, 0.0, 1.0);
        DustParticleOptions dust = new DustParticleOptions(
                holder ? HELD : COLD.lerp(HOT, t, new Vector3f()),
                Mth.lerp(t, MIN_SCALE, MAX_SCALE));
        ring(level, player, RADIUS, Math.round(Mth.lerp(t, MIN_POINTS, MAX_POINTS)), dust);
        if (holder) {
            ring(level, player, INNER_RADIUS, MIN_POINTS, dust);
        }
    }

    /**
     * One circle of dust around a player's feet, a particle at a time with count 1 and zero spread.
     * ⚠ The spread arguments of {@code sendParticles} are a random offset, so asking for eighteen
     * particles in one call gives a cloud rather than a circle.
     */
    private static void ring(ServerLevel level, ServerPlayer player, double radius, int points,
            DustParticleOptions dust) {
        for (int i = 0; i < points; i++) {
            double angle = (Math.PI * 2.0 * i) / points;
            level.sendParticles(dust,
                    player.getX() + Math.cos(angle) * radius,
                    player.getY() + HEIGHT,
                    player.getZ() + Math.sin(angle) * radius,
                    1, 0.0, 0.0, 0.0, 0.0);
        }
    }
}
