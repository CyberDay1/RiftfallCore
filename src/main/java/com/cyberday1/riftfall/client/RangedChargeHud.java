package com.cyberday1.riftfall.client;

import com.cyberday1.riftfall.network.RangedChargePayload;
import com.cyberday1.riftfall.skill.ChargeView;
import com.cyberday1.riftfall.skill.ChargedWeapons;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * The Charge bar: second in the bar group above {@link RiftManaHud}, shown only while a bow or a gun is held.
 * The server's last reading, nothing predicted; dimmed while the held weapon would be refused. How it looks is
 * unverified: no client was booted.
 */
public final class RangedChargeHud {

    /** Multiplied onto the grayscale fill while the held weapon would be refused. */
    private static final int FILL_LOCKED = 0xFF6E5A3C;

    private RangedChargeHud() {
    }

    /** The share of the bank held, rounded down; an overdrawn pool reads 0%. */
    static String label(double current, double max) {
        if (!(max > 0.0) || !(current > 0.0)) {
            return "0%";
        }
        return (int) Math.min(100.0, Math.floor(100.0 * current / max)) + "%";
    }

    /** Fire while it can be spent; a dim brown fill while it cannot. */
    static HudBarStyle style(boolean locked) {
        return locked ? HudBarStyle.TINTED : HudBarStyle.FIRE;
    }

    static int tint(boolean locked) {
        return locked ? FILL_LOCKED : 0;
    }

    /** Shown on the mana bar's terms, and only with a bow or a gun in hand. */
    static @Nullable HudBarRenderer.Reading reading(Minecraft client) {
        RangedChargePayload bar = ChargeView.latest();
        if (bar == null || client.gameMode == null || !client.gameMode.canHurtPlayer()
                || client.player == null || !ChargedWeapons.armed(client.player)) {
            return null;
        }
        return reading(bar.current(), bar.max(), bar.locked());
    }

    static HudBarRenderer.Reading reading(double current, double max, boolean locked) {
        return new HudBarRenderer.Reading(HudLayout.Element.CHARGE, current, max,
                Component.translatable(HudLayout.Element.CHARGE.labelKey()), label(current, max), style(locked),
                tint(locked));
    }
}
