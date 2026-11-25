package com.cyberday1.riftfall.client;

import com.cyberday1.riftfall.RiftfallCore;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.*;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.IoSupplier;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Stellar art for stock-textured container screens, hooked at GuiGraphics so no screen method is redirected.
 * Only listed screens and their own textures change; slots, widgets, tooltips and other mods' art stay native.
 */
public final class StellarContainers {
    private record Theme(Predicate<ResourceLocation> textures, boolean panelless) {}
    private record Art(ResourceLocation id, StellarArt.Result result, int[] source) {}
    private static final Art FAILED = new Art(null, null, null);
    private static final Theme NONE = new Theme(t -> false, false);
    private static final Map<String, Theme> THEMES = new HashMap<>();
    private static final Map<Class<?>, Theme> BY_CLASS = new HashMap<>();
    private static final Map<String, Art> ART = new HashMap<>();
    private static final org.slf4j.Logger LOGGER = LogUtils.getLogger();
    public static final ResourceManagerReloadListener RELOAD = manager -> reset();
    private static int next;

    static {
        Theme vanilla = new Theme(t -> t.getNamespace().equals("minecraft") && t.getPath().startsWith("textures/gui/container/"), false);
        for (Class<?> c : List.of(ContainerScreen.class, ShulkerBoxScreen.class, FurnaceScreen.class, BlastFurnaceScreen.class,
                SmokerScreen.class, CraftingScreen.class, AnvilScreen.class, EnchantmentScreen.class, BrewingStandScreen.class,
                GrindstoneScreen.class, StonecutterScreen.class, SmithingScreen.class, LoomScreen.class,
                CartographyTableScreen.class, HopperScreen.class, DispenserScreen.class, BeaconScreen.class,
                MerchantScreen.class, HorseInventoryScreen.class, CrafterScreen.class))
            THEMES.put(c.getName(), vanilla);
        // Waystones draws no panel; its list is vanilla buttons over the world.
        Theme waystones = new Theme(t -> false, true);
        for (String s : List.of("WaystoneSelectionScreen", "SharestoneSelectionScreen", "AdminSelectionScreen"))
            THEMES.put("net.blay09.mods.waystones.client.gui.screen." + s, waystones);
        // Apothic Enchanting replaces the vanilla enchanting screen in this pack.
        mod("dev.shadowsoffire.apothic_enchanting.table.ApothEnchantmentScreen", "apothic_enchanting", "enchanting_table");
        mod("net.spell_engine.spellbinding.SpellBindingScreen", "spell_engine", "spell_binding");
        mod("io.redspace.ironsspellbooks.gui.inscription_table.InscriptionTableScreen", "irons_spellbooks", "inscription_table");
        mod("io.redspace.ironsspellbooks.gui.scroll_forge.ScrollForgeScreen", "irons_spellbooks", "scroll_forge");
        mod("noppes.npcs.client.gui.player.GuiNPCTrader", "customnpcs", "trader", "slot");
        mod("noppes.npcs.client.gui.player.GuiNPCBankChest", "customnpcs", "bankchest");
        mod("noppes.npcs.client.gui.player.companion.GuiNpcCompanionInv", "customnpcs", "companioninv", "slot");
    }
    private StellarContainers() {}

    private static void mod(String screen, String namespace, String... textures) {
        var paths = java.util.Set.of(java.util.Arrays.stream(textures).map(t -> "textures/gui/" + t + ".png").toArray(String[]::new));
        THEMES.put(screen, new Theme(t -> t.getNamespace().equals(namespace) && paths.contains(t.getPath()), false));
    }

    private static Theme theme(AbstractContainerScreen<?> screen) {
        return BY_CLASS.computeIfAbsent(screen.getClass(), c -> THEMES.getOrDefault(c.getName(), NONE));
    }

    private static AbstractContainerScreen<?> screen() {
        return Minecraft.getInstance().screen instanceof AbstractContainerScreen<?> s && theme(s) != NONE ? s : null;
    }

    /** Labels and button text drawn in the stock dark ink. */
    public static int text(int color) {
        int rgb = color & 0xFFFFFF;
        if (rgb != 0x404040 && rgb != 0x685E4A && rgb != 0x342F25 || screen() == null) return color;
        return color & 0xFF000000 | (rgb == 0x404040 ? StellarWorkshop.GOLD : rgb == 0x685E4A ? StellarWorkshop.MUTED : 0x656A7E);
    }

    /** Waystones' selection screens get the surface their stock look leaves out. */
    public static void background(AbstractContainerScreen<?> screen, GuiGraphics g) {
        if (theme(screen).panelless)
            StellarWorkshop.surface(g, screen.getGuiLeft(), screen.getGuiTop(), screen.getXSize(), screen.getYSize());
    }

    /** One texture blit of a themed screen; false leaves it stock. */
    public static boolean blit(GuiGraphics g, ResourceLocation tex, int x, int y, float fu, float fv, int w, int h, int tw, int th) {
        if (tex.getNamespace().equals(RiftfallCore.MODID)) return false;
        var screen = screen();
        if (screen == null || !theme(screen).textures.test(tex)) return false;
        Art art = art(tex, screen, StellarArt.Mode.PANEL);
        if (art == FAILED || art.result.w() != tw || art.result.h() != th) return false;
        var r = art.result;
        int u = (int) fu, v = (int) fv, ox = x - u, oy = y - v;
        StellarArt.Box panel = r.panel();
        boolean first = panel != null && u == 0 && v == 0;
        if (first) StellarWorkshop.surface(g, ox + panel.x(), oy + panel.y(),
                Math.min(panel.w(), screen.getXSize()), screen.getYSize() - panel.y());
        for (var plate : r.plates()) if (plate.inside(u, v, w, h)) plate(g, ox + plate.x(), oy + plate.y(), plate.w(), plate.h());
        if (first) wells(g, screen);
        if (panel == null || !panel.overlaps(u, v, w, h)) {
            int family = StellarArt.regionFamily(art.source, r.w(), r.h(), u, v, w, h);
            if (family != StellarArt.NONE) control(g, x, y, w, h, family);
            else if (r.slots().contains(new StellarArt.Box(u, v, w, h))) StellarInventory.slot(g, x + 1, y + 1);
        }
        g.blit(art.id, x, y, 0, u, v, w, h, tw, th);
        return true;
    }

    /** Whole-sprite blits: controls are redrawn, glyphs come from recoloured copies. */
    public static boolean sprite(GuiGraphics g, ResourceLocation sprite, int x, int y, int w, int h) {
        if (!sprite.getNamespace().equals("minecraft") || screen() == null) return false;
        String p = sprite.getPath();
        if (p.startsWith("widget/")) return widget(g, p, x, y, w, h);
        if (!p.startsWith("container/")) return false;
        String name = p.substring(p.lastIndexOf('/') + 1);
        if (p.startsWith("container/enchanting_table/enchantment_slot") || p.startsWith("container/beacon/button")
                || p.startsWith("container/stonecutter/recipe") || p.equals("container/loom/pattern")
                || p.startsWith("container/loom/pattern_")
                && !name.equals("pattern_slot")) {
            control(g, x, y, w, h, name.contains("highlighted") ? StellarArt.LIT
                    : name.contains("disabled") ? StellarArt.PRESSED : StellarArt.NORMAL);
            if (name.contains("selected")) g.fill(x + 2, y + h - 2, x + w - 2, y + h - 1, StellarWorkshop.GOLD | 0xFF000000);
            return true;
        }
        if (name.startsWith("scroller")) { bar(g, x, y, w, h, !name.contains("disabled")); return true; }
        if (p.startsWith("container/anvil/text_field")) { field(g, x, y, w, h, !name.contains("disabled"), false); return true; }
        if (p.equals("container/villager/experience_bar_background")) { inset(g, x, y, w, h); return true; }
        if (p.equals("container/crafter/disabled_slot")) { blocked(g, x, y); return true; }
        if (p.equals("container/horse/chest_slots")) return true; // wells come from the menu's slots
        StellarArt.Mode mode = mode(p);
        if (mode == null) return false;
        Art art = art(ResourceLocation.withDefaultNamespace("textures/gui/sprites/" + p + ".png"), null, mode);
        if (art == FAILED) return false;
        g.blit(art.id, x, y, w, h, 0, 0, art.result.w(), art.result.h(), art.result.w(), art.result.h());
        return true;
    }

    /** Partial sprites: progress fills, the merchant's bar, horse chest columns. */
    public static boolean partialSprite(GuiGraphics g, ResourceLocation sprite, int tw, int th, int u, int v, int x, int y, int w, int h) {
        if (!sprite.getNamespace().equals("minecraft") || screen() == null) return false;
        String p = sprite.getPath();
        if (p.equals("container/horse/chest_slots")) return true;
        StellarArt.Mode mode = p.startsWith("container/") ? mode(p) : null;
        if (mode == null) return false;
        Art art = art(ResourceLocation.withDefaultNamespace("textures/gui/sprites/" + p + ".png"), null, mode);
        if (art == FAILED || art.result.w() != tw || art.result.h() != th) return false;
        g.blit(art.id, x, y, 0, u, v, w, h, tw, th);
        return true;
    }

    private static StellarArt.Mode mode(String p) {
        if (p.endsWith("/lit_progress") || p.endsWith("/burn_progress") || p.equals("container/brewing_stand/brew_progress")
                || p.equals("container/brewing_stand/bubbles")) return StellarArt.Mode.PROGRESS;
        if (p.startsWith("container/loom/") && p.endsWith("_slot")) return StellarArt.Mode.ICON;
        if (p.startsWith("container/villager/trade_arrow")) return StellarArt.Mode.INVERSE;
        if (p.startsWith("container/horse/") || p.endsWith("/error") || p.equals("container/villager/out_of_stock"))
            return StellarArt.Mode.GLYPH;
        return null;
    }

    /** Vanilla buttons, fields and list scrollers inside themed screens (merchant offers, Waystones, CustomNPCs). */
    private static boolean widget(GuiGraphics g, String p, int x, int y, int w, int h) {
        switch (p) {
            case "widget/button" -> control(g, x, y, w, h, StellarArt.NORMAL);
            case "widget/button_highlighted" -> control(g, x, y, w, h, StellarArt.LIT);
            case "widget/button_disabled" -> control(g, x, y, w, h, StellarArt.PRESSED);
            case "widget/text_field" -> field(g, x, y, w, h, true, false);
            case "widget/text_field_highlighted" -> field(g, x, y, w, h, true, true);
            case "widget/scroller" -> bar(g, x, y, w, h, true);
            case "widget/scroller_background" -> inset(g, x, y, w, h);
            default -> { return false; }
        }
        return true;
    }

    private static void wells(GuiGraphics g, AbstractContainerScreen<?> screen) {
        int left = screen.getGuiLeft(), top = screen.getGuiTop();
        for (var slot : screen.getMenu().slots)
            if (slot.isActive() && slot.x >= 0 && slot.y >= 0 && slot.x + 16 <= screen.getXSize() && slot.y + 16 <= screen.getYSize())
                StellarInventory.slot(g, left + slot.x, top + slot.y);
    }

    /** Small framed boxes are output plates; large ones are lists, previews and tracks. */
    private static void plate(GuiGraphics g, int x, int y, int w, int h) {
        if (w <= 28 && h <= 28) StellarWorkshop.slab(g, x, y, w, h, 0xFF1D2030);
        else inset(g, x, y, w, h);
    }

    static void control(GuiGraphics g, int x, int y, int w, int h, int family) {
        if (family == StellarArt.PRESSED) {
            if (w < 12 || h < 12) g.fill(x, y, x + w, y + h, 0xFF141927);
            else StellarWorkshop.slab(g, x, y, w, h, 0xFF141927);
        } else StellarInventory.control(g, x, y, w, h, family == StellarArt.LIT, false);
    }

    private static void inset(GuiGraphics g, int x, int y, int w, int h) { g.fill(x, y, x + w, y + h, 0xFF090D17); }

    private static void field(GuiGraphics g, int x, int y, int w, int h, boolean enabled, boolean focused) {
        inset(g, x, y, w, h);
        g.fill(x + 1, y + h - 1, x + w - 1, y + h, enabled ? 0xFF867655 : 0xFF343D52);
        if (focused) g.renderOutline(x, y, w, h, 0xFFDEC68D);
    }

    private static void bar(GuiGraphics g, int x, int y, int w, int h, boolean enabled) {
        int face = enabled ? StellarSophisticated.BAR_FACE : 0xFF343D52;
        g.fill(x + 1, y, x + w - 1, y + h, face);
        g.fill(x, y + 1, x + w, y + h - 1, face);
        if (enabled) g.fill(x + 1, y + 1, x + w - 1, y + 2, 0xFFDEC68D);
    }

    /** The crafter's switched-off slot: a sunken well with a muted bar. */
    private static void blocked(GuiGraphics g, int x, int y) {
        g.fill(x + 1, y + 1, x + 17, y + 17, 0xFF090D17);
        for (int i = 0; i < 12; i++) g.fill(x + 3 + i, y + 14 - i, x + 4 + i, y + 15 - i, 0xFF656A7E);
    }

    private static Art art(ResourceLocation tex, AbstractContainerScreen<?> screen, StellarArt.Mode mode) {
        String key = screen == null ? tex + "|" + mode : tex + "|" + screen.getClass().getName() + "|"
                + screen.getYSize() + "|" + screen.getMenu().slots.size();
        Art art = ART.get(key);
        if (art == null) {
            List<int[]> slots = new ArrayList<>();
            if (screen != null) for (var s : screen.getMenu().slots) slots.add(new int[]{s.x, s.y});
            art = load(tex, mode, slots);
            ART.put(key, art);
        }
        return art;
    }

    /** Recoloured from the installed art at runtime; none of it ships in this jar. */
    private static Art load(ResourceLocation tex, StellarArt.Mode mode, List<int[]> slots) {
        var mc = Minecraft.getInstance();
        try (InputStream in = open(tex); NativeImage image = NativeImage.read(in)) {
            int w = image.getWidth(), h = image.getHeight();
            int[] px = new int[w * h];
            for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) px[y * w + x] = argb(image.getPixelRGBA(x, y));
            var result = StellarArt.derive(px, w, h, mode, slots);
            var out = new NativeImage(w, h, true);
            for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) out.setPixelRGBA(x, y, argb(result.argb()[y * w + x]));
            var id = RiftfallCore.id("dynamic/stellar_container_" + next++);
            mc.getTextureManager().register(id, new DynamicTexture(out));
            return new Art(id, result, px);
        } catch (Exception e) {
            LOGGER.warn("Stellar: {} left stock", tex, e);
            return FAILED;
        }
    }

    /** Vanilla art from the vanilla pack, so a resource pack's palette never skews the recolour. */
    private static InputStream open(ResourceLocation tex) throws java.io.IOException {
        var mc = Minecraft.getInstance();
        if (tex.getNamespace().equals("minecraft")) {
            IoSupplier<InputStream> in = mc.getVanillaPackResources().getResource(PackType.CLIENT_RESOURCES, tex);
            if (in != null) return in.get();
        }
        return mc.getResourceManager().open(tex);
    }

    /** NativeImage is ABGR; swap red and blue both ways. */
    static int argb(int abgr) { return abgr & 0xFF00FF00 | (abgr & 0xFF) << 16 | abgr >> 16 & 0xFF; }

    private static void reset() {
        var textures = Minecraft.getInstance().getTextureManager();
        for (Art art : ART.values()) if (art.id != null) textures.release(art.id);
        ART.clear();
    }
}
