package com.cyberday1.riftfall.worldgen.level;

import com.cyberday1.riftfall.RiftfallCore;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.valueproviders.ConstantInt;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraft.world.level.levelgen.flat.FlatLayerInfo;
import net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorSettings;

import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * {@code riftfall:gallery} &mdash; a lawn with the dungeon pieces hanging just above it.
 *
 * <p>
 * The room library is 124 rooms and several hundred props, and until this existed the only way to
 * see any one of them was to generate a rift and go looking. {@code /riftfall piece} stamps a named
 * piece here instead, either as authored (placeholders left standing) or under a named theme.
 *
 * <h2>Why this is a shipped dimension and not a temporary one</h2>
 * {@code RiftLevels} builds its worlds at runtime and deletes them afterwards, and it goes to
 * considerable trouble to keep them out of {@code level.dat}, because a rift is one entry and is
 * meant to be thrown away. A gallery is the opposite: it is a workbench, and what was laid out in it
 * yesterday should still be there today. So it is an ordinary datapack dimension, generated into
 * {@code data/riftfall/dimension/gallery.json}, and the server loads and saves it like any other.
 *
 * <h2>Why it has a dimension type of its own</h2>
 * Neither existing type works. {@code riftfall:rift} has no sky light and zero ambient light, which
 * is right for a dungeon and means a gallery piece would be a black box. Vanilla's overworld type is
 * lit, but it has a day cycle, and a non-overworld level takes its time of day from
 * {@code DerivedLevelData} &mdash; the overworld's clock &mdash; so a gallery on the overworld type
 * goes dark every time the player's real world does. Half an authoring session in the dark is not a
 * tradeoff worth making, so this type is the overworld's shape with the clock nailed to noon and
 * {@code ambientLight} at full.
 *
 * <p>
 * Full ambient light is deliberately stronger than "well lit": it flattens the shading, and in
 * exchange the inside of a sealed room is legible without the author cutting a hole in it or
 * dropping torches that then have to be cleaned up. Judging a theme's <em>lighting</em> is a job for
 * an actual rift; this is for judging its geometry and its materials.
 *
 * <h2>Why the void biome</h2>
 * Same reason the rift uses it, plus one more that matters here: {@code minecraft:the_void} declares
 * no mob spawns at all, so nothing wanders into the dark interior of a piece while it is being
 * looked at. The dimension type cannot express that on its own &mdash;
 * {@code monster_spawn_light_level} is clamped to {@code [0, 15]} by its codec, so there is no value
 * that means "never" &mdash; but an empty spawn list is exactly that.
 */
public final class GalleryDimension {

    /**
     * The world bottom, and the first of the two bedrock courses.
     *
     * <p>
     * Zero rather than the {@code -64} an overworld uses, because the floor here is three blocks
     * thick and the layer stack always starts at {@code minY}. A dimension type's {@code minY} has to
     * be a multiple of 16, so the only two candidates that put a three-block floor anywhere legible
     * are {@code 0} and {@code -16}, and {@code -16} would leave the grass at {@code y = -14}.
     */
    private static final int MIN_Y = 0;

    /** Top of the grass: two courses of bedrock at {@code y = 0, 1}, then the turf. */
    public static final int FLOOR_TOP_Y = MIN_Y + 2;

    /**
     * The air left between the grass and the underside of a piece.
     *
     * <p>
     * A piece resting on the ground hides its own floor: the bottom course of a room reads as part of
     * the turf, and the one thing a gallery is for is seeing a piece's whole extent. Three blocks is
     * enough to see daylight under a room from standing eye height without the piece floating so far
     * up that it stops belonging to the ground beneath it.
     *
     * <p>
     * It also keeps the grass alive. Turf reverts to dirt when the block directly above it occludes
     * light, so a room set straight down on the floor would print its own footprint into the lawn in
     * brown over the following few minutes.
     */
    public static final int HOVER = 3;

    /** Where a piece's minimum corner goes. */
    public static final int BUILD_Y = FLOOR_TOP_Y + 1 + HOVER;

    private static final int HEIGHT = 384;

    public static final ResourceKey<DimensionType> GALLERY_TYPE =
            ResourceKey.create(Registries.DIMENSION_TYPE, RiftfallCore.id("gallery"));

    public static final ResourceKey<LevelStem> GALLERY_STEM =
            ResourceKey.create(Registries.LEVEL_STEM, RiftfallCore.id("gallery"));

    /** The level key, which is the stem's id under a different registry, as vanilla does it. */
    public static final ResourceKey<Level> GALLERY_LEVEL =
            ResourceKey.create(Registries.DIMENSION, RiftfallCore.id("gallery"));

    private GalleryDimension() {
    }

    public static void dimensionType(BootstrapContext<DimensionType> context) {
        context.register(GALLERY_TYPE, new DimensionType(
                OptionalLong.of(6000L), // fixed noon; see the class doc for why this is not the overworld's clock
                true,  // hasSkyLight
                false, // hasCeiling
                false, // ultraWarm
                false, // natural — nothing here is meant to be lived in or navigated by compass
                1.0,   // coordinateScale
                false, // bedWorks
                false, // respawnAnchorWorks
                MIN_Y,
                HEIGHT,
                HEIGHT,
                BlockTags.INFINIBURN_OVERWORLD,
                BuiltinDimensionTypes.OVERWORLD_EFFECTS,
                1.0F, // ambientLight — sealed rooms have to be readable from inside
                new DimensionType.MonsterSettings(false, false, ConstantInt.of(0), 0)));
    }

    public static void levelStem(BootstrapContext<LevelStem> context) {
        context.register(GALLERY_STEM, new LevelStem(
                context.lookup(Registries.DIMENSION_TYPE).getOrThrow(GALLERY_TYPE),
                new FlatLevelSource(settings(context.lookup(Registries.BIOME)))));
    }

    /**
     * Two courses of bedrock and one of grass, and nothing else.
     *
     * <p>
     * Two of bedrock rather than one because a single course is a floor you can fall out of the world
     * through the moment anything breaks it, and bedrock is the one block a stray command cannot.
     *
     * <p>
     * Grass on top is a deliberate change of mind about the dark neutral this started with. A flat
     * dark plane and a piece sitting on it read as one object; green under grey does not, and with the
     * piece hovering clear of it the ground stops competing for the eye at all &mdash; it becomes the
     * thing that tells you where the piece ends.
     */
    private static FlatLevelGeneratorSettings settings(HolderGetter<Biome> biomes) {
        // An empty override set rather than an absent one: absent means "every structure set in the
        // registry", which would salt the gallery with villages and strongholds.
        FlatLevelGeneratorSettings settings = new FlatLevelGeneratorSettings(
                Optional.of(HolderSet.direct()), biomes.getOrThrow(Biomes.THE_VOID), List.of());
        settings.getLayersInfo().add(new FlatLayerInfo(2, Blocks.BEDROCK));
        settings.getLayersInfo().add(new FlatLayerInfo(1, Blocks.GRASS_BLOCK));
        settings.updateLayers();
        return settings;
    }
}
