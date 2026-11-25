package com.cyberday1.riftfall.home;

import com.cyberday1.riftfall.RiftfallCore;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.player.*;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;

/** Enforces home access even in unclaimed/newly expanded chunks. */
@EventBusSubscriber(modid = RiftfallCore.MODID)
public final class HomeProtection {
    private HomeProtection() {}

    public static boolean denied(Level level, Entity actor) {
        if (!(level instanceof ServerLevel serverLevel)) return false;
        var owner = HomeAccess.owner(level.dimension().location());
        if (owner == null || actor == null) return false;
        // Resolve pets and projectiles to the player responsible, including offline pet owners.
        if (actor instanceof Projectile projectile && projectile.getOwner() != null) actor = projectile.getOwner();
        var id = actor instanceof OwnableEntity pet && pet.getOwnerUUID() != null ? pet.getOwnerUUID() : actor.getUUID();
        if (!(actor instanceof Player) && !(actor instanceof OwnableEntity) && !(actor instanceof Projectile)) return false;
        return !HomeAccess.canInteract(serverLevel.getServer(), owner, id);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void breakBlock(BlockEvent.BreakEvent event) {
        if (event.getLevel() instanceof Level level && denied(level, event.getPlayer())) event.setCanceled(true);
    }
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void place(BlockEvent.EntityPlaceEvent event) {
        if (event.getLevel() instanceof Level level && denied(level, event.getEntity())) event.setCanceled(true);
    }
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void tools(BlockEvent.BlockToolModificationEvent event) {
        if (event.getLevel() instanceof Level level && denied(level, event.getPlayer())) event.setCanceled(true);
    }
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void trample(BlockEvent.FarmlandTrampleEvent event) {
        if (event.getLevel() instanceof Level level && denied(level, event.getEntity())) event.setCanceled(true);
    }
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void rightClick(PlayerInteractEvent.RightClickBlock event) {
        if (denied(event.getLevel(), event.getEntity())) {
            event.setUseBlock(TriState.FALSE); event.setUseItem(TriState.FALSE);
            event.setCancellationResult(InteractionResult.FAIL); event.setCanceled(true);
        }
    }
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void leftClick(PlayerInteractEvent.LeftClickBlock event) {
        if (denied(event.getLevel(), event.getEntity())) event.setCanceled(true);
    }
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void useItem(PlayerInteractEvent.RightClickItem event) {
        if (denied(event.getLevel(), event.getEntity())) {
            event.setCancellationResult(InteractionResult.FAIL); event.setCanceled(true);
        }
    }
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void interact(PlayerInteractEvent.EntityInteract event) {
        if (denied(event.getTarget().level(), event.getEntity())) {
            event.setCancellationResult(InteractionResult.FAIL); event.setCanceled(true);
        }
    }
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void interactSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        if (denied(event.getTarget().level(), event.getEntity())) {
            event.setCancellationResult(InteractionResult.FAIL); event.setCanceled(true);
        }
    }
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void attack(AttackEntityEvent event) {
        if (denied(event.getTarget().level(), event.getEntity())) event.setCanceled(true);
    }
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void pickup(ItemEntityPickupEvent.Pre event) {
        if (denied(event.getItemEntity().level(), event.getPlayer())) event.setCanPickup(TriState.FALSE);
    }
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void damage(LivingIncomingDamageEvent event) {
        if (denied(event.getEntity().level(), event.getSource().getEntity())) event.setCanceled(true);
    }
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void projectile(ProjectileImpactEvent event) {
        if (denied(event.getEntity().level(), event.getProjectile())) {
            event.setCanceled(true);
            event.getProjectile().discard();
        }
    }
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void continuedUse(LivingEntityUseItemEvent.Tick event) {
        // Access can be revoked while a bow/spell is charging or a player is traveling.
        if (denied(event.getEntity().level(), event.getEntity())) {
            event.setDuration(-1);
            event.getEntity().stopUsingItem();
        }
    }
    @SubscribeEvent
    public static void tick(net.neoforged.neoforge.event.tick.PlayerTickEvent.Post event) {
        var player = event.getEntity();
        if (player.containerMenu != player.inventoryMenu && denied(player.level(), player)) player.closeContainer();
    }
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void explosion(ExplosionEvent.Start event) {
        if (denied(event.getLevel(), event.getExplosion().getIndirectSourceEntity())) event.setCanceled(true);
    }
}
