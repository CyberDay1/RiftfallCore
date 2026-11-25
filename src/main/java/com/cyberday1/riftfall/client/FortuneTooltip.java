package com.cyberday1.riftfall.client;

import com.cyberday1.riftfall.RiftfallCore;
import com.cyberday1.riftfall.gear.FortuneGrant;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/**
 * Fortune is Blacksmith-only: a tooltip never claims a level the floor ({@link FortuneGrant}) does not apply. The
 * line shows the granted level, or goes, with a note; items in chests the inventory sweep has not reached yet.
 */
@EventBusSubscriber(modid = RiftfallCore.MODID, value = Dist.CLIENT)
public final class FortuneTooltip {
    private FortuneTooltip() {}

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void describe(ItemTooltipEvent event) {
        var stack = event.getItemStack();
        boolean removed = fix(event, stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY), FortuneGrant.granted(stack));
        removed |= fix(event, stack.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY), 0);
        if (removed) event.getToolTip().add(Component.translatable(com.cyberday1.riftfall.home.npc.smith.ToolWorkLang.FORTUNE_UNEARNED).withStyle(ChatFormatting.DARK_GRAY));
    }

    /** True when a Fortune line was dropped rather than lowered. */
    private static boolean fix(ItemTooltipEvent event, ItemEnchantments enchantments, int granted) {
        for (var entry : enchantments.entrySet()) {
            if (!entry.getKey().is(Enchantments.FORTUNE)) continue;
            int stored = entry.getIntValue(), applies = FortuneGrant.allowed(stored, granted);
            if (applies >= stored) return false;
            String shown = Enchantment.getFullname(entry.getKey(), stored).getString();
            var lines = event.getToolTip();
            for (int i = 0; i < lines.size(); i++) {
                if (!lines.get(i).getString().equals(shown)) continue;
                if (applies > 0) lines.set(i, Enchantment.getFullname(entry.getKey(), applies));
                else lines.remove(i);
                return applies == 0;
            }
            return false;
        }
        return false;
    }
}
