package com.cyberday1.riftfall.worldgen.async;

import com.cyberday1.riftfall.block.marker.MarkerKind;
import com.cyberday1.riftfall.block.marker.PoiAnchorBlock;
import com.cyberday1.riftfall.block.marker.RiftfallMarkers;
import com.cyberday1.riftfall.worldgen.async.processors.AsyncBarrelConversion;
import com.cyberday1.riftfall.worldgen.async.processors.AsyncJigsawReplacement;
import com.cyberday1.riftfall.worldgen.async.processors.AsyncPoiAnchor;
import com.cyberday1.riftfall.worldgen.async.processors.AsyncThemeSubstitution;
import com.cyberday1.riftfall.worldgen.async.processors.AsyncTrialSpawner;
import com.cyberday1.riftfall.worldgen.layout.RiftPlan;
import com.cyberday1.riftfall.worldgen.processor.DecoratorDriver;
import com.cyberday1.riftfall.worldgen.space.TemplatePlacement;
import com.cyberday1.riftfall.worldgen.space.TripleMirror;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import net.minecraft.world.phys.Vec3;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;

/** Pure off-thread processing. Null cells mean no write; authored air remains an explicit write. */
public final class RoomRasterizer {
    private static final Field ENTITY_INFO_FIELD;
    static {
        try {
            ENTITY_INFO_FIELD = StructureTemplate.class.getDeclaredField("entityInfoList");
            ENTITY_INFO_FIELD.setAccessible(true);
        } catch (NoSuchFieldException e) { throw new ExceptionInInitializerError(e); }
    }

    public BakedRoom rasterize(RiftPlan.PlacedRoom room, StructureTemplate template, BakedRoomContext ctx) {
        return rasterizeProp(room.mirror(), room.anchor(), room.bounds(), template, ctx);
    }

    public BakedRoom rasterizeProp(TripleMirror mirror, BlockPos anchor, BoundingBox bounds,
                                   StructureTemplate template, BakedRoomContext ctx) {
        return slice(bakeWhole(mirror, anchor, bounds, template, ctx), template, mirror, anchor,
                ctx.clip(), ctx.ignoreEntities());
    }

    /** A room processed and decorated once. Nothing in it depends on a clip. */
    public record WholeRoom(List<StructureBlockInfo> writes, Map<Long, List<StructureBlockInfo>> columns,
                            List<BlockPos> markers) {}

    public WholeRoom bakeWhole(TripleMirror mirror, BlockPos anchor, BoundingBox bounds,
                               StructureTemplate template, BakedRoomContext ctx) {
        Map<SectionPos, BlockState[]> arrays = new LinkedHashMap<>();
        List<StructureBlockInfo> authored = new ArrayList<>();
        List<BlockPos> markers = new ArrayList<>();
        // Process and decorate the whole template, then clip writes to the column, as vanilla does.
        TemplateBlockIterator.forEach(template, mirror, anchor, bounds, true, false, entry -> {
            if (entry.state().is(Blocks.STRUCTURE_BLOCK)) return;
            StructureBlockInfo info = AsyncJigsawReplacement.replace(new StructureBlockInfo(
                    entry.worldPos(), entry.state(), entry.nbt() == null ? null : entry.nbt().copy()));
            if (info == null) return;
            boolean chest = info.state().is(RiftfallMarkers.block(MarkerKind.RIFT_CHEST));
            if (chest) markers.add(info.pos());
            if (chest || info.state().is(Blocks.TRIAL_SPAWNER) || info.state().getBlock() instanceof PoiAnchorBlock)
                info = new StructureBlockInfo(info.pos(), info.state(), null);
            authored.add(info);
            arrays.computeIfAbsent(SectionPos.of(info.pos()), ignored -> new BlockState[4096])[index(info.pos())] = info.state();
        });
        SectionProcessor[] chain = {new AsyncPoiAnchor(),
                new AsyncTrialSpawner(), new AsyncThemeSubstitution(), new AsyncBarrelConversion()};
        for (var entry : arrays.entrySet()) {
            if (Thread.currentThread().isInterrupted()) throw new CancellationException("Room bake interrupted");
            for (var processor : chain) processor.process(entry.getValue(), entry.getKey(), ctx);
        }
        List<StructureBlockInfo> processed = new ArrayList<>(authored.size());
        for (var info : authored) {
            BlockState state = arrays.get(SectionPos.of(info.pos()))[index(info.pos())];
            if (state != null) processed.add(new StructureBlockInfo(info.pos(), state, info.nbt()));
        }
        processed = DecoratorDriver.run(ctx.resolvedTheme().decorators(), processed, ctx.worldSeed(), ctx.roomKind(),
                ctx.facts());
        List<StructureBlockInfo> writes = new ArrayList<>(processed.size());
        Map<Long, List<StructureBlockInfo>> columns = new HashMap<>();
        for (var info : processed) {
            BlockState state = info.state().mirror(TemplatePlacement.mirrorOf(mirror)).rotate(TemplatePlacement.rotationOf(mirror));
            CompoundTag tag = info.nbt();
            if (tag != null) {
                tag.putInt("x", info.pos().getX());
                tag.putInt("y", info.pos().getY());
                tag.putInt("z", info.pos().getZ());
            }
            StructureBlockInfo write = new StructureBlockInfo(info.pos(), state, tag);
            writes.add(write);
            columns.computeIfAbsent(ChunkPos.asLong(info.pos().getX() >> 4, info.pos().getZ() >> 4),
                    ignored -> new ArrayList<>()).add(write);
        }
        return new WholeRoom(writes, columns, markers);
    }

    /** The part of a whole room inside one clip, in the order a clipped bake would have written it. */
    public BakedRoom slice(WholeRoom whole, StructureTemplate template, TripleMirror mirror, BlockPos anchor,
                           BoundingBox clip, boolean ignoreEntities) {
        boolean oneColumn = clip.minX() >> 4 == clip.maxX() >> 4 && clip.minZ() >> 4 == clip.maxZ() >> 4;
        List<StructureBlockInfo> source = oneColumn
                ? whole.columns().getOrDefault(ChunkPos.asLong(clip.minX() >> 4, clip.minZ() >> 4), List.of())
                : whole.writes();
        List<StructureBlockInfo> writes = new ArrayList<>(oneColumn ? source.size() : 16);
        for (var info : source) if (clip.isInside(info.pos())) writes.add(info);
        List<BlockPos> markers = new ArrayList<>();
        for (var pos : whole.markers()) if (clip.isInside(pos)) markers.add(pos);
        return new BakedRoom(List.copyOf(writes),
                ignoreEntities ? List.of() : entities(template, mirror, anchor, clip),
                List.copyOf(markers));
    }

    static int index(BlockPos pos) { return (pos.getX() & 15) | ((pos.getZ() & 15) << 4) | ((pos.getY() & 15) << 8); }

    @SuppressWarnings("unchecked")
    private static List<BakedRoom.BakedEntity> entities(StructureTemplate template, TripleMirror mirror,
                                                      BlockPos anchor, BoundingBox clip) {
        try {
            var result = new ArrayList<BakedRoom.BakedEntity>();
            var settings = TemplatePlacement.settings(mirror);
            for (var info : (List<StructureTemplate.StructureEntityInfo>) ENTITY_INFO_FIELD.get(template)) {
                BlockPos blockPos = StructureTemplate.calculateRelativePosition(settings, info.blockPos).offset(anchor);
                if (!clip.isInside(blockPos)) continue;
                Vec3 pos = StructureTemplate.transformedVec3d(settings, info.pos).add(Vec3.atLowerCornerOf(anchor));
                result.add(new BakedRoom.BakedEntity(pos, info.nbt.copy(), mirror));
            }
            return List.copyOf(result);
        } catch (IllegalAccessException e) { throw new IllegalStateException(e); }
    }
}
