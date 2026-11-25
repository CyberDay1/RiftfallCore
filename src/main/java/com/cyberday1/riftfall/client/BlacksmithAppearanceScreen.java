package com.cyberday1.riftfall.client;

import com.cyberday1.riftfall.init.RiftfallItems;
import com.cyberday1.riftfall.tool.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class BlacksmithAppearanceScreen extends AbstractContainerScreen<BlacksmithAppearanceMenu> {
    private final StellarWorkshop.Action[] choices = new StellarWorkshop.Action[BlacksmithAppearanceMenu.PER_PAGE];
    private StellarWorkshop.Action previous, next, apply;
    private int pending = -1, pendingTicks;
    private ItemStack model = ItemStack.EMPTY, modelTarget = ItemStack.EMPTY;
    private ToolAppearances.Skin modelSkin;
    public BlacksmithAppearanceScreen(BlacksmithAppearanceMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title); imageWidth = 310; imageHeight = 238;
    }
    @Override protected void init() {
        super.init();
        addRenderableWidget(new StellarWorkshop.Action(leftPos + imageWidth - StellarWorkshop.BACK_RIGHT, topPos + 5, 42, 17,
                CommonComponents.GUI_BACK, b -> send(BlacksmithAppearanceMenu.BACK), false));
        for (int i = 0; i < choices.length; i++) {
            final int row = i;
            choices[i] = addRenderableWidget(new StellarWorkshop.Action(leftPos + 10 + i % 2 * 147,
                    topPos + 62 + i / 2 * 18, 143, 17, Component.empty(), b -> {
                        var skins = menu.pageSkins();
                        if (row < skins.size()) send(BlacksmithAppearanceMenu.SELECT + ToolAppearances.ALL.indexOf(skins.get(row)));
                    }, false));
        }
        previous = addRenderableWidget(new StellarWorkshop.Action(leftPos + 10, topPos + 128, 48, 16,
                Component.translatable(ToolLang.LOOKS_PREVIOUS), b -> send(BlacksmithAppearanceMenu.PREVIOUS), false));
        next = addRenderableWidget(new StellarWorkshop.Action(leftPos + 64, topPos + 128, 40, 16,
                Component.translatable(ToolLang.LOOKS_NEXT), b -> send(BlacksmithAppearanceMenu.NEXT), false));
        apply = addRenderableWidget(new StellarWorkshop.Action(leftPos + 213, topPos + 128, 87, 16,
                Component.translatable(ToolLang.LOOKS_APPLY), b -> { pending = menu.applyButton(); pendingTicks = 0; send(pending); }, true));
    }
    private void send(int id) {
        if (minecraft != null && minecraft.gameMode != null)
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
    }
    @Override protected void containerTick() { if (pending >= 0 && ++pendingTicks >= 40) pending = -1; }
    private String status() {
        if (pending >= 0) return I18n.get(ToolLang.LOOKS_STATUS_APPLYING);
        if (menu.applied()) return I18n.get(ToolLang.LOOKS_STATUS_APPLIED);
        if (!menu.unlocked(menu.selected())) return I18n.get(ToolLang.LOOKS_STATUS_LOCKED, I18n.get(menu.selected().key()));
        if (!LootingToolItem.isTool(menu.input.getItem(0))) return I18n.get(ToolLang.LOOKS_STATUS_NO_PICK);
        return I18n.get(menu.canApply() ? ToolLang.LOOKS_STATUS_FREE : ToolLang.LOOKS_STATUS_CURRENT);
    }
    @Override public void render(GuiGraphics g, int x, int y, float partial) {
        if (pending != menu.applyButton()) pending = -1;
        var skins = menu.pageSkins();
        for (int i = 0; i < choices.length; i++) {
            var button = choices[i]; button.visible = i < skins.size();
            if (!button.visible) continue;
            var skin = skins.get(i);
            boolean unlocked = menu.unlocked(skin);
            button.active = pending < 0; // Locked entries can be previewed; the server rejects their application.
            button.selected(skin.equals(menu.selected()));
            button.setMessage(unlocked ? skin.displayName() : Component.translatable(ToolLang.LOOKS_LOCKED, skin.displayName()));
            button.icon(new ItemStack(BuiltInRegistries.ITEM.containsKey(skin.item())
                    ? BuiltInRegistries.ITEM.get(skin.item()) : Items.IRON_PICKAXE));
            button.setTooltip(Tooltip.create(Component.translatable(
                    unlocked ? ToolLang.LOOKS_UNLOCKED_HINT : ToolLang.LOOKS_LOCKED_HINT, skin.displayName())));
        }
        previous.active = pending < 0 && menu.page() > 0;
        next.active = pending < 0 && menu.page() + 1 < menu.pageCount();
        apply.active = pending < 0 && menu.canApply();
        apply.setMessage(Component.translatable(pending >= 0 ? ToolLang.LOOKS_APPLYING : ToolLang.LOOKS_APPLY));
        apply.setTooltip(Tooltip.create(Component.translatable(ToolLang.LOOKS_APPLY_HINT, status())));
        super.render(g, x, y, partial);
        renderTooltip(g, x, y);
        if (isHovering(10, 49, 290, 10, x, y)) g.renderTooltip(font, Component.literal(status()), x, y);
        else if (isHovering(51, 27, 198, 19, x, y))
            g.renderTooltip(font, Component.translatable(ToolLang.LOOKS_PREVIEW, menu.selected().displayName()), x, y);
    }
    @Override protected void renderBg(GuiGraphics g, float partial, int x, int y) {
        StellarWorkshop.frame(g, leftPos, topPos, imageWidth, imageHeight);
        StellarWorkshop.slab(g, leftPos + 23, topPos + 25, 35, 24, 0xFF1A2030);
        for (var slot : menu.slots) StellarWorkshop.well(g, leftPos + slot.x, topPos + slot.y, slot.index == 0);
        var target = menu.input.getItem(0);
        if (modelSkin != menu.selected() || !ItemStack.matches(target, modelTarget)) {
            modelSkin = menu.selected(); modelTarget = target.copy();
            model = LootingToolItem.isTool(target) ? target.copy() : new ItemStack(RiftfallItems.LOOTING_PICK.get());
            LootingToolItem.applyAppearance(model, modelSkin.item().toString());
        }
        g.pose().pushPose(); g.pose().translate(leftPos + 269, topPos + 27, 0); g.pose().scale(1.25f, 1.25f, 1);
        g.renderItem(model, 0, 0); g.pose().popPose();
    }
    @Override protected void renderLabels(GuiGraphics g, int x, int y) {
        // The page name sits right-aligned before Back; the resident's "Name, Role" title gets the rest of the row.
        String page = I18n.get(ToolLang.LOOKS_HEADER);
        int pageX = imageWidth - StellarWorkshop.BACK_RIGHT - StellarWorkshop.HEADER_GAP - font.width(page);
        g.drawString(font, StellarWorkshop.title(font, title, pageX), StellarWorkshop.TITLE_X, 9, StellarWorkshop.INK, false);
        g.drawString(font, page, pageX, 9, StellarWorkshop.GOLD, false);
        var target = menu.input.getItem(0);
        String current = I18n.get(LootingToolItem.isTool(target) ? LootingToolItem.appearance(target).key() : ToolLang.LOOKS_NO_PICK);
        g.drawString(font, fit(I18n.get(ToolLang.LOOKS_CURRENT, current), 199), 61, 27, StellarWorkshop.MUTED, false);
        g.drawString(font, fit(I18n.get(ToolLang.LOOKS_PREVIEW, I18n.get(menu.selected().key())), 199), 61, 39, StellarWorkshop.INK, false);
        g.drawString(font, fit(status(), 290), 10, 50, menu.unlocked(menu.selected()) ? StellarWorkshop.MUTED : StellarWorkshop.GOLD, false);
        g.drawString(font, I18n.get(ToolLang.LOOKS_PAGE, menu.page() + 1, menu.pageCount()), 10, 118, StellarWorkshop.MUTED, false);
        g.drawString(font, I18n.get(ToolLang.LOOKS_COST), 124, 132, StellarWorkshop.GOLD, false);
        g.drawString(font, playerInventoryTitle, 74, 143, StellarWorkshop.MUTED, false);
    }
    private String fit(String text, int width) {
        return font.width(text) <= width ? text : font.plainSubstrByWidth(text, width - font.width("...")) + "...";
    }
}
