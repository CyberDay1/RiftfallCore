package com.cyberday1.riftfall.client;

import net.minecraft.client.gui.GuiGraphics;

/** Curios art only; no optional API types, inventory state or input handling. */
public final class StellarCurios {
    private StellarCurios() {}

    /** The native atlas builds a panel from overlapping strips, then draws its slot columns. */
    public static boolean panelPiece(GuiGraphics g, int x, int y, int u, int v, int w, int h,
                                     int columns, int rows, boolean paginated) {
        if ((u == 91 || u == 98) && (v == 0 || v == 159)) {
            // Draw one continuous surface instead of seams between every atlas strip.
            if (u == 91 && v == 0)
                StellarInventory.panel(g, x, y, columns == 1 ? 32 : columns * 18 + 14,
                        14 + rows * 18 + (paginated ? 8 : 0));
            return true;
        }
        if (u == 7 && v == 7 && w == 18) {
            for (int row = 0; row < h / 18; row++) StellarInventory.slot(g, x + 1, y + row * 18 + 1);
            return true;
        }
        if (u == 32 && v == 0 && w == 28 && h == 24) {
            StellarInventory.control(g, x, y, w, h, false, false);
            return true;
        }
        if (u == 32 && v == 50 && w == 18 && h == 18) {
            StellarInventory.slot(g, x + 1, y + 1);
            g.fill(x + 5, y + 16, x + 13, y + 17, 0xFFAA9261);
            return true;
        }
        return false; // Preserve unknown atlas art, including slot placeholder glyphs.
    }

    public static void cosmeticButton(GuiGraphics g, int x, int y, int w, int h, boolean hover, boolean on) {
        StellarInventory.control(g, x, y, w, h, hover, on);
        int cx = x + w / 2, cy = y + h / 2, color = on ? 0xFFDEC68D : 0xFFB5BAD0;
        // Two mask lenses: distinct from the inventory button and from slot visibility checks.
        g.renderOutline(cx - 6, cy - 3, 5, 5, color);
        g.renderOutline(cx + 1, cy - 3, 5, 5, color);
        g.fill(cx - 1, cy - 2, cx + 1, cy - 1, color);
    }

    public static void pageButton(GuiGraphics g, int x, int y, int w, int h, boolean hover, boolean enabled, boolean left) {
        StellarInventory.control(g, x, y, w, h, enabled && hover, false);
        int color = enabled ? 0xFFDEC68D : 0xFF656A7E;
        for (int i = 0; i < 3; i++) {
            int dx = left ? i : 2 - i;
            g.fill(x + (w - 3) / 2 + dx, y + h / 2 - i,
                    x + (w - 3) / 2 + dx + 1, y + h / 2 + i + 1, color);
        }
    }

    public static void visibilityToggle(GuiGraphics g, int x, int y, int w, int h, boolean hover, boolean on) {
        StellarInventory.control(g, x, y, w, h, hover, false);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, on ? 0xFFDEC68D : 0xFF656A7E);
        if (!on) g.fill(x + 1, y + h / 2, x + w - 1, y + h / 2 + 1, 0xFF141927);
    }

    public static void inventoryToggle(GuiGraphics g, int x, int y, int w, int h, boolean hover) {
        StellarInventory.control(g, x, y, w, h, hover, false);
        if (w < 10 || h < 10) {
            g.fill(x + 2, y + 2, x + w - 2, y + 3, 0xFFDEC68D);
            g.fill(x + 2, y + h - 3, x + w - 2, y + h - 2, 0xFFB5BAD0);
            return;
        }
        int cx = x + w / 2, cy = y + h / 2;
        g.renderOutline(cx - 4, cy - 5, 3, 9, 0xFFB5BAD0);
        for (int i = 0; i < 3; i++) g.fill(cx + 1, cy - 4 + i * 3, cx + 4, cy - 3 + i * 3, 0xFFDEC68D);
    }
}
