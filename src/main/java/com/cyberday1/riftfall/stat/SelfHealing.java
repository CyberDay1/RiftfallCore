package com.cyberday1.riftfall.stat;

import com.cyberday1.riftfall.init.RiftfallAttributes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import org.jetbrains.annotations.Nullable;

/**
 * Tranquility's term: a Spell Engine heal whose caster is its own target is raised by the caster's
 * {@code riftfall:self_healing}. A heal on anyone else, or one with no caster, is left alone.
 */
public final class SelfHealing {

    private SelfHealing() {
    }

    /** The factor on one heal: {@code 1 + self_healing} on the caster itself, otherwise 1. */
    public static float factor(@Nullable LivingEntity caster, LivingEntity target) {
        if (caster == null || caster != target) {
            return 1.0F;
        }
        AttributeInstance bonus = target.getAttribute(RiftfallAttributes.SELF_HEALING);
        return bonus == null ? 1.0F : of(bonus.getValue());
    }

    /** {@code 1 + bonus}, with a negative or NaN bonus counted as none. */
    public static float of(double bonus) {
        return bonus > 0.0 ? (float) (1.0 + bonus) : 1.0F;
    }
}
