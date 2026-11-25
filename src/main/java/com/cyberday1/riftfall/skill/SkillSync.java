package com.cyberday1.riftfall.skill;

import com.cyberday1.riftfall.network.RiftManaPayload;
import com.cyberday1.riftfall.network.SkillCooldownPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * What the client is told about its skills between screens: each slot's ready-at tick (V3, CD1)
 * and the mana bar (V2). Every send is counted per player, fake ones included, so the rig can
 * count sends it cannot receive.
 */
public final class SkillSync {

    /** Regen is sent at most every this many ticks (4 a second); a cast, a new max or a full bar at once. */
    public static final int MANA_THROTTLE_TICKS = 5;

    record Sent(double current, double max, long at) {
    }

    private static final Map<UUID, Sent> LAST_MANA = new ConcurrentHashMap<>();
    private static final Map<UUID, long[]> LAST_COOLDOWN = new ConcurrentHashMap<>();
    private static final Map<UUID, SkillCooldownPayload[]> LAST_PAYLOAD = new ConcurrentHashMap<>();
    private static final Map<UUID, int[]> COUNTS = new ConcurrentHashMap<>();
    private static final int MANA = 0;
    private static final int COOLDOWN = 1;

    private SkillSync() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(SkillSync::onLoggedOut);
    }

    /** A client that is new or rebuilt: every slot's timer and the bar. From {@code RiftfallPayloads#tell}. */
    public static void tell(ServerPlayer player) {
        cooldowns(player);
        mana(player);
    }

    public static void cooldowns(ServerPlayer player) {
        for (int slot = 0; slot < SkillBook.SLOTS; slot++) {
            cooldown(player, slot);
        }
    }

    /** The slot's skill, its ready-at tick and the timer's length; "", 0 and 0 for an empty slot. */
    public static void cooldown(ServerPlayer player, int slot) {
        SkillBook book = SkillService.bookOf(player);
        String id = book.slot(slot);
        long readyAt = id == null ? 0L : SkillCooldowns.readyAt(player, id);
        int duration = id == null ? 0 : duration(readyAt, player.level().getGameTime(), fullCooldown(player, book, id));
        SkillCooldownPayload payload = new SkillCooldownPayload(slot, id == null ? "" : id, readyAt, duration);
        LAST_COOLDOWN.computeIfAbsent(player.getUUID(), p -> emptyCooldowns())[slot] = readyAt;
        LAST_PAYLOAD.computeIfAbsent(player.getUUID(), p -> new SkillCooldownPayload[SkillBook.SLOTS])[slot] = payload;
        count(player, COOLDOWN);
        send(player, payload);
    }

    /**
     * The length the HUD sweeps over: the slot's full cooldown now, haste included, which on a cast is the timer
     * just started. Never shorter than what is left, so the sweep never starts past full; 0 once ready.
     */
    static int duration(long readyAt, long now, int full) {
        long left = readyAt - now;
        if (left <= 0L) {
            return 0;
        }
        return (int) Math.min(Integer.MAX_VALUE, Math.max(full, left));
    }

    /** What a cast of {@code id} would start now, as {@code SkillCasts#cast} reckons it; 0 for an unknown node. */
    static int fullCooldown(LivingEntity caster, SkillBook book, String id) {
        SkillDef def = SkillDefs.get(id);
        if (def == null) {
            return 0;
        }
        SkillNode node = def.cast();
        int rank = Math.max(0, Math.min(book.rank(id), node.maxRank()));
        return SkillRanks.cooldownTicks(node, rank, SkillCasts.abilityHaste(caster, node));
    }

    /** Unthrottled: a cast, a login, a respawn. */
    public static void mana(ServerPlayer player) {
        sendMana(player, RiftMana.current(player), RiftMana.max(player), player.level().getGameTime());
    }

    /** The regen tick's half: sends only a change, and a regen change at most every {@link #MANA_THROTTLE_TICKS}. */
    public static void manaTick(ServerPlayer player, long now) {
        cooldownsIfChanged(player);
        double current = RiftMana.current(player);
        double max = RiftMana.max(player);
        if (due(LAST_MANA.get(player.getUUID()), current, max, now)) {
            sendMana(player, current, max, now);
        }
    }

    /** Refresh mode changes on the next player tick, including a return to survival. */
    static void cooldownsIfChanged(ServerPlayer player) {
        var book = SkillService.bookOf(player);
        for (int slot = 0; slot < SkillBook.SLOTS; slot++) {
            String id = book.slot(slot);
            long readyAt = id == null ? 0L : SkillCooldowns.readyAt(player, id);
            if (lastCooldown(player.getUUID(), slot) != readyAt) cooldown(player, slot);
        }
    }

    /** Whether a bar reading is worth sending, given the last one sent. */
    static boolean due(@Nullable Sent last, double current, double max, long now) {
        if (last == null || last.max() != max || current >= max) {
            return last == null || last.current() != current || last.max() != max;
        }
        return last.current() != current && now - last.at() >= MANA_THROTTLE_TICKS;
    }

    private static void sendMana(ServerPlayer player, double current, double max, long now) {
        LAST_MANA.put(player.getUUID(), new Sent(current, max, now));
        count(player, MANA);
        send(player, new RiftManaPayload(current, max));
    }

    /** The last bar sent to this player, or null. */
    public static @Nullable RiftManaPayload lastMana(UUID player) {
        Sent last = LAST_MANA.get(player);
        return last == null ? null : new RiftManaPayload(last.current(), last.max());
    }

    /** The last ready-at tick sent for a slot, or -1 if none was. */
    public static long lastCooldown(UUID player, int slot) {
        long[] last = LAST_COOLDOWN.get(player);
        return last == null ? -1L : last[slot];
    }

    /** The last cooldown payload built for a slot, or null if none was. The rig reads what a client would get. */
    public static @Nullable SkillCooldownPayload lastCooldownPayload(UUID player, int slot) {
        SkillCooldownPayload[] last = LAST_PAYLOAD.get(player);
        return last == null || slot < 0 || slot >= last.length ? null : last[slot];
    }

    public static int manaSent(UUID player) {
        return COUNTS.getOrDefault(player, new int[2])[MANA];
    }

    public static int cooldownSent(UUID player) {
        return COUNTS.getOrDefault(player, new int[2])[COOLDOWN];
    }

    public static void forget(UUID player) {
        LAST_MANA.remove(player);
        LAST_COOLDOWN.remove(player);
        LAST_PAYLOAD.remove(player);
        COUNTS.remove(player);
    }

    private static long[] emptyCooldowns() {
        long[] slots = new long[SkillBook.SLOTS];
        Arrays.fill(slots, -1L);
        return slots;
    }

    private static void count(ServerPlayer player, int kind) {
        COUNTS.computeIfAbsent(player.getUUID(), p -> new int[2])[kind]++;
    }

    private static void send(ServerPlayer player, CustomPacketPayload payload) {
        // A fake player has no client to tell.
        if (!(player instanceof FakePlayer)) {
            PacketDistributor.sendToPlayer(player, payload);
        }
    }

    private static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        forget(event.getEntity().getUUID());
    }
}
