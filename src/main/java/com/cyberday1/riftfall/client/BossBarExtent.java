package com.cyberday1.riftfall.client;

import com.cyberday1.riftfall.RiftfallCore;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.CustomizeGuiOverlayEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

/**
 * How far down the screen this frame's boss bars reach, for HUD that sits under them. Vanilla's bars are read from
 * NeoForge's per-bar event after every other listener (so a mod's taller increment counts, and a bar it cancelled to
 * draw itself does not); Enhanced Boss Bars' own stack is reported by {@code MixinEnhancedBossBarsStack}.
 */
@EventBusSubscriber(modid = RiftfallCore.MODID, value = Dist.CLIENT)
public final class BossBarExtent {

    /** Vanilla draws a bar's name 9 px above the bar's y; the next bar's name starts {@code increment} lower. */
    static final int NAME_ABOVE = 9;

    private static int vanilla;
    private static int enhanced;

    private BossBarExtent() {
    }

    /** The lowest boss bar's bottom this frame in GUI pixels, or 0 with none drawn. */
    public static int bottom() {
        return Math.max(vanilla, enhanced);
    }

    /** One vanilla-drawn bar at {@code y}: it owns the rows down to where the next bar's name would start. */
    static int vanillaBottom(int y, int increment) {
        return y + Math.max(increment, NAME_ABOVE + 1) - NAME_ABOVE;
    }

    /**
     * Enhanced Boss Bars' stack: {@code start} and the scaled height as its layout computes them. It grows down from
     * {@code start}, or up to it; only a stack in the top third (where vanilla's sit) counts.
     */
    static int enhancedBottom(int start, int scaledHeight, boolean down, int guiHeight) {
        int top = down ? start : start - scaledHeight;
        return scaledHeight <= 0 || top >= guiHeight / 3 ? 0 : (down ? start + scaledHeight : start);
    }

    /** Called from the Enhanced Boss Bars mixin once its stack is laid out for this frame. */
    public static void enhanced(int start, int scaledHeight, boolean down, int guiHeight) {
        enhanced = Math.max(enhanced, enhancedBottom(start, scaledHeight, down, guiHeight));
    }

    @SubscribeEvent
    static void frame(RenderGuiEvent.Pre event) {
        vanilla = 0;
        enhanced = 0;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void bar(CustomizeGuiOverlayEvent.BossEventProgress event) {
        vanilla = Math.max(vanilla, vanillaBottom(event.getY(), event.getIncrement()));
    }
}
