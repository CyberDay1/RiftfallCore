package com.cyberday1.riftfall.home;

import com.cyberday1.riftfall.rift.RiftLevels;
import com.cyberday1.riftfall.rift.RiftReturns;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerRespawnPositionEvent;

/**
 * On first join ever, creates the player's shattered home dimension and teleports them there.
 * Subsequent joins leave respawn to beds/anchors — the dimension already exists on disk.
 * Also handles border sync and death-respawn redirection back into the home dimension.
 */
@EventBusSubscriber
public final class ShatteredHomeJoinHandler {

    static final String HOME_FLAG = "riftfall:has_shattered_home";

    private ShatteredHomeJoinHandler() {}

    public static void register() {
        // Registration is via @EventBusSubscriber; nothing manual needed.
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        // Re-apply and sync the border for players logging back in inside their home dimension.
        // Without applyBorder, existing saves keep whatever tiny border they had before the tile system.
        if (ShatteredHomeLevels.isShatteredHome(player.level().dimension())) {
            ServerLevel home = player.serverLevel();
            ShatteredHomeProgress.of(home).applyBorder(home);
            ShatteredHomeLevels.syncBorderTo(player);
        }

        if (hasHome(player)) {
            rememberHome(player); // Upgrade the old root-level flag to death-persistent data.
            return;
        }
        // Recovery owns an outstanding return; an active run owns a reconnect. A missing legacy
        // home flag must not make first-login setup hijack either, regardless of listener order.
        if (RiftLevels.isRift(player.level().dimension())
                || RiftReturns.of(player.getServer()).owed(player.getUUID()) != null) return;
        ServerLevel home = ShatteredHomeLevels.getOrCreate(player.getServer(), HomeAccess.homeOwner(player.getServer(), player.getUUID()));
        Vec3 spawn = ShatteredHomeLevels.arrival(home);
        if (player.changeDimension(new DimensionTransition(
                home, spawn, Vec3.ZERO,
                player.getYRot(), player.getXRot(),
                DimensionTransition.DO_NOTHING)) != null) {
            rememberHome(player);
            player.fallDistance = 0;
            ShatteredHomeLevels.syncBorderTo(player);
        }
    }

    /** Syncs the world border when a player changes into a shattered home dimension. */
    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player
                && ShatteredHomeLevels.isShatteredHome(event.getTo())) {
            ShatteredHomeLevels.syncBorderTo(player);
        }
    }

    /**
     * Redirects death respawns back into the player's shattered home instead of the overworld.
     * Without this, vanilla sends them to the default spawn because no bed or anchor is set in
     * a freshly-created per-player dimension.
     */
    @SubscribeEvent
    public static void onRespawnPosition(PlayerRespawnPositionEvent event) {
        ServerPlayer player = (ServerPlayer) event.getEntity();
        if (event.isFromEndFight() || (!hasHome(player) && !RiftLevels.isRift(player.level().dimension()))) return;
        rememberHome(player);
        // Keep a bed only in the current home; leaving a team must not respawn into its old home.
        var owner = HomeAccess.homeOwner(player.getServer(), player.getUUID());
        if (!event.getDimensionTransition().missingRespawnBlock()
                && ShatteredHomeLevels.key(owner).equals(event.getDimensionTransition().newLevel().dimension())) {
            return;
        }
        ServerLevel home = ShatteredHomeLevels.getOrCreate(player.getServer(), owner);
        Vec3 spawn = ShatteredHomeLevels.arrival(home);
        // Re-apply border in case it was lost (e.g. level unloaded between deaths).
        ShatteredHomeProgress progress = ShatteredHomeProgress.of(home);
        progress.applyBorder(home);
        event.setDimensionTransition(new DimensionTransition(
                home, spawn, Vec3.ZERO,
                player.getYRot(), player.getXRot(),
                DimensionTransition.DO_NOTHING));
        // Leave NeoForge's copy policy intact: valid beds survive the player clone, while
        // missing/invalid beds are cleared. This transition changes only this respawn.
    }

    /** Re-syncs the world border after respawn so the client renders it immediately. */
    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            if (ShatteredHomeLevels.isShatteredHome(player.level().dimension())) rememberHome(player);
            ShatteredHomeLevels.syncBorderTo(player);
        }
    }

    private static boolean hasHomeFlag(ServerPlayer player) {
        CompoundTag data = player.getPersistentData();
        return data.getBoolean(HOME_FLAG)
                || data.getCompound(net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG).getBoolean(HOME_FLAG);
    }

    static boolean hasHome(ServerPlayer player) {
        return hasHomeFlag(player) || ShatteredHomeLevels.isShatteredHome(player.level().dimension())
                || (player.getServer() != null && player.getServer().getLevel(ShatteredHomeLevels.key(
                        HomeAccess.homeOwner(player.getServer(), player.getUUID()))) != null);
    }

    private static void rememberHome(ServerPlayer player) {
        CompoundTag data = player.getPersistentData();
        CompoundTag persisted = data.getCompound(net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG).copy();
        persisted.putBoolean(HOME_FLAG, true);
        data.put(net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG, persisted);
        data.putBoolean(HOME_FLAG, true);
    }

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        if (event.getOriginal() instanceof ServerPlayer original && event.getEntity() instanceof ServerPlayer replacement
                && hasHome(original)) rememberHome(replacement);
    }
}
