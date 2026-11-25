package com.cyberday1.riftfall.worldgen.processor;

import com.cyberday1.riftfall.init.RiftfallProcessors;
import com.cyberday1.riftfall.worldgen.decorator.RoomKind;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Carries the {@link RoomKind} of a placement from the generator, which knows it, to the decorators,
 * which cannot work it out. It changes no blocks.
 *
 * <h2>Why a processor and not a field</h2>
 * The room kind has to arrive alongside <em>one</em> placement, and the only per-placement object
 * both ends can see is the {@link StructurePlaceSettings}. Vanilla builds a fresh one per room and
 * hands the same instance to {@code finalizeProcessing}, so a processor added to it is a per-room
 * channel with no shared mutable state — which matters, because worldgen places rooms from several
 * threads at once.
 *
 * <p>
 * The obvious alternatives are both worse. Storing the kind on
 * {@link ThemeSubstitutionProcessor} does not work: {@code RiftChunkGenerator} binds one instance
 * per rift and reuses it for every room, so a field would be a data race whose symptom is rooms
 * decorated as whichever kind happened to be placed last. Building a fresh substitution processor
 * per room would fix the race but throw away the resolution cache that keeps substitution off the
 * expensive path, and would still mean rebuilding the theme's processor list on every placement.
 *
 * <p>
 * Settings carry no free-form user data, so a marker processor is the seam vanilla actually offers.
 */
public final class RoomKindProcessor extends StructureProcessor {

    /**
     * One instance per kind. They are immutable and compared by nothing, so there is no reason for a
     * placement to allocate one, and reusing them keeps the settings list cheap.
     */
    private static final Map<RoomKind, RoomKindProcessor> INSTANCES = buildInstances();

    public static final MapCodec<RoomKindProcessor> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    RoomKind.CODEC.fieldOf("kind").forGetter(RoomKindProcessor::kind)
            ).apply(instance, RoomKindProcessor::of));

    private final RoomKind kind;

    private RoomKindProcessor(RoomKind kind) {
        this.kind = kind;
    }

    /** The shared instance for one kind. */
    public static RoomKindProcessor of(RoomKind kind) {
        return INSTANCES.get(kind == null ? RoomKind.UNKNOWN : kind);
    }

    public RoomKind kind() {
        return kind;
    }

    /**
     * The kind stamped on this placement, or {@link RoomKind#UNKNOWN} if nobody stamped one.
     *
     * <p>
     * Unknown rather than a throw, because plenty of legitimate placements never go near the room
     * placer: the corridor blender's wall copies, POI props, and the gallery all reach the theme
     * processor without being a room of the dungeon.
     */
    public static RoomKind kindOf(@Nullable StructurePlaceSettings settings) {
        if (settings == null) {
            return RoomKind.UNKNOWN;
        }
        List<StructureProcessor> processors = settings.getProcessors();
        // Indexed rather than enhanced-for: this runs once per placement, but the list is vanilla's
        // own mutable ArrayList and an iterator allocation per room is free to avoid.
        for (int i = 0; i < processors.size(); i++) {
            if (processors.get(i) instanceof RoomKindProcessor marker) {
                return marker.kind();
            }
        }
        return RoomKind.UNKNOWN;
    }

    @Override
    protected StructureProcessorType<?> getType() {
        return RiftfallProcessors.ROOM_KIND.get();
    }

    /**
     * Returns its input untouched. Deprecated to steer callers, not to signal removal; see
     * {@link ThemeSubstitutionProcessor}.
     */
    @Nullable
    @SuppressWarnings("deprecation")
    @Override
    public StructureTemplate.StructureBlockInfo processBlock(LevelReader world, BlockPos piecePos,
            BlockPos structurePos, StructureTemplate.StructureBlockInfo rawBlockInfo,
            StructureTemplate.StructureBlockInfo blockInfo, StructurePlaceSettings settings) {
        return blockInfo;
    }

    private static Map<RoomKind, RoomKindProcessor> buildInstances() {
        EnumMap<RoomKind, RoomKindProcessor> map = new EnumMap<>(RoomKind.class);
        for (RoomKind kind : RoomKind.ALL) {
            map.put(kind, new RoomKindProcessor(kind));
        }
        return java.util.Collections.unmodifiableMap(map);
    }
}
