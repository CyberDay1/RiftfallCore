package com.cyberday1.riftfall.client;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLLoader;
import org.jetbrains.annotations.Nullable;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;

/**
 * The modifiers Curios' own tooltip block would print for a stack, one entry per slot.
 * The only client class naming a Curios type; reached only behind a {@code ModList} check.
 */
final class AffixCurios {

    private AffixCurios() {
    }

    /** Mirrors Curios' {@code onAttributeTooltip}: same slots, index 0, sorted for a stable pick. */
    static List<ItemAttributeModifiers> worn(ItemStack stack, @Nullable Player player) {
        TreeSet<String> slots = new TreeSet<>((player != null
                ? CuriosApi.getItemStackSlots(stack, player)
                : CuriosApi.getItemStackSlots(stack, FMLLoader.getDist() == Dist.CLIENT)).keySet());
        if (slots.contains("curio")) {
            slots.retainAll(List.of("curio"));
        }
        List<ItemAttributeModifiers> worn = new ArrayList<>();
        for (String slot : slots) {
            SlotContext context = new SlotContext(slot, player, 0, false, true);
            ItemAttributeModifiers.Builder builder = ItemAttributeModifiers.builder();
            CuriosApi.getAttributeModifiers(context, CuriosApi.getSlotId(context), stack)
                    .forEach((attribute, modifier) -> builder.add(attribute, modifier,
                            EquipmentSlotGroup.ANY));
            worn.add(builder.build());
        }
        return worn;
    }
}
