package com.cyberday1.riftfall.client;

import com.cyberday1.riftfall.network.RiftShieldPayload;
import com.cyberday1.riftfall.shield.ShieldPool;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * The player's own shield, drawn by {@link RiftHudBars} docked above health and armour until the player moves
 * it. Drains the last pool the server sent against the client's clock, so the server only sends on a grant or
 * a hit. Shown only while a shield stands. Unverified visually.
 */
public final class RiftShieldHud {

    /** Gold: the party frame's shield colour, multiplied onto the grayscale fill. */
    static final int FILL = 0xFFE0B040;
    static final String LABEL = HudLayout.Element.SHIELD.labelKey();

    private static volatile ShieldPool latest;

    private RiftShieldHud() {
    }

    /** Handler for the clientbound payload. Named from {@code RiftfallPayloads}. */
    public static void told(RiftShieldPayload payload) {
        latest = payload.pool();
    }

    public static void forget() {
        latest = null;
    }

    /** The shield standing at {@code now}, as the HUD would draw it. */
    static double standing(long now) {
        return shown(latest, now);
    }

    /** The points standing at {@code now}: the sent pool drained forward, zero once under the floor. */
    static double shown(@Nullable ShieldPool pool, long now) {
        return pool == null ? 0.0 : pool.drainedTo(now).amount();
    }

    /** Rounded up, so a standing shield never reads 0. */
    static int rounded(double shield) {
        return (int) Math.ceil(shield);
    }

    /** Against max health, like the party frame's shield row. */
    static @Nullable HudBarRenderer.Reading reading(Minecraft client) {
        if (client.level == null || client.player == null || client.gameMode == null
                || !client.gameMode.canHurtPlayer()) {
            return null;
        }
        double shield = shown(latest, client.level.getGameTime());
        return shield > 0.0 ? reading(shield, client.player.getMaxHealth()) : null;
    }

    static HudBarRenderer.Reading reading(double shield, double maxHealth) {
        return new HudBarRenderer.Reading(HudLayout.Element.SHIELD, shield, maxHealth, Component.translatable(LABEL),
                Integer.toString(rounded(shield)), HudBarStyle.TINTED, FILL);
    }
}
