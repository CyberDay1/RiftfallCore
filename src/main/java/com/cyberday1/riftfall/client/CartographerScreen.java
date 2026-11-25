package com.cyberday1.riftfall.client;

import com.cyberday1.riftfall.home.npc.ResidentRank;
import com.cyberday1.riftfall.home.npc.cartographer.CartographerCrystals;
import com.cyberday1.riftfall.home.npc.cartographer.CartographerLang;
import com.cyberday1.riftfall.home.npc.cartographer.CartographerMenu;
import com.cyberday1.riftfall.home.npc.cartographer.CartographerService;
import com.cyberday1.riftfall.home.npc.depth.BoardLang;
import com.cyberday1.riftfall.network.AtlasPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** The Cartographer's page: the atlas as a scrolling list (themes, then bosses from Expert); the numbers are the server's quote. */
public final class CartographerScreen extends AbstractContainerScreen<CartographerMenu> {
    private static final int ROWS = 6, ROW_H = 14, LIST_X = 10, LIST_Y = 26, LIST_W = 148;
    private record Row(int selection, Component label, boolean charted) {}
    private final StellarWorkshop.Action[] rows = new StellarWorkshop.Action[ROWS];
    private final List<Row> entries = new ArrayList<>();
    private StellarWorkshop.Action confirm, up, down;
    private int scroll, pendingRevision = -1, pendingTicks;

    public CartographerScreen(CartographerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 310; imageHeight = 238;
        inventoryLabelX = 74; inventoryLabelY = 144;
    }

    @Override protected void init() {
        super.init();
        addRenderableWidget(new StellarWorkshop.Action(leftPos + imageWidth - StellarWorkshop.BACK_RIGHT, topPos + 5, 42, 17,
                CommonComponents.GUI_BACK, b -> send(CartographerMenu.BACK), false));
        for (int i = 0; i < ROWS; i++) {
            final int row = i;
            rows[i] = addRenderableWidget(new StellarWorkshop.Action(leftPos + LIST_X, topPos + LIST_Y + i * ROW_H, LIST_W, ROW_H - 1,
                    Component.empty(), b -> pick(row), false));
        }
        up = addRenderableWidget(new StellarWorkshop.Action(leftPos + LIST_X, topPos + LIST_Y + ROWS * ROW_H + 1, 72, 12,
                Component.translatable(CartographerLang.SCROLL_UP), b -> scroll = Math.max(0, scroll - 1), false));
        down = addRenderableWidget(new StellarWorkshop.Action(leftPos + LIST_X + 76, topPos + LIST_Y + ROWS * ROW_H + 1, 72, 12,
                Component.translatable(CartographerLang.SCROLL_DOWN), b -> scroll++, false));
        confirm = addRenderableWidget(new StellarWorkshop.Action(leftPos + 213, topPos + 120, 87, 16,
                Component.translatable(CartographerLang.CONFIRM), b -> {
                    pendingRevision = menu.confirmButton(); pendingTicks = 0;
                    send(pendingRevision); b.active = false;
                }, true));
    }

    private void send(int id) {
        if (minecraft != null && minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
    }

    private void pick(int row) {
        int index = scroll + row;
        if (index >= 0 && index < entries.size()) send(entries.get(index).selection());
    }

    @Override public boolean mouseScrolled(double x, double y, double dx, double dy) {
        if (isHovering(LIST_X, LIST_Y, LIST_W, ROWS * ROW_H, x, y)) { scroll = Math.max(0, scroll - (int) Math.signum(dy)); return true; }
        return super.mouseScrolled(x, y, dx, dy);
    }

    @Override protected void containerTick() {
        if (pendingRevision >= 0 && ++pendingTicks >= 40) pendingRevision = -1;
    }

    private static Component theme(String id) { return Component.translatable(BoardLang.theme(id)); }

    private static Component boss(String id) {
        var rl = ResourceLocation.tryParse(id);
        return rl == null ? Component.literal(id) : BuiltInRegistries.ENTITY_TYPE.getOptional(rl)
                .map(t -> (Component) t.getDescription()).orElse(Component.literal(id));
    }

    private void rebuild() {
        entries.clear();
        var atlas = AtlasClient.latest();
        for (int i = 0; i < Math.min(CartographerService.MAX_THEMES, atlas.themes().size()); i++) {
            var t = atlas.themes().get(i);
            boolean charted = t.visits() > 0;
            entries.add(new Row(i, Component.translatable(charted ? CartographerLang.ROW_THEME : CartographerLang.ROW_UNCHARTED, theme(t.id())), charted));
        }
        if (menu.rank().atLeast(ResidentRank.EXPERT))
            for (int i = 0; i < Math.min(CartographerService.MAX_BOSSES, atlas.bosses().size()); i++) {
                var b = atlas.bosses().get(i);
                entries.add(new Row(CartographerService.BOSS + i, Component.translatable(CartographerLang.ROW_BOSS, boss(b.boss()), theme(b.theme())), true));
            }
        scroll = Math.clamp(scroll, 0, Math.max(0, entries.size() - ROWS));
    }

    private void updateButtons() {
        rebuild();
        if (pendingRevision != menu.confirmButton()) pendingRevision = -1;
        for (int i = 0; i < ROWS; i++) {
            int index = scroll + i;
            boolean exists = index < entries.size();
            rows[i].visible = exists;
            if (!exists) continue;
            var row = entries.get(index);
            rows[i].setMessage(row.label());
            rows[i].selected(menu.selection() == row.selection());
            rows[i].active = pendingRevision < 0;
        }
        up.active = scroll > 0;
        down.active = scroll + ROWS < entries.size();
        confirm.active = pendingRevision < 0 && menu.shown().status() == CartographerService.Status.READY;
        confirm.setMessage(Component.translatable(pendingRevision >= 0 ? com.cyberday1.riftfall.home.npc.ResidentLang.WORKING : CartographerLang.CONFIRM));
    }

    @Override public void render(GuiGraphics g, int x, int y, float partial) {
        updateButtons();
        super.render(g, x, y, partial);
        renderTooltip(g, x, y);
        if (isHovering(10, 112, 290, 9, x, y)) g.renderTooltip(font, Component.literal(status()), x, y);
        else if (isHovering(10, 123, 198, 9, x, y)) g.renderTooltip(font, Component.literal(info()), x, y);
        else if (isHovering(10, 134, 198, 9, x, y)) g.renderTooltip(font, Component.literal(cost()), x, y);
    }

    @Override protected void renderBg(GuiGraphics g, float partial, int mouseX, int mouseY) {
        StellarWorkshop.frame(g, leftPos, topPos, imageWidth, imageHeight);
        StellarWorkshop.slab(g, leftPos + 176, topPos + 25, 108, 40, 0xFF1A2030);
        for (var slot : menu.slots) StellarWorkshop.well(g, leftPos + slot.x, topPos + slot.y, slot.index < menu.inputCount());
        StellarWorkshop.star(g, leftPos + 29, topPos + 174, 0xFF879CB5);
        StellarWorkshop.star(g, leftPos + 27, topPos + 214, 0xFFDEC68D);
    }

    @Override protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, StellarWorkshop.title(font, title, imageWidth - StellarWorkshop.BACK_RIGHT), StellarWorkshop.TITLE_X, 9, StellarWorkshop.INK, false);
        g.drawString(font, I18n.get(CartographerLang.SLOT_KEY), 184, 29, StellarWorkshop.MUTED, false);
        g.drawString(font, I18n.get(CartographerLang.SLOT_CRYSTAL), 222, 29, StellarWorkshop.MUTED, false);
        g.drawString(font, I18n.get(CartographerLang.SLOT_SHARDS), 260, 29, StellarWorkshop.MUTED, false);
        boolean ready = menu.shown().status() == CartographerService.Status.READY;
        g.drawString(font, fit(status(), 290), 10, 112, ready ? StellarWorkshop.MUTED : StellarWorkshop.ERROR, false);
        g.drawString(font, fit(info(), 198), 10, 123, StellarWorkshop.INK, false);
        g.drawString(font, fit(cost(), 198), 10, 134, ready ? StellarWorkshop.GOLD : StellarWorkshop.MUTED, false);
        g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, StellarWorkshop.MUTED, false);
    }

    private String status() {
        if (pendingRevision >= 0) return I18n.get(CartographerLang.WAITING);
        return I18n.get(CartographerLang.status(menu.shown().status()));
    }

    private AtlasPayload.Theme selectedTheme() {
        var atlas = AtlasClient.latest();
        int s = menu.selection();
        if (s >= 0 && s < atlas.themes().size()) return atlas.themes().get(s);
        int b = s - CartographerService.BOSS;
        if (b >= 0 && b < atlas.bosses().size())
            for (var t : atlas.themes()) if (t.id().equals(atlas.bosses().get(b).theme())) return t;
        return null;
    }

    /** The selected theme's atlas entry, or the selected boss's record. */
    private String info() {
        var atlas = AtlasClient.latest();
        int b = menu.selection() - CartographerService.BOSS;
        if (b >= 0 && b < atlas.bosses().size()) {
            var boss = atlas.bosses().get(b);
            return I18n.get(CartographerLang.INFO_BOSS, boss(boss.boss()).getString(), boss.kills(), theme(boss.theme()).getString());
        }
        var t = selectedTheme();
        if (t == null) return "";
        if (t.visits() == 0) return I18n.get(CartographerLang.INFO_UNCHARTED, theme(t.id()).getString());
        return I18n.get(CartographerLang.INFO_THEME, t.visits(), t.clears(), t.best(), t.bossKills());
    }

    private String cost() {
        var s = menu.shown();
        var t = selectedTheme();
        if (t == null) return "";
        var crystal = CartographerCrystals.of(ResourceLocation.parse(t.id()));
        if (s.crystals() <= 0 || crystal.isEmpty()) return I18n.get(CartographerLang.COST_SHARDS, s.shards(), s.balance());
        return I18n.get(CartographerLang.COST, s.shards(), s.balance(), s.crystals(), new ItemStack(crystal.get()).getHoverName().getString());
    }

    private String fit(String text, int width) {
        return font.width(text) <= width ? text : font.plainSubstrByWidth(text, width - font.width("...")) + "...";
    }
}
