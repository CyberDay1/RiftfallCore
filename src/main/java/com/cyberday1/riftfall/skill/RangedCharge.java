package com.cyberday1.riftfall.skill;

import com.cyberday1.riftfall.init.RiftfallAttributes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The Charge pool: one a player, keyed by UUID, so no weapon swap dodges it. A player with no entry is full, as
 * {@link RiftMana} reads one. Persisted by {@link RangedChargeStore} across a relog, a death, a dimension change and
 * a restart; a respawn does not refill it. The lock (a dry pool waiting for its unlock) is in memory only. The bank
 * and the refill are the player's attributes, read at each use, as {@link RiftMana} reads its max.
 */
public final class RangedCharge {

    private static volatile Map<UUID, Double> current = new ConcurrentHashMap<>();
    private static volatile Runnable changed = () -> { };
    private static final Set<UUID> LOCKED = ConcurrentHashMap.newKeySet();
    /** The tick each player's last shot passed {@link #gate} on. */
    private static final Map<UUID, Long> PASSED = new ConcurrentHashMap<>();

    private RangedCharge() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(RangedCharge::onTick);
        NeoForge.EVENT_BUS.addListener(RangedCharge::onLoggedOut);
    }

    /** Makes {@code store} the pool: from here every debit and refill lands in it. From {@link RangedChargeStore}. */
    static void use(Map<UUID, Double> store, Runnable onChange) {
        current = store;
        changed = onChange;
    }

    /** Back to a pool nothing saves, between servers. */
    static void forget() {
        current = new ConcurrentHashMap<>();
        changed = () -> { };
        LOCKED.clear();
        PASSED.clear();
    }

    static boolean backedBy(Map<UUID, Double> store) {
        return current == store;
    }

    /** Whether the pool applies to {@code player}: not in creative or spectator, where nothing is spent. */
    public static boolean applies(Player player) {
        return ammoFree(player) && !player.isCreative() && !player.isSpectator();
    }

    /**
     * Whether {@code player} fires without ammo. A fake player (a machine, a rig probe) does not: it never ticks,
     * so its pool would never refill, and it keeps the ammo rules it had.
     */
    public static boolean ammoFree(Player player) {
        return !(player instanceof FakePlayer);
    }

    /** The player's bank: {@code riftfall:max_charge}, never under 1 so a debuff cannot make fire free. */
    public static double bank(Player player) {
        return player.getAttributeValue(RiftfallAttributes.MAX_CHARGE);
    }

    /** The player's refill a second: {@code riftfall:charge_regen} times the mana combat window's split. */
    public static double refillPerSecond(Player player, boolean inCombat) {
        return ChargeRules.refillPerSecond(player.getAttributeValue(RiftfallAttributes.CHARGE_REGEN), inCombat);
    }

    public static double current(Player player) {
        return current(player.getUUID(), bank(player));
    }

    /** The pool read against {@code bank}: full with no entry, and held inside the bank both ways. */
    public static double current(UUID player, double bank) {
        return Math.clamp(current.getOrDefault(player, bank), -bank, bank);
    }

    public static void debit(Player player, double cost) {
        debit(player.getUUID(), cost, bank(player));
    }

    /** Takes one projectile's cost. Never refuses: the gate is {@link #ready}, and a pool can be overdrawn. */
    public static void debit(UUID player, double cost, double bank) {
        if (cost > 0.0) {
            put(player, ChargeRules.debit(current(player, bank), cost, bank), bank);
        }
    }

    /** Looks at the pool for one shot of {@code shot}, moving the lock. True when the shot may fire. */
    public static boolean ready(UUID player, ChargeRules.Shot shot, double bank) {
        boolean locked = ChargeRules.locked(LOCKED.contains(player), current(player, bank), shot, bank);
        if (locked) {
            LOCKED.add(player);
        } else {
            LOCKED.remove(player);
        }
        return !locked;
    }

    public static boolean gate(Player player, ChargeRules.Shot shot, long now) {
        return gate(player.getUUID(), shot, now, bank(player));
    }

    /** {@link #ready}, asked once a tick: every projectile of one release passes with the first. */
    public static boolean gate(UUID player, ChargeRules.Shot shot, long now, double bank) {
        Long passed = PASSED.get(player);
        if (passed != null && passed == now) {
            return true;
        }
        if (!ready(player, shot, bank)) {
            return false;
        }
        PASSED.put(player, now);
        return true;
    }

    public static boolean locked(UUID player) {
        return LOCKED.contains(player);
    }

    public static void set(Player player, double value) {
        set(player.getUUID(), value, bank(player));
    }

    /** Sets the pool. Probes and tests. */
    public static void set(UUID player, double value, double bank) {
        put(player, Math.clamp(value, -bank, bank), bank);
    }

    /** Back to full and unlocked. */
    public static void clear(UUID player) {
        LOCKED.remove(player);
        if (current.remove(player) != null) {
            changed.run();
        }
    }

    private static void put(UUID player, double value, double bank) {
        if (value >= bank) {
            if (current.remove(player) != null) {
                changed.run();
            }
            return;
        }
        Double before = current.put(player, value);
        if (before == null || before != value) {
            changed.run();
        }
    }

    /** A full pool at the moments mana fills: a fresh rift entry. A respawn is not one. */
    public static boolean refill(UUID player, ManaRules.Moment moment) {
        if (!ManaRules.refills(moment)) {
            return false;
        }
        clear(player);
        return true;
    }

    /**
     * One tick of refill at {@code perSecond}. A full pool does nothing, and so does one at or over {@code bank}:
     * at login the stat modifiers are not on the bank yet, and dropping the entry then would read as full later.
     */
    public static void regenerate(UUID player, double bank, double perSecond) {
        Double now = current.get(player);
        if (now != null && now < bank && perSecond > 0.0) {
            put(player, now + perSecond / 20.0, bank);
        }
    }

    /** Refill first, then the lock against the held weapon, then the client: its bar and its gate read that. */
    public static void tick(ServerPlayer player, long now) {
        UUID id = player.getUUID();
        double bank = bank(player);
        regenerate(id, bank, refillPerSecond(player, ManaCombat.inCombat(player)));
        ChargeRules.Shot held = ChargedWeapons.held(player);
        if (held != null && applies(player)) {
            ready(id, held, bank);
        }
        ChargeSync.tick(player, held != null, now);
    }

    private static void onTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            tick(player, player.level().getGameTime());
        }
    }

    private static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID();
        LOCKED.remove(id);
        PASSED.remove(id);
        ChargeSync.forget(id);
    }
}
