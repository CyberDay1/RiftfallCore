package com.cyberday1.riftfall.skill;

import com.cyberday1.riftfall.RiftfallCore;
import com.cyberday1.riftfall.init.RiftfallAttributes;
import com.cyberday1.riftfall.stat.StatBlock;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;

/**
 * The Cleric's one school for its INT rows (Q7's shape, assumed under the 2026-10-10 standing ruling): attack
 * damage raised by Intellect, as {@link CommandSchool}, landing as holy damage ({@code spell_power:healing}),
 * as {@link HolySchool}. Registered by {@link RiftSpellEngine}.
 */
public final class DivineSchool {

    public static final ResourceLocation ID = RiftfallCore.id("divine");
    public static final ResourceLocation DAMAGE_TYPE = HolySchool.DAMAGE_TYPE;
    /** A pale gold. */
    public static final int COLOR = 0xF2D16B;

    private DivineSchool() {
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
