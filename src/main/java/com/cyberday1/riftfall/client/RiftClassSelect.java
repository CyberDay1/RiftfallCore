package com.cyberday1.riftfall.client;

import com.cyberday1.riftfall.network.ClassSelectPayload;
import net.minecraft.client.Minecraft;

/**
 * The client's copy of the last class view, and whether the server asked for the screen. The open
 * waits for a free moment (no screen up, a player in the world), so a login offer is not closed by
 * the level-loading screen it arrives under.
 */
public final class RiftClassSelect {

    private static volatile ClassSelectPayload latest = ClassSelectPayload.EMPTY;
    private static volatile boolean pendingOpen;

    private RiftClassSelect() {
    }

    /** Handler for the clientbound payload. Named from {@code RiftfallPayloads}. */
    public static void told(ClassSelectPayload payload) {
        latest = payload;
        if (payload.open()) {
            pendingOpen = true;
        }
    }

    static ClassSelectPayload latest() {
        return latest;
    }

    /** Client tick: opens the screen the server asked for once nothing else is showing. */
    static void tick(Minecraft client) {
        if (!pendingOpen || client.player == null) {
            return;
        }
        if (client.screen instanceof ClassSelectScreen) {
            pendingOpen = false;
        } else if (client.screen == null) {
            pendingOpen = false;
            client.setScreen(new ClassSelectScreen());
        }
    }

    public static void forget() {
        latest = ClassSelectPayload.EMPTY;
        pendingOpen = false;
    }
}
