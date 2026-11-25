package com.cyberday1.riftfall.rift;

import com.cyberday1.riftfall.home.HomeAccess;
import com.cyberday1.riftfall.home.ShatteredHomeLevels;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.Vec3;

/** Shared return destination for exits, closure and recovery after a deleted rift. */
final class RiftEvacuation {
    private RiftEvacuation() {}

    static boolean returnPlayer(MinecraftServer server, ServerPlayer player) {
        // Death respawn chooses the home/bed. Keep the corpse in its rift until that happens;
        // moving it early can erase the context before the respawn-position event runs.
        if (!player.isAlive()) return false;
        var returns = RiftReturns.of(server);
        RiftEntry entry = returns.owed(player.getUUID());
        if (entry == null) {
            var run = RiftRuns.in(player.serverLevel().dimension());
            if (run != null) entry = run.entry(player.getUUID());
        }
        var transition = transition(server, player, entry, DimensionTransition.DO_NOTHING);
        if (player.changeDimension(transition) == null) return false;
        completed(server, player);
        return true;
    }

    static DimensionTransition transition(MinecraftServer server, ServerPlayer player, RiftEntry entry,
                                           DimensionTransition.PostDimensionTransition after) {
        ServerLevel destination = entry == null ? null : server.getLevel(entry.origin());
        // A nested/debug rift can itself have vanished or be closing. It is never a safe fallback.
        if (destination != null && RiftLevels.isRiftWorld(destination)) destination = null;
        Vec3 position;
        float yaw = player.getYRot();
        if (destination == null) {
            destination = ShatteredHomeLevels.getOrCreate(server,
                    HomeAccess.homeOwner(server, player.getUUID()));
            position = ShatteredHomeLevels.arrival(destination);
        } else {
            position = entry.doorstep();
            yaw = entry.yaw();
        }
        RiftTeleports.permit(player, destination);
        return new DimensionTransition(destination, position, Vec3.ZERO, yaw, player.getXRot(), after);
    }

    static void completed(MinecraftServer server, ServerPlayer player) {
        player.fallDistance = 0;
        player.setPortalCooldown();
        RiftReturns.of(server).forgive(player.getUUID());
        ShatteredHomeLevels.syncBorderTo(player);
    }
}
