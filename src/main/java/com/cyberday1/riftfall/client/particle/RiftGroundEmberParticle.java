package com.cyberday1.riftfall.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;

/**
 * A spark that reached the floor, skittering outward along it until it stops.
 *
 * <p>
 * Colour and fade are {@link RiftEmberParticle}'s untouched: it is a spark off the same rip. Only the
 * motion differs, and only by three numbers &mdash; no gravity, more drag, and a longer life, so it
 * hugs the ground and comes to rest rather than being deleted mid-slide.
 *
 * <p>
 * {@link #reach} is the whole design. Where the sparks stop has to agree with where the decal's
 * cracks end, and neither the code nor a screenshot can tell you that, so it is a closed form and a
 * test.
 */
public class RiftGroundEmberParticle extends RiftEmberParticle {

    /** Kept per tick. Lower than an airborne ember's, or the slide runs out past the decal. */
    private static final float DRAG = 0.88F;

    /**
     * Ticks. Fixed rather than the base class's 16-to-27: at 16 a fast ember is still moving 0.025
     * blocks a tick when it is removed, which is a cut-off and not a stop.
     */
    public static final int SLIDE_TICKS = 30;

    RiftGroundEmberParticle(ClientLevel level, double x, double y, double z,
                            double dx, double dy, double dz) {
        super(level, x, y, z, dx, dy, dz);
        this.gravity = 0.0F;
        this.friction = DRAG;
        this.lifetime = SLIDE_TICKS;
    }

    /** What is left of a speed after one tick of drag. */
    public static double slow(double speed) {
        return speed * DRAG;
    }

    /**
     * How far an ember born at {@code birthSpeed} travels before it dies, in blocks.
     *
     * <p>
     * {@code Particle#tick} moves by the velocity and then applies the drag, so the steps are a
     * geometric series over the lifetime. Static and argument-only so the number can be checked
     * without a game running, as {@link RiftVortexParticle#turn} is.
     */
    public static double reach(double birthSpeed) {
        return birthSpeed * (1.0 - Math.pow(DRAG, SLIDE_TICKS)) / (1.0 - DRAG);
    }

    public record Provider(SpriteSet sprites) implements ParticleProvider<SimpleParticleType> {

        @Override
        public Particle createParticle(SimpleParticleType options, ClientLevel level,
                                       double x, double y, double z, double dx, double dy, double dz) {
            RiftGroundEmberParticle spark =
                    new RiftGroundEmberParticle(level, x, y, z, dx, dy, dz);
            spark.pickSprite(sprites);
            return spark;
        }
    }
}
