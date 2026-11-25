package com.cyberday1.riftfall.home.npc.jeweler;

import dev.shadowsoffire.apotheosis.event.CanSocketGemEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Gems go into gear only at the Jeweler: {@code CanSocketGemEvent} is cancelled unless the Jeweler's commit
 * is asking. Closes the smithing table, free right-click socketing and any addon that sockets through
 * {@code SocketHelper}. Registered only with Apotheosis loaded.
 */
public final class SocketingGate {
    private SocketingGate() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, CanSocketGemEvent.class, SocketingGate::gate);
    }

    private static void gate(CanSocketGemEvent event) {
        if (!JewelerAuthority.active()) event.setCanceled(true);
    }
}
