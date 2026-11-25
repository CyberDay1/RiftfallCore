package com.cyberday1.riftfall.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Recolours stock container art into a Stellar overlay, pixel for pixel, so layout never drifts.
 * Pure ARGB maths: the panel, wells and plates are drawn by {@link StellarContainers}, this keeps the rest.
 */
final class StellarArt {
    enum Mode { PANEL, GLYPH, PROGRESS, ICON, INVERSE }
    record Box(int x, int y, int w, int h) {
        boolean contains(int px, int py) { return px >= x && py >= y && px < x + w && py < y + h; }
        boolean inside(int u, int v, int rw, int rh) { return x >= u && y >= v && x + w <= u + rw && y + h <= v + rh; }
        boolean overlaps(int u, int v, int rw, int rh) { return x < u + rw && u < x + w && y < v + rh && v < y + h; }
    }
    record Result(int[] argb, int w, int h, Box panel, List<Box> plates, List<Box> slots) {}

    static final int PANEL_GREY = 0xC6C6C6, SLOT_GREY = 0x8B8B8B, SHADE = 0x373737, LIGHT = 0xFFFFFF;
    static final int GLYPH = 0xFF867655, GOLD = 0xFFDEC68D, GOLD_DARK = 0xFFAA9261, MUTED = 0xFFB5BAD0, ICON = 0xFF4A5068;
    // Stock slot frames, including the crafter's raised variant.
    private static final Set<Integer> SLOT_PALETTE = Set.of(SLOT_GREY, SHADE, LIGHT, 0x383838, 0xADADAD, 0x737373, PANEL_GREY);
    private static final Map<Integer, Integer> PALETTE = Map.ofEntries(
            Map.entry(SLOT_GREY, GLYPH), Map.entry(LIGHT, 0xFF6B7088), Map.entry(SHADE, 0xFF2A3044),
            Map.entry(0x555555, 0xFF2A3044), Map.entry(0x000000, 0xFF05070C), Map.entry(0x212121, 0xFF090D17),
            Map.entry(0x51493A, 0xFF090D17), Map.entry(0x6B614C, 0xFF1D2334), Map.entry(0x211D17, 0xFF05070C),
            Map.entry(0x686868, ICON));
    // Enchanting-style button fills: normal, hovered, pressed or disabled.
    private static final Set<Integer> TAN = Set.of(0xA09172, 0xE0CA9F, 0x544C3B),
            PINK = Set.of(0xB688AE, 0xFAB9EF, 0x654C61),
            DARK = Set.of(0x51493A, 0x332E24, 0x211D17, 0x6B614C, 0x4E4737, 0x6D634D, 0x29251C);
    static final int NONE = 0, NORMAL = 1, LIT = 2, PRESSED = 3;
    private StellarArt() {}

    static int family(int rgb) {
        rgb &= 0xFFFFFF;
        return TAN.contains(rgb) ? NORMAL : PINK.contains(rgb) ? LIT : DARK.contains(rgb) ? PRESSED : NONE;
    }

    /** The button family covering most of a region, or NONE. */
    static int regionFamily(int[] px, int w, int h, int u, int v, int rw, int rh) {
        int[] counts = new int[4];
        int total = 0;
        for (int y = Math.max(0, v); y < Math.min(h, v + rh); y++)
            for (int x = Math.max(0, u); x < Math.min(w, u + rw); x++) {
                int p = px[y * w + x];
                if (p >>> 24 == 0) continue;
                total++;
                counts[family(p)]++;
            }
        for (int f = 1; f < 4; f++) if (total > 0 && counts[f] * 2 > total) return f;
        return NONE;
    }

    /**
     * A stock panel starts with a black edge, a white bevel, then panel grey; its origin may sit a row low.
     * Bounded by its own outline, since atlas parts often touch it.
     */
    static Box panel(int[] px, int w, int h) {
        for (int dy = 0; dy <= 2 && 4 + dy < h && w > 4; dy++) {
            int row = 4 + dy;
            if (!black(px, w, h, 0, row) || rgb(px, w, 1, row) != LIGHT || rgb(px, w, 4, row) != PANEL_GREY) continue;
            int right = 4, bottom = row, top = 4, left = row;
            while (right < w && opaque(px, w, right, row)) right++;
            while (bottom < h && opaque(px, w, 4, bottom)) bottom++;
            while (black(px, w, h, top + 1, dy)) top++;
            while (black(px, w, h, 0, left + 1)) left++;
            int pw = top >= 60 ? Math.min(right, top + 4) : right;
            int ph = left - dy >= 60 ? Math.min(bottom, left + 4) - dy : bottom - dy;
            return new Box(0, dy, pw, ph);
        }
        return null;
    }

    /** Frames whose top-left corner is a shade pixel with shade runs of 8+ to the right and down. */
    static List<Box> boxes(int[] px, int w, int h) {
        List<Box> boxes = new ArrayList<>();
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            if (!shade(px, w, h, x, y) || shade(px, w, h, x - 1, y) || shade(px, w, h, x, y - 1)) continue;
            int bw = 0, bh = 0;
            while (shade(px, w, h, x + bw, y)) bw++;
            while (shade(px, w, h, x, y + bh)) bh++;
            if (bw >= 8 && bh >= 8 && x + bw < w && y + bh < h) boxes.add(new Box(x, y, bw + 1, bh + 1));
        }
        return boxes;
    }

    /** {@code slots} are menu slot positions (item origin) in texture space; only PANEL mode keys them. */
    static Result derive(int[] px, int w, int h, Mode mode, List<int[]> slots) {
        int[] out = new int[w * h];
        boolean[] done = new boolean[w * h];
        Box panel = mode == Mode.PANEL ? panel(px, w, h) : null;
        if (panel != null) for (int y = panel.y; y < panel.y + panel.h; y++) for (int x = panel.x; x < panel.x + panel.w; x++)
            if (x < panel.x + 4 || y < panel.y + 4 || x >= panel.x + panel.w - 4 || y >= panel.y + panel.h - 4)
                done[y * w + x] = true; // the stock bevel; the Stellar surface replaces it
        List<Box> keyed = new ArrayList<>();
        if (panel != null) for (int[] s : slots) {
            Box r = new Box(s[0] - 1, s[1] - 1, 18, 18);
            if (panel.contains(r.x + 1, r.y + 1)) { keyed.add(r); slot(px, w, h, r, out, done); }
        }
        List<Box> plates = new ArrayList<>(), slotBoxes = new ArrayList<>();
        for (Box b : boxes(px, w, h)) {
            if (b.w == 18 && b.h == 18) {
                slotBoxes.add(b);
                slot(px, w, h, b, out, done);
            } else if (panel != null && panel.contains(b.x, b.y)) {
                int dominant = uniform(px, w, b, keyed, slotBoxes);
                if (dominant < 0) continue;
                plates.add(b);
                for (int y = b.y; y < b.y + b.h; y++) for (int x = b.x; x < b.x + b.w; x++) {
                    boolean edge = x == b.x || y == b.y || x == b.x + b.w - 1 || y == b.y + b.h - 1;
                    if (edge || (px[y * w + x] & 0xFFFFFF) == dominant) done[y * w + x] = true;
                }
            }
        }
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            int i = y * w + x, p = px[i];
            if (done[i] || p >>> 24 == 0) continue;
            boolean inPanel = panel != null && panel.contains(x, y);
            out[i] = map(p, mode, inPanel);
        }
        return new Result(out, w, h, panel, plates, slotBoxes);
    }

    private static int map(int argb, Mode mode, boolean inPanel) {
        int rgb = argb & 0xFFFFFF, alpha = argb & 0xFF000000;
        return switch (mode) {
            case ICON -> rgb == SLOT_GREY ? 0 : alpha | ICON & 0xFFFFFF;
            case INVERSE -> rgb == PANEL_GREY ? alpha | MUTED & 0xFFFFFF : argb;
            case PROGRESS -> rgb == PANEL_GREY ? 0 : rgb == LIGHT ? alpha | GOLD & 0xFFFFFF
                    : rgb == 0x686868 ? alpha | GOLD_DARK & 0xFFFFFF : rgb == SLOT_GREY ? alpha | GLYPH & 0xFFFFFF : argb;
            default -> {
                if (rgb == PANEL_GREY) yield 0;
                // Off-panel button fills are drawn as controls; their icons stay.
                if (!inPanel && family(rgb) != NONE) yield 0;
                Integer mapped = PALETTE.get(rgb);
                yield mapped == null ? argb : alpha | mapped & 0xFFFFFF;
            }
        };
    }

    /** Frame and fill go; a placeholder glyph inside the slot is lifted so it reads on a dark well. */
    private static void slot(int[] px, int w, int h, Box r, int[] out, boolean[] done) {
        for (int y = Math.max(0, r.y); y < Math.min(h, r.y + r.h); y++)
            for (int x = Math.max(0, r.x); x < Math.min(w, r.x + r.w); x++) {
                int i = y * w + x, p = px[i];
                if (done[i]) continue;
                done[i] = true;
                if (p >>> 24 != 0 && !SLOT_PALETTE.contains(p & 0xFFFFFF)) out[i] = StellarArt.lift(p);
            }
    }

    /** The colour filling 90% of a box interior outside slots, or -1. */
    private static int uniform(int[] px, int w, Box b, List<Box> keyed, List<Box> slotBoxes) {
        Map<Integer, Integer> counts = new java.util.HashMap<>();
        int total = 0;
        outer:
        for (int y = b.y + 1; y < b.y + b.h - 1; y++) for (int x = b.x + 1; x < b.x + b.w - 1; x++) {
            for (Box s : keyed) if (s.contains(x, y)) continue outer;
            for (Box s : slotBoxes) if (s.contains(x, y)) continue outer;
            counts.merge(px[y * w + x] & 0xFFFFFF, 1, Integer::sum);
            total++;
        }
        for (var e : counts.entrySet()) if (e.getValue() * 10 >= total * 9 && total > 0) return e.getKey();
        return -1;
    }

    /** Inverts dark greys toward the theme's muted blue-white; colours pass through. ARGB. */
    static int lift(int argb) {
        int r = argb >> 16 & 255, g = argb >> 8 & 255, b = argb & 255;
        int max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
        if (argb >>> 24 == 0 || max - min >= 24 || max >= 120) return argb;
        int l = 255 - r;
        return argb & 0xFF000000 | clamp(l - 10) << 16 | clamp(l - 4) << 8 | clamp(l + 8);
    }

    private static int clamp(int c) { return Math.max(0, Math.min(255, c)); }
    private static boolean opaque(int[] px, int w, int x, int y) { return px[y * w + x] >>> 24 != 0; }
    private static int rgb(int[] px, int w, int x, int y) { return px[y * w + x] & 0xFFFFFF; }
    private static boolean black(int[] px, int w, int h, int x, int y) {
        return x >= 0 && y >= 0 && x < w && y < h && opaque(px, w, x, y) && rgb(px, w, x, y) == 0;
    }
    private static boolean shade(int[] px, int w, int h, int x, int y) {
        return x >= 0 && y >= 0 && x < w && y < h && opaque(px, w, x, y) && rgb(px, w, x, y) == SHADE;
    }
}
