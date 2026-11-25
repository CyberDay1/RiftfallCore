package com.cyberday1.riftfall.skill;

import com.cyberday1.riftfall.RiftfallCore;
import com.cyberday1.riftfall.init.RiftfallAttributes;
import com.cyberday1.riftfall.stat.StatBlock;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;

/**
 * The Tactician's one school, INT's answer to the Paladin's holy (Q7's shape, assumed under the 2026-10-10
 * standing ruling): attack damage raised by Intellect at the derivation's per-point rate, landing as
 * {@code spell_power:arcane}. Registered by {@link RiftSpellEngine}.
 */
public final class CommandSchool {

    public static final ResourceLocation ID = RiftfallCore.id("command");
    public static final ResourceLocation DAMAGE_TYPE = ResourceLocation.fromNamespaceAndPath("spell_power", "arcane");
    /** A banner red. */
    public static final int COLOR = 0xC8553D;

    private CommandSchool() {
    }

    /** Attack damage times 1 + PER_BAND x effective Intellect points: {@link HolySchool}'s curve on another stat. */
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
