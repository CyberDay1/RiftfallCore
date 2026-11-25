package com.cyberday1.riftfall.shield;

import com.cyberday1.riftfall.init.RiftfallAttributes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Gentle Guard's term: while Riftfall's shield pool holds points, a hit is cut by the entity's
 * {@code riftfall:shielded_reduction} before the pool spends against it, so the pool lasts longer and
 * any overflow is smaller. A {@code LivingDamageEvent.Pre} listener, so it runs before
 * {@code MixinCommonHooks} spends the pool.
 */
public final class ShieldedReduction {

    private static final AtomicLong CUT = new AtomicLong();

    private ShieldedReduction() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(ShieldedReduction::onDamagePre);
    }

    /** The damage left after the cut: {@code damage x (1 - reduction)} when shielded, otherwise unchanged. */
    public static float cut(float damage, double reduction, boolean shielded) {
        if (!shielded || !(reduction > 0.0) || !(damage > 0.0F)) {
            return damage;
        }
        return (float) (damage * (1.0 - Math.min(0.9, reduction)));
    }

    private static void onDamagePre(LivingDamageEvent.Pre event) {
        LivingEntity entity = event.getEntity();
        AttributeInstance reduction = entity.getAttribute(RiftfallAttributes.SHIELDED_REDUCTION);
        if (reduction == null || !(reduction.getValue() > 0.0)) {
            return;
        }
        float before = event.getNewDamage();
        float after = cut(before, reduction.getValue(), RiftShields.total(entity) > 0.0);
        if (after != before) {
            event.setNewDamage(after);
            CUT.incrementAndGet();
        }
    }

    /** Hits cut. Probe reads. */
    public static long cut() {
        return CUT.get();
    }
}
