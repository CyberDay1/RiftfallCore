package com.cyberday1.riftfall.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import dev.ftb.mods.ftblibrary.icon.Icon;
import net.minecraft.client.gui.GuiGraphics;

/** Optional FTB adapter; the drawing implementation has no FTB dependency. */
public final class StellarQuestIcon extends Icon {
    private final String key;
    public StellarQuestIcon(String key) { this.key = key; }
    @Override public void draw(GuiGraphics g, int x, int y, int w, int h) {
        StellarQuests.draw(key, g, x, y, w, h);
    }
    @Override public Icon copy() { return new StellarQuestIcon(key); }
    @Override public JsonElement getJson() { return new JsonPrimitive(key); }
    @Override public String toString() { return key; }
}
