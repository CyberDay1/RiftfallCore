package com.cyberday1.riftfall.client;

import com.cyberday1.riftfall.RiftfallCore;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

/** Class skills own spell progression; donor crafting and import screens are not pack features. */
@EventBusSubscriber(modid = RiftfallCore.MODID, value = Dist.CLIENT)
public final class SpellBuilderAccess {
    private SpellBuilderAccess() { }

    static boolean blocked(String screen) {
        return screen.equals("buildaspell.client.gui.SpellBuilderScreen")
                || screen.equals("buildaspell.client.gui.SpellImportScreen")
                || screen.equals("buildaspell.client.gui.SpellVisualScreen");
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void opening(ScreenEvent.Opening event) {
        if (event.getNewScreen() != null && blocked(event.getNewScreen().getClass().getName())) {
            event.setCanceled(true);
        }
    }
}
