package com.cyberday1.riftfall.skill;

import com.cyberday1.riftfall.init.RiftfallEffects;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.api.spell.event.SpellHandlers;
import net.spell_engine.internals.SpellExecution;
import net.spell_power.api.SpellPower;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.atomic.AtomicLong;

/**
 * The {@code riftfall:shift_aspect} impact: Aspect Shift's form swap. The caster takes the next aspect (none or
 * Tiger to Bear, Bear to Tiger); the old one ends (RiftfallEffects.AspectEffect). Acts on the caster whatever the
 * target, once a tick, so an area that reaches several friendlies swaps once.
 */
public final class ShifterAspects {

    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("riftfall", "shift_aspect");

    private static final AtomicLong SHIFTS = new AtomicLong();

    private ShifterAspects() {
    }

    static void register() {
        SpellHandlers.registerCustomImpact(ID, ShifterAspects::impact);
    }

    /** The aspect after {@code current}: Bear from none or Tiger, Tiger from Bear. */
    public static DeferredHolder<MobEffect, MobEffect> next(@Nullable DeferredHolder<MobEffect, MobEffect> current) {
        return current == RiftfallEffects.BEAR_ASPECT ? RiftfallEffects.TIGER_ASPECT : RiftfallEffects.BEAR_ASPECT;
    }

    /** Swaps {@code caster}'s aspect, unless it took one this tick. */
    public static boolean shift(LivingEntity caster) {
        DeferredHolder<MobEffect, MobEffect> current = RiftfallEffects.aspectOf(caster);
        if (current != null) {
            MobEffectInstance held = caster.getEffect(current);
            if (held != null && held.getDuration() >= RiftfallEffects.ASPECT_TICKS) {
                return false;
            }
        }
        RiftfallEffects.takeAspect(caster, next(current));
        SHIFTS.incrementAndGet();
        return true;
    }

    private static SpellHandlers.ImpactResult impact(Holder<Spell> spell, SpellPower.Result power, LivingEntity caster,
                                                     @Nullable Entity target, SpellExecution.ImpactContext context) {
        if (caster.level().isClientSide) {
            return new SpellHandlers.ImpactResult(false, false);
        }
        return new SpellHandlers.ImpactResult(shift(caster), false);
    }

    /** Swaps that ran. Probe reads. */
    public static long shifts() {
        return SHIFTS.get();
    }
}
