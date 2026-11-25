package com.cyberday1.riftfall.client;

import com.cyberday1.riftfall.network.SkillScreenPayload;
import com.cyberday1.riftfall.skill.SkillBook;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * One HUD slot per equipped skill: its icon, a dark band over the share of the cooldown still to run, the
 * seconds left, and the cast key. Each slot is its own HUD editor element. Unverified visually.
 */
public final class SkillCooldownHud {

    private static final int BACKING = 0xC0101014;
    private static final int SHADE = 0xB0000000;
    private static final int OFF = 0xA0000000;
    private static final int TIME = 0xFFFFFFFF;
    private static final int KEY = HudBarRenderer.VALUE;
    /** A key name wider than this falls back to the slot number. */
    private static final int KEY_MAX_W = 12;

    /** One slot as drawn: its skill ("" for an editor sample), remaining ticks and the timer's length. */
    record Face(int slot, String skill, double remaining, int duration) {
    }

    private SkillCooldownHud() {
    }

    /** Every equipped slot now, in slot order; empty slots are left out. */
    static List<Face> faces(Minecraft client, float partialTick) {
        List<Face> out = new ArrayList<>();
        if (client.level == null) {
            return out;
        }
        double now = client.level.getGameTime() + partialTick;
        for (int slot = 0; slot < SkillBook.SLOTS; slot++) {
            RiftSkillCooldowns.Slot s = RiftSkillCooldowns.slot(slot);
            if (!s.empty()) {
                out.add(new Face(slot, s.skill(), Math.max(0.0, s.readyAt() - now), s.duration()));
            }
        }
        return out;
    }

    /** The editor's stand-in when nothing is equipped: the eight base slots, one of them cooling. */
    static List<Face> sample() {
        List<Face> out = new ArrayList<>();
        for (int slot = 0; slot < SkillBook.ACTIVE_SLOTS; slot++) {
            out.add(new Face(slot, "", slot == 1 ? 240.0 : 0.0, slot == 1 ? 600 : 0));
        }
        return out;
    }

    static void render(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft client = Minecraft.getInstance();
        if (client.options.hideGui || client.level == null || client.player == null
                || client.screen instanceof RiftHudEditorScreen) {
            return;
        }
        HudPlacements placements = HudPlacements.instance();
        List<Face> faces = new ArrayList<>();
        for (Face face : faces(client, delta.getGameTimeDeltaPartialTick(false))) {
            if (!placements.hidden(HudLayout.cooldownId(face.slot()))) {
                faces.add(face);
            }
        }
        if (faces.isEmpty()) {
            return;
        }
        Gui gui = client.gui;
        HudLayout.CooldownRow row = HudLayout.cooldowns(graphics.guiWidth(), graphics.guiHeight(), gui.leftHeight,
                gui.rightHeight, faces.stream().map(Face::slot).toList(), placements);
        for (Face face : faces) {
            HudLayout.CooldownSlot at = row.slot(face.slot());
            if (at != null) {
                draw(graphics, client.font, at.rect(), at.scale(), face, false);
            }
        }
        gui.leftHeight += row.rowsUsed();
        gui.rightHeight += row.rowsUsed();
    }

    /** One slot in {@code box}, scaled about its corner. {@code off} dims it: the editor's look for a hidden slot. */
    static void draw(GuiGraphics graphics, Font font, HudLayout.Rect box, float scale, Face face, boolean off) {
        int size = HudLayout.COOLDOWN;
        graphics.pose().pushPose();
        graphics.pose().translate(box.x(), box.y(), 0.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.fill(0, 0, size, size, BACKING);
        if (!face.skill().isEmpty()) {
            icon(graphics, font, face.skill(), 1, 1);
        }
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, 1.0F);
        float share = CooldownFace.fraction(face.remaining(), face.duration());
        if (share > 0.0F) {
            // The share still to run, shrinking downward as vanilla's item cooldown does, but darkening.
            int band = Mth.ceil(16.0F * share);
            graphics.fill(RenderType.guiOverlay(), 1, 17 - band, 17, 17, SHADE);
            String left = CooldownFace.seconds(face.remaining());
            graphics.drawString(font, left, (size - font.width(left)) / 2 + 1, 2, TIME, true);
        }
        String key = keyLabel(font, face.slot());
        graphics.drawString(font, key, size - 1 - font.width(key), 10, KEY, true);
        if (off) {
            graphics.fill(0, 0, size, size, OFF);
        }
        graphics.pose().popPose();
        graphics.pose().popPose();
    }

    /** The pack's art for the skill, else the codex's lever tile with its two-letter glyph. */
    private static void icon(GuiGraphics graphics, Font font, String skill, int x, int y) {
        ResourceLocation texture = SkillIcons.INSTANCE.texture(skill);
        if (texture != null) {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            graphics.blit(texture, x, y, 16, 16, 0.0F, 0.0F, 16, 16, 16, 16);
            RenderSystem.disableBlend();
            return;
        }
        SkillScreenPayload.Entry entry = entry(skill);
        var lever = entry == null ? null : SkillFigures.lever(entry);
        boolean passive = entry != null && entry.passive();
        graphics.fill(x, y, x + 16, y + 16, passive ? CodexStyle.PASSIVE : CodexStyle.lever(lever));
        graphics.fill(x, y, x + 16, y + 1, 0x30FFFFFF);
        Component mark = Component.translatable(ProgressionTab.SKILLS.key("glyph_" + ProgressionScreen.leverKey(lever, passive)));
        graphics.drawString(font, mark, x + (16 - font.width(mark)) / 2 + 1, y + 4, CodexStyle.ON_TAG, false);
    }

    private static @Nullable SkillScreenPayload.Entry entry(String skill) {
        for (SkillScreenPayload.Entry entry : RiftSkills.latest().entries()) {
            if (entry.id().equals(skill)) {
                return entry;
            }
        }
        return null;
    }

    /** The cast key's own name ("1" for Ctrl+1), or the slot number when unbound or too wide. */
    private static String keyLabel(Font font, int slot) {
        KeyMapping key = RiftCastKeys.SLOTS[slot];
        String bound = key.isUnbound() ? null : key.getKey().getDisplayName().getString();
        return CooldownFace.keyLabel(bound, slot, I18n.get(ProgressionTab.SKILLS.key("slot_glyph_ultimate")), font::width,
                KEY_MAX_W);
    }
}
