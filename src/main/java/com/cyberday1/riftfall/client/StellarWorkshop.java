package com.cyberday1.riftfall.client;

import com.cyberday1.riftfall.RiftfallCore;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Small drawing primitives for the native workshop; all coordinates are logical GUI pixels. */
final class StellarWorkshop {
    static final int INK = 0xEEEAF6, MUTED = 0xB5BAD0, GOLD = 0xDEC68D, ERROR = 0xF2B6A5;
    private static final ResourceLocation SUN = RiftfallCore.id("textures/gui/workshop/sun.png");
    private static final ResourceLocation SCRIPT = RiftfallCore.id("textures/gui/workshop/script.png");
    /** Service pages' header row: the title starts at TITLE_X; Back sits BACK_RIGHT in from the right edge. */
    static final int TITLE_X = 36, BACK_RIGHT = 52, HEADER_GAP = 6;
    private StellarWorkshop() {}

    /** The header title cut to end {@link #HEADER_GAP} before {@code right}, so it never runs under Back. */
    static String title(net.minecraft.client.gui.Font font, Component title, int right) {
        String text = title.getString();
        int room = right - HEADER_GAP - TITLE_X;
        if (font.width(text) <= room) return text;
        return font.plainSubstrByWidth(text, Math.max(0, room - font.width("..."))) + "...";
    }

    /** Worn, gently stepped corners. Never clips text, item slots, or keyboard focus. */
    static void slab(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x + 4, y, x + w - 6, y + 1, color);
        g.fill(x + 2, y + 1, x + w - 3, y + 3, color);
        g.fill(x + 1, y + 3, x + w - 1, y + 5, color);
        g.fill(x, y + 5, x + w, y + h - 5, color);
        g.fill(x + 1, y + h - 5, x + w - 2, y + h - 2, color);
        g.fill(x + 3, y + h - 2, x + w - 4, y + h - 1, color);
        g.fill(x + 5, y + h - 1, x + w - 7, y + h, color);
    }

    static void surface(GuiGraphics g, int x, int y, int w, int h) {
        slab(g, x + 1, y + 1, w, h, 0x70050810);
        slab(g, x, y, w, h, 0xFF111522);
        g.fillGradient(x + 6, y + 2, x + w - 7, y + 23, 0xFF29273F, 0xFF111522);
    }

    static void frame(GuiGraphics g, int x, int y, int w, int h) {
        surface(g, x, y, w, h);
        star(g, x + 174, y + 9, 0xFF869EBB);
        star(g, x + 186, y + 18, 0xFF6B638E);
        star(g, x + w - 16, y + 8, 0xFFDEC68D);
        sun(g, x + 12, y + 5, 16);
    }

    static void star(GuiGraphics g, int x, int y, int color) {
        g.fill(x, y, x + 1, y + 1, color);
    }
    static void sun(GuiGraphics g, int x, int y, int size) {
        g.blit(SUN, x, y, size, size, 0, 0, 64, 64, 64, 64);
    }
    static void script(GuiGraphics g, int x, int y, int w, int h) {
        g.blit(SCRIPT, x, y, w, h, 0, 0, 128, 40, 128, 40);
    }
    static void well(GuiGraphics g, int x, int y, boolean input) {
        int fill = input ? 0xFF090D17 : 0xFF1D2334;
        // A 16px well leaves the native 18px slot pitch visibly separated.
        g.fill(x + 1, y, x + 15, y + 16, fill);
        g.fill(x, y + 1, x + 16, y + 15, fill);
        g.fill(x + 2, y, x + 14, y + 1, input ? 0xFF867655 : 0xFF343D52);
    }

    static final class Action extends Button {
        private boolean selected;
        private final boolean primary;
        private ItemStack icon = ItemStack.EMPTY;
        Action(int x, int y, int w, int h, Component message, OnPress press, boolean primary) {
            super(x, y, w, h, message, press, DEFAULT_NARRATION);
            this.primary = primary;
        }
        void selected(boolean value) { selected = value; }
        void icon(ItemStack value) { icon = value; }
        @Override protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partial) {
            boolean lit = active && isHoveredOrFocused();
            int fill = lit ? 0xFF35364A : selected || primary ? 0xFF2C2B3B : 0xFF1D2334;
            slab(g, getX(), getY(), width, height, fill);
            if (selected || primary) g.fill(getX() + 7, getY() + height - 1,
                    getX() + width - 8, getY() + height, active ? 0xFFAA9261 : 0xFF635B50);
            if (selected) g.fill(getX() + 4, getY() + height / 2, getX() + 6, getY() + height / 2 + 2, 0xFFDEC68D);
            if (isFocused()) g.renderOutline(getX(), getY(), width, height, 0xFFDEC68D);
            // Keep vanilla scrolling labels, keyboard activation, tooltips and narration.
            Font font = Minecraft.getInstance().font;
            if (!icon.isEmpty()) {
                g.renderItem(icon, getX() + 6, getY() + (height - 16) / 2);
                label(g, font, getMessage(), getX() + 26, getY(), getRight() - 6, getBottom(), active ? INK : MUTED);
            } else {
                int margin = selected ? 10 : 6;
                label(g, font, getMessage(), getX() + margin, getY(), getRight() - margin, getBottom(), active ? INK : MUTED);
            }
        }
    }

    /**
     * Vanilla's scrolling button label, with its scissor box carried through the pose: {@code enableScissor} takes
     * window GUI pixels, and a page that shrinks to fit ({@link FittedContainerScreen}) draws its buttons scaled.
     */
    static void label(GuiGraphics g, Font font, Component text, int x0, int y0, int x1, int y1, int color) {
        int width = font.width(text);
        int y = (y0 + y1 - 9) / 2 + 1;
        int room = x1 - x0;
        if (width <= room) {
            g.drawCenteredString(font, text, Mth.clamp((x0 + x1) / 2, x0 + width / 2, x1 - width / 2), y, color);
            return;
        }
        int over = width - room;
        double seconds = Util.getMillis() / 1000.0;
        double period = Math.max(over * 0.5, 3.0);
        double swing = Math.sin(Math.PI / 2 * Math.cos(Math.PI * 2 * seconds / period)) / 2.0 + 0.5;
        Matrix4f pose = g.pose().last().pose();
        Vector3f a = pose.transformPosition(x0, y0, 0.0F, new Vector3f());
        Vector3f b = pose.transformPosition(x1, y1, 0.0F, new Vector3f());
        g.enableScissor((int) Math.floor(a.x), (int) Math.floor(a.y), (int) Math.ceil(b.x), (int) Math.ceil(b.y));
        g.drawString(font, text, x0 - (int) Mth.lerp(swing, 0.0, over), y, color);
        g.disableScissor();
    }
}
