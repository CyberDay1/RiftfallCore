package com.cyberday1.riftfall.client;

import com.cyberday1.riftfall.client.HudLayout.Element;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;

import java.util.EnumMap;
import java.util.Map;

/**
 * The HUD layer for Riftfall's own bars (mana, Charge, rift clock, shield) and the boss finder beside them.
 * Registered above air, so docked bars take the next free rows of vanilla's columns and hand them on.
 */
public final class RiftHudBars {

    /** The column heights this layer last started from; the editor docks its preview on them. */
    private static volatile int lastLeft = 49;
    private static volatile int lastRight = 49;

    private RiftHudBars() {
    }

    static int lastLeft() {
        return lastLeft;
    }

    static int lastRight() {
        return lastRight;
    }

    /** Every bar the player would see now, keyed in group order. */
    static Map<Element, HudBarRenderer.Reading> readings(Minecraft client) {
        Map<Element, HudBarRenderer.Reading> out = new EnumMap<>(Element.class);
        put(out, RiftManaHud.reading(client));
        put(out, RangedChargeHud.reading(client));
        put(out, RiftClockHud.reading(client));
        put(out, RiftShieldHud.reading(client));
        return out;
    }

    private static void put(Map<Element, HudBarRenderer.Reading> out, HudBarRenderer.Reading reading) {
        if (reading != null) {
            out.put(reading.element(), reading);
        }
    }

    static void render(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft client = Minecraft.getInstance();
        Gui gui = client.gui;
        lastLeft = gui.leftHeight;
        lastRight = gui.rightHeight;
        if (client.options.hideGui || client.level == null || client.screen instanceof RiftHudEditorScreen) {
            return;
        }
        Map<Element, HudBarRenderer.Reading> readings = readings(client);
        BossFinderHud.Reading finder = BossFinderHud.reading(client, delta.getGameTimeDeltaPartialTick(true));
        if (readings.isEmpty() && finder == null) {
            return;
        }
        HudPlacements placements = HudPlacements.instance();
        HudLayout.Arrangement layout = HudLayout.arrange(graphics.guiWidth(), graphics.guiHeight(), gui.leftHeight,
                gui.rightHeight, readings.keySet(), placements);
        double ticks = client.level.getGameTime() + delta.getGameTimeDeltaPartialTick(false);
        double guiScale = client.getWindow().getGuiScale();
        for (HudLayout.Slot slot : layout.slots()) {
            HudBarRenderer.draw(graphics, client.font, slot, readings.get(slot.element()), ticks, guiScale);
        }
        gui.leftHeight += layout.leftUsed();
        gui.rightHeight += layout.rightUsed();
        if (finder != null) {
            HudPlacements.Placement at = placements.getOrDocked(Element.BOSS_FINDER.id());
            HudLayout.Rect box = HudLayout.bossFinder(graphics.guiWidth(), graphics.guiHeight(), layout,
                    gui.rightHeight, at);
            BossFinderHud.draw(graphics, client.font, box, at.scale(), finder);
            // With nothing to sit beside it took a row of the right column.
            if (!at.placed() && layout.slot(Element.CLOCK) == null && layout.group() == null) {
                gui.rightHeight += box.h() + 1;
            }
        }
    }
}
