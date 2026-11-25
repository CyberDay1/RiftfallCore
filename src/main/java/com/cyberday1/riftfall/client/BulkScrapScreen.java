package com.cyberday1.riftfall.client;

import com.cyberday1.riftfall.home.npc.ResidentLang;
import com.cyberday1.riftfall.home.npc.smith.BulkScrapLang;
import com.cyberday1.riftfall.home.npc.smith.BulkScrapMenu;
import com.cyberday1.riftfall.home.npc.smith.SmithLang;
import com.cyberday1.riftfall.home.npc.smith.SmithService;
import com.cyberday1.riftfall.init.RiftfallItems;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** The Fitter's bulk scrap page. Totals are the server's; the red refusal line on a hovered item is the client's own read. */
public final class BulkScrapScreen extends FittedContainerScreen<BulkScrapMenu> {
    private static final int PREVIEW_Y = 90, STATUS_Y = 105, BUTTON_Y = 101;
    private StellarWorkshop.Action scrap;
    private int pendingRevision = -1, pendingTicks;

    public BulkScrapScreen(BulkScrapMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 310; imageHeight = 218;
        inventoryLabelX = BulkScrapMenu.GRID_X; inventoryLabelY = BulkScrapMenu.INVENTORY_Y - 11;
    }

    @Override protected void init() {
        super.init();
        addRenderableWidget(new StellarWorkshop.Action(leftPos + imageWidth - StellarWorkshop.BACK_RIGHT, topPos + 5, 42, 17,
                CommonComponents.GUI_BACK, b -> send(BulkScrapMenu.BACK), false));
        scrap = addRenderableWidget(new StellarWorkshop.Action(leftPos + 213, topPos + BUTTON_Y, 87, 16,
                Component.translatable(BulkScrapLang.SCRAP), b -> {
                    pendingRevision = menu.confirmButton(); pendingTicks = 0;
                    send(pendingRevision); b.active = false;
                }, true));
        scrap.setTooltip(Tooltip.create(Component.translatable(BulkScrapLang.OPEN_HINT)));
    }

    private void send(int id) {
        if (minecraft != null && minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
    }

    @Override protected void containerTick() {
        if (pendingRevision >= 0 && ++pendingTicks >= 40) pendingRevision = -1;
    }

    @Override protected void renderFitted(GuiGraphics g, int x, int y, float partial) {
        if (pendingRevision != menu.confirmButton()) pendingRevision = -1;
        var shown = menu.shown();
        scrap.active = pendingRevision < 0 && shown.result() == SmithService.Result.READY;
        scrap.setMessage(Component.translatable(pendingRevision >= 0 ? ResidentLang.WORKING
                : shown.armed() ? BulkScrapLang.CONFIRM : BulkScrapLang.SCRAP));
        super.renderFitted(g, x, y, partial);
        renderTooltip(g, x, y);
        if (isHovering(10, PREVIEW_Y, 290, 9, x, y) && shown.items() > 0) {
            var lines = new ArrayList<Component>();
            lines.add(BulkScrapMenu.count(shown.items()));
            for (var output : outputs(shown)) lines.add(output.copy().withStyle(ChatFormatting.GRAY));
            atScreenScale(g, x, y, (sx, sy) -> g.renderComponentTooltip(font, lines, sx, sy));
        } else if (isHovering(10, STATUS_Y, 198, 9, x, y)) {
            String line = status();
            atScreenScale(g, x, y, (sx, sy) -> g.renderTooltip(font, Component.literal(line), sx, sy));
        }
    }

    /** A hovered item single salvage would refuse says why. */
    @Override protected List<Component> getTooltipFromContainerItem(ItemStack stack) {
        var lines = super.getTooltipFromContainerItem(stack);
        var refusal = hoveredSlot == null ? null : menu.refusal(stack);
        if (refusal == null) return lines;
        var out = new ArrayList<>(lines);
        out.add(Component.translatable(refusal.key()).withStyle(ChatFormatting.RED));
        return out;
    }

    @Override protected void renderBg(GuiGraphics g, float partial, int mouseX, int mouseY) {
        StellarWorkshop.frame(g, leftPos, topPos, imageWidth, imageHeight);
        StellarWorkshop.slab(g, leftPos + BulkScrapMenu.GRID_X - 6, topPos + BulkScrapMenu.GRID_Y - 4, 174, 62, 0xFF1A2030);
        for (var slot : menu.slots) StellarWorkshop.well(g, leftPos + slot.x, topPos + slot.y, slot.index < menu.inputCount());
        StellarWorkshop.star(g, leftPos + 29, topPos + 156, 0xFF879CB5);
        StellarWorkshop.star(g, leftPos + 27, topPos + 196, 0xFFDEC68D);
    }

    @Override protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, StellarWorkshop.title(font, title, imageWidth - StellarWorkshop.BACK_RIGHT), StellarWorkshop.TITLE_X, 9, StellarWorkshop.INK, false);
        var shown = menu.shown();
        boolean ready = shown.result() == SmithService.Result.READY;
        boolean error = !ready && shown.result() != SmithService.Result.NO_ITEM;
        g.drawString(font, fit(preview(shown).getString(), 290), 10, PREVIEW_Y, shown.items() > 0 ? StellarWorkshop.GOLD : StellarWorkshop.MUTED, false);
        g.drawString(font, fit(status(), 198), 10, STATUS_Y, error ? StellarWorkshop.ERROR : StellarWorkshop.MUTED, false);
        g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, StellarWorkshop.MUTED, false);
    }

    private String status() {
        var shown = menu.shown();
        if (pendingRevision >= 0) return Component.translatable(SmithLang.WAITING).getString();
        if (shown.armed()) return Component.translatable(BulkScrapLang.ARMED).getString();
        return Component.translatable(switch (shown.result()) {
            case READY -> BulkScrapLang.READY;
            case NO_ITEM -> BulkScrapLang.EMPTY;
            default -> shown.result().key();
        }).getString();
    }

    /** "Scrap 14 items: 9 Timeworn Fabric, 22 Tempering Stock". */
    private static Component preview(BulkScrapMenu.Shown shown) {
        if (shown.items() <= 0) return Component.translatable(BulkScrapLang.PREVIEW_NONE);
        var joined = Component.empty();
        var outputs = outputs(shown);
        for (int i = 0; i < outputs.size(); i++) joined.append(i == 0 ? Component.empty() : Component.literal(", ")).append(outputs.get(i));
        return Component.translatable(BulkScrapLang.PREVIEW, BulkScrapMenu.count(shown.items()), joined);
    }

    private static List<Component> outputs(BulkScrapMenu.Shown shown) {
        var out = new ArrayList<Component>();
        for (int r = 0; r < shown.materials().length; r++) {
            var material = SmithService.materialFor(r, shown.materials()[r]);
            if (!material.isEmpty()) out.add(Component.translatable(BulkScrapLang.OUTPUT, material.getCount(), material.getHoverName()));
        }
        if (shown.stock() > 0) out.add(Component.translatable(BulkScrapLang.OUTPUT, shown.stock(),
                new ItemStack(RiftfallItems.TEMPERING_STOCK.get()).getHoverName()));
        return out;
    }

    private String fit(String text, int width) {
        return font.width(text) <= width ? text : font.plainSubstrByWidth(text, width - font.width("...")) + "...";
    }
}
