package com.cyberday1.riftfall.skill;

import com.cyberday1.riftfall.network.RangedChargePayload;
import org.jetbrains.annotations.Nullable;

/**
 * The client's copy of its own Charge pool: the last reading the server sent, nothing predicted. No client class is
 * named here, so the shot gates (which run on both sides) can read it.
 */
public final class ChargeView {

    private static volatile RangedChargePayload latest;

    private ChargeView() {
    }

    /** Handler for the clientbound payload. Named from {@code RiftfallPayloads}. */
    public static void told(RangedChargePayload payload) {
        latest = payload;
    }

    public static void forget() {
        latest = null;
    }

    public static @Nullable RangedChargePayload latest() {
        return latest;
    }

    /** Whether the server last said the held weapon would be refused. False before it has said anything. */
    public static boolean locked() {
        RangedChargePayload payload = latest;
        return payload != null && payload.locked();
    }
}
