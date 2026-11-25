package com.cyberday1.riftfall.client;

import com.cyberday1.riftfall.home.npc.jeweler.JewelerGems;
import com.cyberday1.riftfall.home.npc.smith.SmithLang;
import com.cyberday1.riftfall.home.npc.smith.SmithMenu;
import com.cyberday1.riftfall.home.npc.smith.SmithService;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** The Blacksmith's re-temper and salvage page. Every number shown is the server's quote. */
public final class SmithScreen extends AbstractContainerScreen<SmithMenu> {
    private static final int[] IDS = {SmithService.RETEMPER, SmithService.SALVAGE};
    private final StellarWorkshop.Action[] actions = new StellarWorkshop.Action[IDS.length];
    private StellarWorkshop.Action confirm, bulk;
    private int pendingRevision = -1, pendingTicks;

    public SmithScreen(SmithMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 310; imageHeight = 238;
        inventoryLabelX = 74; inventoryLabelY = 144;
    }

    @Override protected void init() {
        super.init();
        addRenderableWidget(new StellarWorkshop.Action(leftPos + imageWidth - StellarWorkshop.BACK_RIGHT, topPos + 5, 42, 17,
                CommonComponents.GUI_BACK, b -> send(SmithMenu.BACK), false));
        String[] labels = {SmithLang.ACTION_RETEMPER, SmithLang.ACTION_SALVAGE};
        String[] hints = {SmithLang.ACTION_RETEMPER_HINT, SmithLang.ACTION_SALVAGE_HINT};
        for (int i = 0; i < IDS.length; i++) {
            int id = IDS[i];
            actions[i] = addRenderableWidget(new StellarWorkshop.Action(leftPos + 10 + i * 97, topPos + 62, 94, 16,
                    Component.translatable(labels[i]), b -> send(id), false));
            actions[i].setTooltip(Tooltip.create(Component.translatable(hints[i])));
        }
        bulk = addRenderableWidget(new StellarWorkshop.Action(leftPos + 10 + IDS.length * 97, topPos + 62, 94, 16,
                Component.translatable(com.cyberday1.riftfall.home.npc.smith.BulkScrapLang.OPEN), b -> send(SmithMenu.BULK_SCRAP), false));
        bulk.setTooltip(Tooltip.create(Component.translatable(com.cyberday1.riftfall.home.npc.smith.BulkScrapLang.OPEN_HINT)));
        confirm = addRenderableWidget(new StellarWorkshop.Action(leftPos + 213, topPos + 120, 87, 16,
                Component.translatable(SmithLang.CONFIRM), b -> {
                    pendingRevision = menu.confirmButton(); pendingTicks = 0;
                    send(pendingRevision); b.active = false;
                }, true));
    }

    private void send(int id) {
        if (minecraft != null && minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
    }

    @Override protected void containerTick() {
        if (pendingRevision >= 0 && ++pendingTicks >= 40) pendingRevision = -1;
    }

    @Override public void render(GuiGraphics g, int x, int y, float partial) {
        if (pendingRevision != menu.confirmButton()) pendingRevision = -1;
        for (int i = 0; i < IDS.length; i++) { actions[i].selected(menu.selection() == IDS[i]); actions[i].active = pendingRevision < 0; }
        bulk.active = pendingRevision < 0;
        confirm.active = pendingRevision < 0 && menu.shown().result() == SmithService.Result.READY;
        confirm.setMessage(Component.translatable(pendingRevision < 0 ? SmithLang.CONFIRM : com.cyberday1.riftfall.home.npc.ResidentLang.WORKING));
        super.render(g, x, y, partial);
        renderTooltip(g, x, y);
        if (isHovering(10, 112, 290, 9, x, y)) g.renderTooltip(font, Component.literal(status()), x, y);
        else if (isHovering(10, 123, 198, 9, x, y)) g.renderTooltip(font, Component.literal(info()), x, y);
        else if (isHovering(10, 134, 198, 9, x, y)) g.renderTooltip(font, Component.literal(cost()), x, y);
    }

    @Override protected void renderBg(GuiGraphics g, float partial, int mouseX, int mouseY) {
        StellarWorkshop.frame(g, leftPos, topPos, imageWidth, imageHeight);
        StellarWorkshop.slab(g, leftPos + 10, topPos + 25, 140, 30, 0xFF1A2030);
        StellarWorkshop.slab(g, leftPos + 228, topPos + 25, 72, 30, 0xFF1A2030);
        for (var slot : menu.slots) StellarWorkshop.well(g, leftPos + slot.x, topPos + slot.y, slot.index < menu.inputCount());
        StellarWorkshop.star(g, leftPos + 29, topPos + 174, 0xFF879CB5);
        StellarWorkshop.star(g, leftPos + 27, topPos + 214, 0xFFDEC68D);
    }

    @Override protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, StellarWorkshop.title(font, title, imageWidth - StellarWorkshop.BACK_RIGHT), StellarWorkshop.TITLE_X, 9, StellarWorkshop.INK, false);
        g.drawString(font, I18n.get(SmithLang.SLOT_GEAR), 16, 27, StellarWorkshop.MUTED, false);
        g.drawString(font, I18n.get(SmithLang.SLOT_STOCK), 72, 27, StellarWorkshop.MUTED, false);
        g.drawString(font, I18n.get(SmithLang.SLOT_SHARDS), 112, 27, StellarWorkshop.MUTED, false);
        g.drawString(font, I18n.get(SmithLang.SLOT_OUT), 234, 27, StellarWorkshop.MUTED, false);
        boolean ready = menu.shown().result() == SmithService.Result.READY;
        g.drawString(font, fit(status(), 290), 10, 112, ready ? StellarWorkshop.MUTED : StellarWorkshop.ERROR, false);
        g.drawString(font, fit(info(), 198), 10, 123, StellarWorkshop.INK, false);
        g.drawString(font, fit(cost(), 198), 10, 134, ready ? StellarWorkshop.GOLD : StellarWorkshop.MUTED, false);
        g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, StellarWorkshop.MUTED, false);
    }

    private String status() {
        if (pendingRevision >= 0) return I18n.get(SmithLang.WAITING);
        return I18n.get(menu.shown().result().key());
    }

    private String info() {
        var s = menu.shown();
        if (menu.inputs.getItem(SmithService.GEAR).isEmpty() || s.tier() <= 0) return I18n.get(SmithLang.TIER_NONE);
        String tier = I18n.get(SmithLang.TIER, s.tier(), s.tier() + 1, s.playerCap());
        String band = s.band() < 0 ? I18n.get(SmithLang.BAND_UNLIMITED, s.steps()) : I18n.get(SmithLang.BAND, s.steps(), s.band());
        return tier + " | " + band;
    }

    private String cost() {
        var s = menu.shown();
        if (menu.selection() == SmithService.SALVAGE) {
            var gear = menu.inputs.getItem(SmithService.GEAR);
            // The salvage rarity: a graded gun's grade, else the gem source's.
            int rarity = SmithService.rarity(gear, JewelerGems.current());
            var material = s.material() > 0 ? SmithService.materialFor(rarity, s.material())
                    : net.minecraft.world.item.ItemStack.EMPTY;
            return material.isEmpty() ? I18n.get(SmithLang.YIELD_STOCK, s.stockOut())
                    : I18n.get(SmithLang.YIELD, material.getHoverName().getString(), material.getCount(), s.stockOut());
        }
        return I18n.get(SmithLang.COST, s.shards(), s.stock(), s.funds());
    }

    private String fit(String text, int width) {
        return font.width(text) <= width ? text : font.plainSubstrByWidth(text, width - font.width("...")) + "...";
    }
}
