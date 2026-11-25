package com.cyberday1.riftfall.client;

import com.cyberday1.riftfall.tool.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public final class BlacksmithScreen extends AbstractContainerScreen<BlacksmithMenu> {
    private final StellarWorkshop.Action[] effects = new StellarWorkshop.Action[ToolStat.values().length];
    private final Inventory inventory;
    private StellarWorkshop.Action install;
    private int pendingRevision = -1;
    private int pendingTicks;
    public BlacksmithScreen(BlacksmithMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.inventory = inventory;
        imageWidth = 310; imageHeight = 238;
        inventoryLabelX = 74; inventoryLabelY = 143;
    }
    @Override protected void init() {
        super.init();
        addRenderableWidget(new StellarWorkshop.Action(leftPos + imageWidth - StellarWorkshop.BACK_RIGHT, topPos + 5, 42, 17,
                CommonComponents.GUI_BACK, b -> send(BlacksmithMenu.BACK), false));
        for (var stat : ToolStat.values()) {
            int i = stat.ordinal();
            effects[i] = addRenderableWidget(new StellarWorkshop.Action(
                    leftPos + 10 + (i % 2) * 147, topPos + 62 + (i / 2) * 18, 143, 17,
                    Component.empty(), b -> send(i), false));
        }
        install = addRenderableWidget(new StellarWorkshop.Action(leftPos + 213, topPos + 128, 87, 16,
                Component.translatable(ToolLang.RUNES_INSTALL), b -> {
                    pendingRevision = menu.confirmButton(); pendingTicks = 0;
                    send(pendingRevision); b.active = false;
                }, true));
        updateButtons();
    }
    private void send(int id) {
        if (minecraft != null && minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
    }
    @Override protected void containerTick() {
        // A rejected/stale request may keep the same revision. Permit a deliberate retry,
        // never an automatic resend; the server still owns all validation and payment.
        if (pendingRevision >= 0 && ++pendingTicks >= 40) pendingRevision = -1;
    }
    private void updateButtons() {
        var quote = menu.preview();
        if (pendingRevision != menu.confirmButton()) pendingRevision = -1;
        for (var stat : ToolStat.values()) {
            var button = effects[stat.ordinal()];
            boolean allowed = ToolUpgrades.supports(quote.grade(), stat, menu.toolwright());
            button.active = pendingRevision < 0 && allowed && LootingToolItem.isTool(menu.inputs.getItem(0));
            button.selected(menu.selected() == stat);
            Component bonus = stat.bonus(ToolUpgrades.ranks(menu.inputs.getItem(0), stat));
            button.setMessage(Component.translatable(ToolLang.RUNES_STAT, stat.displayName(), bonus));
            button.setTooltip(Tooltip.create(allowed
                    ? Component.translatable(ToolLang.RUNES_STAT_INSTALL, stat.displayName(), bonus,
                            stat.bonus(quote.grade().strength), stat.describe())
                    : Component.translatable(ToolLang.RUNES_STAT_REQUIRES, stat.displayName(), bonus,
                            stat.minimum.displayName(), stat.describe())));
        }
        install.active = pendingRevision < 0 && quote.result() == ToolUpgrades.Result.READY;
        install.setMessage(Component.translatable(pendingRevision < 0 ? ToolLang.RUNES_INSTALL : ToolLang.RUNES_INSTALLING));
        install.setTooltip(Tooltip.create(Component.translatable(ToolLang.RUNES_INSTALL_HINT, quote.result().text(), cost(quote))));
    }
    @Override public void render(GuiGraphics g, int x, int y, float partial) {
        updateButtons();
        super.render(g, x, y, partial);
        renderTooltip(g, x, y);
        // Full quotes remain available when the compact layout needs an ellipsis.
        if (isHovering(10, 118, 290, 9, x, y))
            g.renderTooltip(font, Component.literal(preview(menu.preview())), x, y);
        else if (isHovering(10, 130, 198, 11, x, y))
            g.renderTooltip(font, Component.literal(cost(menu.preview())), x, y);
        else if (isHovering(10, 49, 290, 9, x, y))
            g.renderTooltip(font, Component.literal(status(menu.preview())), x, y);
    }
    @Override protected void renderBg(GuiGraphics g, float partial, int mouseX, int mouseY) {
        StellarWorkshop.frame(g, leftPos, topPos, imageWidth, imageHeight);
        for (int i = 0; i < 3; i++)
            StellarWorkshop.slab(g, leftPos + 23 + i * 100, topPos + 25, 76, 24, 0xFF1A2030);
        for (var slot : menu.slots)
            StellarWorkshop.well(g, leftPos + slot.x, topPos + slot.y, slot.index < 3);
        StellarWorkshop.star(g, leftPos + 29, topPos + 174, 0xFF879CB5);
        StellarWorkshop.star(g, leftPos + 42, topPos + 191, 0xFF73698A);
        StellarWorkshop.star(g, leftPos + 27, topPos + 214, 0xFFDEC68D);
    }
    @Override protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        var quote = menu.preview();
        // The page name sits right-aligned before Back; the resident's "Name, Role" title gets the rest of the row.
        String page = I18n.get(ToolLang.RUNES_HEADER);
        int pageX = imageWidth - StellarWorkshop.BACK_RIGHT - StellarWorkshop.HEADER_GAP - font.width(page);
        g.drawString(font, StellarWorkshop.title(font, title, pageX), StellarWorkshop.TITLE_X, 9, StellarWorkshop.INK, false);
        g.drawString(font, page, pageX, 9, StellarWorkshop.GOLD, false);
        g.drawString(font, I18n.get(ToolLang.RUNES_PICK), 51, 34, StellarWorkshop.INK, false);
        g.drawString(font, I18n.get(ToolLang.RUNES_RUNE), 151, 34, StellarWorkshop.INK, false);
        g.drawString(font, I18n.get(ToolLang.RUNES_SHARDS), 251, 34, StellarWorkshop.INK, false);
        boolean ready = quote.result() == ToolUpgrades.Result.READY;
        g.drawString(font, fit(status(quote), 290), 10, 50, ready ? StellarWorkshop.MUTED : StellarWorkshop.ERROR, false);
        g.drawString(font, fit(preview(quote), 290), 10, 118, StellarWorkshop.INK, false);
        g.drawString(font, fit(cost(quote), 198), 10, 132, ready ? StellarWorkshop.GOLD : StellarWorkshop.MUTED, false);
        g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, StellarWorkshop.MUTED, false);
    }
    private String status(ToolUpgrades.Quote quote) {
        if (pendingRevision >= 0) return I18n.get(ToolLang.RUNES_WAITING);
        if (quote.result() == ToolUpgrades.Result.MISSING_SHARDS)
            return I18n.get(ToolLang.RUNES_NEED_SHARDS, quote.shards() - ToolUpgrades.shards(menu.inputs, inventory));
        if (quote.result() == ToolUpgrades.Result.GRADE_TOO_LOW)
            return I18n.get(ToolLang.RUNES_NEEDS_GRADE, I18n.get(quote.stat().nameKey()), I18n.get(quote.stat().minimum.key()));
        return quote.result() == ToolUpgrades.Result.READY
                ? I18n.get(ToolLang.RUNES_READY, I18n.get(quote.grade().key())) : I18n.get(quote.result().key());
    }
    private String preview(ToolUpgrades.Quote quote) {
        return quote.grade() != null && quote.grade().supports(quote.stat()) && LootingToolItem.isTool(menu.inputs.getItem(0))
                ? I18n.get(ToolLang.RUNES_PREVIEW, I18n.get(quote.stat().nameKey()), quote.stat().bonus(quote.before()).getString(),
                    quote.stat().bonus(quote.after()).getString(), quote.level(), (long) quote.level() + 1)
                : I18n.get(ToolLang.RUNES_PREVIEW_EMPTY);
    }
    private String cost(ToolUpgrades.Quote quote) {
        if (!LootingToolItem.isTool(menu.inputs.getItem(0)) || quote.grade() == null) return I18n.get(ToolLang.RUNES_COST_EMPTY);
        return I18n.get(ToolLang.RUNES_COST, quote.shards(), ToolUpgrades.shards(menu.inputs, inventory));
    }
    private String fit(String text, int width) {
        return font.width(text) <= width ? text : font.plainSubstrByWidth(text, width - font.width("...")) + "...";
    }
}
