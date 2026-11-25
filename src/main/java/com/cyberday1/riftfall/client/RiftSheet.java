package com.cyberday1.riftfall.client;

import com.cyberday1.riftfall.network.RiftSheetPayload;
import com.cyberday1.riftfall.progression.RiftXp;

/**
 * The client's copy of what the server last said about this player's XP.
 *
 * <p>
 * A cache and not a request, on {@link RiftAmbience}'s shape: the server pushes at the three
 * moments a client is new or rebuilt, and the key that opens the sheet reads what arrived. Nothing
 * here is serverbound, so there is no trust boundary and nothing to re-check.
 *
 * <p>
 * The starting value is the one a player with no record has anyway, so a sheet opened before the
 * first payload shows the truth rather than a blank.
 */
public final class RiftSheet {

    private static volatile RiftSheetPayload latest = new RiftSheetPayload(0L, RiftXp.LEVEL_CAP);

    private RiftSheet() {
    }

    /** Handler for the clientbound payload. Named from {@code RiftfallPayloads}. */
    public static void told(RiftSheetPayload payload) {
        latest = payload;
    }

    static RiftSheetPayload latest() {
        return latest;
    }

    /** Leaving a world must not carry one character's XP into the next one. */
    public static void forget() {
        latest = new RiftSheetPayload(0L, RiftXp.LEVEL_CAP);
    }
}
