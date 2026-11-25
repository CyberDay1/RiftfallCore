package com.cyberday1.riftfall.client;

import com.cyberday1.riftfall.network.RiftManaPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * Riftfall's mana bar (V2): the current and maximum the server last sent, nothing predicted. Drawn by
 * {@link RiftHudBars} at the bottom of the bar group, docked above hunger and air until the player moves it.
 * How it looks is unverified; no client was booted.
 */
public final class RiftManaHud {

    private static volatile RiftManaPayload latest;

    private RiftManaHud() {
    }

    /** Handler for the clientbound payload. Named from {@code RiftfallPayloads}. */
    public static void told(RiftManaPayload payload) {
        latest = payload;
    }

    public static void forget() {
        latest = null;
    }

    /** The bar's current mana, or NaN before the server has said. */
    static double current() {
        RiftManaPayload payload = latest;
        return payload == null ? Double.NaN : payload.current();
    }

    /** Filled pixels of a {@code width}-px bar. Empty for a zero or negative max; never past full. */
    static int filled(double current, double max, int width) {
        if (!(max > 0.0) || !(current > 0.0)) {
            return 0;
        }
        return (int) Math.min(width, Math.floor(width * current / max));
    }

    /** "current/max", both rounded down, which is what the server's refusals quote. */
    static String label(double current, double max) {
        return (int) Math.floor(current) + "/" + (int) Math.floor(max);
    }

    /** Shown where vanilla draws hunger: a player that can be hurt, once the server has said. */
    static @Nullable HudBarRenderer.Reading reading(Minecraft client) {
        RiftManaPayload bar = latest;
        if (bar == null || client.gameMode == null || !client.gameMode.canHurtPlayer()) {
            return null;
        }
        return reading(bar.current(), bar.max());
    }

    static HudBarRenderer.Reading reading(double current, double max) {
        return new HudBarRenderer.Reading(HudLayout.Element.MANA, current, max,
                Component.translatable(HudLayout.Element.MANA.labelKey()), label(current, max), HudBarStyle.WATER, 0);
    }
}
