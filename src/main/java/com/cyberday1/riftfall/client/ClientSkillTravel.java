package com.cyberday1.riftfall.client;

import com.cyberday1.riftfall.network.TravelStartPayload;
import com.cyberday1.riftfall.skill.TravelController;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;

/** Runs at LocalPlayer.move, immediately before vanilla resolves walls, floors and ceilings. */
public final class ClientSkillTravel {
    private static LocalPlayer owner;
    private static ClientLevel level;
    private static TravelController controller;
    private ClientSkillTravel() { }

    public static void start(TravelStartPayload payload) {
        owner = Minecraft.getInstance().player;
        level = Minecraft.getInstance().level;
        controller = owner == null ? null : new TravelController(payload.start(), payload.end(), payload.mode());
    }

    public static boolean active() { return controller != null && !controller.finished(); }

    public static void stop(boolean brake) {
        if (brake && active() && owner == Minecraft.getInstance().player) {
            Vec3 current = owner.getDeltaMovement();
            owner.setDeltaMovement(0, Math.min(0, current.y), 0);
        }
        controller = null;
        owner = null;
        level = null;
    }

    public static Vec3 movement(LocalPlayer player, MoverType type, Vec3 vanilla) {
        if (controller == null || type != MoverType.SELF) return vanilla;
        if (player != owner || player.level() != level || !player.isAlive() || player.isRemoved() || player.isSpectator()
                || player.isPassenger() || player.isFallFlying()) {
            stop(false);
            return vanilla;
        }
        if (player.isShiftKeyDown()) {
            stop(true);
            return player.getDeltaMovement();
        }
        Vec3 velocity = controller.step(player.position(), vanilla.y, offset ->
                player.level().hasChunkAt(BlockPos.containing(player.position().add(offset)))
                        && player.level().getWorldBorder().isWithinBounds(player.getBoundingBox().move(offset))
                        && player.level().noCollision(player, player.getBoundingBox().move(offset)));
        if (velocity == null) { stop(false); return vanilla; }
        if (!player.level().hasChunkAt(BlockPos.containing(player.position().add(velocity)))
                || !player.level().getWorldBorder().isWithinBounds(player.getBoundingBox().move(velocity))) {
            stop(true);
            return Vec3.ZERO;
        }
        if (controller.airborne()) player.fallDistance = 0;
        player.setDeltaMovement(velocity);
        if (controller.finished()) stop(false);
        return velocity;
    }
}
