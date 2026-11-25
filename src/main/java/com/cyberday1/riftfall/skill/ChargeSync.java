package com.cyberday1.riftfall.skill;

import com.cyberday1.riftfall.network.RangedChargePayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * What the client is told about its Charge pool. A shot, a lock moving and a full pool go at once; refill at most
 * every {@link SkillSync#MANA_THROTTLE_TICKS}, and only while a bow or a gun is held (the bar shows only then).
 */
public final class ChargeSync {

    record Sent(double current, double max, boolean locked, long at) {
    }

    private static final Map<UUID, Sent> LAST = new ConcurrentHashMap<>();

    private ChargeSync() {
    }

    /** Unthrottled: a login, a respawn, a dimension change, a shot. */
    public static void tell(ServerPlayer player) {
        send(player, player.level().getGameTime());
    }

    static void tick(ServerPlayer player, boolean armed, long now) {
        UUID id = player.getUUID();
        double max = RangedCharge.bank(player);
        if (due(LAST.get(id), RangedCharge.current(id, max), max, RangedCharge.locked(id), armed, now)) {
            send(player, now);
        }
    }

    /** Whether a reading is worth sending, given the last one sent. A new bank goes at once: the bar rescales. */
    static boolean due(@Nullable Sent last, double current, double max, boolean locked, boolean armed, long now) {
        if (last == null || last.locked() != locked || last.max() != max) {
            return true;
        }
        if (last.current() == current || !armed) {
            return false;
        }
        return current >= max || now - last.at() >= SkillSync.MANA_THROTTLE_TICKS;
    }

    private static void send(ServerPlayer player, long now) {
        UUID id = player.getUUID();
        double max = RangedCharge.bank(player);
        Sent sent = new Sent(RangedCharge.current(id, max), max, RangedCharge.locked(id), now);
        LAST.put(id, sent);
        // A fake player has no client to tell.
        if (!(player instanceof FakePlayer)) {
            PacketDistributor.sendToPlayer(player, new RangedChargePayload(sent.current(), max, sent.locked()));
        }
    }

    /** The last reading sent to this player, or null. */
    public static @Nullable RangedChargePayload last(UUID player) {
        Sent last = LAST.get(player);
        return last == null ? null : new RangedChargePayload(last.current(), last.max(), last.locked());
    }

    public static void forget(UUID player) {
        LAST.remove(player);
    }
}
