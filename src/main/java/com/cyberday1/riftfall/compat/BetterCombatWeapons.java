package com.cyberday1.riftfall.compat;

import com.cyberday1.riftfall.gear.WeaponFloor;
import net.bettercombat.api.WeaponAttributes;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;

import java.util.Collections;
import java.util.Map;
import java.util.IdentityHashMap;

/** Animations and hitboxes survive; Riftfall's stamped damage remains the damage of each swing. */
public final class BetterCombatWeapons {
    private static final Map<WeaponAttributes, WeaponAttributes> NORMALIZED =
            Collections.synchronizedMap(new IdentityHashMap<>());

    private BetterCombatWeapons() { }

    public static WeaponAttributes forStack(ItemStack stack, WeaponAttributes attributes) {
        // TaCZ owns firing/posing; a fallback melee preset must never swing a firearm.
        if (com.cyberday1.riftfall.gear.GunGrade.isGun(stack)) return null;
        if (attributes == null || !stamped(stack)) return attributes;
        synchronized (NORMALIZED) {
            // Bound old presets retained across resource reloads; keys must use identity because the
            // donor's equals/hashCode omit some visual/range fields.
            if (NORMALIZED.size() > 4096) NORMALIZED.clear();
            return NORMALIZED.computeIfAbsent(attributes, BetterCombatWeapons::normalize);
        }
    }

    public static boolean stamped(ItemStack stack) {
        return stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY)
                .modifiers().stream().anyMatch(entry ->
                        entry.modifier().id().toString().startsWith(WeaponFloor.FLOOR_PREFIX));
    }

    public static WeaponAttributes normalize(WeaponAttributes source) {
        var attacks = source.attacks();
        if (attacks == null) return source;
        var normalized = new WeaponAttributes.Attack[attacks.length];
        for (int i = 0; i < attacks.length; i++) {
            var a = attacks[i];
            normalized[i] = new WeaponAttributes.Attack(a.conditions(), a.hitbox(), 1.0,
                    a.movementSpeedMultiplier(), a.rangeMultiplier(), a.angle(), a.upswing(),
                    a.animation(), a.swingSound(), a.impactSound(), a.trailParticles());
        }
        return new WeaponAttributes(source.attackRange(), source.rangeBonus(), source.pose(),
                source.offHandPose(), source.two_handed(), source.category(), normalized, source.trailAppearance());
    }
}
