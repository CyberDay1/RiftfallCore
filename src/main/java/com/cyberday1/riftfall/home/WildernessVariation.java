package com.cyberday1.riftfall.home;

import com.cyberday1.riftfall.init.RiftfallProcessors;
import com.cyberday1.riftfall.worldgen.async.TemplateBlockIterator;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.*;

import java.util.*;

/** Per-unlock scenery variation; authored templates and already placed terrain stay untouched. */
public final class WildernessVariation extends StructureProcessor {
    private static final int WIDTH = ShatteredHomeTiles.TILE_SIZE;
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    // Runtime-only, like the barrel processor: datapacks cannot supply the home/coordinate seed.
    public static final MapCodec<WildernessVariation> CODEC = MapCodec.unit(() -> new WildernessVariation(null, 0));
    // Weak template identity keys allow a datapack reload to release the old source grids.
    private static final Map<StructureTemplate, Source> SOURCES = new WeakHashMap<>();
    private static final Set<String> GROUND = Set.of(
        "minecraft:grass_block", "minecraft:dirt", "minecraft:coarse_dirt", "minecraft:podzol",
        "minecraft:gravel", "minecraft:terracotta", "biomeswevegone:cracked_sand",
        "biomeswevegone:cracked_red_sand", "biomeswevegone:pale_mud", "twigs:silt");
    private static final Set<String> CLUTTER = Set.of(
        "minecraft:short_grass", "minecraft:fern", "minecraft:brown_mushroom", "minecraft:dead_bush",
        "twigs:pebble", "twigs:twig", "rocks:pinecone", "rocks:andesite_rock", "rocks:granite_rock",
        "biomeswevegone:prairie_grass", "biomeswevegone:beach_grass", "biomeswevegone:shrub",
        "biomeswevegone:cattail_sprout", "biomeswevegone:clover_patch", "biomeswevegone:alpine_bellflower",
        "biomeswevegone:pink_allium_flower_bush", "biomeswevegone:white_allium_flower_bush");

    private final Source source;
    private final int[] heights = new int[WIDTH * WIDTH];
    private final BlockState[] clutter = new BlockState[WIDTH * WIDTH];

    public static WildernessVariation create(StructureTemplate template, long seed) {
        Source source;
        synchronized (SOURCES) { source = SOURCES.computeIfAbsent(template, Source::new); }
        return new WildernessVariation(source, seed);
    }

    /** Independent salt keeps rotations/scenery from changing the established six-template sequence. */
    public static long seed(long homeSeed, ShatteredHomeTiles.GridPos grid) {
        long coordinate = ((long) grid.x() << 32) | Integer.toUnsignedLong(grid.z());
        return mix(homeSeed ^ coordinate ^ 0x57494C445631L);
    }

    public static Rotation rotation(long seed) { return Rotation.values()[(int) (mix(seed) & 3)]; }

    /** Vanilla rotates about zero; this translation retains the exact 100x100 world footprint. */
    public static BlockPos anchor(BlockPos origin, Rotation rotation) {
        return switch (rotation) {
            case NONE -> origin;
            case CLOCKWISE_90 -> origin.offset(WIDTH - 1, 0, 0);
            case CLOCKWISE_180 -> origin.offset(WIDTH - 1, 0, WIDTH - 1);
            case COUNTERCLOCKWISE_90 -> origin.offset(0, 0, WIDTH - 1);
        };
    }

    WildernessVariation(Source source, long seed) {
        this.source = source;
        if (source == null) return;
        for (int z = 0; z < WIDTH; z++) for (int x = 0; x < WIDTH; x++) {
            int col = column(x, z);
            if (!source.safe[col]) continue;
            int oldHeight = source.heights[col];
            // One-block folds, eased away from the unchanged four-block seam. Never excavate below Y10.
            double fade = Math.min(1, (Math.min(Math.min(x, z), Math.min(99-x, 99-z)) - 3) / 10.0);
            int fold = Math.max(-1, Math.min(1, (int) Math.round(noise(seed, x, z, 19) * 1.6 * fade)));
            heights[col] = Math.max(10, oldHeight + fold);
            var ground = source.at(x, oldHeight, z);
            var choices = source.choices.get(ground);
            clutter[col] = AIR;
            if (choices == null || choices.isEmpty()) continue;
            double density = choices.size() / (double) source.groundCounts.get(ground);
            double patches = 1 + .65 * noise(seed ^ 0x5041544348L, x, z, 11);
            if (unit(seed ^ 0x44454E53495459L, x, z) < Math.min(.35, density * patches)) {
                int index = (int) (unit(seed ^ 0x44455441494CL, x, z) * choices.size());
                clutter[col] = choices.get(index);
            }
        }
    }

    BlockState stateAt(int x, int y, int z) {
        int col = column(x, z);
        if (!source.safe[col] || y < 10) return source.at(x, y, z);
        int height = heights[col];
        if (y <= height) return source.at(x, y - (height - source.heights[col]), z);
        return y == height + 1 ? clutter[col] : AIR;
    }

    @Override protected StructureProcessorType<?> getType() { return RiftfallProcessors.WILDERNESS.get(); }

    @Override @SuppressWarnings("deprecation")
    public StructureTemplate.StructureBlockInfo processBlock(LevelReader level, BlockPos piecePos,
            BlockPos structurePos, StructureTemplate.StructureBlockInfo raw,
            StructureTemplate.StructureBlockInfo current, StructurePlaceSettings settings) {
        if (source == null) return current;
        BlockPos p = raw.pos();
        BlockState state = stateAt(p.getX(), p.getY(), p.getZ());
        // Vanilla applies state rotation AFTER processors; current.pos is already transformed.
        return state == current.state() ? current : new StructureTemplate.StructureBlockInfo(current.pos(), state, null);
    }

    /** Compact immutable source, built once per loaded template, with no world or template references. */
    static final class Source {
        final int sizeY;
        final BlockState[] blocks;
        final int[] heights = new int[WIDTH * WIDTH];
        final boolean[] safe = new boolean[WIDTH * WIDTH];
        final Map<BlockState, List<BlockState>> choices = new HashMap<>();
        final Map<BlockState, Integer> groundCounts = new HashMap<>();

        Source(StructureTemplate template) {
            if (template.getSize().getX() != WIDTH || template.getSize().getZ() != WIDTH
                    || template.getSize().getY() < 16 || template.getSize().getY() > 128)
                throw new IllegalArgumentException("Unsupported wilderness footprint");
            sizeY = template.getSize().getY();
            blocks = new BlockState[WIDTH * WIDTH * sizeY];
            boolean[] protectedColumns = new boolean[WIDTH * WIDTH];
            var infos = TemplateBlockIterator.singlePaletteBlocks(template);
            // Air cells must be explicit: the processor needs a destination for raised ground/new props.
            if (infos.size() != blocks.length) throw new IllegalArgumentException("Wilderness requires a dense template");
            for (var info : infos) {
                var p = info.pos();
                if (p.getX()<0 || p.getX()>=WIDTH || p.getZ()<0 || p.getZ()>=WIDTH || p.getY()<0 || p.getY()>=sizeY)
                    throw new IllegalArgumentException("Wilderness block outside template");
                int i = index(p.getX(), p.getY(), p.getZ());
                if (blocks[i] != null) throw new IllegalArgumentException("Duplicate wilderness cell");
                blocks[i] = info.state();
                if (info.nbt() != null) protectedColumns[column(p.getX(), p.getZ())] = true;
            }
            var groundStates = new HashSet<BlockState>();
            var clutterStates = new HashSet<BlockState>();
            for (var state : new HashSet<>(Arrays.asList(blocks))) {
                String id = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
                if (GROUND.contains(id)) groundStates.add(state);
                if (CLUTTER.contains(id) && state.getFluidState().isEmpty()) clutterStates.add(state);
            }
            for (int z=0; z<WIDTH; z++) for (int x=0; x<WIDTH; x++) {
                int col = column(x,z), h = 10;
                while (h+1<sizeY && groundStates.contains(at(x,h+1,z))) h++;
                heights[col] = h;
                if (!groundStates.contains(at(x,h,z))) protectedColumns[col] = true;
                for (int y=10; y<sizeY; y++) {
                    var state = at(x,y,z);
                    if (!state.getFluidState().isEmpty()) protect(protectedColumns,x,z,4);
                    if (y>h && !state.isAir() && !(y==h+1 && clutterStates.contains(state)))
                        protectedColumns[col] = true; // trees, boulders, logs, paired plants and unknown blocks
                }
            }
            boolean[] buffered = protectedColumns.clone();
            for (int z=0; z<WIDTH; z++) for (int x=0; x<WIDTH; x++)
                if (protectedColumns[column(x,z)]) protect(buffered,x,z,2);
            for (int z=4; z<WIDTH-4; z++) for (int x=4; x<WIDTH-4; x++) {
                int col=column(x,z), h=heights[col];
                if (buffered[col] || h+2>=sizeY) continue;
                safe[col] = true;
                var ground=at(x,h,z);
                groundCounts.merge(ground,1,Integer::sum);
                var prop=at(x,h+1,z);
                if (clutterStates.contains(prop)) choices.computeIfAbsent(ground,k -> new ArrayList<>()).add(prop);
            }
        }

        BlockState at(int x, int y, int z) { return y<0 || y>=sizeY ? AIR : blocks[index(x,y,z)]; }
    }

    private static void protect(boolean[] mask, int x, int z, int radius) {
        for (int b=Math.max(0,z-radius); b<=Math.min(99,z+radius); b++)
            for (int a=Math.max(0,x-radius); a<=Math.min(99,x+radius); a++) mask[column(a,b)] = true;
    }
    private static int column(int x, int z) { return z*WIDTH+x; }
    private static int index(int x, int y, int z) { return y*WIDTH*WIDTH+column(x,z); }
    private static long mix(long n) {
        n=(n^(n>>>30))*0xBF58476D1CE4E5B9L; n=(n^(n>>>27))*0x94D049BB133111EBL;
        return n^(n>>>31);
    }
    private static double unit(long seed, int x, int z) {
        return (mix(seed ^ (long)x*0x9E3779B97F4A7C15L ^ (long)z*0xC2B2AE3D27D4EB4FL)>>>11)*0x1.0p-53;
    }
    private static double noise(long seed, int x, int z, int scale) {
        int a=Math.floorDiv(x,scale), b=Math.floorDiv(z,scale);
        double u=Math.floorMod(x,scale)/(double)scale, v=Math.floorMod(z,scale)/(double)scale;
        u=u*u*(3-2*u); v=v*v*(3-2*v);
        double lo=unit(seed,a,b)*(1-u)+unit(seed,a+1,b)*u;
        double hi=unit(seed,a,b+1)*(1-u)+unit(seed,a+1,b+1)*u;
        return 2*(lo*(1-v)+hi*v)-1;
    }
}
