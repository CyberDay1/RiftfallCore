package com.cyberday1.riftfall.client;

import com.cyberday1.riftfall.RiftfallCore;
import com.cyberday1.riftfall.network.RiftClaimPayload;
import com.cyberday1.riftfall.network.RiftRecapPayload;
import com.cyberday1.riftfall.network.RiftRecapRequestPayload;
import com.cyberday1.riftfall.rift.RiftClaimView;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The client's copy of its run recaps, and whether the server asked for the screen. The open waits
 * for a living player with no screen up, held for a moment, so it never lands on the death screen
 * or under the level-loading screen of the trip home. A history asked for from the character sheet opens over it.
 */
@EventBusSubscriber(modid = RiftfallCore.MODID, value = Dist.CLIENT)
public final class RiftRecapClient {

    /** How long the screen must have been clear before the recap opens, in ticks. */
    static final int SETTLE_TICKS = 10;

    private static volatile RiftRecapPayload latest = RiftRecapPayload.EMPTY;
    /** The held-gear line of the latest fresh recap, added to by later hand-backs. */
    private static volatile RiftClaimView claim = RiftClaimView.NONE;
    private static boolean pendingOpen;
    private static int clear;
    /** The sheet's Rift Log link asked for the history and has not had it yet. */
    private static boolean asked;

    private RiftRecapClient() {
    }

    /** Handler for the clientbound payload. Named from {@code RiftfallPayloads}. */
    public static void told(RiftRecapPayload payload) {
        latest = payload;
        boolean wasAsked = asked;
        asked = false;
        if (wasAsked && !payload.fresh()) {
            Minecraft client = Minecraft.getInstance();
            if (overSheet(true, false, client.screen instanceof ProgressionScreen)) {
                client.setScreen(new RiftRecapScreen(payload));
                return;
            }
        }
        pendingOpen = true;
        clear = 0;
    }

    /** The character sheet's Rift Log link: asks the server for the history, as {@code /riftfall recap} does. */
    static void ask() {
        asked = true;
        PacketDistributor.sendToServer(RiftRecapRequestPayload.INSTANCE);
    }

    /** A history the sheet asked for replaces the sheet at once; a fresh recap always waits its turn. */
    static boolean overSheet(boolean asked, boolean fresh, boolean sheetOpen) {
        return asked && !fresh && sheetOpen;
    }

    static RiftRecapPayload latest() {
        return latest;
    }

    /** Handler for the held-gear payload. A fresh one starts the line over; later ones add to it. */
    public static void claimed(RiftClaimPayload payload) {
        claim = payload.fresh() ? payload.view() : claim.then(payload.view());
    }

    static RiftClaimView claim() {
        return claim;
    }

    static boolean pending() {
        return pendingOpen;
    }

    /** One client tick. True when the screen should open now. */
    static boolean ready(boolean playerAlive, boolean screenOpen) {
        if (!pendingOpen) {
            return false;
        }
        if (!playerAlive || screenOpen) {
            clear = 0;
            return false;
        }
        // A history the player asked for needs no settling; a fresh recap follows a level change.
        if (++clear < (latest.fresh() ? SETTLE_TICKS : 1)) {
            return false;
        }
        pendingOpen = false;
        return true;
    }

    @SubscribeEvent
    static void tick(ClientTickEvent.Post event) {
        Minecraft client = Minecraft.getInstance();
        if (pendingOpen && ready(client.player != null && client.player.isAlive(), client.screen != null)) {
            client.setScreen(new RiftRecapScreen(latest));
        }
    }

    @SubscribeEvent
    static void forget(ClientPlayerNetworkEvent.LoggingOut event) {
        latest = RiftRecapPayload.EMPTY;
        claim = RiftClaimView.NONE;
        pendingOpen = false;
        clear = 0;
        asked = false;
    }
}
