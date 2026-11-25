package com.cyberday1.riftfall.gear;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import com.cyberday1.riftfall.gear.RelicSignatures.ActiveSet;
import com.cyberday1.riftfall.gear.RelicSignatures.Signature;
import com.cyberday1.riftfall.network.RelicStacksPayload;

/**
 * Tells a player's client its own relic stacks and signature cooldowns for the HUD icons. {@link #check} runs
 * wherever one can move (the bucket, the signature lines, Soul Stacks' line, a cooldown starting) and sends only when
 * the payload differs from the last send. A cooldown is sent as its end tick, so it holds still while it counts down;
 * {@link #lapse} sends its end. Sends are counted per player, fake ones included, so the rig can count them.
 */
public final class RelicStackSync {

    private static final Map<UUID, RelicStacksPayload> LAST = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> COUNTS = new ConcurrentHashMap<>();

    private RelicStackSync() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent event) -> forget(event.getEntity().getUUID()));
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent event) -> {
            LAST.clear();
            COUNTS.clear();
        });
    }

    /**
     * What the client should show at {@code now}: a signature's stacks, and the tick its cooldown ends, while it is
     * active; 0 when dormant, not worn, or (a cooldown) ready.
     */
    public static RelicStacksPayload stacks(ActiveSet set, int rage, int souls, int momentum, long sporeReady,
                                            long poundReady, long now) {
        return new RelicStacksPayload(
                set.isActive(Signature.BERSERKER_RAGE) ? Math.max(0, rage) : 0,
                set.isActive(Signature.SOUL_STACKS) ? Math.max(0, souls) : 0,
                set.isActive(Signature.MOMENTUM) ? Math.max(0, momentum) : 0,
                cooldown(set.isActive(Signature.SPORE_BURST), sporeReady, now),
                cooldown(set.isActive(Signature.GROUND_POUND), poundReady, now));
    }

    /** A cooldown's end tick while it is active and still running, else 0. */
    public static long cooldown(boolean active, long readyAt, long now) {
        return active && now < readyAt ? readyAt : 0L;
    }

    /** Whether to send: never sent, or the counts moved. */
    public static boolean due(@Nullable RelicStacksPayload last, RelicStacksPayload now) {
        return !now.equals(last);
    }

    public static RelicStacksPayload of(ServerPlayer player, long now) {
        return stacks(RelicSignatures.active(player), RelicSignatureProcs.rage(player),
                RelicSignatureProcs.souls(player), RelicSignatureStats.momentum(player),
                RelicSignatureProcs.sporeReady(player), RelicSignatureProcs.poundReady(player), now);
    }

    /** A stack or cooldown may have moved: send if it did. */
    static void check(ServerPlayer player) {
        check(player, player.level().getGameTime());
    }

    private static void check(ServerPlayer player, long now) {
        RelicStacksPayload stacks = of(player, now);
        if (due(LAST.get(player.getUUID()), stacks)) send(player, stacks);
    }

    /** The player tick, and the probe with a later clock: a cooldown last sent as running has ended, so send. */
    public static void lapse(ServerPlayer player, long now) {
        RelicStacksPayload last = LAST.get(player.getUUID());
        if (last != null && last.lapsed(now)) check(player, now);
    }

    /** A new or rebuilt client: the next check sends whatever the counts are. */
    static void unsent(ServerPlayer player) {
        LAST.remove(player.getUUID());
    }

    private static void send(ServerPlayer player, RelicStacksPayload stacks) {
        LAST.put(player.getUUID(), stacks);
        COUNTS.merge(player.getUUID(), 1, Integer::sum);
        // A fake player has no client to tell.
        if (!(player instanceof FakePlayer)) PacketDistributor.sendToPlayer(player, stacks);
    }

    public static int sent(UUID player) {
        return COUNTS.getOrDefault(player, 0);
    }

    public static @Nullable RelicStacksPayload lastSent(UUID player) {
        return LAST.get(player);
    }

    public static void forget(UUID player) {
        LAST.remove(player);
        COUNTS.remove(player);
    }
}
