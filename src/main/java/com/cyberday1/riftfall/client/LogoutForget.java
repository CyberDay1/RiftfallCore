package com.cyberday1.riftfall.client;

import com.cyberday1.riftfall.RiftfallCore;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

/** One world's atlas, notes and resident board must not be what the next world's screens open showing. */
@EventBusSubscriber(modid = RiftfallCore.MODID, value = Dist.CLIENT)
public final class LogoutForget {

    private LogoutForget() {
    }

    @SubscribeEvent
    static void forget(ClientPlayerNetworkEvent.LoggingOut event) {
        AtlasClient.forget();
        NotesClient.forget();
        ResidentBoardClient.forget();
        SocialClient.forget();
        BossFinderHud.forget();
    }
}
