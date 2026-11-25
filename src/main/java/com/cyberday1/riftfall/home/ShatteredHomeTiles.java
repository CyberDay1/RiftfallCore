package com.cyberday1.riftfall.home;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Places 100x100 structure tiles into a shattered home dimension on unlock. */
public final class ShatteredHomeTiles {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final int TILE_SIZE = 100;

    private ShatteredHomeTiles() {}

    /** Grid position for a tile; block origin = grid * TILE_SIZE. */
    public record GridPos(int x, int z) {
        public BlockPos blockOrigin() { return new BlockPos(x * TILE_SIZE, 0, z * TILE_SIZE); }
    }

    /** Spiral-outward tile order starting at (0,0); wilderness fills it past the crafted 25. */
    public static List<GridPos> spiralOrder(int count) {
        if (count <= 0) return new ArrayList<>();
        List<GridPos> result = new ArrayList<>(count);
        result.add(new GridPos(0, 0));
        int x = 0, z = 0, dx = 1, dz = 0, steps = 1, stepCount = 0, turnCount = 0;
        while (result.size() < count) {
            x += dx; z += dz;
            result.add(new GridPos(x, z));
            stepCount++;
            if (stepCount >= steps) {
                stepCount = 0;
                int tmp = dx; dx = -dz; dz = tmp;
                turnCount++;
                if (turnCount % 2 == 0) steps++;
            }
        }
        return result;
    }

    /** Place a single tile at the given grid position. Loads chunks first. */
    public static boolean placeTile(ServerLevel level, GridPos grid, ResourceLocation templateId) {
        long started=System.nanoTime();
        Optional<HomeTileTemplates.Prepared> opt = HomeTileTemplates.take(level, templateId);
        if (opt.isEmpty()) {
            LOGGER.error("Missing home tile template {}", templateId);
            return false;
        }
        try {
            return place(level, grid, templateId, opt.get(), started);
        } finally {
            HomeTileTemplates.release(level, opt.get());
        }
    }

    private static boolean place(ServerLevel level, GridPos grid, ResourceLocation templateId,
                                 HomeTileTemplates.Prepared prepared, long started) {
        StructureTemplate template = prepared.template();
        long templateReady=System.nanoTime();
        BlockPos origin = grid.blockOrigin();
        int minCX = origin.getX() >> 4;
        int maxCX = (origin.getX() + TILE_SIZE - 1) >> 4;
        int minCZ = origin.getZ() >> 4;
        int maxCZ = (origin.getZ() + TILE_SIZE - 1) >> 4;
        for (int cx = minCX; cx <= maxCX; cx++) {
            for (int cz = minCZ; cz <= maxCZ; cz++) {
                level.getChunk(cx, cz, ChunkStatus.FULL, true);
            }
        }
        long chunksReady=System.nanoTime();
        StructurePlaceSettings settings = new StructurePlaceSettings()
            .setMirror(Mirror.NONE)
            .setRotation(Rotation.NONE)
            .setIgnoreEntities(false);
        BlockPos placementOrigin = origin;
        var placementRandom = level.getRandom();
        boolean wild = ShatteredHomeExpansion.wildernessTemplates().contains(templateId);
        if (wild) {
            long seed = WildernessVariation.seed(ShatteredHomeExpansion.homeSeed(level.dimension().location()), grid);
            Rotation rotation = WildernessVariation.rotation(seed);
            settings.setRotation(rotation).setRandom(net.minecraft.util.RandomSource.create(seed));
            placementOrigin = WildernessVariation.anchor(origin, rotation);
            placementRandom = net.minecraft.util.RandomSource.create(seed);
            try {
                settings.addProcessor(WildernessVariation.create(template, seed));
            } catch (IllegalArgumentException invalid) {
                LOGGER.error("Invalid wilderness template {}: {}", templateId, invalid.getMessage());
                return false;
            }
        }
        java.util.List<com.cyberday1.riftfall.home.npc.HomeResidentData.Resident> residents;
        try {
            residents = com.cyberday1.riftfall.home.npc.HomeResidents.readMarkers(level, template, placementOrigin, settings);
        } catch (IllegalArgumentException invalid) {
            LOGGER.error("Invalid resident markers in {}: {}", templateId, invalid.getMessage());
            return false;
        }
        var notes = com.cyberday1.riftfall.lore.NotePlacement.read(template, templateId, placementOrigin, settings);
        BlockPos at = placementOrigin;
        var random = placementRandom;
        // A wilderness tile is dense (air == null): only crafted, unrotated tiles carry a mask.
        var outcome = TilePlacementGuard.placing(templateId, () -> {
            HomeTileTemplates.clearStrays(level, at, wild ? null : prepared.air());
            return template.placeInWorld(level, at, at, settings, random, 2);
        });
        boolean placed = outcome.result();
        if (outcome.retried() + outcome.skipped() > 0) LOGGER.warn("Home tile {}: {} block(s) placed on a retry, {} skipped",
                templateId, outcome.retried(), outcome.skipped());
        if (placed) com.cyberday1.riftfall.home.npc.HomeResidents.recordMarkers(level, residents);
        if (placed) com.cyberday1.riftfall.lore.NotePlacement.resolve(level, notes, grid);
        if (placed && wild) com.cyberday1.riftfall.lore.NotePlacement.wild(level, grid,
                WildernessVariation.seed(ShatteredHomeExpansion.homeSeed(level.dimension().location()), grid));
        if (placed) LOGGER.info("Placed home tile {} at grid ({},{}): template={}ms, chunks={}ms, placement={}ms",
                templateId,grid.x(),grid.z(),(templateReady-started)/1_000_000,
                (chunksReady-templateReady)/1_000_000,(System.nanoTime()-chunksReady)/1_000_000);
        else LOGGER.error("Failed to place home tile {} at grid ({},{})", templateId, grid.x(), grid.z());
        return placed;
    }

    /** Compute the world border diameter that encompasses all unlocked tile positions. */
    public static double borderDiameter(List<GridPos> tiles) {
        if (tiles.isEmpty()) return ShatteredHomeProgress.INITIAL_DIAMETER;
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        for (GridPos g : tiles) {
            minX = Math.min(minX, g.x());
            maxX = Math.max(maxX, g.x());
            minZ = Math.min(minZ, g.z());
            maxZ = Math.max(maxZ, g.z());
        }
        int widthBlocks = (maxX - minX + 1) * TILE_SIZE;
        int depthBlocks = (maxZ - minZ + 1) * TILE_SIZE;
        double span = Math.max(widthBlocks, depthBlocks);
        return span;
    }

    /** Centre of the tile arrangement for border centering. */
    public static BlockPos borderCenter(List<GridPos> tiles) {
        if (tiles.isEmpty()) return BlockPos.ZERO;
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        for (GridPos g : tiles) {
            minX = Math.min(minX, g.x());
            maxX = Math.max(maxX, g.x());
            minZ = Math.min(minZ, g.z());
            maxZ = Math.max(maxZ, g.z());
        }
        int cx = (minX + maxX + 1) * TILE_SIZE / 2;
        int cz = (minZ + maxZ + 1) * TILE_SIZE / 2;
        return new BlockPos(cx, 0, cz);
    }
}
