package com.cyberday1.riftfall.rift;

import com.cyberday1.riftfall.network.RiftPartyPayload;
import com.cyberday1.riftfall.shield.RiftShields;
import com.cyberday1.riftfall.shield.ShieldPool;
import com.cyberday1.riftfall.skill.RiftMana;
import com.mojang.authlib.GameProfile;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The party HUD's feed: every {@link #INTERVAL_TICKS}, each online player is sent their party's
 * health, shield and mana if it differs from what they were last sent. Polled, because health moves
 * from anywhere; a party of one, or none, is sent as empty once so the HUD clears.
 */
public final class PartyHudSync {

    /** Twice a second. The shield rides along as a stored pool, so the drain costs no sends. */
    public static final int INTERVAL_TICKS = 10;

    /** Past this a party is a hand-edited file; the codec refuses longer lists. */
    static final int MAX_MEMBERS = 64;

    private static final int NAME_LENGTH = 16;

    private static final Map<UUID, RiftPartyPayload> LAST = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> COUNTS = new ConcurrentHashMap<>();

    private PartyHudSync() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(PartyHudSync::onServerTick);
        NeoForge.EVENT_BUS.addListener(PartyHudSync::onLoggedOut);
    }

    /** One pass over every online player. Public so the rig can drive it. */
    public static void sync(MinecraftServer server) {
        RiftParties parties = RiftParties.of(server);
        Map<UUID, RiftPartyPayload> byParty = new HashMap<>();
        for (ServerPlayer viewer : server.getPlayerList().getPlayers()) {
            RiftParty party = parties.of(viewer.getUUID());
            RiftPartyPayload payload = party == null || party.size() < 2
                    ? RiftPartyPayload.NONE
                    : byParty.computeIfAbsent(party.id(), id -> snapshot(server, party));
            if (due(LAST.get(viewer.getUUID()), payload)) {
                send(viewer, payload);
            }
        }
    }

    static RiftPartyPayload snapshot(MinecraftServer server, RiftParty party) {
        List<RiftPartyPayload.Member> members = new ArrayList<>();
        for (UUID id : party.members()) {
            if (members.size() == MAX_MEMBERS) {
                break;
            }
            members.add(member(id, server.getPlayerList().getPlayer(id), nameOf(server, id)));
        }
        return new RiftPartyPayload(members);
    }

    /** One row. {@code player} is null for a member who is not online. */
    static RiftPartyPayload.Member member(UUID id, @Nullable ServerPlayer player, String name) {
        if (player == null) {
            return new RiftPartyPayload.Member(id, name, false, 0.0F, 0.0F, 0.0, 0L, 0.0, 0.0);
        }
        ShieldPool shield = RiftShields.stored(player);
        return new RiftPartyPayload.Member(id, name, true, player.getHealth(), player.getMaxHealth(),
                shield.amount(), shield.updatedAt(), RiftMana.current(player), RiftMana.max(player));
    }

    /** Nothing sent yet is due even when empty, so a client from a previous world is cleared. */
    static boolean due(@Nullable RiftPartyPayload last, RiftPartyPayload next) {
        return last == null || !last.equals(next);
    }

    private static String nameOf(MinecraftServer server, UUID id) {
        ServerPlayer online = server.getPlayerList().getPlayer(id);
        String name = online != null ? online.getGameProfile().getName()
                : server.getProfileCache() == null ? null
                : server.getProfileCache().get(id).map(GameProfile::getName).orElse(null);
        if (name == null || name.isEmpty()) {
            name = id.toString().substring(0, 8);
        }
        return name.length() > NAME_LENGTH ? name.substring(0, NAME_LENGTH) : name;
    }

    private static void send(ServerPlayer player, RiftPartyPayload payload) {
        LAST.put(player.getUUID(), payload);
        COUNTS.merge(player.getUUID(), 1, Integer::sum);
        if (!(player instanceof FakePlayer)) {
            PacketDistributor.sendToPlayer(player, payload);
        }
    }

    public static int sent(UUID player) {
        return COUNTS.getOrDefault(player, 0);
    }

    public static @Nullable RiftPartyPayload lastSent(UUID player) {
        return LAST.get(player);
    }

    public static void forget(UUID player) {
        LAST.remove(player);
        COUNTS.remove(player);
    }

    private static void onServerTick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % INTERVAL_TICKS == 0) {
            sync(event.getServer());
        }
    }

    private static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        forget(event.getEntity().getUUID());
    }
}
