package com.cyberday1.riftfall.stat;

import com.cyberday1.riftfall.init.RiftfallAttributes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import org.jetbrains.annotations.Nullable;

/**
 * The Cleric's healing done: a Spell Engine heal is raised by its caster's {@code riftfall:healing_done},
 * whoever it lands on. Adds to Tranquility's self-healing on a heal the caster lands on itself.
 */
public final class HealingDone {

    private HealingDone() {
    }

    /** The factor on one heal: {@code 1 + healing_done + (self_healing on the caster itself)}. */
    public static float factor(@Nullable LivingEntity caster, LivingEntity target) {
        if (caster == null) {
            return 1.0F;
        }
        AttributeInstance done = caster.getAttribute(RiftfallAttributes.HEALING_DONE);
        double bonus = done == null ? 0.0 : done.getValue();
        return of(bonus, SelfHealing.factor(caster, target) - 1.0F);
    }

    /** {@code 1 + done + self}, a negative or NaN part counted as none. */
    public static float of(double done, double self) {
        return (float) (1.0 + (done > 0.0 ? done : 0.0) + (self > 0.0 ? self : 0.0));
    }
}
