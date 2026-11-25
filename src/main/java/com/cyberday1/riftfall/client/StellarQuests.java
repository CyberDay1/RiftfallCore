package com.cyberday1.riftfall.client;

import net.minecraft.client.gui.GuiGraphics;
import java.util.Set;

/** Shattered stellar surfaces shared through FTB's theme, without changing quest data or input handling. */
public final class StellarQuests {
    private static final String PREFIX = "riftfall:quest/";
    private static final Set<String> PARTS = Set.of("map", "page", "chapter", "panel", "button", "hover",
            "disabled", "slot", "text_box", "track", "thumb", "check", "check_inactive");
    private StellarQuests() { }

    public static boolean supports(String key) { return key.startsWith(PREFIX) && PARTS.contains(key.substring(PREFIX.length())); }

    public static void draw(String key, GuiGraphics g, int x, int y, int w, int h) {
        if (w <= 0 || h <= 0 || !supports(key)) return;
        String part = key.substring(PREFIX.length());
        switch (part) {
            case "map" -> map(g, x, y, w, h);
            case "page", "chapter" -> {
                slab(g, x, y, w, h, 0xFF101522);
                if (w >= 40 && h >= 40) {
                    g.fillGradient(x + 6, y + 3, x + w - 7, y + 22, 0xFF26263A, 0xFF101522);
                    g.fill(x + 12, y + h - 3, x + Math.min(w - 12, 54), y + h - 2, 0xFF8F7A53);
                }
            }
            case "panel" -> slab(g, x, y, w, h, 0xFF171E2E);
            case "button", "hover", "disabled" -> {
                slab(g, x, y, w, h, part.equals("hover") ? 0xFF35364A
                        : part.equals("disabled") ? 0xFF171C29 : 0xFF23293B);
                if (part.equals("hover") && w > 8 && h > 5)
                    g.fill(x + 4, y + h - 2, x + w - 4, y + h - 1, 0xFFDEC68D);
            }
            case "slot", "text_box" -> {
                slab(g, x, y, w, h, 0xFF080D17);
                if (w > 6 && h > 4) g.fill(x + 3, y, x + w - 3, y + 1, 0xFF51566D);
            }
            case "track" -> slab(g, x, y, w, h, 0xFF090E19);
            case "thumb" -> slab(g, x, y, w, h, 0xFF9D8965);
            case "check", "check_inactive" -> check(g, x, y, w, h, part.equals("check") ? 0xFFDEC68D : 0xFF69758E);
            default -> { }
        }
    }

    private static void slab(GuiGraphics g, int x, int y, int w, int h, int color) {
        if (w < 16 || h < 14) g.fill(x, y, x + w, y + h, color);
        else StellarWorkshop.slab(g, x, y, w, h, color);
    }

    private static void map(GuiGraphics g, int x, int y, int w, int h) {
        g.fillGradient(x, y, x + w, y + h, 0xFF171829, 0xFF090F1B);
        // Fixed screen-space stars, sparse enough to leave dependency lines and labels clear.
        int count = Math.min(45, w * h / 3400);
        for (int i = 0; i < count; i++) {
            int px = x + Math.floorMod(i * 137 + 47, Math.max(1, w - 8)) + 4;
            int py = y + Math.floorMod(i * i * 19 + 53, Math.max(1, h - 8)) + 4;
            if (px < x + w && py < y + h) g.fill(px, py, px + 1, py + 1, i % 7 == 0 ? 0xFF747087 : 0xFF353E53);
        }
        // A few broken edge seams; no repeating runes behind the prose.
        if (w > 160 && h > 100) {
            int edge = x + w - 6;
            for (int i = 0; i < 28; i++) {
                int px = edge - i / 3, py = y + 12 + i;
                g.fill(px, py, px + 1, py + 1, 0xFF464252);
            }
        }
    }

    private static void check(GuiGraphics g, int x, int y, int w, int h, int color) {
        if (w < 8 || h < 8) { g.fill(x, y, x + w, y + h, color); return; }
        int unit = Math.max(1, Math.min(w, h) / 10);
        int left = x + (w - 8 * unit) / 2, top = y + (h - 7 * unit) / 2 + unit;
        for (int i = 0; i < 3; i++) g.fill(left + i * unit, top + (2 + i) * unit,
                left + (i + 1) * unit, top + (4 + i) * unit, color);
        for (int i = 0; i < 6; i++) g.fill(left + (2 + i) * unit, top + (4 - i) * unit,
                left + (3 + i) * unit, top + (6 - i) * unit, color);
    }
}
