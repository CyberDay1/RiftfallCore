package com.cyberday1.riftfall.worldgen.space;

import com.cyberday1.riftfall.RiftfallCore;
import com.cyberday1.riftfall.block.marker.PoiAnchorBlock;
import com.cyberday1.riftfall.block.marker.PoiAnchorKind;
import com.cyberday1.riftfall.block.marker.PoiAnchorSize;
import com.cyberday1.riftfall.block.marker.PoiAnchors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.JigsawBlock;
import net.minecraft.world.level.block.entity.JigsawBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Reads jigsaw blocks out of a 1.21.1 {@link StructureTemplate} and turns the layout-relevant ones
 * into {@link RiftSpace} orientations.
 *
 * <p>
 * The 1.21.1 problem and how it is dodged: {@code StructureTemplate#palettes} is private with no
 * accessor, so there is no direct way to walk the block list. But
 * {@link StructureTemplate#filterBlocks(BlockPos, StructurePlaceSettings, net.minecraft.world.level.block.Block, boolean)}
 * <em>is</em> public, and with the trailing flag false it returns raw template-local positions. So
 * asking it for {@code Blocks.JIGSAW} gets the whole jigsaw list with no mixin and no access
 * transformer. Multi-palette templates share one position list &mdash; vanilla's own save path
 * indexes every palette by the same block index &mdash; so which palette that call happens to pick
 * cannot change the sockets. A fixed seed is used anyway, so the parse is deterministic.
 */
public final class TemplateJigsaws {

    /**
     * The substring in a target pool's path that marks a jigsaw as a layout socket rather than a
     * prop anchor. Our ring pools are {@code riftfall:rift/ring_{chaos,stable,unstable,portal}}.
     */
    public static final String RING_POOL_MARKER = "rift/ring";

    /** Every prop anchor names itself this, so a piece's own socket has something to match. */
    public static final ResourceLocation POI_ANCHOR_NAME = ResourceLocation.withDefaultNamespace("empty");

    /** ...and targets this, which is the name every POI piece's socket carries. */
    public static final ResourceLocation POI_ANCHOR_TARGET = RiftfallCore.id("poi");

    /** Blocks per section edge, as a shift. */
    private static final int SECTION_SHIFT = 4;

    private TemplateJigsaws() {
    }

    /**
     * Every jigsaw block in the template, in palette order, followed by one for each
     * {@link PoiAnchorBlock} in it.
     *
     * <p>
     * The anchor blocks are jigsaws written short: five of a prop anchor's six fields are the same
     * across all 841 the builders authored, and the sixth is a pool path derived from the block's
     * own category and size. Expanding them here rather than at some later stage is what makes a
     * hand-placed anchor and an authored jigsaw indistinguishable to everything downstream.
     */
    public static List<JigsawInfo> parse(StructureTemplate template) {
        StructurePlaceSettings settings = new StructurePlaceSettings().setRandom(RandomSource.create(0L));
        List<StructureTemplate.StructureBlockInfo> raw =
                template.filterBlocks(BlockPos.ZERO, settings, Blocks.JIGSAW, false);

        List<JigsawInfo> jigsaws = new ArrayList<>(raw.size());
        for (StructureTemplate.StructureBlockInfo info : raw) {
            JigsawInfo parsed = parseOne(info);
            if (parsed != null) {
                jigsaws.add(parsed);
            }
        }
        appendAnchorBlocks(template, settings, jigsaws);
        return jigsaws;
    }

    /**
     * Expands the template's POI anchor blocks into jigsaws.
     *
     * <p>
     * Four more {@code filterBlocks} walks, which is affordable because the palette memoises the
     * answer per block type: the walk happens once per template per block and every later call is a
     * map hit.
     */
    private static void appendAnchorBlocks(StructureTemplate template, StructurePlaceSettings settings,
                                           List<JigsawInfo> into) {
        for (PoiAnchorKind kind : PoiAnchorKind.ALL) {
            PoiAnchorBlock block = PoiAnchors.block(kind);
            for (StructureTemplate.StructureBlockInfo info
                    : template.filterBlocks(BlockPos.ZERO, settings, block, false)) {
                into.add(anchorOf(kind, block, info));
            }
        }
    }

    private static JigsawInfo anchorOf(PoiAnchorKind kind, PoiAnchorBlock block,
                                       StructureTemplate.StructureBlockInfo info) {
        PoiAnchorSize size = info.state().getValue(PoiAnchorBlock.SIZE);
        BlockState jigsaw = Blocks.JIGSAW.defaultBlockState()
                .setValue(JigsawBlock.ORIENTATION, kind.orientation(block.facing(info.state())));
        return new JigsawInfo(info.pos(), jigsaw, POI_ANCHOR_NAME, POI_ANCHOR_TARGET, kind.pool(size),
                kind.finalState().toString(), kind.joint(), 0, 0);
    }

    /**
     * Decodes one jigsaw block entity tag. Returns null when the block carries no NBT at all, which
     * a hand-edited template can produce; a jigsaw with no pool cannot be a socket or an anchor, so
     * dropping it is the correct outcome rather than an exception mid-worldgen.
     */
    @Nullable
    public static JigsawInfo parseOne(StructureTemplate.StructureBlockInfo info) {
        CompoundTag nbt = info.nbt();
        if (nbt == null) {
            return null;
        }
        ResourceLocation name = readId(nbt, JigsawBlockEntity.NAME);
        ResourceLocation target = readId(nbt, JigsawBlockEntity.TARGET);
        ResourceLocation pool = readId(nbt, JigsawBlockEntity.POOL);
        if (name == null || target == null || pool == null) {
            return null;
        }
        String finalState = nbt.contains(JigsawBlockEntity.FINAL_STATE, Tag.TAG_STRING)
                ? nbt.getString(JigsawBlockEntity.FINAL_STATE)
                : "minecraft:air";
        JigsawBlockEntity.JointType joint = JigsawBlockEntity.JointType
                .byName(nbt.getString(JigsawBlockEntity.JOINT))
                .orElse(JigsawBlockEntity.JointType.ROLLABLE);

        return new JigsawInfo(info.pos(), info.state(), name, target, pool, finalState, joint,
                nbt.getInt(JigsawBlockEntity.PLACEMENT_PRIORITY),
                nbt.getInt(JigsawBlockEntity.SELECTION_PRIORITY));
    }

    /**
     * The subset of {@code jigsaws} that are layout sockets, judged solely by the target pool's
     * path containing {@value #RING_POOL_MARKER}. Namespace is checked too so a third-party pool
     * called {@code somemod:rift/ring_x} cannot inject a socket into our rooms.
     */
    public static List<JigsawInfo> ringJigsaws(List<JigsawInfo> jigsaws, String namespace) {
        List<JigsawInfo> ring = new ArrayList<>();
        for (JigsawInfo jigsaw : jigsaws) {
            if (jigsaw.pool().getNamespace().equals(namespace)
                    && jigsaw.pool().getPath().contains(RING_POOL_MARKER)) {
                ring.add(jigsaw);
            }
        }
        return ring;
    }

    /**
     * Sockets in the room's own, untransformed frame.
     *
     * <p>
     * Two jigsaws in the same section pointing the same way <em>at the same height</em> collapse to
     * one socket &mdash; a builder who stacks a pair of markers on one wall meant one door, and a
     * duplicate would otherwise bias the layout's random placement pick toward that face. Two at
     * different heights are two doors, and the room pass and the doorway pass both have to be told
     * so; that is why the elevation is part of the socket rather than dropped here.
     */
    public static List<RoomSocket> socketsOf(List<JigsawInfo> ringJigsaws) {
        Set<RoomSocket> sockets = new LinkedHashSet<>();
        for (JigsawInfo jigsaw : ringJigsaws) {
            sockets.add(new RoomSocket(jigsaw.section(), jigsaw.front(), jigsaw.elevation()));
        }
        return List.copyOf(sockets);
    }

    /** Block extent to section extent, rounding up: a 40-block room still needs 3 sections. */
    public static Vec3i sizeInSections(Vec3i blockSize) {
        return new Vec3i(
                ceilDivSection(blockSize.getX()),
                ceilDivSection(blockSize.getY()),
                ceilDivSection(blockSize.getZ()));
    }

    /**
     * One authored template to its eight placeable orientations, sockets permuted to match.
     *
     * <p>
     * Symmetric rooms produce duplicates; they are kept, matching the reference design, because
     * dropping them would quietly change the weighting of a symmetric room against an asymmetric
     * one in the same pool. Callers that want distinct geometry can filter on
     * {@code (size, sockets)} themselves.
     */
    public static List<RiftSpace> orientations(@Nullable ResourceLocation templateId, Vec3i blockSize,
                                               List<RoomSocket> sockets) {
        Vec3i sectionSize = sizeInSections(blockSize);
        List<RiftSpace> spaces = new ArrayList<>(TripleMirror.PERMUTATIONS.size());
        for (TripleMirror mirror : TripleMirror.PERMUTATIONS) {
            List<RoomSocket> transformed = new ArrayList<>(sockets.size());
            for (RoomSocket socket : sockets) {
                transformed.add(socket.transformed(mirror, sectionSize));
            }
            spaces.add(new RiftSpace(mirror.transformSize(sectionSize), Vec3i.ZERO, transformed, templateId, mirror));
        }
        return spaces;
    }

    /** The whole chain: template plus id to eight placeable orientations. */
    public static List<RiftSpace> orientations(StructureTemplate template, ResourceLocation templateId,
                                               String namespace) {
        List<RoomSocket> sockets = socketsOf(ringJigsaws(parse(template), namespace));
        return orientations(templateId, template.getSize(), sockets);
    }

    private static int ceilDivSection(int blocks) {
        return (blocks + (1 << SECTION_SHIFT) - 1) >> SECTION_SHIFT;
    }

    @Nullable
    private static ResourceLocation readId(CompoundTag nbt, String key) {
        if (!nbt.contains(key, Tag.TAG_STRING)) {
            return null;
        }
        return ResourceLocation.tryParse(nbt.getString(key));
    }
}
