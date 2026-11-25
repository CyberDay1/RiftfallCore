package com.cyberday1.riftfall.client;

import com.cyberday1.riftfall.home.npc.ResidentRank;
import com.cyberday1.riftfall.home.npc.quarry.QuarryLang;
import com.cyberday1.riftfall.home.npc.quarry.QuarryMenu;
import com.cyberday1.riftfall.home.npc.quarry.QuarryService;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.ArrayList;
import java.util.List;

/** The Quarry Specialist's page: a scrolling list of recipes, the survey and swaps; the numbers are the server's quote. */
public final class QuarryScreen extends AbstractContainerScreen<QuarryMenu> {
    private static final int ROWS = 6, ROW_H = 14, LIST_X = 10, LIST_Y = 26, LIST_W = 148;
    private record Row(int selection, Component label, ItemStack icon) {}
    private final StellarWorkshop.Action[] rows = new StellarWorkshop.Action[ROWS];
    private final List<Row> entries = new ArrayList<>();
    private StellarWorkshop.Action confirm, up, down;
    private int scroll, pendingRevision = -1, pendingTicks;
    private boolean scrolledToStart;

    public QuarryScreen(QuarryMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 310; imageHeight = 238;
        inventoryLabelX = 74; inventoryLabelY = 144;
    }

    @Override protected void init() {
        super.init();
        addRenderableWidget(new StellarWorkshop.Action(leftPos + imageWidth - StellarWorkshop.BACK_RIGHT, topPos + 5, 42, 17,
                CommonComponents.GUI_BACK, b -> send(QuarryMenu.BACK), false));
        for (int i = 0; i < ROWS; i++) {
            final int row = i;
            rows[i] = addRenderableWidget(new StellarWorkshop.Action(leftPos + LIST_X, topPos + LIST_Y + i * ROW_H, LIST_W, ROW_H - 1,
                    Component.empty(), b -> pick(row), false));
        }
        up = addRenderableWidget(new StellarWorkshop.Action(leftPos + LIST_X, topPos + LIST_Y + ROWS * ROW_H + 1, 72, 12,
                Component.translatable(QuarryLang.SCROLL_UP), b -> scroll = Math.max(0, scroll - 1), false));
        down = addRenderableWidget(new StellarWorkshop.Action(leftPos + LIST_X + 76, topPos + LIST_Y + ROWS * ROW_H + 1, 72, 12,
                Component.translatable(QuarryLang.SCROLL_DOWN), b -> scroll++, false));
        confirm = addRenderableWidget(new StellarWorkshop.Action(leftPos + 213, topPos + 120, 87, 16,
                Component.translatable(QuarryLang.CONFIRM), b -> {
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

    private void rebuild() {
        entries.clear();
        var recipes = menu.recipes();
        for (int i = 0; i < recipes.size(); i++) {
            var r = recipes.get(i).value();
            var name = r.result().getHoverName().copy().append(" x" + r.result().getCount());
            entries.add(new Row(i, r.ordered() ? Component.translatable(QuarryLang.ENTRY_ORDER, name) : name, r.result()));
        }
        entries.add(new Row(QuarryService.SURVEY, Component.translatable(QuarryLang.ENTRY_SURVEY), new ItemStack(net.minecraft.world.item.Items.SPYGLASS)));
        if (menu.rank().atLeast(ResidentRank.JOURNEYMAN)) {
            var targets = menu.targets();
            for (int i = 0; i < targets.size(); i++) {
                var stack = new ItemStack(targets.get(i));
                entries.add(new Row(QuarryService.SWAP + i, Component.translatable(QuarryLang.ENTRY_SWAP, stack.getHoverName()), stack));
            }
        }
        if (!scrolledToStart && !entries.isEmpty() && menu.selection() != 0) {
            for (int i = 0; i < entries.size(); i++) if (entries.get(i).selection() == menu.selection()) scroll = i;
            scrolledToStart = true;
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
            int target = row.selection() - QuarryService.SWAP;
            String tip = target < 0 ? null : menu.surveyed(target) ? QuarryLang.ENTRY_SURVEYED : menu.charted(target) ? QuarryLang.ENTRY_CHARTED : null;
            rows[i].setTooltip(tip == null ? null : Tooltip.create(Component.translatable(tip)));
        }
        up.active = scroll > 0;
        down.active = scroll + ROWS < entries.size();
        var shown = menu.shown();
        confirm.active = pendingRevision < 0 && QuarryService.READY.equals(shown.status());
        confirm.setMessage(Component.translatable(pendingRevision >= 0 ? com.cyberday1.riftfall.home.npc.ResidentLang.WORKING
                : shown.ordered() ? QuarryLang.START_ORDER : QuarryLang.CONFIRM));
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
        StellarWorkshop.slab(g, leftPos + 162, topPos + 25, 60, 47, 0xFF1A2030);
        StellarWorkshop.slab(g, leftPos + 232, topPos + 25, 32, 30, 0xFF1A2030);
        StellarWorkshop.slab(g, leftPos + 270, topPos + 25, 32, 40, 0xFF1A2030);
        for (var slot : menu.slots) StellarWorkshop.well(g, leftPos + slot.x, topPos + slot.y, slot.index < menu.inputCount());
        StellarWorkshop.star(g, leftPos + 29, topPos + 174, 0xFF879CB5);
        StellarWorkshop.star(g, leftPos + 27, topPos + 214, 0xFFDEC68D);
    }

    @Override protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, StellarWorkshop.title(font, title, imageWidth - StellarWorkshop.BACK_RIGHT), StellarWorkshop.TITLE_X, 9, StellarWorkshop.INK, false);
        g.drawString(font, I18n.get(QuarryLang.SLOT_INPUTS), 166, 26, StellarWorkshop.MUTED, false);
        g.drawString(font, I18n.get(QuarryLang.SLOT_SHARDS), 234, 26, StellarWorkshop.MUTED, false);
        g.drawString(font, I18n.get(QuarryLang.SLOT_OUTPUT), 276, 30, StellarWorkshop.MUTED, false);
        boolean ready = QuarryService.READY.equals(menu.shown().status());
        g.drawString(font, fit(status(), 290), 10, 112, ready ? StellarWorkshop.MUTED : StellarWorkshop.ERROR, false);
        g.drawString(font, fit(info(), 198), 10, 123, StellarWorkshop.INK, false);
        g.drawString(font, fit(cost(), 198), 10, 134, ready ? StellarWorkshop.GOLD : StellarWorkshop.MUTED, false);
        g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, StellarWorkshop.MUTED, false);
    }

    private String status() {
        if (pendingRevision >= 0) return I18n.get(QuarryLang.WAITING);
        return I18n.get(QuarryLang.status(menu.shown().status()));
    }

    /** What the selection needs and makes. */
    private String info() {
        int selection = menu.selection();
        if (selection == QuarryService.SURVEY) return I18n.get(QuarryLang.SURVEY_HINT);
        var recipes = menu.recipes();
        if (selection >= 0 && selection < recipes.size()) {
            var r = recipes.get(selection).value();
            var parts = new ArrayList<String>();
            for (var input : r.inputs()) parts.add(input.count() + " " + name(input.ingredient()));
            String needs = I18n.get(QuarryLang.NEEDS, String.join(", ", parts));
            var shown = menu.shown();
            return shown.ordered() ? needs + " | " + I18n.get(QuarryLang.ORDER_TIME, shown.runs(), shown.minutes()) : needs;
        }
        int target = selection - QuarryService.SWAP;
        var targets = menu.targets();
        if (target >= 0 && target < targets.size()) return new ItemStack(targets.get(target)).getHoverName().getString();
        return "";
    }

    private static String name(Ingredient ingredient) {
        var items = ingredient.getItems();
        if (items.length == 0) return "?";
        return items.length == 1 ? items[0].getHoverName().getString() : items[0].getHoverName().getString() + "+";
    }

    private String cost() {
        var s = menu.shown();
        String shards = I18n.get(QuarryLang.COST_SHARDS, s.shards(), s.balance());
        int target = menu.selection() - QuarryService.SWAP;
        var targets = menu.targets();
        if (target >= 0 && target < targets.size()) {
            boolean rare = new ItemStack(targets.get(target)).is(com.cyberday1.riftfall.init.RiftfallTags.RARE_CRYSTALS);
            return I18n.get(QuarryLang.SWAP_COST, QuarryService.swapCost(rare, menu.path()), QuarryService.SWAP_SHARDS);
        }
        return s.ordered() ? shards + " | " + I18n.get(QuarryLang.ORDER_SLOTS, s.freeOrders()) : shards;
    }

    private String fit(String text, int width) {
        return font.width(text) <= width ? text : font.plainSubstrByWidth(text, width - font.width("...")) + "...";
    }
}
