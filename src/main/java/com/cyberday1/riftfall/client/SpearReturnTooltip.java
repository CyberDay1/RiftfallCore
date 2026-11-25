package com.cyberday1.riftfall.client;

import com.cyberday1.riftfall.RiftfallCore;
import com.cyberday1.riftfall.compat.SpearLoyalty;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/** Says on the five Simply Swords throwables that they come back (MixinSimplySwordsSpearLoyalty). */
@EventBusSubscriber(modid = RiftfallCore.MODID, value = Dist.CLIENT)
public final class SpearReturnTooltip {
    private SpearReturnTooltip() {}

    @SubscribeEvent
    public static void describe(ItemTooltipEvent event) {
        if (SpearLoyalty.applies(event.getItemStack())) {
            // Under the name, above the stats.
            event.getToolTip().add(Math.min(1, event.getToolTip().size()),
                    Component.translatable(SpearLoyalty.TOOLTIP).withStyle(ChatFormatting.GRAY));
        }
    }
}
