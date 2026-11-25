package com.cyberday1.riftfall.client;

import com.cyberday1.riftfall.RiftfallCore;
import net.minecraft.resources.ResourceLocation;

/**
 * The framed bar looks, copied from NeoOrigins' bar_fx presets with their numbers: a 126x6 strip that loops
 * every {@code loop} texels and drifts left at {@code speed} GUI px a second, or a static grayscale fill
 * multiplied by a tint.
 */
public enum HudBarStyle {
    WATER("water", 126, 125, 10.0F, true),
    FIRE("fire", 126, 125, 16.0F, true),
    VOID("void", 126, 125, 10.0F, true),
    TINTED("grayscale", 69, 69, 0.0F, false);

    public static final ResourceLocation FRAME = RiftfallCore.id("textures/gui/hud_bars/empty_bar.png");
    static final int STRIP_H = 6;

    final ResourceLocation texture;
    final int width;
    final int loop;
    final float speed;
    final boolean scrolls;

    HudBarStyle(String name, int width, int loop, float speed, boolean scrolls) {
        this.texture = RiftfallCore.id("textures/gui/hud_bars/" + name + ".png");
        this.width = width;
        this.loop = loop;
        this.speed = speed;
        this.scrolls = scrolls;
    }

    /**
     * How far the strip has drifted left, in GUI px within {@code [0, loop)}: game ticks plus the partial tick,
     * so it stops while paused, snapped down to whole screen pixels so texels are never resampled.
     */
    static double offset(double ticks, float speed, int loop, double guiScale) {
        if (loop <= 0) {
            return 0.0;
        }
        double off = (ticks / 20.0 * speed) % loop;
        if (off < 0) {
            off += loop;
        }
        if (guiScale > 0) {
            off = Math.floor(off * guiScale) / guiScale;
        }
        return off >= loop ? 0.0 : off;
    }
}
