package com.cyberday1.riftfall.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;

/**
 * A spark caught by the tear, turning about it and falling in.
 *
 * <p>
 * The same speck as {@link RiftEmberParticle} and the same colour rules &mdash; it is a spark off the
 * same rip, so it inherits both &mdash; but the opposite errand. An ember is thrown clear and burns
 * down on the way out; this is matter the tear has hold of, and it spirals inward until it is gone.
 *
 * <h2>Why it is flown rather than simulated</h2>
 * Orbits are not integrated here. The angle and the radius are advanced directly and the position
 * follows from them, with the velocity worked backwards so that the inherited {@code tick} still does
 * the moving. Integrating a central force instead would be more honest and worse: the interesting
 * band between escaping and collapsing is narrow, it moves with the frame rate, and a spark that
 * slingshots out of a tear reads as a bug. Flying the path costs the physics nothing anybody can see
 * and cannot go wrong.
 *
 * <p>
 * The angular speed goes as {@code 1 / radius}, which is the one thing borrowed from real orbits and
 * the reason this reads as gravity rather than as a fan: the sparks visibly quicken as they close.
 *
 * <h2>Why it turns about the vertical</h2>
 * A vortex about the tear's own up-axis is the same shape seen from anywhere, which the flow it sits
 * beside is not &mdash; the face-thrown embers leave along {@code AXIS} and do not turn with the
 * picture, so they and the billboarded rip disagree from most angles. Nothing here can disagree with
 * anything, because there is no direction in it to be wrong.
 *
 * <p>
 * ⚠ Client-spawned only. The centre is handed over in the velocity slots, and the vanilla particle
 * packet hands every particle a random velocity &mdash; sent from a server this would orbit a point
 * picked by dice. {@code animateTick} is the only caller for exactly that reason, the same one that
 * keeps {@link RiftMoteParticle} off {@code sendParticles}.
 */
public class RiftVortexParticle extends RiftEmberParticle {

    /**
     * Angular momentum, in radians &times; blocks per tick. Divided by the radius to get the turn
     * rate, so this is the whole of how fast the vortex looks: at the outer edge of the ring it is
     * about two and a half seconds to go round, and it tightens from there.
     */
    private static final double SPIN = 0.19;

    /** How much closer a spark gets each tick, in blocks. */
    private static final double PULL = 0.045;

    /**
     * How close to the middle the turn rate stops climbing. Without a floor the last few ticks of a
     * spark's life are spent spinning arbitrarily fast about a point it has already reached, which
     * strobes.
     */
    private static final double MIN_RADIUS = 0.35;

    private final double centreX;
    private final double centreZ;
    private final double rise;
    private double radius;
    private double angle;

    RiftVortexParticle(ClientLevel level, double x, double y, double z,
                       double centreX, double rise, double centreZ) {
        super(level, x, y, z, 0.0, 0.0, 0.0);
        this.centreX = centreX;
        this.centreZ = centreZ;
        this.rise = rise;

        this.radius = Math.hypot(x - centreX, z - centreZ);
        this.angle = Math.atan2(z - centreZ, x - centreX);

        // The inherited fall and drag are an ember's, and this is not falling or being dragged. They
        // have to go, or the path below is advanced against and the spiral sags out of true.
        this.gravity = 0.0F;
        this.friction = 1.0F;
    }

    /**
     * How far round a spark at this radius goes in one tick, in radians.
     *
     * <p>
     * Inversely with the radius, which is the borrowed half of a real orbit and the reason the flow
     * reads as gravity: sparks quicken visibly as they close. The floor is not a detail &mdash; the
     * radius reaches zero by design, and without it the last ticks of a spark's life are an unbounded
     * spin about a point it is already on, which strobes rather than settles.
     *
     * <p>
     * Static, and taking its one argument plainly, so the path can be checked without a game running.
     */
    public static double turn(double radius) {
        return SPIN / Math.max(radius, MIN_RADIUS);
    }

    /**
     * The radius one tick later. Clamped at the centre: a radius allowed past zero comes back out the
     * far side, and a spark that falls in and then leaves is not falling in.
     */
    public static double close(double radius) {
        return Math.max(0.0, radius - PULL);
    }

    /**
     * One step round and one step in, expressed as the velocity that would get there.
     *
     * <p>
     * Writing the velocity rather than the position is what lets the inherited {@code tick} keep
     * doing its job &mdash; ageing, fading, and the previous-position bookkeeping the renderer
     * interpolates between. With gravity at zero and friction at one it moves by exactly what it is
     * given, so the spark lands on the path and not near it.
     */
    @Override
    public void tick() {
        this.angle += turn(this.radius);
        this.radius = close(this.radius);

        this.xd = this.centreX + Math.cos(this.angle) * this.radius - this.x;
        this.zd = this.centreZ + Math.sin(this.angle) * this.radius - this.z;
        this.yd = this.rise;

        super.tick();
    }

    public record Provider(SpriteSet sprites) implements ParticleProvider<SimpleParticleType> {

        @Override
        public Particle createParticle(SimpleParticleType options, ClientLevel level,
                                       double x, double y, double z,
                                       double centreX, double rise, double centreZ) {
            RiftVortexParticle spark = new RiftVortexParticle(level, x, y, z, centreX, rise, centreZ);
            spark.pickSprite(sprites);
            return spark;
        }
    }
}
