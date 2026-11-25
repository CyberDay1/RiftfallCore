package com.cyberday1.riftfall.client;

import com.cyberday1.riftfall.RiftfallCore;
import com.cyberday1.riftfall.gear.ApotheosisProgression;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

@EventBusSubscriber(modid = RiftfallCore.MODID, value = Dist.CLIENT)
public final class ApotheosisTierAccess {
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void opening(ScreenEvent.Opening event) {
        if (event.getNewScreen() != null && ApotheosisProgression.blockedScreen(event.getNewScreen().getClass().getName())) {
            event.setCanceled(true);
        }
    }
}
