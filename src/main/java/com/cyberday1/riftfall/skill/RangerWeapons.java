package com.cyberday1.riftfall.skill;

import com.cyberday1.riftfall.RiftfallCore;
import com.cyberday1.riftfall.gear.WeaponFloor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;

import java.util.function.Supplier;

/** A Ranger's bow contributes to attack-powered skills for the duration of the cast only. */
public final class RangerWeapons {
    public static final ResourceLocation CAST_BOW_ID = RiftfallCore.id("ranger_bow");
    private static final ResourceLocation RANGED_DAMAGE = ResourceLocation.parse("ranged_weapon:damage");

    /** The starter shortbow and sword anchor the two weapon scales; slower bows retain their heavier hit. */
    static final double POWER_PER_RANGED_DAMAGE = WeaponFloor.meleeDamageAt(1, WeaponFloor.Melee.SWORD)
            / WeaponFloor.rangedDamageAt(1, WeaponFloor.Ranged.SHORTBOW);

    private RangerWeapons() { }

    public static double castBonus(double rangedDamage) {
        return Double.isFinite(rangedDamage) ? Math.max(0, rangedDamage * POWER_PER_RANGED_DAMAGE - 1.0) : 0;
    }

    static <T> T withBow(LivingEntity caster, SkillNode node, Supplier<T> body) {
        SkillDef def = SkillDefs.get(node.id());
        if (def == null || !"ranger".equals(def.skillClass()) || !Attributes.ATTACK_DAMAGE.is(node.powerAttribute())) {
            return body.get();
        }
        var item = caster.getMainHandItem().getItem();
        if (!(item instanceof BowItem) && !(item instanceof CrossbowItem)) return body.get();
        var ranged = BuiltInRegistries.ATTRIBUTE.getHolder(RANGED_DAMAGE)
                .map(caster::getAttribute).orElse(null);
        if (ranged == null) return body.get();
        return withBonus(caster.getAttribute(Attributes.ATTACK_DAMAGE), castBonus(ranged.getValue()), body);
    }

    static <T> T withBonus(AttributeInstance attack, double bonus, Supplier<T> body) {
        if (attack == null || !(bonus > 0) || attack.getModifier(CAST_BOW_ID) != null) return body.get();
        attack.addTransientModifier(new AttributeModifier(CAST_BOW_ID, bonus, AttributeModifier.Operation.ADD_VALUE));
        try {
            return body.get();
        } finally {
            attack.removeModifier(CAST_BOW_ID);
        }
    }
}
