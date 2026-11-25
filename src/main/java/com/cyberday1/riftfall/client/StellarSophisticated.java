package com.cyberday1.riftfall.client;

import com.cyberday1.riftfall.RiftfallCore;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/** Sophisticated Core art by atlas region. Names no Sophisticated type, so it loads without the mod. */
public final class StellarSophisticated {
    static final ResourceLocation CONTROLS = core("gui_controls"), SLOTS = core("slots_background"), ICONS = core("icons");
    private static final ResourceLocation LIFTED = RiftfallCore.id("dynamic/sophisticated_icons");
    private static final int INSET = 0xFF090D17, PANEL = 0xFF111522;
    public static final int SEARCH = INSET, BAR_TRACK = INSET, BAR_SHADE = 0xFF635B50, BAR_FACE = 0xFFAA9261;
    public static final ResourceManagerReloadListener RELOAD = manager -> reset();
    private static final org.slf4j.Logger LOGGER = LogUtils.getLogger();
    private static final Map<Long, Boolean> DARK = new HashMap<>();
    private static NativeImage source;
    private static boolean loaded;
    private StellarSophisticated() {}

    private static ResourceLocation core(String name) {
        return ResourceLocation.fromNamespaceAndPath("sophisticatedcore", "textures/gui/" + name + ".png");
    }

    public static int label(int color) { return color == 0x404040 ? StellarInventory.labelColor() : color; }

    /** Panel plus player-inventory wells; the stock texture draws those wells, the menu may not own the slots. */
    public static void background(GuiGraphics g, int x, int y, int w, int h) {
        StellarInventory.panel(g, x, y, w, h);
        int left = x + (w - 176) / 2 + 8;
        for (int row = 0; row < 4; row++) for (int col = 0; col < 9; col++)
            StellarInventory.slot(g, left + col * 18, y + h - (row < 3 ? 82 - row * 18 : 24));
    }

    /** The upgrade column is three strips plus a nub per switch; the middle strip draws the whole column. */
    public static boolean upgradePiece(GuiGraphics g, ResourceLocation tex, int x, int y, int v, int w, int h) {
        if (!tex.equals(CONTROLS)) return false;
        if (v == 4) {
            StellarInventory.panel(g, x, y - 4, w, h + 10);
            for (int i = 0; i < (h - 2) / 16; i++) StellarInventory.slot(g, x + 6, y + 2 + i * 16);
        } else if (v >= 204) g.fill(x, y, x + 7, y + h, PANEL);
        return true;
    }

    /** One blit of gui_controls or slots_background. Arrows, overlays and progress art stay stock. */
    public static boolean controlPiece(GuiGraphics g, ResourceLocation tex, int x, int y, int u, int v, int w, int h) {
        if (tex.equals(SLOTS)) {
            for (int row = 0; row < h / 18; row++) for (int col = 0; col < w / 18; col++)
                StellarInventory.slot(g, x + 1 + col * 18, y + 1 + row * 18);
            return true;
        }
        if (!tex.equals(CONTROLS)) return false;
        if (v < 18 && u >= 29 && u < 77) // 18px buttons, context-button pieces, upgrade switches
            StellarInventory.control(g, x, y, w, h, u >= 47 && u < 65 || u == 71, false);
        else if (v == 18 && u >= 29 && u < 85 || u == 63 && v == 30) // 12px buttons
            StellarInventory.control(g, x, y, w, h, u == 41 || u == 61 || u == 63, false);
        else if (u == 29 && (v == 30 || v == 48 || v == 66) || (u == 29 || u == 96) && v == 146
                || u == 28 && (v == 99 || v == 115)) inset(g, x, y, w, h);
        else if (u == 71 && v == 216) {
            inset(g, x + 1, y + 1, w - 2, h - 2);
            g.fill(x + 4, y + 1, x + w - 4, y + 2, 0xFF867655);
        } else if (u == 110 && (v == 148 || v == 166 || v == 184) || u == 63 && v == 60)
            StellarInventory.control(g, x, y + 1, w, h - 1, v == 184, v == 166 || v == 60);
        else if (v == 131 && (u == 29 || u == 41) && w == 12)
            StellarInventory.control(g, x, y, w, h, u == 29, false);
        else return false;
        return true;
    }

    /** Leaf of renderControlBackground / renderTiledControlBackground, before it splits into tiles. */
    public static boolean controlBackground(GuiGraphics g, int x, int y, int w, int h, int u, int v) {
        if (u == 128 && v == 0) StellarInventory.panel(g, x, y, w, h);
        else if ((u == 29 || u == 96) && v == 146 || u == 29 && v == 30) inset(g, x, y, w, h);
        else return false;
        return true;
    }

    /** Stock tabs overlap the panel edge by three pixels. */
    public static void tab(GuiGraphics g, int x, int y, int w, int h) {
        StellarInventory.panel(g, x - 3, y, w + 3, h);
    }

    private static void inset(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, INSET);
    }

    /** Dark monochrome glyphs (sort letters, match toggles) vanish on the panel; draw a lifted copy. */
    public static boolean icon(GuiGraphics g, ResourceLocation tex, int x, int y, float u, float v,
                               int w, int h, int tw, int th) {
        if (!tex.equals(ICONS) || !dark((int) u, (int) v, w, h)) return false;
        g.blit(LIFTED, x, y, u, v, w, h, tw, th);
        return true;
    }

    private static boolean dark(int u, int v, int w, int h) {
        long key = (long) u << 48 | (long) v << 32 | (long) w << 16 | h;
        return DARK.computeIfAbsent(key, k -> load() && darkRegion(source, u, v, w, h));
    }

    /** Built from the installed pack's own icons at runtime; none of Sophisticated's art ships here. */
    private static boolean load() {
        if (loaded) return source != null;
        loaded = true;
        var mc = Minecraft.getInstance();
        try (InputStream in = mc.getResourceManager().open(ICONS)) {
            source = NativeImage.read(in);
            var lifted = new NativeImage(source.getWidth(), source.getHeight(), false);
            for (int y = 0; y < source.getHeight(); y++) for (int x = 0; x < source.getWidth(); x++)
                lifted.setPixelRGBA(x, y, lift(source.getPixelRGBA(x, y)));
            mc.getTextureManager().register(LIFTED, new DynamicTexture(lifted));
        } catch (Exception e) {
            LOGGER.warn("Stellar: Sophisticated icons left stock", e);
            source = null;
        }
        return source != null;
    }

    private static void reset() {
        DARK.clear();
        loaded = false;
        if (source != null) source.close();
        source = null;
        Minecraft.getInstance().getTextureManager().release(LIFTED);
    }

    static boolean darkRegion(NativeImage image, int u, int v, int w, int h) {
        boolean any = false;
        for (int y = Math.max(0, v); y < Math.min(image.getHeight(), v + h); y++)
            for (int x = Math.max(0, u); x < Math.min(image.getWidth(), u + w); x++) {
                int p = image.getPixelRGBA(x, y);
                if (p >>> 24 == 0) continue;
                if (!darkGrey(p)) return false;
                any = true;
            }
        return any;
    }

    /** NativeImage pixels are ABGR. */
    static boolean darkGrey(int abgr) {
        int r = abgr & 255, gr = abgr >> 8 & 255, b = abgr >> 16 & 255;
        int max = Math.max(r, Math.max(gr, b)), min = Math.min(r, Math.min(gr, b));
        return abgr >>> 24 != 0 && max - min < 24 && max < 120;
    }

    /** Inverts a dark grey toward the theme's muted blue-white; colours pass through. */
    static int lift(int abgr) {
        if (!darkGrey(abgr)) return abgr;
        int l = 255 - (abgr & 255);
        return abgr & 0xFF000000 | clamp(l + 8) << 16 | clamp(l - 4) << 8 | clamp(l - 10);
    }

    private static int clamp(int c) { return Math.max(0, Math.min(255, c)); }
}
