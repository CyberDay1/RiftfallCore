package com.cyberday1.riftfall.home.npc.depth;

import com.cyberday1.riftfall.RiftfallCore;
import com.cyberday1.riftfall.block.marker.NpcRole;
import com.cyberday1.riftfall.home.HomeAccess;
import com.cyberday1.riftfall.home.ShatteredHomeExpansion;
import com.cyberday1.riftfall.home.ShatteredHomeLayout;
import com.cyberday1.riftfall.home.ShatteredHomeLevels;
import com.cyberday1.riftfall.home.ShatteredHomeProgress;
import com.cyberday1.riftfall.home.ShatteredHomeTiles;
import com.cyberday1.riftfall.home.npc.HomeResidentData;
import com.cyberday1.riftfall.home.npc.HomeResidents;
import com.cyberday1.riftfall.home.npc.ResidentRank;
import com.cyberday1.riftfall.worldgen.async.TemplateBlockIterator;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Function;

/**
 * Station upgrades: a small overlay template per role per level, {@code structure/home/station/<role>_<level>.nbt}
 * (level 1 Journeyman, 2 Expert, 3 Master; Master may be {@code <role>_3_<path>}; each paid once in gem-fused slate
 * and shards on the Path tab), centred on the resident's anchor
 * with its bottom layer one block below it. Applied in place like the home repairs: four chunk portions per home
 * level per pass, never force-loading, each portion recorded once. A cell is written only over air or the block the
 * tile template had there (ST1); overlays carry only plain vanilla blocks without block entities.
 */
@EventBusSubscriber(modid = RiftfallCore.MODID)
public final class StationOverlays {
    public static final int CHUNKS_PER_PASS = 4, PASS_TICKS = 10;
    public record Cell(BlockPos local, BlockState state) {}
    public record Overlay(ResourceLocation id, List<Cell> cells, int sizeX, int sizeZ) {}
    public record Applied(int written, int skipped) {}

    private static final Map<ResourceLocation, Optional<Overlay>> CACHE = new HashMap<>();

    private StationOverlays() {}

    /** ST1: only air, or the block the original tile template had at that position. */
    public static boolean mayWrite(BlockState current, @Nullable BlockState original) {
        return current.isAir() || original != null && current.equals(original);
    }

    /** What an overlay may place: a plain vanilla block that holds nothing and that no other mod drives. */
    public static boolean placeable(BlockState state, boolean hasData) {
        return !state.isAir() && !hasData && !state.hasBlockEntity()
                && "minecraft".equals(BuiltInRegistries.BLOCK.getKey(state.getBlock()).getNamespace());
    }

    /** The station level a rank shows: Apprentice 0 to Master 3. */
    public static int level(ResidentRank rank) { return rank.ordinal(); }

    public static ResourceLocation id(NpcRole role, int level, @Nullable String path) {
        return RiftfallCore.id("home/station/" + role.id() + "_" + level + (path == null ? "" : "_" + path));
    }

    public static BlockPos origin(BlockPos anchor, int sizeX, int sizeZ) {
        return anchor.offset(-sizeX / 2, -1, -sizeZ / 2);
    }

    /** Loads and vets an overlay; empty when there is no file. Cells that break the placement rule are dropped. */
    public static Optional<Overlay> overlay(ServerLevel level, ResourceLocation id) {
        synchronized (CACHE) {
            return CACHE.computeIfAbsent(id, key -> level.getStructureManager().get(key).map(template -> {
                var cells = new ArrayList<Cell>();
                for (var info : TemplateBlockIterator.singlePaletteBlocks(template)) {
                    if (info.state().isAir() || info.state().is(Blocks.STRUCTURE_VOID)) continue;
                    if (placeable(info.state(), info.nbt() != null)) cells.add(new Cell(info.pos().immutable(), info.state()));
                    else LogUtils.getLogger().error("Station overlay {} refuses {} at {}", key, info.state(), info.pos());
                }
                return new Overlay(key, List.copyOf(cells), template.getSize().getX(), template.getSize().getZ());
            }));
        }
    }

    /** For tests and the probe; the reload of structure files clears nothing else. */
    public static void forget() { synchronized (CACHE) { CACHE.clear(); } }

    /**
     * Writes the cells that fall in {@code chunk}. {@code original} answers the tile template's block at a world
     * position, or null where the template had none. Cells over player work are skipped and logged.
     */
    public static Applied apply(ServerLevel level, BlockPos origin, List<Cell> cells, ChunkPos chunk,
                                Function<BlockPos, BlockState> original, String what) {
        int written = 0, skipped = 0;
        for (var cell : cells) {
            var pos = origin.offset(cell.local());
            if (!new ChunkPos(pos).equals(chunk)) continue;
            var current = level.getBlockState(pos);
            if (current.equals(cell.state())) continue;
            if (!mayWrite(current, original.apply(pos))) { skipped++; continue; }
            level.setBlock(pos, cell.state(), Block.UPDATE_CLIENTS);
            written++;
        }
        if (skipped > 0) LogUtils.getLogger().info("Station overlay {}: left {} player-changed block(s) in chunk {}", what, skipped, chunk);
        return new Applied(written, skipped);
    }

    /** Puts back the original block where the overlay's block still stands; anything changed since is left. */
    public static int revert(ServerLevel level, BlockPos origin, List<Cell> cells, ChunkPos chunk,
                             Function<BlockPos, BlockState> original) {
        int reverted = 0;
        for (var cell : cells) {
            var pos = origin.offset(cell.local());
            if (!new ChunkPos(pos).equals(chunk) || !level.getBlockState(pos).equals(cell.state())) continue;
            var back = original.apply(pos);
            level.setBlock(pos, back == null ? Blocks.AIR.defaultBlockState() : back, Block.UPDATE_CLIENTS);
            reverted++;
        }
        return reverted;
    }

    public static Set<ChunkPos> chunks(BlockPos origin, List<Cell> cells) {
        var out = new LinkedHashSet<ChunkPos>();
        for (var cell : cells) out.add(new ChunkPos(origin.offset(cell.local())));
        return out;
    }

    /** The authored tile's blocks inside a box, by world position; empty for layouts or tiles it cannot read. */
    static Map<BlockPos, BlockState> originals(ServerLevel level, BlockPos anchor, BlockPos origin, Overlay overlay) {
        var progress = ShatteredHomeProgress.of(level);
        if (!ShatteredHomeLayout.supports(progress.layoutVersion())) return Map.of();
        var grid = new ShatteredHomeTiles.GridPos(Math.floorDiv(anchor.getX(), ShatteredHomeTiles.TILE_SIZE),
                Math.floorDiv(anchor.getZ(), ShatteredHomeTiles.TILE_SIZE));
        var tile = ShatteredHomeLayout.at(progress.layoutVersion(), grid).orElse(null);
        if (tile == null || ShatteredHomeExpansion.wildernessTemplates().contains(tile.template())) return Map.of();
        StructureTemplate template = level.getStructureManager().get(tile.template()).orElse(null);
        if (template == null) return Map.of();
        var wanted = new HashSet<BlockPos>();
        for (var cell : overlay.cells()) wanted.add(origin.offset(cell.local()));
        var out = new HashMap<BlockPos, BlockState>();
        var tileOrigin = grid.blockOrigin();
        for (var info : TemplateBlockIterator.singlePaletteBlocks(template)) {
            var world = tileOrigin.offset(info.pos());
            if (wanted.contains(world)) out.put(world, info.state());
        }
        return out;
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        var server = event.getServer();
        if (server.getTickCount() % PASS_TICKS != 0) return;
        for (var level : server.getAllLevels()) {
            if (!ShatteredHomeLevels.isShatteredHome(level.dimension())) continue;
            var owner = HomeAccess.owner(level.dimension().location());
            var home = owner == null ? null : HomeResidentDepth.of(server).peek(owner).orElse(null);
            if (home == null) continue;
            try { pass(level, home); }
            catch (RuntimeException failed) { LogUtils.getLogger().error("Station overlay pass failed in {}", level.dimension().location(), failed); }
        }
    }

    static void pass(ServerLevel level, HomeResidentDepth.Home home) {
        var data = HomeResidentData.of(level);
        int budget = CHUNKS_PER_PASS;
        for (var resident : data.residents()) {
            if (budget <= 0) return;
            var r = home.roles().get(resident.role());
            if (r == null || !HomeResidents.unlocked(level, resident.anchor())) continue;
            String path = r.specialisation().orElse(null);
            var applied = data.stationPath(resident.role());
            if (applied != null && !applied.equals(path)) {
                budget = swap(level, data, resident, applied, budget);
                if (data.stationPath(resident.role()) != null && !Objects.equals(data.stationPath(resident.role()), path)) continue;
            }
            for (int lvl = 1; lvl <= Math.min(level(r.rank()), r.stationFunded()) && budget > 0; lvl++) {
                var overlay = resolve(level, resident.role(), lvl, lvl == 3 ? path : null);
                if (overlay.isEmpty()) continue;
                var o = overlay.get();
                var origin = origin(resident.anchor(), o.sizeX(), o.sizeZ());
                Map<BlockPos, BlockState> originals = null;
                boolean complete = true;
                for (var chunk : chunks(origin, o.cells())) {
                    String key = o.id() + "@" + origin.asLong() + "@" + chunk.toLong();
                    if (data.stationDone(key)) continue;
                    if (budget <= 0 || level.getChunkSource().getChunkNow(chunk.x, chunk.z) == null) { complete = false; continue; }
                    if (originals == null) originals = originals(level, resident.anchor(), origin, o);
                    apply(level, origin, o.cells(), chunk, originals::get, o.id().toString());
                    data.markStation(key);
                    budget--;
                }
                if (complete) data.stationApplied(resident.role(), lvl, path);
            }
        }
    }

    /** The Master overlay for a path, falling back to the shared one. */
    static Optional<Overlay> resolve(ServerLevel level, NpcRole role, int lvl, @Nullable String path) {
        if (path != null) {
            var specific = overlay(level, id(role, lvl, path));
            if (specific.isPresent()) return specific;
        }
        return overlay(level, id(role, lvl, null));
    }

    /** The highest level this role has an overlay for (0 if none), for the Path tab's price. */
    public static int top(ServerLevel level, NpcRole role, @Nullable String path) {
        for (int lvl = 3; lvl >= 1; lvl--) if (resolve(level, role, lvl, lvl == 3 ? path : null).isPresent()) return lvl;
        return 0;
    }

    /** A respec: take the old path's Master overlay back down, then let the new one go up. */
    private static int swap(ServerLevel level, HomeResidentData data, HomeResidentData.Resident resident, String old, int budget) {
        var overlay = overlay(level, id(resident.role(), 3, old));
        if (overlay.isEmpty()) { data.stationReverted(resident.role()); return budget; }
        var o = overlay.get();
        var origin = origin(resident.anchor(), o.sizeX(), o.sizeZ());
        var originals = originals(level, resident.anchor(), origin, o);
        boolean complete = true;
        for (var chunk : chunks(origin, o.cells())) {
            String key = o.id() + "@" + origin.asLong() + "@" + chunk.toLong();
            if (!data.stationDone(key)) continue;
            if (budget <= 0 || level.getChunkSource().getChunkNow(chunk.x, chunk.z) == null) { complete = false; continue; }
            revert(level, origin, o.cells(), chunk, originals::get);
            data.forgetStation(key);
            budget--;
        }
        if (complete) data.stationReverted(resident.role());
        return budget;
    }
}
