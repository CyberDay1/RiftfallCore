package com.cyberday1.riftfall.skill;

import com.cyberday1.riftfall.init.RiftfallAttributes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Riftfall's own mana bar. The engines still cast free; this is what a Riftfall cast pays.
 *
 * <p>Current mana is kept like {@link SkillCooldowns}' timers: while a server runs the map is
 * {@link RiftManaStore}'s, so a relog or restart keeps the bar. A player with no entry is full, and
 * every read clamps to the live max. Max and regen are the attributes {@code riftfall:max_mana} and
 * {@code riftfall:mana_regen}.
 */
public final class RiftMana {

    /** 2026-11-08: cut from 300, "way too much mana". Default of {@code riftfall:max_mana}. */
    public static final double BASE_POOL = 150.0;
    /** 2026-11-08: cut from 10. Mana per second, the default of {@code riftfall:mana_regen}. */
    public static final double BASE_REGEN = 6.0;

    private static final double TICKS_PER_SECOND = 20.0;

    private static final Runnable NOTHING = () -> {
    };

    private static volatile Map<UUID, Double> current = new ConcurrentHashMap<>();
    private static volatile Runnable changed = NOTHING;

    private RiftMana() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(RiftMana::onTick);
    }

    /** The bar now lives in {@code store}, and {@code onChange} runs after every write. Server start. */
    static void use(Map<UUID, Double> store, Runnable onChange) {
        current = store;
        changed = onChange;
    }

    /** Back to a fresh in-memory map. Server stop, so one world's bars never reach the next. */
    static void forget() {
        use(new ConcurrentHashMap<>(), NOTHING);
    }

    static boolean backedBy(Map<UUID, Double> store) {
        return current == store;
    }

    public static double max(Player player) {
        return player.getAttributeValue(RiftfallAttributes.MAX_MANA);
    }

    public static double current(Player player) {
        return current(player.getUUID(), max(player));
    }

    public static double current(UUID player, double max) {
        return Math.min(current.getOrDefault(player, max), max);
    }

    /** Takes all of {@code cost} or none of it. */
    public static boolean tryCharge(UUID player, double cost, double max) {
        double have = current(player, max);
        if (have < cost) {
            return false;
        }
        put(player, have - cost);
        return true;
    }

    public static void refund(UUID player, double amount, double max) {
        put(player, Math.min(max, current(player, max) + amount));
    }

    /** Probe use: puts the bar at a value. */
    public static void set(UUID player, double amount) {
        put(player, amount);
    }

    /** Refills one player's bar. Probe use; logging in or out no longer does this. */
    public static void clear(UUID player) {
        if (current.remove(player) != null) {
            changed.run();
        }
    }

    public static void clearAll() {
        current.clear();
        changed.run();
    }

    private static void put(UUID player, double amount) {
        current.put(player, amount);
        changed.run();
    }

    /** One tick's regeneration, faster out of combat (M-5). The body of the tick listener, public so the rig can drive it. */
    public static void regenerate(Player player) {
        boolean fighting = player instanceof ServerPlayer server && ManaCombat.inCombat(server);
        regenerate(player.getUUID(), max(player), regenPerSecond(player, fighting));
    }

    /** The attribute times {@link ManaRules#regenMultiplier}. */
    public static double regenPerSecond(Player player, boolean inCombat) {
        return player.getAttributeValue(RiftfallAttributes.MANA_REGEN) * ManaRules.regenMultiplier(inCombat);
    }

    /** Refills the bar if {@code moment} is one that refills (M-4: rift entry only). Answers whether it did. */
    public static boolean refill(UUID player, ManaRules.Moment moment) {
        if (!ManaRules.refills(moment)) {
            return false;
        }
        clear(player);
        return true;
    }

    /**
     * One tick at {@code perSecond}, without a player. A stored value at or above {@code max} is left
     * alone: at login worn gear is not on the attribute yet, and dropping the entry then would read as
     * full once it is.
     */
    public static void regenerate(UUID id, double max, double perSecond) {
        Double have = current.get(id);
        if (have != null && have < max) {
            double next = have + perSecond / TICKS_PER_SECOND;
            if (next >= max) {
                current.remove(id);
            } else {
                current.put(id, next);
            }
            changed.run();
        }
    }

    /** The tick listener's body: regenerate, then tell the HUD (throttled). Public so the rig can drive it. */
    public static void tick(ServerPlayer player, long now) {
        regenerate(player);
        SkillSync.manaTick(player, now);
    }

    private static void onTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            tick(player, player.level().getGameTime());
        }
    }
}
