package com.cyberday1.riftfall.worldgen.level;

import com.cyberday1.riftfall.RiftfallCore;
import com.cyberday1.riftfall.block.marker.MarkerKind;
import com.cyberday1.riftfall.block.marker.RiftfallMarkers;
import com.cyberday1.riftfall.data.TemplatePoolProvider;
import com.cyberday1.riftfall.init.RiftfallRegistries;
import com.cyberday1.riftfall.rift.RiftKeyItem;
import com.cyberday1.riftfall.rift.RiftLevelState;
import com.cyberday1.riftfall.rift.RiftLevels;
import com.cyberday1.riftfall.theme.RiftTheme;
import com.cyberday1.riftfall.theme.ResolvedTheme;
import com.cyberday1.riftfall.worldgen.async.BakeScheduler;
import com.cyberday1.riftfall.worldgen.async.BakedRoom;
import com.cyberday1.riftfall.worldgen.async.BakedRoomContext;
import com.cyberday1.riftfall.worldgen.async.SectionSwapper;
import com.cyberday1.riftfall.worldgen.barrel.BarrelRules;
import com.cyberday1.riftfall.worldgen.barrel.BarrelTally;
import com.cyberday1.riftfall.worldgen.decorator.RoomKind;
import com.cyberday1.riftfall.worldgen.processor.BarrelConversionProcessor;
import com.cyberday1.riftfall.worldgen.processor.ClipProcessor;
import com.cyberday1.riftfall.worldgen.processor.DecoratorDriver;
import com.cyberday1.riftfall.worldgen.processor.PoiAnchorProcessor;
import com.cyberday1.riftfall.worldgen.processor.RoomKindProcessor;
import com.cyberday1.riftfall.worldgen.processor.ThemeSubstitutionProcessor;
import com.cyberday1.riftfall.worldgen.processor.TrialSpawnerProcessor;
import com.cyberday1.riftfall.worldgen.layout.BranchLayout;
import com.cyberday1.riftfall.worldgen.layout.RiftPlan;
import com.cyberday1.riftfall.worldgen.layout.RiftRoomLibrary;
import com.cyberday1.riftfall.worldgen.layout.RiftScale;
import com.cyberday1.riftfall.worldgen.layout.RoomIndex;
import com.cyberday1.riftfall.worldgen.layout.RiftShape;
import com.cyberday1.riftfall.worldgen.poi.JigsawFilters;
import com.cyberday1.riftfall.worldgen.decorator.GemVeinDecorator;
import com.cyberday1.riftfall.worldgen.ore.OreRooms;
import com.cyberday1.riftfall.worldgen.decorator.RoomFacts;
import com.cyberday1.riftfall.worldgen.ore.RiftOres;
import com.cyberday1.riftfall.worldgen.processor.RoomFactsProcessor;
import com.cyberday1.riftfall.worldgen.poi.PoiRecursion;
import com.cyberday1.riftfall.worldgen.poi.PoolPieces;
import com.cyberday1.riftfall.worldgen.space.TemplateJigsaws;
import com.cyberday1.riftfall.worldgen.space.TemplatePlacement;
import com.cyberday1.riftfall.worldgen.space.TripleMirror;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.SectionPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.JigsawReplacementProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorList;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.core.Registry;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The generator for a rift dimension: a void that a single pre-planned dungeon is stamped into.
 * Nothing comes from noise &mdash; {@link #fillFromNoise} hands the chunk straight back, and every
 * terrain-height method answers with the floor of the world.
 *
 * <p>
 * Everything is written from {@link #applyBiomeDecoration}, which is handed a {@link WorldGenLevel}
 * and can therefore place templates without a mixin. Rooms are bigger than a chunk, so each column
 * stamps every room reaching into it, clipped to itself; every random is keyed on the room or the
 * seam rather than the chunk, so the independent columns agree.
 */
@net.neoforged.fml.common.EventBusSubscriber(modid = RiftfallCore.MODID)
public class RiftChunkGenerator extends ChunkGenerator {

    private static final Logger LOGGER = LogUtils.getLogger();
    private volatile int layoutVersion = RiftScale.CURRENT_VERSION;

    /** Matches vanilla's own template placement: notify clients, and skip the shape-update pass. */
    private static final int PLACE_FLAGS = 18;

    /** How far off the way out {@link #arrival} puts a player, in blocks. */
    public static final int ARRIVAL_NEAR = 4;
    public static final int ARRIVAL_FAR = 5;

    /** How far {@link #standing} will climb or drop from the way out's own floor line. */
    private static final int ARRIVAL_RISE = 2;

    private static final List<Vec3i> ARRIVAL_RING = ring(ARRIVAL_NEAR, ARRIVAL_FAR);

    /**
     * What the space between the rooms is made of when a dimension does not say. Bedrock because it
     * cannot be dug: a rift is meant to be walked, not tunnelled through.
     */
    public static final BlockState DEFAULT_FILLER = Blocks.BEDROCK.defaultBlockState();

    public static final MapCodec<RiftChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            BiomeSource.CODEC.fieldOf("biome_source").forGetter(generator -> generator.biomeSource),
            Codec.intRange(1, 64).fieldOf("radius_sections").forGetter(generator -> generator.radiusSections),
            Codec.intRange(16, 4064).fieldOf("height").forGetter(generator -> generator.height),
            Codec.LONG.optionalFieldOf("seed").forGetter(generator -> generator.seed),
            BlockState.CODEC.optionalFieldOf("filler", DEFAULT_FILLER).forGetter(generator -> generator.filler),
            Codec.intRange(RiftKeyItem.DEFAULT_TIER, Integer.MAX_VALUE)
                    .optionalFieldOf("tier", RiftKeyItem.DEFAULT_TIER).forGetter(generator -> generator.tier),
            ResourceKey.codec(RiftfallRegistries.RIFT_THEMES).optionalFieldOf("theme")
                    .forGetter(generator -> generator.theme),
            ResourceKey.codec(RiftfallRegistries.RIFT_THEMES).listOf().optionalFieldOf("attuned", List.of())
                    .forGetter(generator -> generator.attuned)
    ).apply(instance, RiftChunkGenerator::new));

    /**
     * Keeps the theme roll off the layout's stream of numbers, so adding a theme to the registry
     * cannot change which rooms a seed builds.
     */
    private static final long THEME_SALT = 0x7B1EF7A11L;

    private final int radiusSections;
    private final int height;

    /**
     * The block every unclaimed part of the rift is made of. Any state is accepted rather than a
     * curated list; a diggable one gives up the guarantee that the dungeon must be walked.
     */
    private final BlockState filler;

    /**
     * The seed this rift's layout is drawn from, or empty to use the level's. Carried rather than
     * read off the level because {@code ServerLevel#getSeed} returns the <em>server's</em> seed, so
     * per-portal rift levels would every one of them build the same dungeon.
     */
    private final Optional<Long> seed;

    /**
     * The tier the key that opened this rift was forged at, and so how big the dungeon is; only
     * {@link RiftScale} reads it. {@code radius_sections} is <em>not</em> derived from it, because
     * the envelope is geometry the dimension owns; {@link RiftDimensions#stem} keeps the two in step.
     */
    private final int tier;

    /**
     * The theme every room and prop in this rift is rendered in, or empty to roll one from the seed.
     * A theme belongs to the <em>rift</em>, not a room, so it is decided once here and bound into
     * every placement &mdash; the runtime decision {@link TemplatePoolProvider} leaves open.
     */
    private final Optional<ResourceKey<RiftTheme>> theme;

    /** The themes an attuned key asked for; the roll is made among these instead of every theme. */
    private final List<ResourceKey<RiftTheme>> attuned;

    /**
     * Guards everything derived from the seed alone: {@link #plan}, {@link #themed},
     * {@link #poiPieces} and {@link #barrels}. Worldgen workers take this, so <strong>nothing may
     * read a block or ask for a chunk while holding it</strong> &mdash; that is the whole reason it
     * exists as its own object.
     */
    private final Object planLock = new Object();

    /**
     * Guards the four fields found by walking a room: {@link #entry} and the three boss markers. The
     * server thread takes this and then does read blocks, which can generate the columns it reads.
     *
     * <p>
     * Separate from {@link #planLock} on purpose, and the separation is load-bearing rather than
     * tidiness. One monitor for both deadlocked the server outright: the server thread held it inside
     * {@code entry()} and blocked in {@code ServerChunkCache.getChunk}, while the worldgen worker
     * filling that column blocked wanting the same monitor for {@code themeFor}. Walking into a
     * portal on a rift nobody had warmed hung the game for good on 08-31.
     *
     * <p>
     * ⚠ The ordering that keeps it fixed: this lock may be taken and then {@link #planLock} (both
     * walks ask for the plan first), never the other way round. Nothing under {@link #planLock} may
     * call {@link #entry}, {@link #bossPad}, {@link #bossEntry} or {@link #bossGate}.
     */
    private final Object markerLock = new Object();

    /**
     * Built once, then only read. A rift is rebuilt from {@link #riftSeed} every server start, so a
     * chunk generated before a restart and its neighbour after must come from the same plan.
     */
    private volatile RiftPlan plan;

    /**
     * This rift's theme and the two processor lists bound to it. Resolved once for the same reason
     * {@link #plan} is: a rift that re-rolled per chunk would be a different colour per column.
     */
    private volatile Themed themed;

    /** Resolves POI pools against the live registry. Built with the plan, then only read. */
    private volatile PoolPieces poiPieces;

    /**
     * Where a player arrives, and the two pads in the boss room. All three may be found by consuming
     * a marker an author placed, so all three are resolved exactly once: a second scan would find
     * nothing. The boss pads are resolved together, in one walk of the room.
     */
    private volatile BlockPos entry;
    private volatile Optional<BlockPos> bossPad;
    private volatile Optional<BlockPos> bossEntry;
    private volatile List<BlockPos> bossGate;
    private volatile Optional<BlockPos> bossExit;

    /**
     * What each room is furnished with, keyed on the room's corner. A pure function of the room and
     * the seed, so the cache is an optimisation only: a room spans up to nine columns and each would
     * otherwise redo the whole recursion. Concurrent because worldgen builds several chunks at once.
     */
    private final Map<Vec3i, List<Prop>> furnishings = new ConcurrentHashMap<>();
    private volatile Set<Vec3i> oreRooms;

    private final AtomicInteger propsPlaced = new AtomicInteger();
    private final AtomicInteger propsDropped = new AtomicInteger();

    /** Chest markers offered and barrels written. */
    private final BarrelTally barrelTally = new BarrelTally();

    /**
     * Off-thread room rasterizer with backpressure. Created lazily on first use so singleplayer
     * debug sessions that never generate a rift pay nothing. Closed when the generator is discarded.
     */
    private volatile BakeScheduler scheduler;
    private final RiftLoadStats stats = new RiftLoadStats();
    private volatile boolean bakesClosed;

    /**
     * This rift's converter, holding the rules it was opened under. Lazy for the reason
     * {@link #themed} is: the rift seed is not known until a level asks for a column.
     */
    private volatile BarrelConversionProcessor barrels;

    /**
     * The barrel take-up as one line, for {@code /riftfall rift list} &mdash; the same numbers the
     * debug screen carries, asked of a headless server that has just force-loaded a rift.
     */
    public RiftLoadStats loadStats() {
        return stats;
    }

    public String barrelReport() {
        return barrelTally.report();
    }

    /**
     * One resolved prop, in the same terms a room is placed in.
     *
     * @param corner the minimum block corner of the oriented piece, in world blocks
     * @param size   the authored, untransformed extent, as {@link TemplatePlacement#anchor} expects
     */
    private record Prop(ResourceLocation template, BlockPos corner, TripleMirror mirror, Vec3i size) {
    }

    /**
     * The theme this rift settled on, and the pool-time processor lists rewritten to name it.
     *
     * @param room the list rooms and the blender's wall copies are stamped with
     * @param poi  the list props are stamped with, kept separate so a pack can process the furniture
     *             differently from the building
     */
    private record Themed(ResourceKey<RiftTheme> theme, List<StructureProcessor> room,
                          List<StructureProcessor> poi) {
    }

    public RiftChunkGenerator(BiomeSource biomeSource, int radiusSections, int height, Optional<Long> seed,
                              BlockState filler, int tier, Optional<ResourceKey<RiftTheme>> theme) {
        this(biomeSource, radiusSections, height, seed, filler, tier, theme, List.of());
    }

    public RiftChunkGenerator(BiomeSource biomeSource, int radiusSections, int height, Optional<Long> seed,
                              BlockState filler, int tier, Optional<ResourceKey<RiftTheme>> theme,
                              List<ResourceKey<RiftTheme>> attuned) {
        super(biomeSource);
        this.radiusSections = radiusSections;
        this.height = height;
        this.seed = seed;
        this.filler = filler;
        this.tier = tier;
        this.theme = theme;
        this.attuned = List.copyOf(attuned);
    }

    @Override
    protected MapCodec<? extends ChunkGenerator> codec() {
        return CODEC;
    }

    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(Blender blender, RandomState randomState,
                                                        StructureManager structures, ChunkAccess chunk) {
        return CompletableFuture.completedFuture(chunk);
    }

    @Override
    public void applyBiomeDecoration(WorldGenLevel level, ChunkAccess chunk, StructureManager structures) {
        RiftPlan current = planFor(level.getLevel());
        long columnStart = System.nanoTime();
        fill(chunk);
        stats.add(RiftLoadStats.Phase.FILL, columnStart);
        List<RiftPlan.PlacedRoom> rooms = current.roomsIn(chunk.getPos());
        // Not folded into the room test: an archway on a west or north face closes a plane in the
        // column next door, which is very often nothing but filler.
        List<RiftPlan.Blank> blanks = current.blanksIn(chunk.getPos());
        if (!rooms.isEmpty() || !blanks.isEmpty()) {
            StructureTemplateManager templates = level.getLevel().getStructureManager();
            Themed look = themeFor(level.getLevel());
            BoundingBox column = new BoundingBox(
                    chunk.getPos().getMinBlockX(), level.getMinBuildHeight(), chunk.getPos().getMinBlockZ(),
                    chunk.getPos().getMaxBlockX(), level.getMaxBuildHeight() - 1, chunk.getPos().getMaxBlockZ());
            ServerLevel serverLevel = level.getLevel();
            ResolvedTheme resolvedTheme = asyncTheme(level, look.room());
            BarrelRules barrelRules = RiftLevels.barrelRules(serverLevel);
            BakeScheduler activeScheduler = scheduler;
            if (activeScheduler == null) {
                synchronized (planLock) {
                    activeScheduler = scheduler;
                    if (activeScheduler == null && !bakesClosed) {
                        activeScheduler = new BakeScheduler();
                        scheduler = activeScheduler;
                    }
                }
            }
            // Retain plan order, including rejected submissions, so fallback cannot reverse overlays.
            List<Runnable> placements = new ArrayList<>();
            for (RiftPlan.PlacedRoom room : rooms) {
                Optional<StructureTemplate> template = templates.get(room.template());
                CompletableFuture<BakedRoom> future = null;
                if (activeScheduler != null && resolvedTheme != null && template.isPresent()) {
                    BakedRoomContext ctx = new BakedRoomContext(riftSeed(serverLevel), level.getSeed(),
                            RoomKind.fromTemplate(room.template()),
                            barrelRules, column, resolvedTheme, true, false, roomFacts(level, room, templates));
                    future = activeScheduler.submit(room, template.get(), ctx, stats);
                }
                CompletableFuture<BakedRoom> submitted = future;
                placements.add(() -> {
                    long waitStart = System.nanoTime();
                    BakeScheduler.finish(submitted, baked -> {
                        stats.add(RiftLoadStats.Phase.ROOM_WAIT, waitStart);
                        long applyStart = System.nanoTime();
                        SectionSwapper.apply(level, baked, roomRandom(level, room));
                        baked.recordBarrels(barrelTally, barrelRules, riftSeed(serverLevel));
                        stats.add(RiftLoadStats.Phase.APPLY, applyStart);
                    }, () -> {
                        long nativeStart = System.nanoTime();
                        place(level, room, room.anchor(), column, templates, look.room(), false);
                        stats.add(RiftLoadStats.Phase.NATIVE, nativeStart);
                    }, error -> LOGGER.warn("Riftfall: bake failed for room {} in {}; placing synchronously",
                            room.template(), chunk.getPos(), error));
                });
            }
            placements.forEach(Runnable::run);
            placements.clear();
            // Props overlay rooms, and seams must see the finished furniture.
            ResolvedTheme poiTheme = asyncTheme(level, look.poi());
            for (RiftPlan.PlacedRoom room : rooms) {
                for (Prop prop : furnishingFor(level, room, templates)) {
                    Optional<StructureTemplate> template = templates.get(prop.template());
                    CompletableFuture<BakedRoom> future = null;
                    if (activeScheduler != null && poiTheme != null && template.isPresent()) {
                        BlockPos anchor = TemplatePlacement.anchor(prop.size(), prop.mirror(), prop.corner());
                        BoundingBox bounds = TemplatePlacement.boundsAt(prop.size(), prop.mirror(), prop.corner());
                        BakedRoomContext ctx = new BakedRoomContext(riftSeed(serverLevel), level.getSeed(),
                                RoomKind.UNKNOWN,
                                barrelRules, column, poiTheme, true, false, RoomFacts.tier(tier));
                        future = activeScheduler.submitProp(prop.mirror(), anchor, bounds, template.get(), ctx,
                                stats);
                    }
                    CompletableFuture<BakedRoom> submitted = future;
                    placements.add(() -> BakeScheduler.finish(submitted, baked -> {
                        SectionSwapper.apply(level, baked, propRandom(level, prop));
                        baked.recordBarrels(barrelTally, barrelRules, riftSeed(serverLevel));
                    }, () -> placeProp(level, prop, column, templates, look.poi()),
                            error -> LOGGER.warn("Riftfall: bake failed for prop {} in {}; placing synchronously",
                                    prop.template(), chunk.getPos(), error)));
                }
            }
            placements.forEach(Runnable::run);
            // A face has different marker/entity rules and a shifted origin. A normal column bake
            // cannot supply it, especially when the source wall belongs to an adjacent column.
            long seamStart = System.nanoTime();
            CorridorBlender.FaceSampler faces = (room, toward, window) -> {
                long faceStart = System.nanoTime();
                place(level, room, room.anchor().relative(toward), window, templates, look.room(), true);
                stats.add(RiftLoadStats.Phase.FACE, faceStart);
            };
            for (RiftPlan.Seam seam : current.seamsIn(chunk.getPos())) {
                CorridorBlender.blend(level, riftSeed(serverLevel), seam, faces);
            }
            for (RiftPlan.Blank blank : blanks) {
                CorridorBlender.brick(level, riftSeed(serverLevel), blank, faces);
            }
            stats.add(RiftLoadStats.Phase.SEAM, seamStart);
        }
        super.applyBiomeDecoration(level, chunk, structures);
        stats.add(RiftLoadStats.Phase.COLUMN, columnStart);
    }

    /**
     * Turns every section of this column inside the rift's envelope into solid rock, the sections
     * the rooms sit in included, so the only air in a rift is air some template asked for. Skipping
     * a claimed section is not equivalent: a room is {@code 16n - 1} blocks in an {@code n}-section
     * box, so the maximum plane of each axis belongs to no template and would be left void.
     *
     * <p>
     * Writes to the {@link ChunkAccess} rather than through the {@link WorldGenLevel} the template
     * passes need: this is a bulk fill of up to 65,536 blocks per column, and going direct skips a
     * bounds check per block. Downward so the heightmap is set by the first write, not updated.
     */
    private void fill(ChunkAccess chunk) {
        RiftShape envelope = fillShape();
        ChunkPos column = chunk.getPos();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int index = chunk.getSectionsCount() - 1; index >= 0; index--) {
            int sectionY = chunk.getSectionYFromSectionIndex(index);
            if (!envelope.isSectionValid(column.x, sectionY, column.z)) {
                continue;
            }
            int bottom = SectionPos.sectionToBlockCoord(sectionY);
            for (int y = bottom + 15; y >= bottom; y--) {
                for (int x = column.getMinBlockX(); x <= column.getMaxBlockX(); x++) {
                    for (int z = column.getMinBlockZ(); z <= column.getMaxBlockZ(); z++) {
                        chunk.setBlockState(cursor.set(x, y, z), filler, false);
                    }
                }
            }
        }
    }

    /**
     * Stamps one room, clipped to {@code box}. The anchor is a parameter because the blender calls
     * this with a shifted one and a one-plane box to lift a wall into the seam; everything else,
     * above all the random that picks the palette, must stay what the real placement will use.
     *
     * @param face true for the blender's copy rather than the room itself: the one case where
     *             entities must not come along, since they would be duplicated a block away
     */
    private void place(WorldGenLevel level, RiftPlan.PlacedRoom room, BlockPos anchor, BoundingBox box,
                       StructureTemplateManager templates, List<StructureProcessor> theme, boolean face) {
        // The only place the 1.21.1 stand-in copies are consulted. The plan was built from the
        // canonical files, so only which palette gets stamped changes here, never the layout.
        Optional<StructureTemplate> template = templates.get(room.template());
        if (template.isEmpty()) {
            LOGGER.warn("Riftfall: the plan wants {} but the template is gone; leaving a hole",
                    room.template());
            return;
        }

        // Vanilla's own pool placement, with our mirror added. Known shape makes a template land as
        // authored rather than settling like player-placed blocks, and the jigsaw replacement turns
        // connectors into their final_state instead of leaving jigsaw blocks in a finished room.
        StructurePlaceSettings settings = TemplatePlacement.settings(room.mirror())
                .setBoundingBox(box)
                .setKnownShape(true)
                .setIgnoreEntities(face)
                .setFinalizeEntities(!face);
        if (face) {
            // A face is a few blocks of a whole room: drop the rest before anything processes it.
            BoundingBox needed = faceClip(level, box, theme);
            if (needed != null) {
                settings.addProcessor(new ClipProcessor(needed));
            }
        }
        settings.addProcessor(BlockIgnoreProcessor.STRUCTURE_BLOCK)
                .addProcessor(JigsawReplacementProcessor.INSTANCE)
                .addProcessor(PoiAnchorProcessor.INSTANCE)
                // Before the theme list on the same terms the anchors are: an authored spawner is a
                // marker, and a marker has to be gone by the time the room is being rendered.
                .addProcessor(face ? TrialSpawnerProcessor.DROP : TrialSpawnerProcessor.CONVERT)
                // Changes nothing; it is how the theme's decorators learn which room they are in.
                // The blender's copy is stamped unknown on purpose: it is one plane of somebody
                // else's wall lifted into a seam, not a room, and floor decoration placed into it
                // would be a second copy of something the room itself already decided about.
                .addProcessor(RoomKindProcessor.of(
                        face ? RoomKind.UNKNOWN : RoomKind.fromTemplate(room.template())))
                // The tier and the prop boxes, for the ore pass; a face copy gets none.
                .addProcessor(new RoomFactsProcessor(face ? RoomFacts.NONE : roomFacts(level, room, templates)));
        theme.forEach(settings::addProcessor);
        // Last, so nothing downstream sees a barrel where a marker used to be. The blender's shifted
        // copy gets the instance that only deletes: rolling a marker at anchor+1 could stand a second
        // barrel beside the one the room itself placed.
        settings.addProcessor(face ? BarrelConversionProcessor.DROP_MARKERS : barrels(level.getLevel()));

        template.get().placeInWorld(level, anchor, anchor, settings, roomRandom(level, room), PLACE_FLAGS);
    }

    /** The window plus what its decoration can depend on; null when that has no known bound. */
    @Nullable
    public static BoundingBox faceClip(WorldGenLevel level, BoundingBox window, List<StructureProcessor> theme) {
        if (theme.isEmpty()) {
            return window;
        }
        ResolvedTheme resolved = asyncTheme(level, theme);
        int reach = resolved == null ? -1 : DecoratorDriver.reach(resolved.decorators(), RoomKind.UNKNOWN);
        return reach < 0 ? null : window.inflatedBy(reach);
    }

    /** The converter for this rift, built on first use once a level can be asked for the seed. */
    private BarrelConversionProcessor barrels(ServerLevel level) {
        BarrelConversionProcessor current = barrels;
        if (current == null) {
            synchronized (planLock) {
                current = barrels;
                if (current == null) {
                    current = new BarrelConversionProcessor(
                            RiftLevels.barrelRules(level), riftSeed(level), barrelTally);
                    barrels = current;
                }
            }
        }
        return current;
    }

    /** Stamps one prop, clipped to {@code box}, with the POI processor list rather than the room one. */
    private void placeProp(WorldGenLevel level, Prop prop, BoundingBox box, StructureTemplateManager templates,
                           List<StructureProcessor> theme) {
        Optional<StructureTemplate> template = templates.get(prop.template());
        if (template.isEmpty()) {
            return;
        }
        StructurePlaceSettings settings = TemplatePlacement.settings(prop.mirror())
                .setBoundingBox(box)
                .setKnownShape(true)
                .setIgnoreEntities(false)
                .setFinalizeEntities(true)
                .addProcessor(BlockIgnoreProcessor.STRUCTURE_BLOCK)
                .addProcessor(JigsawReplacementProcessor.INSTANCE)
                .addProcessor(PoiAnchorProcessor.INSTANCE)
                // Where all 336 of the library's authored spawners actually are: on POI pieces.
                .addProcessor(TrialSpawnerProcessor.CONVERT)
                .addProcessor(new RoomFactsProcessor(RoomFacts.tier(tier)));
        theme.forEach(settings::addProcessor);
        settings.addProcessor(barrels(level.getLevel()));

        BlockPos anchor = TemplatePlacement.anchor(prop.size(), prop.mirror(), prop.corner());
        template.get().placeInWorld(level, anchor, anchor, settings, propRandom(level, prop), PLACE_FLAGS);
    }

    private List<Prop> furnishingFor(WorldGenLevel level, RiftPlan.PlacedRoom room,
                                     StructureTemplateManager templates) {
        // An ore room is bare: no synthetic anchors, no props, so the floor is the boulders'.
        if (oreRoomsFor(level.getLevel()).contains(room.corner())) return List.of();
        return furnishings.computeIfAbsent(room.corner(), key -> furnish(level, room, templates));
    }

    /** The rift's tier, the boxes this room's props will be stamped into after it, and whether it is an ore room. */
    private RoomFacts roomFacts(WorldGenLevel level, RiftPlan.PlacedRoom room, StructureTemplateManager templates) {
        List<BoundingBox> boxes = new ArrayList<>();
        for (Prop prop : furnishingFor(level, room, templates)) {
            boxes.add(TemplatePlacement.boundsAt(prop.size(), prop.mirror(), prop.corner()));
        }
        return new RoomFacts(tier, boxes, oreRoomsFor(level.getLevel()).contains(room.corner()));
    }

    /**
     * This rift's ore rooms by corner: every placed room {@link OreRooms#isOreRoom} names. A version-1 rift
     * plans no caverns, so only a listed room there (hub-hub-bub-4-mine in the original's slot) is one.
     */
    public Set<Vec3i> oreRoomsFor(ServerLevel level) {
        Set<Vec3i> current = oreRooms;
        if (current != null) return current;
        RiftPlan planned = planFor(level);
        synchronized (planLock) {
            if (oreRooms == null) {
                Set<Vec3i> out = new java.util.LinkedHashSet<>();
                if (RiftOres.ENABLED) {
                    for (RiftPlan.PlacedRoom room : planned.rooms()) {
                        if (OreRooms.isOreRoom(room.template())) out.add(room.corner());
                    }
                }
                oreRooms = Set.copyOf(out);
            }
            return oreRooms;
        }
    }

    /** The theme's gem-vein share of in-wall anchors; 0 with the ore switch off or no gem_vein decorator. */
    private float gemVeinChance(WorldGenLevel level) {
        if (!RiftOres.ENABLED) return 0F;
        for (StructureProcessor processor : themeFor(level.getLevel()).poi()) {
            if (!(processor instanceof ThemeSubstitutionProcessor substitution)) continue;
            for (var decorator : substitution.resolvedTheme(level).decorators()) {
                if (decorator instanceof GemVeinDecorator gems) return gems.anchorChance();
            }
        }
        return 0F;
    }

    /** The tier this rift's key was forged at. */
    public int tier() {
        return tier;
    }

    /** One resolved prop of a room, for probes: its template and its world box. */
    public record PropView(ResourceLocation template, BoundingBox box) {
    }

    /** The props a room is furnished with (computes and caches the furnishing). Builds the plan if needed. */
    public List<PropView> props(ServerLevel level, RiftPlan.PlacedRoom room) {
        planFor(level);
        List<PropView> out = new ArrayList<>();
        for (Prop prop : furnishingFor(level, room, level.getStructureManager())) {
            out.add(new PropView(prop.template(), TemplatePlacement.boundsAt(prop.size(), prop.mirror(), prop.corner())));
        }
        return out;
    }

    /**
     * Resolves everything one room's builder anchored into it. The room itself is the collision
     * origin and is then dropped from the output, since {@link #place} already stamps it.
     *
     * <p>
     * A prop can be dropped after the recursion accepted it.
     * {@link com.cyberday1.riftfall.worldgen.poi.CollisionMask} bounds props to a 64-block cube at
     * the room's corner, stopping one leaving through the low faces but not the high ones, and rooms
     * are one plane apart, so an overhang writes into the room next door. The test against
     * {@link RiftPlan.PlacedRoom#bounds()} makes it symmetric. A dropped prop keeps its claim in the
     * mask, so it can still shut a sibling out of that space.
     */
    private List<Prop> furnish(WorldGenLevel level, RiftPlan.PlacedRoom room,
                               StructureTemplateManager templates) {
        Optional<StructureTemplate> template = templates.get(room.template());
        if (template.isEmpty()) {
            return List.of();
        }
        PoiRecursion.Piece self = new PoiRecursion.Piece(room.template(), template.get().getSize(),
                TemplateJigsaws.parse(template.get()));

        List<Prop> collected = new ArrayList<>();
        int[] dropped = new int[1];
        PoiRecursion.furnish(self, Vec3i.ZERO, room.mirror(), poiPieces,
                List.of(JigsawFilters.stripRings(RiftfallCore.MODID), JigsawFilters.shuffle(),
                        JigsawFilters.gemVeins(gemVeinChance(level), riftSeed(level.getLevel())
                                ^ BlockPos.asLong(room.corner().getX(), room.corner().getY(), room.corner().getZ())
                                * 0x9E3779B97F4A7C15L)),
                roomRandom(level, room),
                (piece, shift, mirror) -> {
                    if (piece == self) {
                        return;
                    }
                    BlockPos corner = new BlockPos(room.corner().getX() + shift.getX(),
                            room.corner().getY() + shift.getY(), room.corner().getZ() + shift.getZ());
                    Vec3i extent = mirror.transformSize(piece.size());
                    if (!contains(room.bounds(), corner, extent)) {
                        dropped[0]++;
                        return;
                    }
                    collected.add(new Prop(piece.id(), corner, mirror, piece.size()));
                });

        // The recursion reports a piece only once its children are resolved, so the list arrives
        // deepest-first. Stamping it that way lets a shelf paint over the candle standing on it.
        Collections.reverse(collected);
        propsPlaced.addAndGet(collected.size());
        propsDropped.addAndGet(dropped[0]);
        return List.copyOf(collected);
    }

    /** Whether the box {@code [corner, corner + extent)} lies wholly inside {@code bounds}. */
    private static boolean contains(BoundingBox bounds, BlockPos corner, Vec3i extent) {
        return corner.getX() >= bounds.minX() && corner.getX() + extent.getX() - 1 <= bounds.maxX()
                && corner.getY() >= bounds.minY() && corner.getY() + extent.getY() - 1 <= bounds.maxY()
                && corner.getZ() >= bounds.minZ() && corner.getZ() + extent.getZ() - 1 <= bounds.maxZ();
    }

    /**
     * The random one prop is placed with, keyed on the prop's own corner: a prop wider than a chunk
     * is stamped once per column it touches, and a per-chunk random would give it two palettes.
     */
    private RandomSource propRandom(WorldGenLevel level, Prop prop) {
        long mixed = riftSeed(level.getLevel())
                ^ (long) prop.corner().getX() * 341873128712L
                ^ (long) prop.corner().getY() * 132897987541L
                ^ (long) prop.corner().getZ() * 6364136223846793005L;
        return RandomSource.create(mixed);
    }

    /**
     * The random one room is placed with, keyed on where the room is rather than which chunk is
     * being built. A multi-palette template picks its palette from this, so a per-chunk random would
     * draw a straight seam down a chunk border.
     */
    private RandomSource roomRandom(WorldGenLevel level, RiftPlan.PlacedRoom room) {
        long mixed = riftSeed(level.getLevel())
                ^ (long) room.corner().getX() * 341873128712L
                ^ (long) room.corner().getY() * 132897987541L
                ^ (long) room.corner().getZ() * 6364136223846793005L;
        return RandomSource.create(mixed);
    }

    /**
     * Everything random about this rift derives from here: the layout, every palette and every
     * doorway coin flip. One accessor so a rift cannot get its layout and decoration from different
     * seeds.
     */
    private long riftSeed(ServerLevel level) {
        return seed.orElseGet(level::getSeed);
    }

    /**
     * The theme this rift is rendered in, for the passes that run long after generation. Spawners ask
     * at the moment they fire rather than carrying a copy in their saved data, so the roll here is
     * the only roll.
     */
    public ResourceKey<RiftTheme> theme(ServerLevel level) {
        return themeFor(level).theme();
    }

    private Themed themeFor(ServerLevel level) {
        Themed current = themed;
        if (current != null) {
            return current;
        }
        synchronized (planLock) {
            if (themed == null) {
                themed = resolveTheme(level);
            }
            return themed;
        }
    }

    /**
     * Decides what this rift is made of, and rewrites the pool-time processor lists to say so. A
     * swap, not a replacement: every registered processor is kept in order and only an
     * <em>unbound</em> {@link ThemeSubstitutionProcessor} is exchanged, so a datapack keeps its own
     * processors and any theme it pinned itself.
     */
    private Themed resolveTheme(ServerLevel level) {
        Optional<ResourceKey<RiftTheme>> tuned = theme.isPresent() ? Optional.empty() : rollAttuned(level);
        ResourceKey<RiftTheme> chosen = theme.orElseGet(() -> tuned.orElseGet(() -> roll(level)));
        Registry<StructureProcessorList> lists = level.registryAccess().registryOrThrow(Registries.PROCESSOR_LIST);
        LOGGER.info("Riftfall rift: rendering in theme {} ({})", chosen.location(),
                theme.isPresent() ? "stated" : tuned.isPresent() ? "attuned" : "rolled");
        return new Themed(chosen,
                bind(lists, TemplatePoolProvider.ROOM_THEME, chosen),
                bind(lists, TemplatePoolProvider.POI_THEME, chosen));
    }

    private static List<StructureProcessor> bind(Registry<StructureProcessorList> lists,
                                                 ResourceKey<StructureProcessorList> key,
                                                 ResourceKey<RiftTheme> theme) {
        return lists.getHolderOrThrow(key).value().list().stream()
                .map(processor -> processor instanceof ThemeSubstitutionProcessor substitution
                        && substitution.theme().isEmpty()
                        ? new ThemeSubstitutionProcessor(Optional.of(theme))
                        : processor)
                .toList();
    }

    /** Captures the supported theme chain; null requests native placement for a custom chain. */
    @Nullable
    static ResolvedTheme asyncTheme(WorldGenLevel level, List<StructureProcessor> processors) {
        // Arbitrary datapack processors may read the world or change ordering. Keep their native
        // placement path, and honor an explicitly pinned theme on the supported processor.
        return processors.size() == 1 && processors.getFirst() instanceof ThemeSubstitutionProcessor theme
                ? theme.resolvedTheme(level) : null;
    }

    /** Read SavedData on the server thread before warmup or any worker builds the plan. */
    @net.neoforged.bus.api.SubscribeEvent
    public static void onLevelLoad(net.neoforged.neoforge.event.level.LevelEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level
                && level.getChunkSource().getGenerator() instanceof RiftChunkGenerator generator) {
            generator.layoutVersion = RiftLevelState.of(level).layoutVersion();
        }
    }

    @net.neoforged.bus.api.SubscribeEvent
    public static void onLevelUnload(net.neoforged.neoforge.event.level.LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level
                && level.getChunkSource().getGenerator() instanceof RiftChunkGenerator generator) {
            generator.closeBakes();
        }
    }

    public void closeBakes() {
        BakeScheduler closing;
        synchronized (planLock) {
            bakesClosed = true;
            closing = scheduler;
            scheduler = null;
        }
        if (closing != null) closing.close();
    }

    /**
     * Picks one of the registered themes for this rift, from the rift's own seed. Every theme is a
     * candidate, {@code riftfall:default} included: it is an authored file, not a null object, and a
     * pack excludes one by pinning {@code theme} on the dimension. Sorted by id before the draw,
     * because a datapack registry iterates in load order, which nothing guarantees.
     */
    private ResourceKey<RiftTheme> roll(ServerLevel level) {
        Registry<RiftTheme> themes = level.registryAccess().registry(RiftfallRegistries.RIFT_THEMES).orElse(null);
        if (themes == null) {
            return RiftfallRegistries.DEFAULT_THEME;
        }
        List<ResourceKey<RiftTheme>> candidates = themes.registryKeySet().stream()
                .sorted(Comparator.comparing(key -> key.location().toString()))
                .toList();
        if (candidates.isEmpty()) {
            LOGGER.warn("Riftfall: no rift themes are registered, so this rift falls back to {}",
                    RiftfallRegistries.DEFAULT_THEME.location());
            return RiftfallRegistries.DEFAULT_THEME;
        }
        return candidates.get(RandomSource.create(riftSeed(level) ^ THEME_SALT).nextInt(candidates.size()));
    }

    /** The attuned themes still registered, drawn from the rift's seed; empty when none is left. */
    private Optional<ResourceKey<RiftTheme>> rollAttuned(ServerLevel level) {
        if (attuned.isEmpty()) {
            return Optional.empty();
        }
        Registry<RiftTheme> themes = level.registryAccess().registry(RiftfallRegistries.RIFT_THEMES).orElse(null);
        List<ResourceKey<RiftTheme>> candidates = attuned.stream()
                .filter(key -> themes != null && themes.containsKey(key))
                .sorted(Comparator.comparing(key -> key.location().toString()))
                .toList();
        if (candidates.isEmpty()) {
            LOGGER.warn("Riftfall: attuned themes {} are not registered; rolling from every theme", attuned);
            return Optional.empty();
        }
        // Xoroshiro mixes its seed: the legacy source's first nextInt(2) barely moves between nearby seeds.
        return Optional.of(candidates.get(new net.minecraft.world.level.levelgen.XoroshiroRandomSource(riftSeed(level) ^ THEME_SALT)
                .nextInt(candidates.size())));
    }

    private RiftPlan planFor(ServerLevel level) {
        RiftPlan current = plan;
        if (current != null) {
            return current;
        }
        synchronized (planLock) {
            if (plan == null) {
                plan = build(level);
            }
            return plan;
        }
    }

    /**
     * The envelope the layout may fill: as wide as the generator was configured and as tall as the
     * world, less a section at each end so a room at the rim cannot reach past the build limit.
     */
    private RiftShape layoutShape() {
        int halfHeight = height / 32;
        return RiftShape.cylinder(radiusSections, -halfHeight + 1, halfHeight - 1);
    }

    /**
     * The envelope the filler fills: {@link #layoutShape} plus one section in every direction. A
     * room may sit hard against the layout rim with a doorway opening into the section beyond, which
     * no room could occupy; fill only what the layout could reach and that doorway is the one hole
     * left in the rift.
     */
    private RiftShape fillShape() {
        int halfHeight = height / 32;
        return RiftShape.cylinder(radiusSections + 1, -halfHeight, halfHeight);
    }

    /** True when the world holds its own copy of {@code room}, which the template manager prefers. */
    private static boolean savedInWorld(StructureTemplateManager templates, ResourceLocation room) {
        try {
            return Files.exists(templates.createAndValidatePathToGeneratedStructure(room, ".nbt"));
        } catch (RuntimeException unusable) {
            return true;
        }
    }

    /** Starts reading every placed room off-thread, entrance first; the manager keeps what it reads. */
    private void preload(RiftPlan planned, StructureTemplateManager templates) {
        Set<ResourceLocation> asked = new HashSet<>();
        for (RiftPlan.PlacedRoom room : planned.rooms()) {
            ResourceLocation id = room.template();
            if (!asked.add(id)) {
                continue;
            }
            try {
                Util.backgroundExecutor().execute(() -> {
                    long start = System.nanoTime();
                    templates.get(id);
                    stats.add(RiftLoadStats.Phase.TEMPLATE, start);
                });
            } catch (RejectedExecutionException stopping) {
                return;
            }
        }
    }

    private RiftPlan build(ServerLevel level) {
        Registry<StructureTemplatePool> pools = level.registryAccess().registryOrThrow(Registries.TEMPLATE_POOL);
        // Written before the plan is, so a thread that sees a plan is guaranteed to see this too.
        poiPieces = new PoolPieces(pools, level.getStructureManager());
        long libraryStart = System.nanoTime();
        StructureTemplateManager templates = level.getStructureManager();
        RiftRoomLibrary library = RiftRoomLibrary.load(pools, templates,
                RoomIndex.load(level.getServer().getResourceManager(), room -> savedInWorld(templates, room)));
        stats.add(RiftLoadStats.Phase.LIBRARY, libraryStart);
        long planStart = System.nanoTime();
        if (library.isEmpty()) {
            LOGGER.error("Riftfall: no rooms could be loaded, so this rift will be empty void");
            return RiftPlan.of(new BranchLayout.Result(List.of(), List.of(), List.of(), 0, 0, 0, 0, -1),
                    id -> null);
        }

        long layoutSeed = riftSeed(level);
        BranchLayout.Config budget = RiftScale.forTier(tier, layoutVersion).layout();
        BranchLayout.Result layout = BranchLayout.generate(budget, library.supply(),
                layoutShape(), Vec3i.ZERO, RandomSource.create(layoutSeed));
        RiftPlan built = RiftPlan.of(layout, library::blockSize);
        stats.add(RiftLoadStats.Phase.PLAN, planStart);
        preload(built, templates);

        // Every socket in the rift, deliberately a slightly larger number than 2 * seams + blanks:
        // a vertical socket has no authored aperture to open or close, and a socket facing the
        // room's own middle is an interior wall. Expect a residue of a few, not zero.
        int archways = 0;
        for (BranchLayout.Placement placement : layout.placements()) {
            archways += placement.space().sockets().size();
        }
        int againstRooms = 0;
        for (RiftPlan.Blank blank : built.blanks()) {
            if (blank.facesRoom()) {
                againstRooms++;
            }
        }
        // How many stubs actually came out dead ends. Reported rather than assumed: a stub is an
        // ordinary library piece with more than one socket, so findJoins can and sometimes does open
        // a second doorway in one. The stubs are the last placements, see BranchLayout.Result.
        int stubDeadEnds = 0;
        for (int room = layout.roomCount() - layout.stubs(); room < layout.roomCount(); room++) {
            boolean joined = false;
            for (BranchLayout.Join join : layout.joins()) {
                joined |= join.fromIndex() == room || join.toIndex() == room;
            }
            stubDeadEnds += joined ? 0 : 1;
        }
        LOGGER.info("Riftfall rift: tier {} ({} deep, {} rooms allowed, radius {}) built {} rooms over {} chunk "
                        + "columns, {} of them stubs of {} asked for ({} dead ends), {} seams ({} beyond the "
                        + "tree) across {} archways ({}% lead somewhere), {} walled up ({} against a room, "
                        + "{} against the filler), {} deep, boss room {}, extent {}, seed {} ({})",
                tier, budget.maxDepth(), budget.maxRooms(), radiusSections,
                built.rooms().size(), built.occupiedColumns(), layout.stubs(), budget.stubRooms(),
                stubDeadEnds, built.seams().size(), layout.joins().size(),
                archways, archways == 0 ? 0 : 200 * built.seams().size() / archways,
                built.blanks().size(), againstRooms, built.blanks().size() - againstRooms, layout.maxDepth(),
                built.boss() == null ? "not placed" : "at depth " + built.boss().depth(),
                built.bounds(), layoutSeed, seed.isPresent() ? "own" : "level");
        return built;
    }

    /**
     * Somewhere in the root room a player can be dropped and be standing up, and where the way out is
     * raised. An {@code exit_portal} marker wins where the room's author placed one; otherwise the
     * geometric scan below, which is the only answer the ~270 imported rooms can give. Resolved once
     * and kept, because finding the marker also spends it.
     */
    public BlockPos entry(ServerLevel level) {
        BlockPos current = entry;
        if (current != null) {
            return current;
        }
        synchronized (markerLock) {
            if (entry == null) {
                // A rift restored after a restart has already spent its marker; the state remembers
                // where it was (G9).
                RiftLevelState state = RiftLevelState.of(level);
                BlockPos remembered = state.entry();
                entry = remembered != null ? remembered : findEntry(level);
                if (remembered == null) {
                    state.entry(entry);
                }
            }
            return entry;
        }
    }

    private BlockPos findEntry(ServerLevel level) {
        // An empty layout means an empty library, which build() already logged as an error; there is
        // no room to stand in either way.
        RiftPlan.PlacedRoom entrance = planFor(level).entrance();
        if (entrance == null) {
            return BlockPos.ZERO;
        }
        BoundingBox room = entrance.bounds();
        BlockPos marked = consumeMarker(level, room, MarkerKind.EXIT_PORTAL);
        if (marked != null) {
            return marked;
        }

        // Not the layout origin (0, 0, 0): RiftPlan places a room at the minimum corner of its
        // section box, so the origin is floor and wall. So the horizontal middle, then the lowest
        // spot with a solid block under it and two clear above -- scanned rather than assumed,
        // because a room may have a pit or a plinth in the middle of it.
        int x = (room.minX() + room.maxX()) / 2;
        int z = (room.minZ() + room.maxZ()) / 2;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int y = room.minY() + 1; y < room.maxY(); y++) {
            boolean floor = !level.getBlockState(cursor.set(x, y - 1, z)).isAir();
            boolean headroom = level.getBlockState(cursor.set(x, y, z)).isAir()
                    && level.getBlockState(cursor.set(x, y + 1, z)).isAir();
            if (floor && headroom) {
                return new BlockPos(x, y, z);
            }
        }
        return new BlockPos(x, room.minY() + 1, z);
    }

    /**
     * Where a player arriving lands: a standing spot four or five blocks off {@link #entry}, picked
     * at random from every one the room offers.
     *
     * <p>
     * Arrival and the way out used to be the same block, which was fine while the way out was a frame
     * raised a few blocks to the side. The tear that replaced it stands <em>on</em> {@link #entry}, so
     * landing there would drop a player straight back through it. The ring is what keeps the rip in
     * the middle of the room where it can be seen whole, and puts the player far enough back to see
     * it. Random per arrival, so a party does not stack on one block.
     *
     * <p>
     * Falls back to {@link #entry} itself when the room offers nothing in the ring &mdash; a player
     * standing in the way out is recoverable, a player inside a wall is not.
     */
    public BlockPos arrival(ServerLevel level) {
        BlockPos centre = entry(level);
        List<BlockPos> spots = new ArrayList<>();
        for (Vec3i step : ARRIVAL_RING) {
            BlockPos spot = standing(level, centre.offset(step));
            if (spot != null) {
                spots.add(spot);
            }
        }
        return spots.isEmpty() ? centre : spots.get(level.random.nextInt(spots.size()));
    }

    /**
     * The block at {@code at}'s column a player can stand on, or null if there is not one within
     * {@link #ARRIVAL_RISE} of it. Solid underfoot and two clear above, the same test
     * {@link #findEntry} scans for.
     *
     * <p>
     * Level ground is tried first and the search widens from there, so a room with a step or a shallow
     * pit still answers rather than losing the whole column.
     */
    @Nullable
    private static BlockPos standing(ServerLevel level, BlockPos at) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int spread = 0; spread <= ARRIVAL_RISE; spread++) {
            for (int rise : spread == 0 ? new int[] {0} : new int[] {-spread, spread}) {
                int y = at.getY() + rise;
                boolean floor = !level.getBlockState(cursor.set(at.getX(), y - 1, at.getZ())).isAir();
                boolean headroom = level.getBlockState(cursor.set(at.getX(), y, at.getZ())).isAir()
                        && level.getBlockState(cursor.set(at.getX(), y + 1, at.getZ())).isAir();
                if (floor && headroom) {
                    return new BlockPos(at.getX(), y, at.getZ());
                }
            }
        }
        return null;
    }

    /**
     * Every horizontal step whose distance falls in the band, corners and all. Built once: it depends
     * on nothing but the two constants, and it is walked on every arrival.
     *
     * <p>
     * Compared squared, so the band is the true circular one and not a square ring &mdash; a diagonal
     * step of five is over seven blocks out, and would put a player through the wall of a small room.
     */
    static List<Vec3i> ring(int near, int far) {
        List<Vec3i> steps = new ArrayList<>();
        for (int dx = -far; dx <= far; dx++) {
            for (int dz = -far; dz <= far; dz++) {
                int square = dx * dx + dz * dz;
                if (square >= near * near && square <= far * far) {
                    steps.add(new Vec3i(dx, 0, dz));
                }
            }
        }
        return List.copyOf(steps);
    }

    /**
     * The pad the boss is put on. Empty when the layout fitted no boss room, or when the piece that
     * took the role carries no marker &mdash; the supply falls back to an ordinary room rather than
     * refusing to build a rift.
     *
     * <p>Reads blocks, so it plans and generates the boss room's columns on the calling thread.
     */
    public Optional<BlockPos> bossPad(ServerLevel level) {
        resolveBossMarkers(level);
        return bossPad;
    }

    /**
     * The pad a run arrives on, which is a different block from {@link #bossPad} on purpose: a party
     * dropped onto the boss's own footprint starts the fight already inside it.
     *
     * <p>
     * Empty when the room's author placed no {@code boss_entry}, and deliberately does <em>not</em>
     * fall back to the arena pad &mdash; the whole point of the second marker is that those two
     * positions are never the same one. A caller with no answer here should use the way it would
     * have moved a player before boss rooms existed.
     */
    public Optional<BlockPos> bossEntry(ServerLevel level) {
        resolveBossMarkers(level);
        return bossEntry;
    }

    /**
     * Every block of the doorway the gate fills, lowest row first, or empty when the room's author
     * marked no gate. Unlike the two pads this is a rectangle, so it is a list and its order is the
     * order the gate rises in.
     */
    public List<BlockPos> bossGate(ServerLevel level) {
        resolveBossMarkers(level);
        return bossGate;
    }

    /**
     * Where the boss room sits, or null when the layout fitted none.
     *
     * <p>
     * Reads no blocks, which is the point: a caller that has to ask every tick whether anybody is in
     * the boss room cannot be the thing that generates it. {@link #bossGate} may then be asked once
     * somebody is actually standing in there and the chunks are loaded anyway.
     *
     * <p>
     * It does <em>plan</em>, though, on the first call, which is a few hundred rooms. That is free
     * in practice because {@code RiftWarmup} has already planned every rift off-thread by the time
     * anybody is inside one to be asked about &mdash; but a caller polling this on the server thread
     * is relying on that, not on this method being cheap.
     */
    @Nullable
    public BoundingBox bossRoomBounds(ServerLevel level) {
        RiftPlan.PlacedRoom boss = bossRoom(level);
        return boss == null ? null : boss.bounds();
    }

    /** The boss room as planned (template, orientation, corner), or null; reads no blocks, as above. */
    @Nullable
    public RiftPlan.PlacedRoom bossRoom(ServerLevel level) {
        return planFor(level).boss();
    }

    /**
     * Where the way home opens when the boss dies, or empty when the arena's author marked no
     * {@code exit_portal}.
     *
     * <p>
     * ⛔ This is the only thing that ever reads an {@code exit_portal} in a boss room. Until it
     * existed the marker was consumed nowhere &mdash; {@code findEntry} walks the entrance room and
     * nothing else looked in the arena &mdash; so the block the author placed survived generation as
     * visible crying obsidian on the arena floor. Resolving it and consuming it are the same walk,
     * which is why reading the position is also what removes the scenery.
     */
    public Optional<BlockPos> bossExit(ServerLevel level) {
        resolveBossMarkers(level);
        return bossExit;
    }

    /** All four boss-room markers, consumed in a single walk of the room. Idempotent. */
    private void resolveBossMarkers(ServerLevel level) {
        if (bossPad != null) {
            return;
        }
        synchronized (markerLock) {
            if (bossPad != null) {
                return;
            }
            // The walk consumes the markers, so a rift restored after a restart reads what the first
            // walk found rather than walking an emptied room (G9).
            RiftLevelState state = RiftLevelState.of(level);
            if (state.bossResolved()) {
                bossEntry = state.bossEntry();
                bossGate = state.bossGate();
                bossExit = state.bossExit();
                bossPad = state.bossPad();
                return;
            }
            RiftPlan.PlacedRoom boss = planFor(level).boss();
            Map<MarkerKind, List<BlockPos>> found = boss == null ? Map.of()
                    : consumeMarkers(level, boss.bounds(), EnumSet.of(MarkerKind.BOSS_ARENA,
                            MarkerKind.BOSS_ENTRY, MarkerKind.BOSS_GATE, MarkerKind.EXIT_PORTAL));
            Optional<BlockPos> pad = first(found, MarkerKind.BOSS_ARENA);
            bossEntry = first(found, MarkerKind.BOSS_ENTRY);
            bossGate = found.getOrDefault(MarkerKind.BOSS_GATE, List.of());
            bossExit = first(found, MarkerKind.EXIT_PORTAL);
            state.bossMarkers(pad, bossEntry, bossExit, bossGate);
            // Written last: every other field is published by the time a reader that raced us on
            // the null check above sees this one set.
            bossPad = pad;
        }
    }

    private static Optional<BlockPos> first(Map<MarkerKind, List<BlockPos>> found, MarkerKind kind) {
        return found.getOrDefault(kind, List.of()).stream().findFirst();
    }

    /**
     * The first position in {@code room} carrying {@code kind}, taken out of the world as it is
     * found. Consumed rather than left: the marker has said its one thing, and an inert block in the
     * middle of a portal or an arena floor is scenery nobody authored.
     */
    @Nullable
    private static BlockPos consumeMarker(ServerLevel level, BoundingBox room, MarkerKind kind) {
        List<BlockPos> found = consumeMarkers(level, room, EnumSet.of(kind)).get(kind);
        return found == null || found.isEmpty() ? null : found.getFirst();
    }

    /**
     * Every position of each of {@code kinds}, in one walk. A room is up to 47 blocks a side, so
     * asking twice costs a second 100k-block scan for an answer the first walk went past.
     *
     * <p>
     * A {@linkplain MarkerKind#rectangle() rectangle} kind collects all of its positions; a point
     * kind keeps only its first, so a duplicate an author left behind is still consumed out of the
     * world rather than shipped as scenery. The walk can only stop early when every requested kind is
     * a point kind and all of them have been seen &mdash; a rectangle is never known to be complete.
     */
    private static Map<MarkerKind, List<BlockPos>> consumeMarkers(ServerLevel level, BoundingBox room,
            Set<MarkerKind> kinds) {
        Map<MarkerKind, List<BlockPos>> found = new EnumMap<>(MarkerKind.class);
        boolean canStopEarly = kinds.stream().noneMatch(MarkerKind::rectangle);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        // y outermost so a gate's positions arrive bottom row first, which is the order it rises in.
        for (int y = room.minY(); y <= room.maxY(); y++) {
            for (int x = room.minX(); x <= room.maxX(); x++) {
                for (int z = room.minZ(); z <= room.maxZ(); z++) {
                    MarkerKind kind = RiftfallMarkers.identify(level.getBlockState(cursor.set(x, y, z)))
                            .orElse(null);
                    if (kind == null || !kinds.contains(kind)) {
                        continue;
                    }
                    BlockPos at = cursor.immutable();
                    level.setBlock(at, Blocks.AIR.defaultBlockState(), PLACE_FLAGS);
                    List<BlockPos> positions = found.computeIfAbsent(kind, key -> new ArrayList<>());
                    if (kind.rectangle() || positions.isEmpty()) {
                        positions.add(at);
                    }
                    if (canStopEarly && found.size() == kinds.size()) {
                        return found;
                    }
                }
            }
        }
        return found;
    }

    /**
     * The chunk columns worth having ready before anybody walks in; see
     * {@link RiftPlan#entranceColumns}. Plans the rift if it has not been planned yet, so call it off
     * the server thread: that is the whole cost of a rift up to the moment its blocks are wanted.
     */
    public Set<ChunkPos> entranceColumns(ServerLevel level) {
        return planFor(level).entranceColumns();
    }

    /**
     * Which authored room a block position falls in, or null for the space between rooms. Asked of
     * {@link RiftPlan.PlacedRoom#bounds} rather than the section box, since the bounds are what was
     * built; the two differ by the seam plane, so standing in a doorway answers with neither room.
     * Builds the plan if needed, the same cost the chunk underfoot already paid.
     */
    @Nullable
    public RiftPlan.PlacedRoom roomAt(ServerLevel level, BlockPos pos) {
        return roomAt(planFor(level), pos);
    }

    /** Every placed room, building the plan if needed. Probe read (the rift light survey). */
    public List<RiftPlan.PlacedRoom> rooms(ServerLevel level) {
        return planFor(level).rooms();
    }

    @Nullable
    private static RiftPlan.PlacedRoom roomAt(RiftPlan plan, BlockPos pos) {
        for (RiftPlan.PlacedRoom room : plan.roomsIn(new ChunkPos(pos))) {
            if (room.bounds().isInside(pos)) {
                return room;
            }
        }
        return null;
    }

    @Override
    public void applyCarvers(WorldGenRegion region, long seed, RandomState randomState, BiomeManager biomes,
                             StructureManager structures, ChunkAccess chunk, GenerationStep.Carving step) {
    }

    @Override
    public void buildSurface(WorldGenRegion region, StructureManager structures, RandomState randomState,
                             ChunkAccess chunk) {
    }

    @Override
    public void spawnOriginalMobs(WorldGenRegion region) {
    }

    @Override
    public int getGenDepth() {
        return height;
    }

    @Override
    public int getSeaLevel() {
        return getMinY();
    }

    @Override
    public int getMinY() {
        return -height / 2;
    }

    @Override
    public int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor level, RandomState randomState) {
        return getMinY();
    }

    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor level, RandomState randomState) {
        BlockState[] air = new BlockState[level.getHeight()];
        Arrays.fill(air, Blocks.AIR.defaultBlockState());
        return new NoiseColumn(level.getMinBuildHeight(), air);
    }

    @Override
    public void addDebugScreenInfo(List<String> lines, RandomState randomState, BlockPos pos) {
        RiftPlan current = plan;
        lines.add("Riftfall: " + (current == null ? "no plan yet"
                : current.rooms().size() + " rooms, " + current.occupiedColumns() + " columns"));
        if (current != null) {
            lines.add("Riftfall props: " + propsPlaced.get() + " placed, " + propsDropped.get()
                    + " dropped for overhang, " + furnishings.size() + " rooms furnished");
            lines.add("Riftfall barrels: " + barrelTally.report());
            // The plan already built rather than planFor: F3 is a readout, and a readout must not
            // generate a dungeon on the render thread.
            RiftPlan.PlacedRoom room = roomAt(current, pos);
            lines.add("Riftfall room: " + (room == null ? "between rooms"
                    : room.template() + " (depth " + room.depth() + ")"));
        }
    }
}
