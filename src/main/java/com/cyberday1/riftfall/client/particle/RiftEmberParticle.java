package com.cyberday1.riftfall.client.particle;

import com.cyberday1.riftfall.client.RiftPalette;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;

/**
 * A speck thrown off a way home, drifting up and out until it burns down.
 *
 * <p>
 * The opposite reading to {@link RiftMoteParticle} and the opposite motion: a real velocity, dragged
 * down over the flight rather than a destination to arrive at, because nothing out there is pulling.
 * The three-argument super is used deliberately &mdash; the six-argument one randomises and
 * renormalises whatever speed it is given, which would throw away the direction the portal chose.
 *
 * <p>
 * A mote has a colour of its own. A spark does not: it is a piece of the way home that came loose,
 * so it comes out the colour that end of the rip was when it left, off {@link RiftPalette}.
 */
public class RiftEmberParticle extends TextureSheetParticle {

    private static final int BASE_LIFETIME = 16;
    private static final int LIFETIME_SPREAD = 12;

    /** The brightest channel a spark is allowed to come out at. See the constructor. */
    private static final float EMBER_FLOOR = 0.75F;

    RiftEmberParticle(ClientLevel level, double x, double y, double z, double dx, double dy, double dz) {
        super(level, x, y, z);
        this.xd = dx;
        this.yd = dy;
        this.zd = dz;
        this.hasPhysics = false;
        this.friction = 0.92F;
        this.gravity = -0.08F;
        this.lifetime = BASE_LIFETIME + this.random.nextInt(LIFETIME_SPREAD);
        this.quadSize = 0.13F * (this.random.nextFloat() * 0.5F + 0.75F);

        float[] rgb = RiftPalette.at(phase(level, y));
        // The palette's dark rung is there to keep the rip reading as a hole. A spark is not a hole,
        // and a spark that dark is not a spark at all -- it is a missing particle for a sixth of the
        // cycle. Lifting the darkest stops to a floor keeps their hue and drops the vanishing.
        float peak = Math.max(rgb[0], Math.max(rgb[1], rgb[2]));
        float lift = peak < EMBER_FLOOR ? EMBER_FLOOR / Math.max(peak, 1.0E-3F) : 1.0F;

        // Hotter at the core than the rip is, because the rip is lit from behind and this is not.
        float shade = (this.random.nextFloat() * 0.3F + 0.7F) * lift;
        this.rCol = Math.min(1.0F, rgb[0] * shade);
        this.gCol = Math.min(1.0F, rgb[1] * shade);
        this.bCol = Math.min(1.0F, rgb[2] * shade);
    }

    /**
     * Where in the wheel a spark born here and now comes out.
     *
     * <p>
     * Both terms mirror the generator: the wheel lies along the rip's height and turns over time, so
     * a spark leaving the rip is the colour the rip was where it left. The height term is negated
     * because the strip is drawn top row first and the world counts the other way.
     *
     * <p>
     * The height is the raw world one rather than an offset from the tear's base, and it has to be:
     * a particle is handed a position and nothing else. That fixes the rate and the direction but
     * not the registration &mdash; which is all that was ever on offer, since the sprite's own phase
     * counts from atlas load and Java cannot read it. See {@link RiftPalette}.
     */
    private static float phase(ClientLevel level, double y) {
        return level.getGameTime() / RiftPalette.TICKS_PER_CYCLE - (float) y * RiftPalette.CYCLES_PER_BLOCK;
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    @Override
    public void tick() {
        super.tick();
        this.alpha = Math.max(0.0F, 1.0F - (float) this.age / this.lifetime);
    }

    @Override
    public float getQuadSize(float partialTick) {
        float spent = (this.age + partialTick) / this.lifetime;
        return this.quadSize * (1.0F - 0.6F * spent);
    }

    public record Provider(SpriteSet sprites) implements ParticleProvider<SimpleParticleType> {

        @Override
        public Particle createParticle(SimpleParticleType options, ClientLevel level,
                                       double x, double y, double z, double dx, double dy, double dz) {
            RiftEmberParticle ember = new RiftEmberParticle(level, x, y, z, dx, dy, dz);
            ember.pickSprite(sprites);
            return ember;
        }
    }
}
