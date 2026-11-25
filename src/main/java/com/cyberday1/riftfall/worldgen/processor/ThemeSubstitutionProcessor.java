package com.cyberday1.riftfall.worldgen.processor;

import com.cyberday1.riftfall.block.processor.ProcessorBlocks;
import com.cyberday1.riftfall.init.RiftfallProcessors;
import com.cyberday1.riftfall.init.RiftfallRegistries;
import com.cyberday1.riftfall.theme.NoiseScale;
import com.cyberday1.riftfall.theme.ResolvedTheme;
import com.cyberday1.riftfall.theme.RiftTheme;
import com.cyberday1.riftfall.theme.family.BlockFamilyTable;
import com.cyberday1.riftfall.theme.family.BlockFamilyTableLoader;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Replaces {@code riftfall:processor_block_*} placeholders with a theme's real blocks.
 *
 * <p>
 * This is the whole point of the placeholder system: a room is authored once against abstract
 * materials ("slot 6, stairs") and this processor decides at generation time what slot 6 is made
 * of. One authored room therefore serves every visual theme.
 *
 * <h2>Why noise and not random</h2>
 * The material for a position is chosen by <strong>coherent 3D simplex noise</strong> sampled at
 * the block's absolute world position. Per-block randomness is the classic reason reskinned rooms
 * look wrong: it produces confetti. Coherent noise makes neighbouring blocks pick the same entry,
 * so materials appear in blotches that read as geology.
 *
 * <h2>Why properties are copied</h2>
 * The output blockstate inherits every property the input carried and the output understands —
 * facing, half, shape, type, waterlogged, axis, wall/pane connections. That is the mechanism by
 * which authored geometry survives substitution: a placeholder stair with
 * {@code facing=east,half=top,shape=outer_left} becomes a <em>real</em> stair with exactly that
 * orientation. Skip this and every room turns to rubble.
 *
 * <p>
 * Placeholder blocks are registered as subclasses of the vanilla class for their shape precisely so
 * these property names line up.
 */
public final class ThemeSubstitutionProcessor extends StructureProcessor {

    public static final MapCodec<ThemeSubstitutionProcessor> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    ResourceKey.codec(RiftfallRegistries.RIFT_THEMES).optionalFieldOf("theme")
                            .forGetter(processor -> processor.theme)
            ).apply(instance, ThemeSubstitutionProcessor::new));

    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Noise generators are keyed by effective seed and shared across every processor and every
     * thread. Building one is cheap but not free, and worldgen calls this from several threads at
     * once.
     */
    private static final Map<Long, SimplexNoise> NOISE_BY_SEED = new ConcurrentHashMap<>();

    /** Guards against the "unknown theme" log firing once per block. */
    private static final java.util.Set<ResourceLocation> WARNED_MISSING = ConcurrentHashMap.newKeySet();

    private final Optional<ResourceKey<RiftTheme>> theme;

    /**
     * Resolution is expensive (15 slots x 12 variants x N materials) and the answer only changes
     * when the theme object or the family table changes, both of which happen on reload. Caching on
     * the processor instance rather than in a static map keeps the hot path to a field read and two
     * reference comparisons.
     */
    private volatile CachedResolution cached;

    public ThemeSubstitutionProcessor(Optional<ResourceKey<RiftTheme>> theme) {
        this.theme = theme;
    }

    /**
     * The theme this processor was built against, or empty if it was left to the default.
     *
     * <p>
     * Empty is the interesting answer rather than the boring one: it is how a processor list says
     * "whoever places me decides". {@code RiftChunkGenerator} reads it to swap an unbound processor
     * for one carrying the theme the rift rolled, and leaves a bound one alone, so a datapack that
     * did name a theme in its list keeps it.
     */
    public Optional<ResourceKey<RiftTheme>> theme() {
        return theme;
    }

    @Override
    protected StructureProcessorType<?> getType() {
        return RiftfallProcessors.THEME.get();
    }

    // -------------------------------------------------------------------------------------------
    // Substitution
    // -------------------------------------------------------------------------------------------

    /**
     * Vanilla marks this {@code @Deprecated} to steer <em>callers</em> towards the template's own
     * dispatch, not to signal that it is going away: it is the only extension point a processor
     * has, and every vanilla processor overrides it.
     */
    @Nullable
    @SuppressWarnings("deprecation")
    @Override
    public StructureTemplate.StructureBlockInfo processBlock(LevelReader world, BlockPos piecePos,
            BlockPos structurePos, StructureTemplate.StructureBlockInfo rawBlockInfo,
            StructureTemplate.StructureBlockInfo blockInfo, StructurePlaceSettings settings) {
        BlockState input = blockInfo.state();
        ProcessorBlocks.Placeholder placeholder = ProcessorBlocks.identify(input.getBlock());
        if (placeholder == null) {
            return blockInfo;
        }

        ResolvedTheme resolved = resolvedTheme(world);
        BlockPos pos = blockInfo.pos();

        // Short-circuit: a single-material slot has nothing to choose between, so do not pay for a
        // noise sample. In practice most slots in most themes are single-material.
        double sample = resolved.size(placeholder.slot()) == 1
                ? 0.0D
                : sampleNoise(world, resolved, pos);

        BlockState output = copyProperties(input, resolved.state(placeholder.slot(), placeholder.variant(), sample));
        return new StructureTemplate.StructureBlockInfo(pos, output, blockInfo.nbt());
    }

    /**
     * Copies every property the input carries onto the output, where the output has one.
     *
     * <p>
     * Iterating the input's properties (rather than the output's) is deliberate: it means a
     * substitution that <em>loses</em> a shape — say a stair degrading to a slab through the
     * fallback chain — still transfers what it can ({@code half} maps to a slab's {@code type} only
     * by name, so it will not, but {@code waterlogged} will) instead of throwing.
     */
    public static BlockState copyProperties(BlockState from, BlockState to) {
        BlockState result = to;
        for (Property<?> property : from.getProperties()) {
            result = copyProperty(from, result, property);
        }
        return result;
    }

    private static <T extends Comparable<T>> BlockState copyProperty(BlockState from, BlockState to,
            Property<T> property) {
        return to.hasProperty(property) ? to.setValue(property, from.getValue(property)) : to;
    }

    private double sampleNoise(LevelReader world, ResolvedTheme resolved, BlockPos pos) {
        long seed = worldSeed(world) + resolved.seedAdjustment();
        SimplexNoise noise = NOISE_BY_SEED.computeIfAbsent(seed, s -> new SimplexNoise(RandomSource.create(s)));
        NoiseScale scale = resolved.noiseScale();
        double value = noise.getValue(pos.getX() * scale.x(), pos.getY() * scale.y(), pos.getZ() * scale.z());
        // abs() folds the [-1, 1] output into [0, 1] so it can walk cumulative weights directly.
        // The clamp stops a rare |value| >= 1 from falling off the end of the table.
        return Math.min(Math.abs(value), 0.999999D);
    }

    private static long worldSeed(LevelReader world) {
        return world instanceof WorldGenLevel level ? level.getSeed() : 0L;
    }

    // -------------------------------------------------------------------------------------------
    // Decoration
    // -------------------------------------------------------------------------------------------

    @Override
    public List<StructureTemplate.StructureBlockInfo> finalizeProcessing(ServerLevelAccessor level, BlockPos offset,
            BlockPos pos, List<StructureTemplate.StructureBlockInfo> originalBlockInfos,
            List<StructureTemplate.StructureBlockInfo> processedBlockInfos, StructurePlaceSettings settings) {
        ResolvedTheme resolved = resolvedTheme(level);
        if (resolved.decorators().isEmpty()) {
            return processedBlockInfos;
        }
        // The room kind rides on the placement rather than on this processor, which is shared by
        // every room in the rift; see RoomKindProcessor for why that distinction is load-bearing.
        return DecoratorDriver.run(resolved.decorators(), processedBlockInfos, worldSeed(level),
                RoomKindProcessor.kindOf(settings), RoomFactsProcessor.factsOf(settings));
    }

    // -------------------------------------------------------------------------------------------
    // Theme lookup
    // -------------------------------------------------------------------------------------------

    /**
     * Finds and resolves the theme for this placement, degrading rather than failing at every step:
     * the named theme, else {@code riftfall:default}, else the Java built-in.
     *
     * <p>
     * This is defence #4. Whatever has gone wrong — no theme named, theme deleted by a datapack,
     * registry absent entirely — a placeholder that reaches this processor comes out as a real
     * material. It never comes out as air, because air is the one failure mode nobody can see.
     */
    public ResolvedTheme resolvedTheme(LevelReader world) {
        RegistryAccess registries = world.registryAccess();
        Registry<RiftTheme> registry = registries.registry(RiftfallRegistries.RIFT_THEMES).orElse(null);

        ResourceKey<RiftTheme> wanted = theme.orElse(RiftfallRegistries.DEFAULT_THEME);
        RiftTheme source = registry == null ? null : registry.get(wanted);
        ResourceKey<RiftTheme> used = wanted;
        if (source == null && registry != null && wanted != RiftfallRegistries.DEFAULT_THEME) {
            warnOnce(wanted.location(), "not found; falling back to " + RiftfallRegistries.DEFAULT_THEME.location());
            source = registry.get(RiftfallRegistries.DEFAULT_THEME);
            used = RiftfallRegistries.DEFAULT_THEME;
        }
        if (source == null) {
            warnOnce(used.location(), "not found and neither is the built-in default theme file; "
                    + "using the hard-coded stone fallback");
            return ResolvedTheme.builtin();
        }

        int generation = BlockFamilyTableLoader.generation();
        CachedResolution snapshot = cached;
        if (snapshot != null && snapshot.source == source && snapshot.generation == generation) {
            return snapshot.resolved;
        }
        BlockFamilyTable families = BlockFamilyTableLoader.table();
        ResolvedTheme fresh = ResolvedTheme.resolve(source, used.location(), families);
        cached = new CachedResolution(source, generation, fresh);
        return fresh;
    }

    private static void warnOnce(ResourceLocation id, String message) {
        if (WARNED_MISSING.add(id)) {
            LOGGER.error("Rift theme '{}' {}", id, message);
        }
    }

    private record CachedResolution(RiftTheme source, int generation, ResolvedTheme resolved) {
    }
}
