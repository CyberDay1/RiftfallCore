package com.cyberday1.riftfall.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.settings.KeyModifier;

import java.util.List;

/**
 * One key per {@link ProgressionTab}, named {@link ProgressionTab#keyName()}, so a tab added later
 * arrives with a key. A press opens that tab, only with no screen already open.
 */
public final class RiftMenuKeys {

    static final ProgressionTab[] TABS = ProgressionTab.values();
    static final KeyMapping[] KEYS = new KeyMapping[TABS.length];

    static {
        for (int i = 0; i < TABS.length; i++) {
            KEYS[i] = RiftKeys.key(TABS[i].keyName(), defaultKey(TABS[i]), KeyModifier.NONE);
        }
    }

    private RiftMenuKeys() {
    }

    /**
     * J opens the book; the skills and class pages remain on its bookmark strip.
     */
    private static int defaultKey(ProgressionTab tab) {
        return switch (tab) {
            case CHARACTER -> InputConstants.KEY_J;
            case SKILLS -> RiftKeys.UNBOUND;
            // Unbound like the skills page: the bookmark and /riftfall skill class open it.
            case CLASSES -> RiftKeys.UNBOUND;
            case NOTES -> RiftKeys.UNBOUND;
            case SOCIAL -> RiftKeys.UNBOUND;
        };
    }

    static List<KeyMapping> all() {
        return List.of(KEYS);
    }

    /** Client tick: drains every key, then opens the first pressed tab if nothing is open. */
    static void tick(Minecraft client) {
        ProgressionTab pressed = null;
        for (int i = 0; i < KEYS.length; i++) {
            if (RiftKeys.drain(KEYS[i]) > 0 && pressed == null) {
                pressed = TABS[i];
            }
        }
        if (pressed != null && client.screen == null && client.player != null) {
            client.setScreen(ProgressionScreen.open(pressed));
        }
    }
}
