package com.cyberday1.riftfall.client;

import com.cyberday1.riftfall.network.RiftPartyPayload;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import java.util.List;
import java.util.UUID;

/**
 * A rough party frame down the left edge: each other member's name, health, shield and mana, as the
 * server last sent them, the shield drained forward on the client. By default below Xaero's minimap, which
 * holds the top-left corner in this pack; the HUD editor moves and resizes it. Unverified visually.
 */
public final class RiftPartyHud {

    static final int WIDTH = 80;
    private static final int X = 4;
    private static final int NAME = 10;
    private static final int BAR = 3;
    static final int BLOCK = NAME + 3 * (BAR + 1) + 4;
    private static final int BACK = 0x90101018;
    private static final int TRACK = 0xFF303030;
    private static final int HEALTH = 0xFFD03030;
    private static final int MANA = 0xFF3A6FE0;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int OFFLINE = 0xFF808080;

    private static volatile RiftPartyPayload latest = RiftPartyPayload.NONE;

    private RiftPartyHud() {
    }

    /** Handler for the clientbound payload. Named from {@code RiftfallPayloads}. */
    public static void told(RiftPartyPayload payload) {
        latest = payload;
        com.cyberday1.riftfall.rift.PartyCombat.clientMembers(payload.members().stream()
                .map(RiftPartyPayload.Member::id).collect(java.util.stream.Collectors.toSet()));
    }

    public static void forget() {
        latest = RiftPartyPayload.NONE;
        com.cyberday1.riftfall.rift.PartyCombat.clientMembers(java.util.Set.of());
    }

    static RiftPartyPayload latest() {
        return latest;
    }

    /** Everybody but the viewer, in the server's order (leader first). */
    static List<RiftPartyPayload.Member> others(RiftPartyPayload payload, UUID self) {
        return payload.members().stream().filter(member -> !member.id().equals(self)).toList();
    }

    /** Default top-left of the first block: a third of the way down, pulled up so {@code rows} clear the chat. */
    static int[] place(int guiHeight, int rows) {
        int top = guiHeight / 3;
        int bottom = guiHeight - 60;
        return new int[] {X, Math.max(0, Math.min(top, bottom - rows * BLOCK))};
    }

    static final String OFFLINE_LABEL = "hud.riftfall.party.offline";

    static String healthLabel(RiftPartyPayload.Member member) {
        return member.online()
                ? (int) Math.ceil(member.health()) + " / " + (int) Math.ceil(member.maxHealth())
                : net.minecraft.client.resources.language.I18n.get(OFFLINE_LABEL);
    }

    static void render(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft client = Minecraft.getInstance();
        if (client.options.hideGui || client.level == null || client.player == null
                || client.screen instanceof RiftHudEditorScreen) {
            return;
        }
        List<RiftPartyPayload.Member> rows = others(latest, client.player.getUUID());
        if (rows.isEmpty()) {
            return;
        }
        HudPlacements.Placement at = HudPlacements.instance().getOrDocked(HudLayout.Element.PARTY.id());
        HudLayout.Rect box = HudLayout.party(graphics.guiWidth(), graphics.guiHeight(), rows.size(), at);
        draw(graphics, client, box, at.scale(), rows, client.level.getGameTime());
    }

    /** The member blocks in {@code box}, scaled about its corner. The editor draws its preview with this. */
    static void draw(GuiGraphics graphics, Minecraft client, HudLayout.Rect box, float scale,
                     List<RiftPartyPayload.Member> rows, long now) {
        graphics.pose().pushPose();
        graphics.pose().translate(box.x(), box.y(), 0.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        int x = 2;
        int y = 2;
        for (RiftPartyPayload.Member member : rows) {
            graphics.fill(x - 2, y - 2, x + WIDTH + 2, y + BLOCK - 4, BACK);
            int colour = member.online() ? TEXT : OFFLINE;
            String hp = healthLabel(member);
            // A sixteen-letter name runs past the frame and into the health figure; cut it to what is left.
            graphics.drawString(client.font, fitted(client.font, member.name(), WIDTH - client.font.width(hp) - 4), x, y,
                    colour, true);
            graphics.drawString(client.font, hp, x + WIDTH - client.font.width(hp), y, colour, true);
            int row = y + NAME;
            bar(graphics, x, row, RiftManaHud.filled(member.health(), member.maxHealth(), WIDTH), HEALTH);
            row += BAR + 1;
            double shield = RiftShieldHud.shown(member.shieldPool(), now);
            bar(graphics, x, row, RiftManaHud.filled(shield, member.maxHealth(), WIDTH), RiftShieldHud.FILL);
            row += BAR + 1;
            bar(graphics, x, row, RiftManaHud.filled(member.mana(), member.maxMana(), WIDTH), MANA);
            y += BLOCK;
        }
        graphics.pose().popPose();
    }

    /** {@code name}, or as much of it as fits {@code room} followed by "..". */
    static String fitted(net.minecraft.client.gui.Font font, String name, int room) {
        if (font.width(name) <= room) {
            return name;
        }
        return font.plainSubstrByWidth(name, Math.max(0, room - font.width(".."))) + "..";
    }

    private static void bar(GuiGraphics graphics, int x, int y, int filled, int colour) {
        graphics.fill(x, y, x + WIDTH, y + BAR, TRACK);
        graphics.fill(x, y, x + filled, y + BAR, colour);
    }
}
