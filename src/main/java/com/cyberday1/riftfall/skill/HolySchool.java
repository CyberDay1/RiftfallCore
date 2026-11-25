package com.cyberday1.riftfall.skill;

import com.cyberday1.riftfall.RiftfallCore;
import com.cyberday1.riftfall.init.RiftfallAttributes;
import com.cyberday1.riftfall.stat.Derivation;
import com.cyberday1.riftfall.stat.StatBlock;
import com.cyberday1.riftfall.stat.StatModifiers;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * The Paladin's one school (ruled 2026-10-09, Q7): holy, powered by CON. Power is attack damage, the basis
 * every Riftfall ability's coefficient is set against, raised by Vitality through the derivation's curve.
 * Strength's multiplier is divided out first: it feeds physical skills, never a class school on another primary.
 * Damage lands as {@code spell_power:healing}, the pack's holy type. Registered by {@link RiftSpellEngine}.
 */
public final class HolySchool {

    public static final ResourceLocation ID = RiftfallCore.id("holy");
    public static final ResourceLocation DAMAGE_TYPE = ResourceLocation.fromNamespaceAndPath("spell_power", "healing");
    /** Spell Engine's holy colour. */
    public static final int COLOR = 0xFFFFCC;

    private HolySchool() {
    }

    /** Attack damage times 1 + PER_BAND x effective Vitality points. */
    public static double power(double attackDamage, double vitality) {
        if (!(attackDamage > 0.0)) {
            return 0.0;
        }
        return attackDamage * (1.0 + Derivation.PER_BAND * Derivation.effective(vitality));
    }

    public static double power(LivingEntity caster) {
        return power(attack(caster),
                value(caster.getAttribute(RiftfallAttributes.primary(StatBlock.Primary.VITALITY))));
    }

    /** The caster's attack damage without Strength's multiplier: the base every class school starts from. */
    static double attack(LivingEntity caster) {
        return StatModifiers.withoutStrength(caster.getAttribute(Attributes.ATTACK_DAMAGE));
    }

    private static double value(AttributeInstance instance) {
        return instance == null ? 0.0 : instance.getValue();
    }
}
