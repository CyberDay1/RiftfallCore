package com.cyberday1.riftfall.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;

/**
 * A speck of the far side being pulled into an entrance.
 *
 * <p>
 * The velocity it is handed is not a speed, it is the whole journey: the offset from where it was
 * spawned to the pane it is going into. Interpolating that rather than integrating a speed is what
 * lets it land <em>on</em> the pane and stop, instead of drifting past it or stalling short, and it
 * means the portal block picks the destination without this class knowing what a portal is.
 */
public class RiftMoteParticle extends TextureSheetParticle {

    /** How long the trip takes, in ticks, before the random spread. */
    private static final int BASE_LIFETIME = 22;
    private static final int LIFETIME_SPREAD = 10;

    private final double xStart;
    private final double yStart;
    private final double zStart;

    RiftMoteParticle(ClientLevel level, double x, double y, double z, double dx, double dy, double dz) {
        super(level, x, y, z);
        this.xStart = x;
        this.yStart = y;
        this.zStart = z;
        this.xd = dx;
        this.yd = dy;
        this.zd = dz;
        this.hasPhysics = false;
        this.lifetime = BASE_LIFETIME + this.random.nextInt(LIFETIME_SPREAD);
        this.quadSize = 0.11F * (this.random.nextFloat() * 0.5F + 0.75F);

        // The pane's own teal, off the texture, varied per mote so a dense portal does not read as
        // one flat colour.
        float shade = this.random.nextFloat() * 0.35F + 0.65F;
        this.rCol = shade * 0.24F;
        this.gCol = shade * 0.93F;
        this.bCol = shade * 0.88F;
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    /** Slow at the far end, snatched in at the near one. */
    @Override
    public void tick() {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        if (this.age++ >= this.lifetime) {
            this.remove();
            return;
        }
        float travelled = (float) this.age / this.lifetime;
        travelled *= travelled;
        this.setPos(this.xStart + this.xd * travelled,
                this.yStart + this.yd * travelled,
                this.zStart + this.zd * travelled);
    }

    /** Shrinks to nothing as it arrives, so it is swallowed rather than cut off. */
    @Override
    public float getQuadSize(float partialTick) {
        float travelled = (this.age + partialTick) / this.lifetime;
        return this.quadSize * (1.0F - travelled * travelled);
    }

    public record Provider(SpriteSet sprites) implements ParticleProvider<SimpleParticleType> {

        @Override
        public Particle createParticle(SimpleParticleType options, ClientLevel level,
                                       double x, double y, double z, double dx, double dy, double dz) {
            RiftMoteParticle mote = new RiftMoteParticle(level, x, y, z, dx, dy, dz);
            mote.pickSprite(sprites);
            return mote;
        }
    }
}
