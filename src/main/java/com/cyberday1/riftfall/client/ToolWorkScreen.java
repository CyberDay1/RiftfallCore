package com.cyberday1.riftfall.client;

import com.cyberday1.riftfall.home.npc.smith.SmithService;
import com.cyberday1.riftfall.home.npc.smith.ToolWorkLang;
import com.cyberday1.riftfall.home.npc.smith.ToolWorkMenu;
import com.cyberday1.riftfall.home.npc.smith.ToolWorkService;
import com.cyberday1.riftfall.tool.ToolMode;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** The Blacksmith's tool upgrade page. Every number shown is the server's quote. */
public final class ToolWorkScreen extends AbstractContainerScreen<ToolWorkMenu> {
    private static final int ACTIONS = 1 + ToolMode.values().length;
    private final StellarWorkshop.Action[] actions = new StellarWorkshop.Action[ACTIONS];
    private StellarWorkshop.Action confirm;
    private int pendingRevision = -1, pendingTicks;

    public ToolWorkScreen(ToolWorkMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 310; imageHeight = 238;
        inventoryLabelX = 74; inventoryLabelY = 144;
    }

    @Override protected void init() {
        super.init();
        addRenderableWidget(new StellarWorkshop.Action(leftPos + imageWidth - StellarWorkshop.BACK_RIGHT, topPos + 5, 42, 17,
                CommonComponents.GUI_BACK, b -> send(ToolWorkMenu.BACK), false));
        for (int i = 0; i < ACTIONS; i++) {
            int id = i;
            var mode = ToolWorkService.mode(i);
            var label = mode == null ? Component.translatable(ToolWorkLang.ACTION_FORTUNE) : Component.translatable(ToolWorkLang.mode(mode));
            actions[i] = addRenderableWidget(new StellarWorkshop.Action(leftPos + 10 + i * 73, topPos + 62, 70, 16, label, b -> send(id), false));
            actions[i].setTooltip(Tooltip.create(mode == null ? Component.translatable(ToolWorkLang.ACTION_FORTUNE_HINT)
                    : Component.translatable(ToolWorkLang.ACTION_MODE_HINT, Component.translatable(ToolWorkLang.mode(mode)))));
        }
        confirm = addRenderableWidget(new StellarWorkshop.Action(leftPos + 213, topPos + 120, 87, 16,
                Component.translatable(ToolWorkLang.CONFIRM), b -> {
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
        for (int i = 0; i < ACTIONS; i++) { actions[i].selected(menu.selection() == i); actions[i].active = pendingRevision < 0; }
        confirm.active = pendingRevision < 0 && menu.shown().result() == ToolWorkService.Result.READY;
        confirm.setMessage(Component.translatable(pendingRevision < 0 ? ToolWorkLang.CONFIRM : com.cyberday1.riftfall.home.npc.ResidentLang.WORKING));
        super.render(g, x, y, partial);
        renderTooltip(g, x, y);
        if (isHovering(10, 112, 290, 9, x, y)) g.renderTooltip(font, Component.literal(status()), x, y);
        else if (isHovering(10, 123, 198, 9, x, y)) g.renderTooltip(font, Component.literal(info()), x, y);
        else if (isHovering(10, 134, 198, 9, x, y)) g.renderTooltip(font, Component.literal(cost()), x, y);
    }

    @Override protected void renderBg(GuiGraphics g, float partial, int mouseX, int mouseY) {
        StellarWorkshop.frame(g, leftPos, topPos, imageWidth, imageHeight);
        StellarWorkshop.slab(g, leftPos + 10, topPos + 25, 180, 30, 0xFF1A2030);
        for (var slot : menu.slots) StellarWorkshop.well(g, leftPos + slot.x, topPos + slot.y, slot.index < menu.inputCount());
        StellarWorkshop.star(g, leftPos + 29, topPos + 174, 0xFF879CB5);
        StellarWorkshop.star(g, leftPos + 27, topPos + 214, 0xFFDEC68D);
    }

    @Override protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, StellarWorkshop.title(font, title, imageWidth - StellarWorkshop.BACK_RIGHT), StellarWorkshop.TITLE_X, 9, StellarWorkshop.INK, false);
        g.drawString(font, I18n.get(ToolWorkLang.SLOT_TOOL), 16, 27, StellarWorkshop.MUTED, false);
        g.drawString(font, I18n.get(ToolWorkLang.SLOT_MATERIAL), 66, 27, StellarWorkshop.MUTED, false);
        g.drawString(font, I18n.get(ToolWorkLang.SLOT_CRYSTAL), 112, 27, StellarWorkshop.MUTED, false);
        g.drawString(font, I18n.get(ToolWorkLang.SLOT_SHARDS), 152, 27, StellarWorkshop.MUTED, false);
        boolean ready = menu.shown().result() == ToolWorkService.Result.READY;
        g.drawString(font, fit(status(), 290), 10, 112, ready ? StellarWorkshop.MUTED : StellarWorkshop.ERROR, false);
        g.drawString(font, fit(info(), 198), 10, 123, StellarWorkshop.INK, false);
        g.drawString(font, fit(cost(), 198), 10, 134, ready ? StellarWorkshop.GOLD : StellarWorkshop.MUTED, false);
        g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, StellarWorkshop.MUTED, false);
    }

    private String status() {
        if (pendingRevision >= 0) return I18n.get(ToolWorkLang.WAITING);
        return I18n.get(menu.shown().result().key());
    }

    private String info() {
        var s = menu.shown();
        if (menu.inputs.getItem(ToolWorkService.TOOL).isEmpty()) return I18n.get(ToolWorkLang.NONE);
        var mode = ToolWorkService.mode(menu.selection());
        if (mode != null) return I18n.get(ToolWorkLang.MODE_STATE, I18n.get(ToolWorkLang.mode(mode)));
        if (s.target() <= 0) return I18n.get(ToolWorkLang.NONE);
        return s.result() == ToolWorkService.Result.AT_CAP ? I18n.get(ToolWorkLang.FORTUNE_TOP, s.level())
                : I18n.get(ToolWorkLang.FORTUNE_LEVEL, s.level(), s.target());
    }

    private String cost() {
        var s = menu.shown();
        if (s.rarity() < 0 || s.material() <= 0) return "";
        var material = SmithService.materialFor(s.rarity(), s.material());
        String name = material.isEmpty() ? "?" : material.getHoverName().getString();
        if (s.faceted() > 0) return I18n.get(ToolWorkLang.COST_FACETED, s.shards(), name, s.material(), s.faceted(), s.funds());
        return s.crystals() > 0 ? I18n.get(ToolWorkLang.COST_CRYSTAL, s.shards(), name, s.material(), s.crystals(), s.funds())
                : I18n.get(ToolWorkLang.COST, s.shards(), name, s.material(), s.funds());
    }

    private String fit(String text, int width) {
        return font.width(text) <= width ? text : font.plainSubstrByWidth(text, width - font.width("...")) + "...";
    }
}
