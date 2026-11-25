package com.cyberday1.riftfall.init;

import com.cyberday1.riftfall.RiftfallCore;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Particle registry for Riftfall Core.
 *
 * <p>
 * All four are {@link SimpleParticleType}: the direction a mote travels and how many of them there
 * are is decided by the portal that spawns them and handed over as an ordinary velocity, so there is
 * nothing extra to serialize. None overrides the limiter &mdash; a wall of portal blocks on a low
 * particle setting should thin out like every other block's ambience.
 *
 * <p>
 * All four are json-backed, so {@code assets/riftfall/particles/&lt;name&gt;.json} has to name their
 * sprites or the atlas load fails outright.
 */
public final class RiftfallParticles {

    public static final DeferredRegister<ParticleType<?>> PARTICLES =
            DeferredRegister.create(Registries.PARTICLE_TYPE, RiftfallCore.MODID);

    /** Drawn into the pane and gone when it arrives. An entrance's particle. */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> RIFT_MOTE =
            PARTICLES.register("rift_mote", () -> new SimpleParticleType(false));

    /** Thrown off the pane and fading as it goes. A way home's particle. */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> RIFT_EMBER =
            PARTICLES.register("rift_ember", () -> new SimpleParticleType(false));

    /**
     * Turning about a tear and falling into it. A tear's particle, and only a tear's: it reads its
     * velocity as the point to orbit, so one sent from a server would circle somewhere arbitrary.
     */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> RIFT_VORTEX =
            PARTICLES.register("rift_vortex", () -> new SimpleParticleType(false));

    /** Sliding outward along the floor under a tear until it stops. Only the bottom segment spawns it. */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> RIFT_GROUND_EMBER =
            PARTICLES.register("rift_ground_ember", () -> new SimpleParticleType(false));

    private RiftfallParticles() {
    }

    public static void register(IEventBus modEventBus) {
        PARTICLES.register(modEventBus);
    }
}
