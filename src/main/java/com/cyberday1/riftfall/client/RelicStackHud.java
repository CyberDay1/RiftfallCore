package com.cyberday1.riftfall.client;

import com.cyberday1.riftfall.gear.RelicSignatures.Signature;
import com.cyberday1.riftfall.gear.RelicStackIcons;
import com.cyberday1.riftfall.gear.RelicStackIcons.Icon;
import com.cyberday1.riftfall.init.RiftfallEffects;
import com.cyberday1.riftfall.network.RelicStacksPayload;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Small HUD icons for the relic signatures: the relic's own item with its stack count, or with the vanilla
 * cooldown sweep and seconds left (Spore Burst, Ground Pound, Berserk). Which icons show is
 * {@link RelicStackIcons}; the HUD editor moves and resizes the row. Unverified visually.
 */
public final class RelicStackHud {

    /** What the editor previews before anything has come in: a row a player could have, the box full. */
    static final List<Icon> SAMPLE = List.of(Icon.stack(Signature.BERSERKER_RAGE, 5), Icon.stack(Signature.MOMENTUM, 6),
            new Icon(Signature.SPORE_BURST, 30, 0.5F), new Icon(Signature.BERSERK, 3, 0.5F));

    private static volatile RelicStacksPayload latest = RelicStacksPayload.NONE;
    private static final Map<Signature, ItemStack> ITEMS = new EnumMap<>(Signature.class);

    private RelicStackHud() {
    }

    /** Handler for the clientbound payload. Named from {@code RiftfallPayloads}. */
    public static void told(RelicStacksPayload payload) {
        latest = payload;
    }

    public static void forget() {
        latest = RelicStacksPayload.NONE;
    }

    static RelicStacksPayload latest() {
        return latest;
    }

    /** The icons now: the last payload against the level's clock, Berserk from the player's own effect. */
    static List<Icon> icons(Minecraft client, float partialTick) {
        if (client.level == null || client.player == null) return List.of();
        MobEffectInstance berserk = client.player.getEffect(RiftfallEffects.BERSERK);
        double berserkLeft = berserk == null ? 0.0D
                : berserk.isInfiniteDuration() ? RelicStackIcons.FOREVER : berserk.getDuration() - partialTick;
        return RelicStackIcons.icons(latest, client.level.getGameTime() + partialTick, berserkLeft);
    }

    static void render(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft client = Minecraft.getInstance();
        if (client.options.hideGui || client.level == null || client.screen instanceof RiftHudEditorScreen) {
            return;
        }
        List<Icon> icons = icons(client, delta.getGameTimeDeltaPartialTick(true));
        if (icons.isEmpty()) {
            return;
        }
        HudPlacements.Placement at = HudPlacements.instance().getOrDocked(HudLayout.Element.RELIC_STACKS.id());
        draw(graphics, client.font, HudLayout.relicStacks(graphics.guiWidth(), graphics.guiHeight(), at), at, icons);
    }

    /** The icons in {@code box}, scaled about its corner, never more than it holds. The editor previews with this. */
    static void draw(GuiGraphics graphics, Font font, HudLayout.Rect box, HudPlacements.Placement at, List<Icon> icons) {
        graphics.pose().pushPose();
        graphics.pose().translate(box.x(), box.y(), 0.0F);
        graphics.pose().scale(at.scale(), at.scale(), 1.0F);
        int n = 0;
        for (Icon icon : icons) {
            ItemStack item = item(icon.signature());
            if (item.isEmpty()) {
                continue;
            }
            if (n >= HudLayout.ICON_SLOTS) {
                break;
            }
            int[] xy = HudLayout.iconAt(n++, at.vertical());
            graphics.renderItem(item, xy[0], xy[1]);
            graphics.pose().pushPose();
            graphics.pose().translate(0.0F, 0.0F, 200.0F);
            if (icon.sweep() > 0.0F) {
                // Vanilla's item cooldown: translucent white over the share still to run, shrinking downward.
                int top = xy[1] + Mth.floor(16.0F * (1.0F - icon.sweep()));
                graphics.fill(RenderType.guiOverlay(), xy[0], top, xy[0] + 16, top + Mth.ceil(16.0F * icon.sweep()),
                        Integer.MAX_VALUE);
            }
            if (icon.count() > 0) {
                // Vanilla's item count: right-aligned at the slot's corner, shadowed; over the sweep so it stays sharp.
                String count = Integer.toString(icon.count());
                graphics.drawString(font, count, xy[0] + 17 - font.width(count), xy[1] + 9, 0xFFFFFF, true);
            }
            graphics.pose().popPose();
        }
        graphics.pose().popPose();
    }

    /** The relic's item, or empty when its mod is not loaded. */
    private static ItemStack item(Signature signature) {
        return ITEMS.computeIfAbsent(signature, s -> BuiltInRegistries.ITEM.getOptional(s.item())
                .map(ItemStack::new).orElse(ItemStack.EMPTY));
    }
}
