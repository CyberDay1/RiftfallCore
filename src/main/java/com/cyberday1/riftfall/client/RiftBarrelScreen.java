package com.cyberday1.riftfall.client;

import com.cyberday1.riftfall.block.barrel.RiftBarrelMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Nine barrel rows, six visible, with the shared stellar shell and unchanged scroll mapping. */
public class RiftBarrelScreen extends AbstractContainerScreen<RiftBarrelMenu> {

    private boolean dragging;

    public RiftBarrelScreen(RiftBarrelMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = RiftBarrelMenu.WIDTH;
        this.imageHeight = RiftBarrelMenu.HEIGHT;
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        StellarWorkshop.surface(graphics, leftPos, topPos, imageWidth, imageHeight);
        for (int row = 0; row < RiftBarrelMenu.VISIBLE_ROWS; row++) {
            for (int column = 0; column < RiftBarrelMenu.COLUMNS; column++) {
                well(graphics, leftPos + RiftBarrelMenu.LEFT + column * RiftBarrelMenu.SLOT,
                        topPos + RiftBarrelMenu.TOP + row * RiftBarrelMenu.SLOT);
            }
        }
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                well(graphics, leftPos + RiftBarrelMenu.LEFT + column * RiftBarrelMenu.SLOT,
                        topPos + RiftBarrelMenu.PLAYER_TOP + row * RiftBarrelMenu.SLOT);
            }
        }
        for (int column = 0; column < 9; column++) {
            well(graphics, leftPos + RiftBarrelMenu.LEFT + column * RiftBarrelMenu.SLOT,
                    topPos + RiftBarrelMenu.HOTBAR_TOP);
        }
        scrollbar(graphics);
    }

    /** The trough, then the grip at whichever of the four rows the view is on. */
    private void scrollbar(GuiGraphics graphics) {
        int x = leftPos + BarrelScrollbar.X;
        int y = topPos + BarrelScrollbar.TOP;
        int width = BarrelScrollbar.WIDTH;
        int grip = BarrelScrollbar.GRIP_HEIGHT;
        graphics.fill(x + 2, y, x + width - 2, y + BarrelScrollbar.HEIGHT, 0xFF090D17);
        int gripY = BarrelScrollbar.gripTop(topPos, menu.scrollRow());
        StellarWorkshop.slab(graphics, x, gripY, width, grip, 0xFF35364A);
        graphics.fill(x + 3, gripY + grip / 2, x + width - 3, gripY + grip / 2 + 1, 0xFFDEC68D);
    }

    @Override protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        String name = title.getString();
        if (font.width(name) > 143) name = font.plainSubstrByWidth(name, 131) + "...";
        graphics.drawString(font, name, 8, 5, StellarWorkshop.GOLD, false);
        graphics.drawString(font, playerInventoryTitle, 8, inventoryLabelY, StellarWorkshop.MUTED, false);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        if (deltaY != 0 && scrollTo(menu.scrollRow() - (int) Math.signum(deltaY))) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, deltaX, deltaY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (overBar(mouseX, mouseY)) {
            dragging = true;
            dragTo(mouseY);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX,
            double dragY) {
        if (dragging) {
            dragTo(mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        dragging = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private boolean overBar(double mouseX, double mouseY) {
        return BarrelScrollbar.overBar(leftPos, topPos, mouseX, mouseY);
    }

    /** Where in the trough the pointer is, as a row. The grip's own height is the dead band. */
    private void dragTo(double mouseY) {
        scrollTo(BarrelScrollbar.rowFor(topPos, mouseY));
    }

    /**
     * Move the view, and tell the server if it moved.
     *
     * <p>
     * The client applies it first so the grid does not wait a round trip, then sends the same row as
     * a menu button. The server re-resolves every click against its own offset, so the two agreeing
     * is what makes a click land on the stack the player is looking at.
     */
    private boolean scrollTo(int row) {
        if (!menu.scrollTo(row)) {
            return false;
        }
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, menu.scrollRow());
        }
        return true;
    }

    private static void well(GuiGraphics graphics, int x, int y) {
        StellarWorkshop.well(graphics, x, y, false);
    }
}
