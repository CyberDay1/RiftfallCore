package com.cyberday1.riftfall.client;

import com.cyberday1.riftfall.network.RiftClockPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * The rift's time limit as a draining bar at the top of the bar group, above mana and Charge. Counted down from
 * the end time the server sent against the client's level clock. Shown only while a timed run is on. How it
 * looks is unverified: no client was booted.
 */
public final class RiftClockHud {

    static final String LABEL = HudLayout.Element.CLOCK.labelKey();
    /** Under this much time the bar turns red. */
    static final int LOW_TICKS = 5 * 60 * 20;
    private static final int FILL_LOW = 0xFFB03A3A;

    private static volatile RiftClockPayload latest = RiftClockPayload.NONE;

    private RiftClockHud() {
    }

    /** Handler for the clientbound payload. Named from {@code RiftfallPayloads}. */
    public static void told(RiftClockPayload payload) {
        latest = payload;
    }

    public static void forget() {
        latest = RiftClockPayload.NONE;
    }

    static RiftClockPayload latest() {
        return latest;
    }

    /** Ticks left at {@code now}: never negative, never more than the whole limit. */
    static int remaining(RiftClockPayload clock, long now) {
        return (int) Math.clamp(clock.endsAt() - now, 0L, (long) clock.limitTicks());
    }

    /** "44:59", seconds rounded up so it reads 0:00 only when the time is out. Minutes are not folded into hours. */
    static String time(int ticks) {
        int seconds = (Math.max(0, ticks) + 19) / 20;
        return seconds / 60 + ":" + (seconds % 60 < 10 ? "0" : "") + seconds % 60;
    }

    /** The void strip, or a red fill for the last five minutes. */
    static HudBarStyle style(int ticks) {
        return ticks < LOW_TICKS ? HudBarStyle.TINTED : HudBarStyle.VOID;
    }

    static int tint(int ticks) {
        return ticks < LOW_TICKS ? FILL_LOW : 0;
    }

    /** Shown on the mana bar's terms, and only while a timed run is on. */
    static @Nullable HudBarRenderer.Reading reading(Minecraft client) {
        RiftClockPayload clock = latest;
        if (!clock.running() || client.level == null || client.gameMode == null || !client.gameMode.canHurtPlayer()) {
            return null;
        }
        return reading(remaining(clock, client.level.getGameTime()), clock.limitTicks());
    }

    static HudBarRenderer.Reading reading(int left, int limit) {
        return new HudBarRenderer.Reading(HudLayout.Element.CLOCK, left, limit, Component.translatable(LABEL),
                time(left), style(left), tint(left));
    }
}
