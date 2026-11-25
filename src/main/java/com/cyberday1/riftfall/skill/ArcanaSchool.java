package com.cyberday1.riftfall.skill;

import com.cyberday1.riftfall.RiftfallCore;
import com.cyberday1.riftfall.init.RiftfallAttributes;
import com.cyberday1.riftfall.stat.StatBlock;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;

/**
 * The Arcanist's rows (every one INT; assumed under the 2026-10-10 standing ruling): attack damage
 * raised by Intellect, as {@link CommandSchool}, landing as arcane damage. Registered by {@link RiftSpellEngine}.
 */
public final class ArcanaSchool {

    public static final ResourceLocation ID = RiftfallCore.id("arcana");
    public static final ResourceLocation DAMAGE_TYPE = ResourceLocation.fromNamespaceAndPath("spell_power", "arcane");
    /** Spell Engine's arcane violet, a shade deeper. */
    public static final int COLOR = 0x8E5CC8;

    private ArcanaSchool() {
    }

    /** Attack damage times 1 + PER_BAND x effective Intellect points. */
    public static double power(double attackDamage, double intellect) {
        return HolySchool.power(attackDamage, intellect);
    }

    public static double power(LivingEntity caster) {
        return power(HolySchool.attack(caster),
                value(caster.getAttribute(RiftfallAttributes.primary(StatBlock.Primary.INTELLECT))));
    }

    private static double value(AttributeInstance instance) {
        return instance == null ? 0.0 : instance.getValue();
    }
}
