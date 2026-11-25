package com.cyberday1.riftfall.client;

import com.cyberday1.riftfall.RiftfallCore;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyModifier;

import java.util.ArrayList;
import java.util.List;

/**
 * Every key the mod registers, in one category, each display name starting "Riftfall" so the
 * controls search finds them all. The book uses J and skill slots use Ctrl+number defaults.
 */
public final class RiftKeys {

    public static final String CATEGORY = "key.categories." + RiftfallCore.MODID;
    static final int UNBOUND = InputConstants.UNKNOWN.getValue();
    /** Opens the HUD editor; unbound, the pause menu has a button too. */
    static final KeyMapping EDIT_HUD = key("key." + RiftfallCore.MODID + ".edit_hud", UNBOUND, KeyModifier.NONE);

    private RiftKeys() {
    }

    /** What {@code RiftfallClient#registerKeys} registers: the menu keys, the cast keys, then Edit HUD. */
    static List<KeyMapping> all() {
        List<KeyMapping> keys = new ArrayList<>(RiftMenuKeys.all());
        keys.addAll(RiftCastKeys.all());
        keys.add(EDIT_HUD);
        return keys;
    }

    static KeyMapping key(String name, int keyCode, KeyModifier modifier) {
        return new KeyMapping(name, KeyConflictContext.IN_GAME, modifier, InputConstants.Type.KEYSYM,
                keyCode, CATEGORY);
    }

    /** Presses queued since the last tick; draining is what makes a held key count once. */
    static int drain(KeyMapping key) {
        int presses = 0;
        while (key.consumeClick()) {
            presses++;
        }
        return presses;
    }
}
