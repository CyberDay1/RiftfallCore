package com.cyberday1.riftfall.skill;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Who is in combat, for M-5's regen split. A player is marked when an entity damages them, when they
 * damage another living thing, and when they cast a Riftfall skill. Environmental damage (a fall, fire,
 * drowning) has no entity and does not mark. In memory only: after a restart everyone is out of combat.
 */
public final class ManaCombat {

    private static final Map<UUID, Long> LAST = new ConcurrentHashMap<>();

    private ManaCombat() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(ManaCombat::onDamaged);
        NeoForge.EVENT_BUS.addListener(ManaCombat::onLoggedOut);
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent event) -> LAST.clear());
    }

    public static void mark(UUID player, long now) {
        LAST.put(player, now);
    }

    /** Marks a player now, by the server's tick count. Anything else is ignored. */
    public static void mark(Entity entity) {
        if (entity instanceof ServerPlayer player) {
            mark(player.getUUID(), now(player.server));
        }
    }

    public static boolean inCombat(UUID player, long now) {
        return ManaRules.inCombat(LAST.getOrDefault(player, -1L), now);
    }

    public static boolean inCombat(ServerPlayer player) {
        return inCombat(player.getUUID(), now(player.server));
    }

    public static void forget(UUID player) {
        LAST.remove(player);
    }

    /** The server's own tick count: one clock for every dimension. */
    public static long now(MinecraftServer server) {
        return server.getTickCount();
    }

    private static void onDamaged(LivingDamageEvent.Post event) {
        Entity attacker = event.getSource().getEntity();
        if (attacker == null || event.getNewDamage() <= 0.0F) {
            return;
        }
        mark(event.getEntity());
        if (attacker != event.getEntity()) {
            mark(attacker);
        }
    }

    private static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        forget(event.getEntity().getUUID());
    }
}
