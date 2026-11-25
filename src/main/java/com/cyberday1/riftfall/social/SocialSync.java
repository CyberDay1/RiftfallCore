package com.cyberday1.riftfall.social;

import com.cyberday1.riftfall.network.SocialBadgePayload;
import com.cyberday1.riftfall.network.SocialViewPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keeps the Party bookmark live: every half second, a changed view to each player with the page open, and a
 * changed invitation count to everyone (the bookmark's badge). Polled rather than hooked, like the party
 * frame ({@code PartyHudSync}), because OPAC teams change through OPAC's own commands and menu too.
 */
public final class SocialSync {

    public static final int INTERVAL_TICKS = 10;

    private static final Set<UUID> WATCHING = ConcurrentHashMap.newKeySet();
    private static final Map<UUID, SocialViewPayload> VIEWS = new ConcurrentHashMap<>();
    private static final Map<UUID, SocialBadgePayload> BADGES = new ConcurrentHashMap<>();

    private SocialSync() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(SocialSync::onServerTick);
        NeoForge.EVENT_BUS.addListener(SocialSync::onLoggedOut);
        NeoForge.EVENT_BUS.addListener(SocialSync::onStopping);
    }

    static void watch(ServerPlayer player) {
        WATCHING.add(player.getUUID());
        push(player);
    }

    static void unwatch(ServerPlayer player) {
        WATCHING.remove(player.getUUID());
        VIEWS.remove(player.getUUID());
    }

    /** The view now, whether or not it changed: the page just opened, or the player just acted. */
    static void push(ServerPlayer player) {
        SocialViewPayload view = SocialService.view(player);
        VIEWS.put(player.getUUID(), view);
        send(player, view);
        SocialBadgePayload badge = SocialService.badge(player);
        BADGES.put(player.getUUID(), badge);
        send(player, badge);
    }

    static void sync(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player instanceof FakePlayer) {
                continue;
            }
            SocialBadgePayload badge = SocialService.badge(player);
            if (!badge.equals(BADGES.put(player.getUUID(), badge))) {
                send(player, badge);
            }
            if (WATCHING.contains(player.getUUID())) {
                SocialViewPayload view = SocialService.view(player);
                if (!view.equals(VIEWS.put(player.getUUID(), view))) {
                    send(player, view);
                }
            }
        }
    }

    static void send(ServerPlayer player, CustomPacketPayload payload) {
        if (!(player instanceof FakePlayer) && player.connection != null) {
            PacketDistributor.sendToPlayer(player, payload);
        }
    }

    static boolean watching(UUID player) {
        return WATCHING.contains(player);
    }

    static void forget(UUID player) {
        WATCHING.remove(player);
        VIEWS.remove(player);
        BADGES.remove(player);
    }

    private static void onServerTick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % INTERVAL_TICKS == 0) {
            sync(event.getServer());
        }
    }

    private static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        forget(event.getEntity().getUUID());
    }

    private static void onStopping(ServerStoppingEvent event) {
        WATCHING.clear();
        VIEWS.clear();
        BADGES.clear();
    }
}
