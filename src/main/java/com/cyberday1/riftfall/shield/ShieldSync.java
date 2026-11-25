package com.cyberday1.riftfall.shield;

import com.cyberday1.riftfall.network.RiftShieldPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tells a player's client its own shield pool, on a write and on a new or rebuilt client. Never on
 * the drain: the client drains the last pool forward from its own clock. Sends are counted per
 * player, fake ones included, so the rig can count sends it cannot receive.
 */
public final class ShieldSync {

    private static final Map<UUID, ShieldPool> LAST = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> COUNTS = new ConcurrentHashMap<>();

    private ShieldSync() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(ShieldSync::onLoggedOut);
    }

    /** From {@code RiftShields#store}. Only a player has a HUD to tell. */
    static void changed(LivingEntity entity, ShieldPool pool) {
        if (entity instanceof ServerPlayer player && due(LAST.get(player.getUUID()), pool)) {
            send(player, pool);
        }
    }

    /** A client that is new or rebuilt: unconditional. From {@code RiftfallPayloads#tell}. */
    public static void tell(ServerPlayer player) {
        send(player, RiftShields.stored(player));
    }

    /** Whether a write changes what the client would draw. An empty pool written over an empty one does not. */
    static boolean due(@Nullable ShieldPool last, ShieldPool next) {
        if (last == null) {
            return true;
        }
        if (last.isEmpty() && next.isEmpty()) {
            return false;
        }
        return !last.equals(next);
    }

    private static void send(ServerPlayer player, ShieldPool pool) {
        LAST.put(player.getUUID(), pool);
        COUNTS.merge(player.getUUID(), 1, Integer::sum);
        // A fake player has no client to tell.
        if (!(player instanceof FakePlayer)) {
            PacketDistributor.sendToPlayer(player, RiftShieldPayload.of(pool));
        }
    }

    public static int sent(UUID player) {
        return COUNTS.getOrDefault(player, 0);
    }

    public static @Nullable ShieldPool lastSent(UUID player) {
        return LAST.get(player);
    }

    public static void forget(UUID player) {
        LAST.remove(player);
        COUNTS.remove(player);
    }

    private static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        forget(event.getEntity().getUUID());
    }
}
