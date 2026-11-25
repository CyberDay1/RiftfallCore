package com.cyberday1.riftfall.rift;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;

import java.util.Set;
import java.util.UUID;

/** Riftfall parties are independent of scoreboard teams and FTB teams. */
public final class PartyCombat {
    private static volatile Set<UUID> clientMembers = Set.of();

    private PartyCombat() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, PartyCombat::damage);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, PartyCombat::attack);
    }

    public static void clientMembers(Set<UUID> members) {
        clientMembers = Set.copyOf(members);
    }

    /** Resolve projectiles and pets without requiring an offline owner to be loaded. */
    static UUID owner(Entity entity) {
        for (int depth = 0; entity != null && depth < 8; depth++) {
            if (entity instanceof Player) return entity.getUUID();
            if (entity instanceof OwnableEntity pet) return pet.getOwnerUUID();
            if (entity instanceof Projectile projectile) entity = projectile.getOwner();
            else return null;
        }
        return null;
    }

    public static boolean allies(Entity actor, Entity target) {
        if (actor == null || target == null || actor == target) return false;
        UUID from = owner(actor);
        UUID to = owner(target);
        if (from == null || to == null) return false;
        if (from.equals(to)) return true; // An owner's summons/pets.
        if (actor.level().isClientSide) return clientMembers.contains(from) && clientMembers.contains(to);
        var server = actor.getServer();
        if (server == null) return false;
        RiftParty party = RiftParties.of(server).of(from);
        return party != null && party.has(to);
    }

    static void damage(LivingIncomingDamageEvent event) {
        if (allies(event.getSource().getEntity(), event.getEntity())) event.setCanceled(true);
    }

    static void attack(AttackEntityEvent event) {
        if (allies(event.getEntity(), event.getTarget())) event.setCanceled(true);
    }
}
