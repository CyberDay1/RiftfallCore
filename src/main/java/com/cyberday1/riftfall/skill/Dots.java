package com.cyberday1.riftfall.skill;

import com.cyberday1.riftfall.init.RiftfallAttributes;
import com.cyberday1.riftfall.init.RiftfallEffects;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.util.EnumMap;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Riftfall's damage over time (H6). A DoT is its type's effect on the target, ticking once a second while the effect
 * lasts, plus the per-second damage and caster stored here. The rate is fixed when it lands: power x coefficient over
 * the authored seconds, times 1 + the caster's {@code riftfall:<type>_damage}. A rank's extra seconds add ticks at the
 * same rate. A re-application replaces the rate and caster; the effect keeps the longer of the two durations.
 * Server side only, held for the entity's life; a DoT carried across a reload still runs, with no rate, so it deals
 * nothing.
 */
public final class Dots {

    /** One tick a second. */
    public static final int INTERVAL = 20;

    private record Dot(double perTick, WeakReference<LivingEntity> source) {
    }

    private static final Map<LivingEntity, EnumMap<DotType, Dot>> ACTIVE = new WeakHashMap<>();
    private static final AtomicLong APPLIED = new AtomicLong();
    private static final AtomicLong TICKS = new AtomicLong();

    private Dots() {
    }

    /** Damage a tick: {@code power x coefficient / seconds x (1 + bonus)}. */
    public static double perTick(double power, double coefficient, double seconds, double bonus) {
        if (!(seconds > 0.0)) {
            return 0.0;
        }
        return power * coefficient / seconds * (1.0 + Math.max(0.0, bonus));
    }

    /** Ticks a DoT of {@code durationTicks} deals: one on every remaining duration of 1 mod 20, so the first a second in. */
    public static int ticksIn(int durationTicks) {
        return Math.max(0, (durationTicks + INTERVAL - 1) / INTERVAL);
    }

    /** Whether the effect ticks at this remaining duration. */
    public static boolean ticksAt(int remaining) {
        return remaining % INTERVAL == 1;
    }

    public static Holder<MobEffect> effect(DotType type) {
        return switch (type) {
            case BLEED -> RiftfallEffects.BLEEDING;
            case BURN -> RiftfallEffects.BURNING;
            case VENOM -> RiftfallEffects.ENVENOMED;
        };
    }

    public static Holder<Attribute> bonusAttribute(DotType type) {
        return switch (type) {
            case BLEED -> RiftfallAttributes.BLEED_DAMAGE;
            case BURN -> RiftfallAttributes.BURN_DAMAGE;
            case VENOM -> RiftfallAttributes.VENOM_DAMAGE;
        };
    }

    /** The caster's {@code riftfall:<type>_damage}; 0 without it. */
    public static double bonus(@Nullable LivingEntity caster, DotType type) {
        if (caster == null) {
            return 0.0;
        }
        AttributeInstance instance = caster.getAttribute(bonusAttribute(type));
        return instance == null ? 0.0 : instance.getValue();
    }

    /** Lands a DoT of {@code perTick} for {@code durationTicks} on {@code target}. */
    public static void apply(LivingEntity target, @Nullable LivingEntity source, DotType type, double perTick,
                             int durationTicks) {
        if (target.level().isClientSide || durationTicks <= 0 || !(perTick > 0.0)) {
            return;
        }
        target.addEffect(new MobEffectInstance(effect(type), durationTicks, 0), source);
        if (!target.hasEffect(effect(type))) {
            return;
        }
        ACTIVE.computeIfAbsent(target, t -> new EnumMap<>(DotType.class)).put(type, new Dot(perTick, new WeakReference<>(source)));
        APPLIED.incrementAndGet();
    }

    /** The rate stored for {@code type} on {@code target}, 0 when none. */
    public static double perTickOn(LivingEntity target, DotType type) {
        EnumMap<DotType, Dot> dots = ACTIVE.get(target);
        Dot dot = dots == null ? null : dots.get(type);
        return dot == null ? 0.0 : dot.perTick();
    }

    /**
     * One tick of {@code type} on {@code target}. Lands whatever invulnerability the target is in (the type bypasses
     * the cooldown) and leaves that invulnerability as it was, so a tick never shields the target from the next hit.
     */
    public static void tick(LivingEntity target, DotType type) {
        if (target.level().isClientSide) {
            return;
        }
        EnumMap<DotType, Dot> dots = ACTIVE.get(target);
        Dot dot = dots == null ? null : dots.get(type);
        if (dot == null || !target.isAlive()) {
            return;
        }
        LivingEntity source = dot.source().get();
        if (source != null && source.isRemoved()) {
            source = null;
        }
        Holder<DamageType> damageType = target.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(type.damageType());
        int invulnerable = target.invulnerableTime;
        DamageSource tick = source == null ? new DamageSource(damageType) : new DamageSource(damageType, source);
        SkillDamage.dealing(() -> target.hurt(tick, (float) dot.perTick()));
        target.invulnerableTime = invulnerable;
        TICKS.incrementAndGet();
    }

    /** DoTs landed and ticks dealt. Probe reads. */
    public static long applied() {
        return APPLIED.get();
    }

    public static long ticks() {
        return TICKS.get();
    }
}
