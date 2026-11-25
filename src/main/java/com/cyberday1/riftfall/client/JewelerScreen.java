package com.cyberday1.riftfall.client;

import com.cyberday1.riftfall.home.npc.jeweler.JewelerGems;
import com.cyberday1.riftfall.home.npc.jeweler.JewelerLang;
import com.cyberday1.riftfall.home.npc.jeweler.JewelerMenu;
import com.cyberday1.riftfall.home.npc.jeweler.JewelerService;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** The Jeweler's page. Every number shown is the server's quote; the client only lays it out. 256 px tall, so it shrinks to fit. */
public final class JewelerScreen extends FittedContainerScreen<JewelerMenu> {
    private static final int UNSET_SHOWN = 8, STATUS_Y = 134, INFO_Y = 145, COST_Y = 156;
    private static final int[] IDS = {JewelerService.APPRAISE, JewelerService.SET, JewelerService.DISSOLVE, JewelerService.REFINE,
            JewelerService.ADD_SOCKET, JewelerService.REFINE_ORDER};
    private final StellarWorkshop.Action[] actions = new StellarWorkshop.Action[IDS.length];
    private final StellarWorkshop.Action[] unset = new StellarWorkshop.Action[UNSET_SHOWN];
    private StellarWorkshop.Action confirm;
    private int pendingRevision = -1, pendingTicks;

    public JewelerScreen(JewelerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 310; imageHeight = 256;
        inventoryLabelX = 74; inventoryLabelY = 162;
    }

    @Override protected void init() {
        super.init();
        addRenderableWidget(new StellarWorkshop.Action(leftPos + imageWidth - StellarWorkshop.BACK_RIGHT, topPos + 5, 42, 17,
                CommonComponents.GUI_BACK, b -> send(JewelerMenu.BACK), false));
        String[] labels = {JewelerLang.ACTION_APPRAISE, JewelerLang.ACTION_SET, JewelerLang.ACTION_DISSOLVE, JewelerLang.ACTION_REFINE,
                JewelerLang.ACTION_SOCKET, JewelerLang.ACTION_ORDER};
        String[] hints = {JewelerLang.LANDING_APPRAISE_HINT, JewelerLang.ACTION_SET_HINT, JewelerLang.ACTION_DISSOLVE_HINT,
                JewelerLang.ACTION_REFINE_HINT, JewelerLang.ACTION_SOCKET_HINT, JewelerLang.ACTION_ORDER_HINT};
        for (int i = 0; i < IDS.length; i++) {
            int id = IDS[i];
            actions[i] = addRenderableWidget(new StellarWorkshop.Action(leftPos + 10 + (i % 4) * 73, topPos + 60 + (i / 4) * 18, 70, 16,
                    Component.translatable(labels[i]), b -> send(id), false));
            actions[i].setTooltip(Tooltip.create(Component.translatable(hints[i])));
        }
        for (int i = 0; i < UNSET_SHOWN; i++) {
            int id = JewelerService.UNSET + i;
            unset[i] = addRenderableWidget(new StellarWorkshop.Action(leftPos + 10 + (i % 4) * 73, topPos + 96 + (i / 4) * 18, 70, 16,
                    Component.translatable(JewelerLang.ACTION_UNSET, i + 1), b -> send(id), false));
        }
        confirm = addRenderableWidget(new StellarWorkshop.Action(leftPos + 213, topPos + 142, 87, 16,
                Component.translatable(JewelerLang.CONFIRM), b -> {
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

    private void updateButtons() {
        if (pendingRevision != menu.confirmButton()) pendingRevision = -1;
        int selection = menu.selection();
        for (int i = 0; i < IDS.length; i++) { actions[i].selected(selection == IDS[i]); actions[i].active = pendingRevision < 0; }
        var gems = JewelerGems.current();
        List<ItemStack> sockets = gems.gems(menu.inputs.getItem(JewelerService.ITEM));
        for (int i = 0; i < UNSET_SHOWN; i++) {
            boolean exists = i < sockets.size();
            unset[i].visible = exists;
            unset[i].active = pendingRevision < 0 && exists && !sockets.get(i).isEmpty();
            unset[i].selected(selection == JewelerService.UNSET + i);
            if (exists) unset[i].setTooltip(Tooltip.create(Component.translatable(JewelerLang.ACTION_UNSET_HINT, i + 1,
                    sockets.get(i).isEmpty() ? Component.translatable(JewelerLang.SOCKET_EMPTY) : sockets.get(i).getHoverName())));
        }
        var shown = menu.shown();
        confirm.visible = selection != JewelerService.APPRAISE;
        confirm.active = pendingRevision < 0 && shown.result() == JewelerService.Result.READY;
        confirm.setMessage(Component.translatable(pendingRevision >= 0 ? JewelerLang.WORKING
                : selection == JewelerService.REFINE_ORDER ? JewelerLang.START_ORDER : JewelerLang.CONFIRM));
    }

    @Override protected void renderFitted(GuiGraphics g, int x, int y, float partial) {
        updateButtons();
        super.renderFitted(g, x, y, partial);
        renderTooltip(g, x, y);
        String line = isHovering(10, STATUS_Y, 290, 9, x, y) ? status() : isHovering(10, INFO_Y, 198, 9, x, y) ? appraisal()
                : isHovering(10, COST_Y, 198, 9, x, y) ? cost() : null;
        if (line != null) atScreenScale(g, x, y, (sx, sy) -> g.renderTooltip(font, Component.literal(line), sx, sy));
    }

    @Override protected void renderBg(GuiGraphics g, float partial, int mouseX, int mouseY) {
        StellarWorkshop.frame(g, leftPos, topPos, imageWidth, imageHeight);
        StellarWorkshop.slab(g, leftPos + 10, topPos + 25, 98, 30, 0xFF1A2030);
        StellarWorkshop.slab(g, leftPos + 108, topPos + 25, 94, 30, 0xFF1A2030);
        StellarWorkshop.slab(g, leftPos + 228, topPos + 25, 72, 30, 0xFF1A2030);
        for (var slot : menu.slots) StellarWorkshop.well(g, leftPos + slot.x, topPos + slot.y, slot.index < menu.inputCount());
        StellarWorkshop.star(g, leftPos + 29, topPos + 192, 0xFF879CB5);
        StellarWorkshop.star(g, leftPos + 27, topPos + 232, 0xFFDEC68D);
    }

    @Override protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, StellarWorkshop.title(font, title, imageWidth - StellarWorkshop.BACK_RIGHT), StellarWorkshop.TITLE_X, 9, StellarWorkshop.INK, false);
        g.drawString(font, I18n.get(JewelerLang.SLOT_ITEM), 16, 27, StellarWorkshop.MUTED, false);
        g.drawString(font, I18n.get(JewelerLang.SLOT_GEM), 54, 27, StellarWorkshop.MUTED, false);
        g.drawString(font, I18n.get(JewelerLang.SLOT_MATERIAL), 80, 27, StellarWorkshop.MUTED, false);
        g.drawString(font, I18n.get(JewelerLang.SLOT_GRIT), 114, 27, StellarWorkshop.MUTED, false);
        g.drawString(font, I18n.get(JewelerLang.SLOT_CRYSTALS), 152, 27, StellarWorkshop.MUTED, false);
        g.drawString(font, I18n.get(JewelerLang.SLOT_SHARDS), 234, 27, StellarWorkshop.MUTED, false);
        boolean ready = menu.shown().result() == JewelerService.Result.READY;
        boolean error = !ready && menu.shown().result() != JewelerService.Result.APPRAISE;
        g.drawString(font, fit(status(), 290), 10, STATUS_Y, error ? StellarWorkshop.ERROR : StellarWorkshop.MUTED, false);
        g.drawString(font, fit(appraisal(), 198), 10, INFO_Y, StellarWorkshop.INK, false);
        g.drawString(font, fit(cost(), 198), 10, COST_Y, ready ? StellarWorkshop.GOLD : StellarWorkshop.MUTED, false);
        g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, StellarWorkshop.MUTED, false);
    }

    private String status() {
        if (pendingRevision >= 0) return I18n.get(JewelerLang.WAITING);
        return I18n.get(menu.shown().result().key());
    }

    /** The free read: the gear's sockets and cap, and the loose gem's purity and next step. */
    private String appraisal() {
        var gems = JewelerGems.current();
        var item = menu.inputs.getItem(JewelerService.ITEM);
        var gem = menu.inputs.getItem(JewelerService.GEM);
        var shown = menu.shown();
        var parts = new ArrayList<String>();
        if (!item.isEmpty()) {
            var sockets = gems.gems(item);
            long filled = sockets.stream().filter(s -> !s.isEmpty()).count();
            parts.add(sockets.isEmpty() ? I18n.get(JewelerLang.APPRAISE_NO_SOCKETS) : I18n.get(JewelerLang.APPRAISE_SOCKETS, filled, sockets.size()));
            if ((menu.selection() == JewelerService.APPRAISE || menu.selection() == JewelerService.ADD_SOCKET) && shown.cap() > 0)
                parts.add(I18n.get(JewelerLang.APPRAISE_CAP, shown.cap()));
        }
        int purity = gems.purity(gem);
        if (purity >= 0) {
            boolean refining = menu.selection() == JewelerService.REFINE || menu.selection() == JewelerService.REFINE_ORDER;
            if (refining && shown.result() != JewelerService.Result.MAX_PURITY)
                parts.add(I18n.get(JewelerLang.PREVIEW_REFINE, gems.purityName(purity).getString(), gems.purityName(purity + 1).getString()));
            else parts.add(purity >= 5 ? I18n.get(JewelerLang.APPRAISE_GEM_TOP, gems.purityName(purity).getString())
                    : I18n.get(JewelerLang.APPRAISE_GEM, gems.purityName(purity).getString(), gems.purityName(purity + 1).getString()));
        }
        return parts.isEmpty() ? I18n.get(JewelerLang.APPRAISE_EMPTY) : String.join(" | ", parts);
    }

    private String cost() {
        var s = menu.shown();
        var parts = new ArrayList<String>();
        if (s.yield() > 0) parts.add(I18n.get(JewelerLang.COST_YIELD, s.yield()));
        if (s.shards() > 0) parts.add(I18n.get(JewelerLang.COST_SHARDS, s.shards()));
        if (s.grit() > 0) parts.add(I18n.get(JewelerLang.COST_GRIT, s.grit()));
        if (s.theme() > 0) parts.add(I18n.get(JewelerLang.COST_THEME, s.theme()));
        if (s.rare() > 0) parts.add(I18n.get(JewelerLang.COST_RARE, s.rare()));
        if (s.faceted() > 0) parts.add(I18n.get(JewelerLang.COST_FACETED, s.faceted()));
        if (menu.selection() == JewelerService.ADD_SOCKET) parts.add(I18n.get(JewelerLang.COST_CHISEL));
        if (menu.selection() >= JewelerService.UNSET && s.material() > 0) parts.add(I18n.get(JewelerLang.COST_WITHDRAWAL));
        if (parts.isEmpty()) parts.add(I18n.get(JewelerLang.COST_FREE));
        String line = String.join(" + ", parts) + " (" + I18n.get(JewelerLang.COST_HELD, s.funds()) + ")";
        if (menu.selection() == JewelerService.REFINE_ORDER) line += " | " + I18n.get(JewelerLang.ORDER_TIME, s.orderRuns(), s.orderMinutes());
        return line;
    }

    private String fit(String text, int width) {
        return font.width(text) <= width ? text : font.plainSubstrByWidth(text, width - font.width("...")) + "...";
    }
}
