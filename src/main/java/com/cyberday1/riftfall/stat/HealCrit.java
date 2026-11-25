package com.cyberday1.riftfall.stat;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;

/**
 * The one crit roll, taken at the heal site. A heal never enters the damage chain Apothic rolls in,
 * so a Spell Engine heal rolls here on the caster's {@code crit_chance} and {@code crit_damage}, the
 * same loop as {@code AttributeEvents#apothCriticalStrike}. One difference: {@code <} where Apothic
 * has {@code <=}, so a chance of 0 never lands. Names no mod type.
 */
public final class HealCrit {

    static final ResourceLocation CHANCE = ResourceLocation.fromNamespaceAndPath("apothic_attributes", "crit_chance");
    static final ResourceLocation DAMAGE = ResourceLocation.fromNamespaceAndPath("apothic_attributes", "crit_damage");

    private HealCrit() {
    }

    /** The factor on the caster's heal: 1.0, or more on a crit. 1.0 for a caster without Apothic's pair. */
    public static float roll(LivingEntity caster, RandomSource random) {
        return factor(value(caster, CHANCE, 0.0), (float) value(caster, DAMAGE, 1.0), random);
    }

    /** Each crit adds {@code m - 1}; the next rolls at {@code chance - 1} with {@code m x 0.85}, as Apothic's. */
    public static float factor(double chance, float critDamage, RandomSource random) {
        float factor = 1.0F;
        for (float m = critDamage; random.nextFloat() < chance && m > 1.0F; m *= 0.85F) {
            chance--;
            factor += m - 1.0F;
        }
        return factor;
    }

    private static double value(LivingEntity entity, ResourceLocation id, double absent) {
        return BuiltInRegistries.ATTRIBUTE.getHolder(id)
                .map(entity::getAttribute)
                .map(AttributeInstance::getValue)
                .orElse(absent);
    }
}
