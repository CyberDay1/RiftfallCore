package com.cyberday1.riftfall.skill;

import com.cyberday1.riftfall.compat.TaczGuns;
import com.cyberday1.riftfall.gear.GunGrade;
import com.cyberday1.riftfall.gear.WeaponFloor;
import com.cyberday1.riftfall.init.RiftfallDataComponents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import org.jetbrains.annotations.Nullable;

/**
 * Which weapons draw on Charge, and what one shot of each costs. A bow is priced off the weapon floor's own rows on
 * the stack (its common-rarity damage and pull time); a gun off its TaCZ data, through {@link TaczGuns}.
 */
public final class ChargedWeapons {

    static final ResourceLocation FLOOR_DAMAGE = ResourceLocation.parse(WeaponFloor.FLOOR_PREFIX + "ranged_damage");
    static final ResourceLocation FLOOR_PULL = ResourceLocation.parse(WeaponFloor.FLOOR_PREFIX + "pull_time");

    private ChargedWeapons() {
    }

    /** Whether {@code stack} is a bow or a crossbow. */
    public static boolean isBow(ItemStack stack) {
        return stack.getItem() instanceof ProjectileWeaponItem;
    }

    /** Whether {@code entity} holds a weapon the bar is shown for: a gun in the main hand, a bow in either. */
    public static boolean armed(LivingEntity entity) {
        ItemStack main = entity.getMainHandItem();
        return isBow(main) || GunGrade.isGun(main) || isBow(entity.getOffhandItem());
    }

    /** The shot of what {@code entity} holds, main hand first; null with no bow or gun. */
    public static @Nullable ChargeRules.Shot held(LivingEntity entity) {
        ChargeRules.Shot main = of(entity.getMainHandItem());
        return main != null ? main : bow(entity.getOffhandItem());
    }

    /** The shot of {@code stack}; null for anything that is not a bow, a crossbow or a gun. */
    public static @Nullable ChargeRules.Shot of(ItemStack stack) {
        if (isBow(stack)) {
            return bow(stack);
        }
        return GunGrade.isGun(stack) && GunnerGuns.loaded() ? TaczGuns.chargeShot(stack) : null;
    }

    /** A bow's shot. A bow the floor never stamped is priced as its tier's shortbow (or crossbow). */
    public static @Nullable ChargeRules.Shot bow(ItemStack stack) {
        if (!isBow(stack)) {
            return null;
        }
        int tier = Math.max(1, stack.getOrDefault(RiftfallDataComponents.RIFT_TIER.get(), 0));
        WeaponFloor.Ranged row = stack.getItem() instanceof CrossbowItem
                ? WeaponFloor.Ranged.CROSSBOW : WeaponFloor.Ranged.SHORTBOW;
        Double damage = floor(stack, FLOOR_DAMAGE);
        Double pull = floor(stack, FLOOR_PULL);
        return bow(damage == null ? WeaponFloor.rangedDamageAt(tier, row) : damage,
                pull == null ? row.pullTime() : pull, tier);
    }

    /** A bow landing {@code damage} at common rarity every {@code pullSeconds}. */
    static ChargeRules.Shot bow(double damage, double pullSeconds, int tier) {
        return ChargeRules.shot(damage, tier, pullSeconds > 0.0 ? 1.0 / pullSeconds : 0.0);
    }

    /** The value a floor row gives its attribute (the attribute's default plus the row), or null without the row. */
    private static @Nullable Double floor(ItemStack stack, ResourceLocation id) {
        ItemAttributeModifiers modifiers = stack.get(DataComponents.ATTRIBUTE_MODIFIERS);
        if (modifiers == null) {
            return null;
        }
        for (ItemAttributeModifiers.Entry entry : modifiers.modifiers()) {
            if (entry.modifier().id().equals(id)) {
                return entry.attribute().value().getDefaultValue() + entry.modifier().amount();
            }
        }
        return null;
    }
}
