package com.cyberday1.riftfall.rift;

import com.cyberday1.riftfall.RiftfallCore;
import com.cyberday1.riftfall.network.RiftClaimPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Held gear comes back without a command (ruled 2026-11-17). Three moments, one hand-over
 * ({@link RiftClaims#handOver(ServerPlayer)}):
 * <ul>
 * <li>the end-of-rift recap, just before it is sent ({@link RiftRecaps});</li>
 * <li>its Claim the rest button;</li>
 * <li>a retry every second for anybody alive outside a rift with something held and room for it,
 * which covers a party death (no recap until the party is done), overflow, and a restart.</li>
 * </ul>
 * The retry waits while a recap is owed, so the screen is where a death's gear shows up.
 */
public final class RiftAutoClaims {

    private static final String BACK = "message." + RiftfallCore.MODID + ".claim_auto";
    private static final String PARTLY_BACK = "message." + RiftfallCore.MODID + ".claim_auto_partial";
    private static final String WAITING = "message." + RiftfallCore.MODID + ".claim_waiting";

    /** How often the retry runs, in ticks: the recap's delivery interval. */
    static final int INTERVAL = 20;

    /** How many stacks each player was last told are waiting, this session, so it is said once. */
    private static final Map<UUID, Integer> TOLD = new HashMap<>();

    private RiftAutoClaims() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(RiftAutoClaims::tick);
        NeoForge.EVENT_BUS.addListener(RiftAutoClaims::loggedOut);
        NeoForge.EVENT_BUS.addListener(RiftAutoClaims::stopped);
    }

    private static void loggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        TOLD.remove(event.getEntity().getUUID());
    }

    private static void stopped(ServerStoppedEvent event) {
        TOLD.clear();
    }

    /** Hands back before a fresh recap is sent, and sends the line the screen shows. */
    static void atRecap(ServerPlayer player) {
        RiftClaimView view = RiftClaimView.NONE;
        if (RiftClaims.of(player.server).count(player.getUUID()) > 0) {
            RiftClaims.HandOver handed = RiftClaims.handOver(player);
            remember(player.getUUID(), handed.left());
            view = RiftClaimView.of(handed);
        }
        PacketDistributor.sendToPlayer(player, new RiftClaimPayload(view, true));
    }

    /** The screen's Claim the rest. */
    public static void requested(ServerPlayer player) {
        RiftClaims.HandOver handed = RiftClaims.handOver(player);
        remember(player.getUUID(), handed.left());
        PacketDistributor.sendToPlayer(player, new RiftClaimPayload(RiftClaimView.of(handed), false));
    }

    private static void tick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % INTERVAL != 0) {
            return;
        }
        RiftClaims claims = RiftClaims.of(server);
        if (claims.isEmpty()) {
            return;
        }
        RiftRecapLog recaps = RiftRecapLog.of(server);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            UUID id = player.getUUID();
            int held = claims.count(id);
            if (held == 0 || !retries(player.isAlive(), RiftLevels.isRift(player.level().dimension()),
                    recaps.unseen(id))) {
                continue;
            }
            if (RiftClaims.roomFor(player.getInventory(), claims.heldFor(id), player.hasInfiniteMaterials())) {
                RiftClaims.HandOver handed = RiftClaims.handOver(player);
                if (!handed.back().isEmpty()) {
                    remember(id, handed.left());
                    say(player, handed.left() > 0
                            ? Component.translatable(PARTLY_BACK, handed.back().size(), handed.left())
                            : Component.translatable(BACK, handed.back().size()));
                    PacketDistributor.sendToPlayer(player, new RiftClaimPayload(RiftClaimView.of(handed), false));
                    continue;
                }
            }
            if (!Integer.valueOf(held).equals(TOLD.get(id))) {
                TOLD.put(id, held);
                say(player, Component.translatable(WAITING, held));
            }
        }
    }

    /**
     * Whether the retry may hand back now: alive, outside a rift (the command's own rule), and no
     * recap waiting to be shown, because the recap does the hand-over itself a moment later.
     */
    static boolean retries(boolean alive, boolean inRift, boolean recapWaiting) {
        return alive && !inRift && !recapWaiting;
    }

    private static void remember(UUID id, int left) {
        if (left > 0) {
            TOLD.put(id, left);
        } else {
            TOLD.remove(id);
        }
    }

    private static void say(ServerPlayer player, Component line) {
        player.sendSystemMessage(line.copy().withStyle(ChatFormatting.GRAY));
    }
}
