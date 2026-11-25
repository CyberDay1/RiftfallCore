package com.cyberday1.riftfall.skill;

import com.cyberday1.riftfall.network.SkillCastPayload;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * The cast keys' server half (V3). A press names a slot; {@link SkillService#castSlot} does the rest
 * with the rank from {@code RiftStats}. Action-bar feedback explains refusals; successful casts
 * stay quiet. An out-of-range slot is refused there, never clamped.
 */
public final class SkillKeys {

    /** What one press did, and its diagnostic line (only displayed when feedback is needed). */
    public record Pressed(SkillService.Casting casting, Component line) {
    }

    private SkillKeys() {
    }

    /** The serverbound handler's body, public so the rig can drive it with a fake player. */
    public static Pressed handle(ServerPlayer player, SkillCastPayload payload) {
        SkillService.Casting casting = SkillService.castSlot(player, payload.slot());
        Component line = SkillCommand.castLine(player, payload.slot(), casting);
        if (showsFeedback(casting.result())) player.displayClientMessage(line, true);
        return new Pressed(casting, line);
    }

    public static boolean showsFeedback(SkillService.Cast result) {
        return result != SkillService.Cast.CAST && result != SkillService.Cast.NO_TARGET;
    }
}
