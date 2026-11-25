package com.cyberday1.riftfall.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;

/**
 * A resident page that shrinks to fit a window too small for it, the way the Rift Codex book does: one scale from
 * {@link ScreenFit}, the page drawn under it, the mouse divided by it. Pages lay out at their own size from
 * {@code leftPos}/{@code topPos} and draw in {@link #renderFitted}. Tooltips draw at full size at the mouse.
 */
abstract class FittedContainerScreen<M extends AbstractContainerMenu> extends AbstractContainerScreen<M> {

    @FunctionalInterface
    interface At {
        void draw(int x, int y);
    }

    private float scale = 1.0F;

    protected FittedContainerScreen(M menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected void init() {
        scale = ScreenFit.page(width, height, imageWidth, imageHeight);
        super.init();
        leftPos = ScreenFit.origin(width, imageWidth, scale);
        topPos = ScreenFit.origin(height, imageHeight, scale);
    }

    private int in(double screen) {
        return (int) Math.floor(screen / scale);
    }

    @Override
    public final void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        g.pose().pushPose();
        g.pose().scale(scale, scale, 1.0F);
        renderFitted(g, in(mouseX), in(mouseY), partial);
        g.pose().popPose();
    }

    /** The page's render, in page pixels: the mouse is already divided by the scale. */
    protected void renderFitted(GuiGraphics g, int mouseX, int mouseY, float partial) {
        super.render(g, mouseX, mouseY, partial);
    }

    /** The dim fills the window, not the scaled frame; the page's own art draws scaled as before. */
    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partial) {
        g.pose().pushPose();
        g.pose().scale(1.0F / scale, 1.0F / scale, 1.0F);
        renderTransparentBackground(g);
        g.pose().popPose();
        renderBg(g, partial, mouseX, mouseY);
    }

    /** Draws at the window's own scale with the mouse back in GUI pixels, as the book draws its tooltips. */
    protected void atScreenScale(GuiGraphics g, int mouseX, int mouseY, At draw) {
        if (scale == 1.0F) {
            draw.draw(mouseX, mouseY);
            return;
        }
        g.pose().pushPose();
        g.pose().scale(1.0F / scale, 1.0F / scale, 1.0F);
        draw.draw(Math.round(mouseX * scale), Math.round(mouseY * scale));
        g.pose().popPose();
    }

    @Override
    protected void renderTooltip(GuiGraphics g, int mouseX, int mouseY) {
        atScreenScale(g, mouseX, mouseY, (x, y) -> super.renderTooltip(g, x, y));
    }

    /**
     * While the page is shrunk, JEI's panels would sit off it: JEI lays out from these in window pixels and draws
     * and clicks outside the page's scale. A zero size is JEI's own "no overlay here".
     */
    @Override
    public int getXSize() {
        return scale < 1.0F ? 0 : super.getXSize();
    }

    @Override
    public int getYSize() {
        return scale < 1.0F ? 0 : super.getYSize();
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        return super.mouseClicked(x / scale, y / scale, button);
    }

    @Override
    public boolean mouseReleased(double x, double y, int button) {
        return super.mouseReleased(x / scale, y / scale, button);
    }

    @Override
    public boolean mouseDragged(double x, double y, int button, double dragX, double dragY) {
        return super.mouseDragged(x / scale, y / scale, button, dragX / scale, dragY / scale);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        return super.mouseScrolled(x / scale, y / scale, scrollX, scrollY);
    }

    @Override
    public void mouseMoved(double x, double y) {
        super.mouseMoved(x / scale, y / scale);
    }

    @Override
    public boolean isMouseOver(double x, double y) {
        return super.isMouseOver(x / scale, y / scale);
    }
}
