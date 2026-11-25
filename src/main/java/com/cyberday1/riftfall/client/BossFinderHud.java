package com.cyberday1.riftfall.client;

import com.cyberday1.riftfall.network.BossFinderPayload;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The rift HUD's arrow to the boss room: "↑ Boss 84m", the arrow turned toward the target, a "▲12m" when it is
 * well above or below. The server picks the target; this only draws it, and hides inside the boss room.
 * Unverified visually: no client was booted.
 */
public final class BossFinderHud {

    static final String LABEL = HudLayout.Element.BOSS_FINDER.labelKey();
    static final String DISTANCE = LABEL + ".distance";
    static final String ARROW = "↑";
    static final String UP = "▲";
    static final String DOWN = "▼";
    private static final int ARROW_COLOUR = 0xFFFFFFFF;

    /** What the editor previews before a target has come in. */
    static final Reading SAMPLE = new Reading(45.0F, 84, 12);

    private static volatile BossFinderPayload latest = BossFinderPayload.NONE;

    /** One frame's arrow turn in degrees, ground distance, and the rise to show (0 for none). */
    record Reading(float bearing, int metres, int rise) {
    }

    private BossFinderHud() {
    }

    /** Handler for the clientbound payload. Named from {@code RiftfallPayloads}. */
    public static void told(BossFinderPayload payload) {
        latest = payload;
    }

    public static void forget() {
        latest = BossFinderPayload.NONE;
    }

    static BossFinderPayload latest() {
        return latest;
    }

    /** The arrow now, or null: no target, another level, or standing in the boss room. */
    static @Nullable Reading reading(Minecraft client, float partialTick) {
        Player player = client.player;
        if (player == null || client.level == null) {
            return null;
        }
        BossFinderPayload.Target t = latest.target().orElse(null);
        if (t == null || !t.dimension().equals(client.level.dimension().location())) {
            return null;
        }
        Vec3 at = player.getPosition(partialTick);
        return reading(t, at.x, at.y, at.z, player.getViewYRot(partialTick));
    }

    static @Nullable Reading reading(BossFinderPayload.Target t, double x, double y, double z, float yaw) {
        if (t.inRoom((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z))) {
            return null;
        }
        double dx = t.pos().getX() + 0.5 - x;
        double dz = t.pos().getZ() + 0.5 - z;
        return new Reading(BossFinderBearing.relative(dx, dz, yaw), BossFinderBearing.metres(dx, dz),
                BossFinderBearing.rise(t.pos().getY() - y));
    }

    /** The text after the arrow: label, distance and rise. */
    static Component[] parts(Reading r) {
        Component label = Component.translatable(LABEL);
        Component distance = Component.translatable(DISTANCE, r.metres());
        Component rise = r.rise() == 0 ? null
                : Component.literal(r.rise() > 0 ? UP : DOWN).append(Component.translatable(DISTANCE, Math.abs(r.rise())));
        return rise == null ? new Component[] {label, distance} : new Component[] {label, distance, rise};
    }

    /** Draws the line into {@code box}, scaled about its corner. The editor previews with this. */
    static void draw(GuiGraphics g, Font font, HudLayout.Rect box, float scale, Reading r) {
        var pose = g.pose();
        pose.pushPose();
        pose.translate(box.x(), box.y(), 0.0F);
        pose.scale(scale, scale, 1.0F);
        int mid = HudLayout.FINDER_H / 2;
        pose.pushPose();
        pose.translate(4.5F, mid + 0.5F, 0.0F);
        pose.mulPose(Axis.ZP.rotationDegrees(r.bearing()));
        g.drawString(font, ARROW, -font.width(ARROW) / 2, -4, ARROW_COLOUR, true);
        pose.popPose();
        int x = 12;
        int y = mid - 4;
        Component[] parts = parts(r);
        for (int i = 0; i < parts.length; i++) {
            g.drawString(font, parts[i], x, y, i == 0 ? HudBarRenderer.LABEL : HudBarRenderer.VALUE, true);
            x += font.width(parts[i]) + 4;
        }
        pose.popPose();
    }
}
