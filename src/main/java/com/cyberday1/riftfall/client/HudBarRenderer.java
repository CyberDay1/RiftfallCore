package com.cyberday1.riftfall.client;

import com.cyberday1.riftfall.client.HudLayout.Element;
import com.cyberday1.riftfall.client.HudLayout.Slot;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Draws one framed bar with its label and value into a {@link Slot}, the way NeoOrigins'
 * {@code ResourceHudOverlay#renderFramedBar} does: frame, then the fill inside it cut at the level. A vertical
 * bar is the same drawing turned a quarter, so its fill rises from the bottom.
 */
public final class HudBarRenderer {

    static final int LABEL = 0xFFFFFFFF;
    static final int VALUE = 0xFFD8D8D8;

    /** What one bar shows: a level, its words, and the look; {@code tint} 0 draws the strip as it is. */
    public record Reading(Element element, double current, double max, Component label, String value,
                          HudBarStyle style, int tint) {
    }

    private HudBarRenderer() {
    }

    public static void draw(GuiGraphics g, Font font, Slot slot, Reading r, double ticks, double guiScale) {
        var pose = g.pose();
        pose.pushPose();
        pose.translate(slot.rect().x(), slot.rect().y(), 0.0F);
        pose.scale(slot.scale(), slot.scale(), 1.0F);
        int labelW = font.width(r.label());
        int valueW = font.width(r.value());
        if (!slot.vertical()) {
            int[] x = HudLayout.textX(labelW, valueW);
            g.drawString(font, r.label(), x[0], 0, LABEL, true);
            g.drawString(font, r.value(), x[1], 0, VALUE, true);
            bar(g, r, 0, HudLayout.LINE, ticks, guiScale);
        } else {
            int w = HudLayout.VERTICAL_W;
            g.drawString(font, r.label(), (w - labelW) / 2, 0, LABEL, true);
            pose.pushPose();
            pose.translate((w - HudLayout.BAR_H) / 2.0F, HudLayout.LINE + HudLayout.BAR_W, 0.0F);
            pose.mulPose(Axis.ZP.rotationDegrees(-90.0F));
            bar(g, r, 0, 0, ticks, guiScale);
            pose.popPose();
            g.drawString(font, r.value(), (w - valueW) / 2, HudLayout.LINE + HudLayout.BAR_W + 1, VALUE, true);
        }
        pose.popPose();
    }

    private static void bar(GuiGraphics g, Reading r, int x, int y, double ticks, double guiScale) {
        int winX = x + 1;
        int winY = y + 1;
        int winW = HudLayout.BAR_W - 2;
        int winH = HudLayout.BAR_H - 2;
        HudBarStyle s = r.style();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        g.blit(HudBarStyle.FRAME, x, y, 0.0F, 0.0F, HudLayout.BAR_W, HudLayout.BAR_H, HudLayout.BAR_W, HudLayout.BAR_H);
        int fillW = RiftManaHud.filled(r.current(), r.max(), winW);
        if (fillW > 0) {
            if (r.tint() != 0) {
                g.setColor(((r.tint() >> 16) & 0xFF) / 255.0F, ((r.tint() >> 8) & 0xFF) / 255.0F,
                        (r.tint() & 0xFF) / 255.0F, 1.0F);
            }
            if (!s.scrolls) {
                g.blit(s.texture, winX, winY, fillW, winH, 0.0F, 0.0F, fillW, HudBarStyle.STRIP_H, s.width, HudBarStyle.STRIP_H);
            } else {
                double off = HudBarStyle.offset(ticks, s.speed, s.loop, guiScale);
                int whole = (int) off;
                scissor(g, winX, winY, winX + fillW, winY + winH);
                g.pose().pushPose();
                g.pose().translate(-(float) (off - whole), 0.0F, 0.0F);
                for (int dx = -whole; dx <= fillW; dx += s.loop) {
                    g.blit(s.texture, winX + dx, winY, s.loop, winH, 0.0F, 0.0F, s.loop, HudBarStyle.STRIP_H,
                            s.width, HudBarStyle.STRIP_H);
                }
                g.pose().popPose();
                g.disableScissor();
            }
            g.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        }
        RenderSystem.disableBlend();
    }

    /** {@code enableScissor} through the pose: on 1.21.1 it ignores the pose, so a scaled or turned bar would clip wrong. */
    private static void scissor(GuiGraphics g, int x0, int y0, int x1, int y1) {
        Matrix4f m = g.pose().last().pose();
        Vector3f a = m.transformPosition(x0, y0, 0.0F, new Vector3f());
        Vector3f b = m.transformPosition(x1, y1, 0.0F, new Vector3f());
        g.enableScissor((int) Math.floor(Math.min(a.x, b.x)), (int) Math.floor(Math.min(a.y, b.y)),
                (int) Math.ceil(Math.max(a.x, b.x)), (int) Math.ceil(Math.max(a.y, b.y)));
    }
}
