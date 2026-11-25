package com.cyberday1.riftfall.home;

import com.cyberday1.riftfall.RiftfallCore;
import com.cyberday1.riftfall.home.npc.HomeResidentData;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.level.PistonEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A home tile with a resident on it is permanent terrain for everyone, owners and creative players included. The
 * resident's saved anchor decides which tile, not the layout, so a dead resident keeps its tile. Plain setBlock stays
 * open: tile placement, the repair passes and station overlays use it.
 */
@EventBusSubscriber(modid = RiftfallCore.MODID)
public final class ResidentTileProtection {
    /** One action-bar line at most this often per player while they keep trying. */
    static final int TELL_EVERY_TICKS = 20;
    private static final Map<UUID, Long> TOLD = new ConcurrentHashMap<>();

    private ResidentTileProtection() { }

    static ShatteredHomeTiles.GridPos tile(BlockPos pos) {
        return new ShatteredHomeTiles.GridPos(Math.floorDiv(pos.getX(), ShatteredHomeTiles.TILE_SIZE),
                Math.floorDiv(pos.getZ(), ShatteredHomeTiles.TILE_SIZE));
    }

    static boolean contains(ShatteredHomeProgress progress, HomeResidentData residents, BlockPos pos) {
        var grid = tile(pos);
        if (!progress.unlockedTiles().contains(grid)) return false;
        for (var resident : residents.residents()) {
            if (HomeResidentData.supported(resident.role()) && tile(resident.anchor()).equals(grid)) return true;
        }
        return false;
    }

    public static boolean protectedAt(Level level, BlockPos pos) {
        return level instanceof ServerLevel server && ShatteredHomeLevels.isShatteredHome(level.dimension())
                && contains(ShatteredHomeProgress.of(server), HomeResidentData.of(server), pos);
    }

    /** Fluid may not cross into a locked tile from outside it; fluid already inside flows as before. */
    public static boolean fluidBlocked(Level level, BlockPos from, BlockPos to) {
        return !tile(from).equals(tile(to)) && protectedAt(level, to) && !protectedAt(level, from);
    }

    /** The action-bar line, only to a player who could otherwise build here (a stranger is refused silently, as before). */
    public static void tell(Entity actor, Level level) {
        if (!(actor instanceof ServerPlayer player) || HomeProtection.denied(level, player)) return;
        long now = level.getGameTime();
        Long last = TOLD.get(player.getUUID());
        if (last != null && now - last >= 0 && now - last < TELL_EVERY_TICKS) return;
        TOLD.put(player.getUUID(), now);
        player.displayClientMessage(Component.translatable(HomeCommands.RESIDENT_TILE), true);
    }

    private static boolean decoration(Entity entity) {
        return (entity instanceof HangingEntity || entity instanceof ArmorStand)
                && protectedAt(entity.level(), entity.blockPosition());
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void breakBlock(BlockEvent.BreakEvent event) {
        if (event.getLevel() instanceof Level level && protectedAt(level, event.getPos())) {
            event.setCanceled(true);
            tell(event.getPlayer(), level);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void leftClick(PlayerInteractEvent.LeftClickBlock event) {
        if (protectedAt(event.getLevel(), event.getPos())) {
            event.setCanceled(true);
            if (event.getAction() == PlayerInteractEvent.LeftClickBlock.Action.START) tell(event.getEntity(), event.getLevel());
        }
    }

    /** Covers EntityMultiPlaceEvent too (beds, doors): refused if any of its blocks lands on a locked tile. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void place(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof Level level)) return;
        boolean locked = event instanceof BlockEvent.EntityMultiPlaceEvent multi
                ? multi.getReplacedBlockSnapshots().stream().anyMatch(s -> protectedAt(level, s.getPos()))
                : protectedAt(level, event.getPos());
        if (locked) {
            event.setCanceled(true);
            tell(event.getEntity(), level);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void modify(BlockEvent.BlockToolModificationEvent event) {
        if (event.getLevel() instanceof Level level && protectedAt(level, event.getPos())) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void trample(BlockEvent.FarmlandTrampleEvent event) {
        if (event.getLevel() instanceof Level level && protectedAt(level, event.getPos())) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void explosion(ExplosionEvent.Detonate event) {
        event.getAffectedBlocks().removeIf(pos -> protectedAt(event.getLevel(), pos));
        event.getAffectedEntities().removeIf(ResidentTileProtection::decoration);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void piston(PistonEvent.Pre event) {
        if (!(event.getLevel() instanceof Level level) || !ShatteredHomeLevels.isShatteredHome(level.dimension())) return;
        if (protectedAt(level, event.getPos()) || protectedAt(level, event.getFaceOffsetPos())) {
            event.setCanceled(true);
            return;
        }
        var resolver = event.getStructureHelper();
        if (resolver == null || !resolver.resolve()) return;
        var movement = event.getPistonMoveType().isExtend ? event.getDirection() : event.getDirection().getOpposite();
        if (resolver.getToDestroy().stream().anyMatch(pos -> protectedAt(level, pos))
                || resolver.getToPush().stream().anyMatch(pos -> protectedAt(level, pos) || protectedAt(level, pos.relative(movement)))) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void attack(AttackEntityEvent event) {
        if (decoration(event.getTarget())) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void interact(PlayerInteractEvent.EntityInteract event) {
        if (decoration(event.getTarget())) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void interactSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        if (decoration(event.getTarget())) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void projectile(ProjectileImpactEvent event) {
        if (event.getRayTraceResult() instanceof EntityHitResult hit && decoration(hit.getEntity())) {
            event.setCanceled(true);
            event.getProjectile().discard();
        }
    }

    @SubscribeEvent
    public static void loggedOut(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        TOLD.remove(event.getEntity().getUUID());
    }
}
