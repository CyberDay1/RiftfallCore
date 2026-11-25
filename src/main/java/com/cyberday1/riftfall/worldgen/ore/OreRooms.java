package com.cyberday1.riftfall.worldgen.ore;

import com.cyberday1.riftfall.worldgen.decorator.OreChunkDecorator;
import com.cyberday1.riftfall.worldgen.processor.DecoratorDriver;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;

/**
 * Which rooms are ore rooms: the caverns under {@link #CAVERN_PREFIX}, plus the rooms named in
 * {@link #EXISTING}. Nothing is inferred at runtime. The layout tops every version-2 rift up to
 * {@link #quota} of them.
 */
public final class OreRooms {

    /** The dedicated caverns' pools; the room library holds them for the ore quota only. */
    public static final String CAVERN_PREFIX = "rift/room/ore/";

    /**
     * Ore rooms that sit in a main pool, so growth can draw them too: hub-hub-bub-4-mine, the mine-dressed
     * hub-hub-bub-4-mal, which takes the vetoed original's pool slot. An ore room in every layout version.
     */
    public static final Set<ResourceLocation> EXISTING = Set.of(
            ResourceLocation.fromNamespaceAndPath("riftfall", "rift/room/unstable/323/hub-hub-bub-4-mine"));

    /** Ore rooms at tier 1, one more every {@link #QUOTA_STEP} tiers, at most {@link #QUOTA_MAX}. */
    public static final int QUOTA_BASE = 3, QUOTA_STEP = 5, QUOTA_MAX = 6;

    private OreRooms() {
    }

    public static boolean isOreRoom(@Nullable ResourceLocation template) {
        return template != null && (template.getPath().startsWith(CAVERN_PREFIX) || EXISTING.contains(template));
    }

    /** Ore rooms a version-2 rift of this tier holds at least. */
    public static int quota(int tier) {
        return Math.min(QUOTA_MAX, QUOTA_BASE + (Math.max(1, tier) - 1) / QUOTA_STEP);
    }

    /** A template's clearings at the configured radius and one wider; the census tests read it. */
    public record Openness(int clearings, int wide) {
    }

    /** Clearings of a template's blocks, before theme and props: a jigsaw becomes its final state. */
    public static Openness measure(List<StructureTemplate.StructureBlockInfo> blocks, OreChunkDecorator config) {
        BlockState[] states = new BlockState[blocks.size()];
        BlockPos[] positions = new BlockPos[blocks.size()];
        for (int i = 0; i < states.length; i++) {
            StructureTemplate.StructureBlockInfo info = blocks.get(i);
            positions[i] = info.pos();
            BlockState s = info.state();
            if (s.is(Blocks.JIGSAW)) {
                boolean air = info.nbt() != null && info.nbt().getString("final_state").equals("minecraft:air");
                s = air ? Blocks.AIR.defaultBlockState() : Blocks.STONE.defaultBlockState();
            }
            states[i] = s;
        }
        int[] around = DecoratorDriver.neighbours(positions);
        int c = config.clearings(states, around, new boolean[states.length]).size();
        int wide = c == 0 ? 0 : config.withRadius(config.clearingRadius() + 1)
                .clearings(states, around, new boolean[states.length]).size();
        return new Openness(c, wide);
    }
}
