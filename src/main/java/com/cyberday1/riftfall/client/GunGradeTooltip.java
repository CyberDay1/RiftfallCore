package com.cyberday1.riftfall.client;

import com.cyberday1.riftfall.RiftfallCore;
import com.cyberday1.riftfall.gear.GunGrade;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

import java.util.List;

/**
 * A graded gun's tooltip: the name in its rarity's colour, then "Rift Tier 3: Rare" and its one-pull damage, where
 * Apotheosis puts the rest of the gear's rarity and affix lines.
 */
@EventBusSubscriber(modid = RiftfallCore.MODID, value = Dist.CLIENT)
public final class GunGradeTooltip {

    private static boolean attached;

    private GunGradeTooltip() {
    }

    /** Puts the hook on a dedicated server's bus for the probe; false on a client or a second call. */
    public static synchronized boolean attach() {
        if (attached || FMLEnvironment.dist.isClient()) {
            return false;
        }
        attached = true;
        NeoForge.EVENT_BUS.addListener(EventPriority.LOW, GunGradeTooltip::describe);
        return true;
    }

    /** Public so the probe can call the shipped body headlessly. */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void describe(ItemTooltipEvent event) {
        GunGrade grade = GunGrade.read(event.getItemStack());
        List<Component> tooltip = event.getToolTip();
        if (grade == null || tooltip.isEmpty()) {
            return;
        }
        tooltip.set(0, grade.colourName(tooltip.get(0)));
        tooltip.addAll(1, grade.lines());
    }
}
