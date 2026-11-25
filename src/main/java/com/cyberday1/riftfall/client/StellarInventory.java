package com.cyberday1.riftfall.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import java.util.function.BooleanSupplier;

/** Shared inventory art. Vanilla and optional mod screens still own their menus and interactions. */
public final class StellarInventory {
    // Scoped on the render thread; children keep their native item art and interaction code.
    private static boolean inventoryRecipes;
    private StellarInventory() {}
    public static int labelColor() { return StellarWorkshop.GOLD; }

    public static boolean inventoryRecipes(Object menu) {
        // Curios replaces InventoryMenu outright. Match its name without loading an optional class.
        return menu instanceof InventoryMenu || menu instanceof net.minecraft.world.inventory.CraftingMenu || menu != null && menu.getClass().getName().equals(
                "top.theillusivec4.curios.common.inventory.container.CuriosContainer");
    }

    public static void slot(GuiGraphics g, int x, int y) { StellarWorkshop.well(g, x, y, false); }

    public static void panel(GuiGraphics g, int x, int y, int w, int h) {
        if (w < 20 || h < 24) control(g, x, y, w, h, false, false);
        else StellarWorkshop.surface(g, x, y, w, h);
    }

    public static void control(GuiGraphics g, int x, int y, int w, int h, boolean lit, boolean selected) {
        int color = lit ? 0xFF35364A : 0xFF1D2334;
        // Visibility toggles can be as small as five pixels; large slab corners cannot fit them.
        if (w < 12 || h < 12) {
            g.fill(x + 1, y, x + w - 1, y + h, color);
            g.fill(x, y + 1, x + w, y + h - 1, color);
        } else StellarWorkshop.slab(g, x, y, w, h, color);
        if (selected) g.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, 0xFFDEC68D);
    }

    public static void aetherBackground(GuiGraphics g, int x, int y, int w, int h,
                                        net.minecraft.world.inventory.AbstractContainerMenu menu) {
        panel(g, x, y, w, h);
        StellarWorkshop.slab(g, x + 9, y + 8, 49, 70, 0xFF1D2030);
        for (var slot : menu.slots) if (slot.isActive()) slot(g, x + slot.x, y + slot.y);
        // Crafting output and creative delete slot remain native slots with native tooltips.
        arrow(g, x + 149, y + 36, false);
        if (w > 176) {
            slot(g, x + 172, y + 142);
            deleteMark(g, x + 172, y + 142);
        }
    }

    public static void creativeBackground(GuiGraphics g, int x, int y, int w, int h,
            net.minecraft.world.inventory.AbstractContainerMenu menu, boolean inventory, boolean scroll) {
        panel(g, x, y, w, h);
        if (inventory) {
            StellarWorkshop.slab(g, x + 73, y + 6, 32, 43, 0xFF1D2030);
        }
        for (var slot : menu.slots) if (slot.isActive() && slot.x >= 0 && slot.y >= 0
                && slot.x + 16 <= w && slot.y + 16 <= h) slot(g, x + slot.x, y + slot.y);
        if (inventory) deleteMark(g, x + 173, y + 112);
        if (scroll) g.fill(x + 178, y + 18, x + 184, y + 130, 0xFF090D17);
    }

    private static void deleteMark(GuiGraphics g, int x, int y) {
        for (int i = 0; i < 8; i++) {
            g.fill(x + 4 + i, y + 4 + i, x + 5 + i, y + 5 + i, 0xFFF2B6A5);
            g.fill(x + 11 - i, y + 4 + i, x + 12 - i, y + 5 + i, 0xFFF2B6A5);
        }
    }

    public static void arrow(GuiGraphics g, int x, int y, boolean left) {
        for (int i = 0; i < 4; i++) {
            int dx = left ? i : 3 - i;
            g.fill(x + dx, y - i, x + dx + 1, y + i + 1, 0xFFDEC68D);
        }
    }

    /** Only Accessories' structural textures; icons and cosmetic overlays keep their native art. */
    public static boolean accessoryTexture(GuiGraphics g, ResourceLocation texture, int x, int y, int w, int h) {
        if (!texture.getNamespace().equals("accessories")) return false;
        String p = texture.getPath();
        if (!p.startsWith("textures/gui/theme/")) return false;
        if (p.contains("/player_inv/") || p.contains("/entity_view/") || p.endsWith("/crafting_grid.png")) {
            panel(g, x, y, w, h);
            return true;
        }
        if (p.endsWith("/toggle_background.png")) {
            control(g, x, y, w, h, false, false);
            return true;
        }
        return false;
    }

    public static boolean accessoryPatch(GuiGraphics g, ResourceLocation texture, int x, int y, int w, int h) {
        if (!texture.getNamespace().equals("accessories") || !texture.getPath().startsWith("theme/")) return false;
        String p = texture.getPath();
        if (p.endsWith("/panel")) panel(g, x, y, w, h);
        else if (p.endsWith("/inset") || p.endsWith("/track")) {
            g.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xFF090D17);
        } else if (p.contains("/button/") || p.contains("/scrollbar/")) {
            control(g, x, y, w, h, p.contains("hovered"), p.contains("enabled") || p.contains("/scrollbar/vanilla_"));
        } else return false;
        return true;
    }

    public static void renderRecipes(boolean inventory, Runnable render) {
        boolean previous = inventoryRecipes;
        inventoryRecipes = inventory;
        try { render.run(); } finally { inventoryRecipes = previous; }
    }

    public static boolean recipeControl(GuiGraphics g, ResourceLocation sprite, int x, int y, int w, int h,
                                        boolean hover, boolean focused) {
        if (!inventoryRecipes || !sprite.getNamespace().equals("minecraft")) return false;
        String path = sprite.getPath();
        boolean tab = path.startsWith("recipe_book/tab"), slot = path.startsWith("recipe_book/slot"),
                filter = path.startsWith("recipe_book/filter"), arrow = path.startsWith("recipe_book/page_");
        if (!tab && !slot && !filter && !arrow) return false;
        boolean selected = path.contains("selected"), craftable = slot && !path.contains("uncraftable");
        StellarWorkshop.slab(g, x, y, w, h, hover || selected ? 0xFF35364A : craftable ? 0xFF2C2B3B : 0xFF1D2334);
        if (selected || craftable) g.fill(x + 5, y + h - 1, x + w - 5, y + h, 0xFFAA9261);
        if (slot && path.contains("many")) g.fill(x + 5, y + 2, x + w - 5, y + 3, 0xFF867655);
        if (filter) {
            g.renderItem(new ItemStack(Items.CRAFTING_TABLE), x + w - 16, y);
            if (path.contains("enabled")) {
                g.fill(x + 3, y + 8, x + 5, y + 10, 0xFFDEC68D);
                g.fill(x + 5, y + 6, x + 7, y + 9, 0xFFDEC68D);
            } else for (int i = 0; i < 3; i++) g.fill(x + 3, y + 4 + i * 3, x + 7, y + 5 + i * 3, 0xFFB5BAD0);
        }
        if (arrow) {
            int direction = path.contains("forward") ? 1 : -1;
            for (int i = 0; i < 4; i++) g.fill(x + w / 2 + direction * (2 - i), y + h / 2 - i,
                    x + w / 2 + direction * (2 - i) + 1, y + h / 2 + i + 1, 0xFFDEC68D);
        }
        if (focused) g.renderOutline(x, y, w, h, 0xFFDEC68D);
        return true;
    }

    public static void recipeBackground(GuiGraphics g, int x, int y, int width, int height) {
        StellarWorkshop.surface(g, x, y, width, height);
        book(g, x + 8, y + 13);
    }

    public static void background(GuiGraphics g, int x, int y, int width, int height,
                                  net.minecraft.world.inventory.AbstractContainerMenu menu) {
        StellarWorkshop.surface(g, x, y, width, height);
        StellarWorkshop.slab(g, x + 26, y + 8, 49, 70, 0xFF1D2030);
        g.fill(x + 34, y + 76, x + 67, y + 77, 0xFF87734F);
        for (var slot : menu.slots) if (slot.isActive() && slot.x >= 0 && slot.y >= 0
                && slot.x + 16 <= width && slot.y + 16 <= height)
            StellarWorkshop.well(g, x + slot.x, y + slot.y, slot.index <= 8 || slot.index == 45);
        g.fill(x + 136, y + 35, x + 141, y + 37, 0xFFDEC68D);
        g.fill(x + 140, y + 33, x + 142, y + 39, 0xFFDEC68D);
        g.fill(x + 142, y + 35, x + 143, y + 37, 0xFFDEC68D);
    }

    private static final String RECIPE_BOOK = "screen.riftfall.inventory.recipe_book";

    public static ImageButton recipeButton(int x, int y, int width, int height, WidgetSprites sprites,
                                           Button.OnPress press, BooleanSupplier open) {
        var button = new ImageButton(x, y, width, height, sprites, press, Component.translatable(RECIPE_BOOK)) {
            @Override public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partial) {
                StellarWorkshop.slab(g, getX(), getY(), getWidth(), getHeight(), isHoveredOrFocused() ? 0xFF35364A : 0xFF1D2334);
                book(g, getX() + 4, getY() + 3);
                if (open.getAsBoolean()) g.fill(getX() + 5, getBottom() - 1, getRight() - 5, getBottom(), 0xFFDEC68D);
                if (isFocused()) g.renderOutline(getX(), getY(), getWidth(), getHeight(), 0xFFDEC68D);
            }
        };
        button.setTooltip(Tooltip.create(Component.translatable(RECIPE_BOOK)));
        return button;
    }

    /** A functional recipe-book pictogram instead of another decorative rune. */
    private static void book(GuiGraphics g, int x, int y) {
        g.fill(x, y + 1, x + 5, y + 10, 0xFFB5BAD0);
        g.fill(x + 6, y + 1, x + 11, y + 10, 0xFFDEC68D);
        g.fill(x + 5, y + 3, x + 6, y + 11, 0xFF867655);
        for (int row = 0; row < 3; row++) {
            g.fill(x + 1, y + 3 + row * 2, x + 4, y + 4 + row * 2, 0xFF343D52);
            g.fill(x + 7, y + 3 + row * 2, x + 10, y + 4 + row * 2, 0xFF343D52);
        }
    }
}
