package com.cyberday1.riftfall.home.npc;

import com.cyberday1.riftfall.RiftfallCore;
import com.cyberday1.riftfall.block.marker.NpcRole;
import com.cyberday1.riftfall.block.marker.NpcSpawnMarkerBlock;
import com.cyberday1.riftfall.home.HomeAccess;
import com.cyberday1.riftfall.home.ShatteredHomeProgress;
import com.cyberday1.riftfall.home.ShatteredHomeTiles;
import com.cyberday1.riftfall.init.RiftfallBlocks;
import com.mojang.logging.LogUtils;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.*;

/** Optional CNPC integration. All saved state and permissions belong to Core, not NPC scripts. */
@EventBusSubscriber(modid = RiftfallCore.MODID)
public final class HomeResidents {
    public static final String ROLE_TAG = "riftfall_resident_role";
    private HomeResidents() {}

    public static boolean available() {
        return ModList.get() != null && ModList.get().isLoaded("customnpcs");
    }
    public static boolean isHome(ServerLevel level) { return HomeAccess.owner(level.dimension().location()) != null; }
    public static boolean unlocked(ServerLevel level, BlockPos pos) {
        return ShatteredHomeProgress.of(level).unlockedTiles().contains(new ShatteredHomeTiles.GridPos(
                Math.floorDiv(pos.getX(), ShatteredHomeTiles.TILE_SIZE),
                Math.floorDiv(pos.getZ(), ShatteredHomeTiles.TILE_SIZE)));
    }

    public static HomeResidentData.Resident marker(ServerLevel level, BlockPos pos, BlockState state) {
        if (!(state.getBlock() instanceof NpcSpawnMarkerBlock block) || !HomeResidentData.supported(block.role()))
            return null;
        return new HomeResidentData.Resident(block.role(), pos, state.getValue(NpcSpawnMarkerBlock.FACING),
                state.getValue(NpcSpawnMarkerBlock.ROAM_RADIUS), HomeResidentData.identity(level.dimension().location(), block.role()));
    }

    /** Read local states before transforming, so mirrors and rotations are each applied exactly once. */
    public static List<HomeResidentData.Resident> readMarkers(ServerLevel level, StructureTemplate template,
                                                              BlockPos origin, StructurePlaceSettings settings) {
        if (!isHome(level)) return List.of();
        var found = new ArrayList<HomeResidentData.Resident>();
        var roles = EnumSet.noneOf(NpcRole.class);
        var data = HomeResidentData.of(level);
        for (var role : NpcRole.values()) {
            for (var info : template.filterBlocks(BlockPos.ZERO, new StructurePlaceSettings(),
                    RiftfallBlocks.NPC_SPAWN_MARKERS.get(role).get())) {
                var pos = StructureTemplate.calculateRelativePosition(settings, info.pos()).offset(origin);
                var state = info.state().mirror(settings.getMirror()).rotate(settings.getRotation());
                var resident = marker(level, pos, state);
                var old = data.get(role);
                if (!roles.add(role) || old != null && !old.anchor().equals(pos))
                    throw new IllegalArgumentException("Duplicate " + role.id() + " resident marker at " + pos);
                found.add(resident);
            }
        }
        return List.copyOf(found);
    }

    public static void recordMarkers(ServerLevel level, List<HomeResidentData.Resident> markers) {
        for (var marker : markers) remember(level, marker);
    }

    /** Registers the marker and unlocks the role for the home's ranks and boards. */
    static boolean remember(ServerLevel level, HomeResidentData.Resident resident) {
        if (!HomeResidentData.of(level).register(resident)) return false;
        com.cyberday1.riftfall.home.npc.depth.ResidentDepth.unlock(level.getServer(),
                HomeAccess.owner(level.dimension().location()), resident.role());
        return true;
    }

    /** Recovery for a world saved after tile placement but before the resident record was saved. */
    @SubscribeEvent public static void chunkLoaded(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !isHome(level)
                || !(event.getChunk() instanceof LevelChunk chunk)) return;
        level.getServer().execute(() -> {
            if (level.getChunkSource().getChunkNow(chunk.getPos().x, chunk.getPos().z) != chunk) return;
            for (int section = 0; section < chunk.getSectionsCount(); section++) {
                var states = chunk.getSection(section);
                if (!states.maybeHas(s -> s.getBlock() instanceof NpcSpawnMarkerBlock b
                        && HomeResidentData.supported(b.role()))) continue;
                int baseY = chunk.getSectionYFromSectionIndex(section) << 4;
                for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
                    var state = states.getBlockState(x, y, z);
                    var pos = new BlockPos(chunk.getPos().getMinBlockX() + x, baseY + y, chunk.getPos().getMinBlockZ() + z);
                    var resident = marker(level, pos, state);
                    if (resident != null && unlocked(level, pos)) remember(level, resident);
                }
            }
        });
    }

    @SubscribeEvent public static void tick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.getGameTime() % 20 != 0
                || !isHome(level) || !available()) return;
        for (var resident : HomeResidentData.of(level).residents()) {
            if (!unlocked(level, resident.anchor()) || !level.areEntitiesLoaded(ChunkPos.asLong(resident.anchor()))
                    || !level.isPositionEntityTicking(resident.anchor())) continue;
            reconcile(level, resident);
        }
    }

    /** Spawns or keeps the resident now; the tick does this every second. Public for the rig probe. */
    public static void reconcile(ServerLevel level, HomeResidentData.Resident resident) {
        var existing = level.getEntity(resident.entityId());
        if (existing != null) {
            if (owned(existing) == null) return; // Never repurpose an unrelated entity with a conflicting UUID.
            CnpcResidents.keepAtStation(existing, resident);
            consumeMarker(level, resident);
            return;
        }
        var state = level.getBlockState(resident.anchor());
        boolean markerPresent = markerMatches(state, resident.role());
        if (!markerPresent && !state.getCollisionShape(level, resident.anchor()).isEmpty()) return;
        if (!level.getBlockState(resident.anchor().below()).isFaceSturdy(level, resident.anchor().below(), Direction.UP)
                || !level.getBlockState(resident.anchor().above()).getCollisionShape(level, resident.anchor().above()).isEmpty()
                || !level.getFluidState(resident.anchor()).isEmpty()) return;
        // Persist the anchor before removing its physical source. A failed spawn restores the marker.
        level.getDataStorage().save();
        if (markerPresent) level.setBlock(resident.anchor(), Blocks.AIR.defaultBlockState(), 3);
        boolean spawned = false;
        try { spawned = CnpcResidents.spawn(level, resident); }
        catch (RuntimeException ex) { LogUtils.getLogger().error("Could not create home resident {}", resident.role(), ex); }
        finally { if (!spawned && markerPresent) level.setBlock(resident.anchor(), state, 3); }
    }

    private static boolean markerMatches(BlockState state, NpcRole role) {
        return state.getBlock() instanceof NpcSpawnMarkerBlock block && block.role() == role;
    }
    private static void consumeMarker(ServerLevel level, HomeResidentData.Resident resident) {
        if (markerMatches(level.getBlockState(resident.anchor()), resident.role())) {
            level.getDataStorage().save();
            level.setBlock(resident.anchor(), Blocks.AIR.defaultBlockState(), 3);
        }
    }

    /** Both the saved identity and tag must match. A copied NPC tag alone grants no service. */
    public static HomeResidentData.Resident owned(Entity entity) {
        if (!(entity.level() instanceof ServerLevel level) || !isHome(level)
                || !BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).getNamespace().equals("customnpcs")) return null;
        String roleId = entity.getPersistentData().getString(ROLE_TAG);
        for (var role : NpcRole.values()) {
            if (!role.id().equals(roleId)) continue;
            var resident = HomeResidentData.of(level).get(role);
            return resident != null && resident.entityId().equals(entity.getUUID()) ? resident : null;
        }
        return null;
    }
    public static boolean canUse(ServerPlayer player, Entity entity) {
        var resident = owned(entity);
        return resident != null && entity.isAlive() && player.isAlive() && player.level() == entity.level()
                && player.distanceToSqr(entity) <= 36 && unlocked(player.serverLevel(), resident.anchor())
                && HomeAccess.canInteract(player.serverLevel().getServer(), HomeAccess.owner(entity.level().dimension().location()), player.getUUID());
    }

    private static boolean interact(PlayerInteractEvent event, Entity target) {
        var resident = owned(target);
        if (resident == null || !(event.getEntity() instanceof ServerPlayer player)) return false;
        if (event.getHand() != InteractionHand.MAIN_HAND) return true;
        // Every role lands on its native screen; CNPC keeps only the body (spawn, skin, identity).
        if (canUse(player, target)) ResidentMenus.openLanding(player, target.getUUID());
        else player.displayClientMessage(Component.translatable(com.cyberday1.riftfall.home.HomeCommands.NO_SERVICE_ACCESS), true);
        return true;
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void interact(PlayerInteractEvent.EntityInteract event) {
        if (interact(event, event.getTarget())) { event.setCanceled(true); event.setCancellationResult(InteractionResult.SUCCESS); }
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void interactSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        if (interact(event, event.getTarget())) { event.setCanceled(true); event.setCancellationResult(InteractionResult.SUCCESS); }
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void damage(LivingIncomingDamageEvent event) { if (owned(event.getEntity()) != null) event.setCanceled(true); }
    @SubscribeEvent public static void travel(EntityTravelToDimensionEvent event) {
        if (owned(event.getEntity()) != null) event.setCanceled(true);
    }

    @SubscribeEvent public static void drops(net.neoforged.neoforge.event.entity.living.LivingDropsEvent event) {
        if (owned(event.getEntity()) != null) event.setCanceled(true);
    }
    @SubscribeEvent public static void experience(net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent event) {
        if (owned(event.getEntity()) != null) event.setCanceled(true);
    }

    @SubscribeEvent public static void commands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("riftfall").then(Commands.literal("npc")
            .requires(s -> s.hasPermission(2))
            .then(Commands.literal("register").executes(ctx -> {
                var player = ctx.getSource().getPlayerOrException(); var level = player.serverLevel();
                if (!isHome(level) || !(player.pick(6, 0, false) instanceof BlockHitResult hit)) return 0;
                var resident = marker(level, hit.getBlockPos(), level.getBlockState(hit.getBlockPos()));
                if (resident == null || !unlocked(level, resident.anchor())) {
                    ctx.getSource().sendFailure(Component.literal("Look at an NPC spawn marker on an unlocked home tile."));
                    return 0;
                }
                if (!remember(level, resident)) {
                    ctx.getSource().sendFailure(Component.literal("This home already has that resident at another anchor.")); return 0;
                }
                ctx.getSource().sendSuccess(() -> Component.literal("Registered " + resident.role().displayName()
                        + (available() ? ". It will appear when the space is clear." : ". Waiting for CustomNPCs.")), false);
                return 1;
            }))
            .then(Commands.literal("status").executes(ctx -> {
                var level = ctx.getSource().getLevel();
                if (!isHome(level)) { ctx.getSource().sendFailure(Component.literal("Enter a Shattered Home first.")); return 0; }
                ctx.getSource().sendSuccess(() -> Component.literal("CustomNPCs: " + (available() ? "loaded" : "missing")), false);
                for (var resident : HomeResidentData.of(level).residents()) ctx.getSource().sendSuccess(() -> Component.literal(
                        resident.role().displayName() + " at " + resident.anchor().toShortString() + " | "
                                + (level.getEntity(resident.entityId()) != null ? "present" : "waiting for loaded, clear space")), false);
                return 1;
            }))));
    }
}
