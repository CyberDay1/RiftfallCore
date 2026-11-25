package com.cyberday1.riftfall.gear;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;

import java.util.Set;

/** These weapon packs supply appearance; rift-stamped copies use Riftfall's combat and skill system. */
public final class WeaponDonors {
    private static final Set<String> NAMESPACES = Set.of("simple_staves", "scythes", "op_scythes",
            "too_many_bows", "glixtors_random_additions", "majestic_arsenal", "better_weaponry");

    private WeaponDonors() { }

    public static boolean applies(ResourceLocation id, ItemAttributeModifiers modifiers) {
        return NAMESPACES.contains(id.getNamespace()) && modifiers.modifiers().stream()
                .anyMatch(entry -> entry.modifier().id().toString().startsWith(WeaponFloor.FLOOR_PREFIX));
    }

    public static boolean applies(ItemStack stack) {
        return !stack.isEmpty() && applies(BuiltInRegistries.ITEM.getKey(stack.getItem()),
                stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY));
    }
}
