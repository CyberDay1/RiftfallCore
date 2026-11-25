package com.cyberday1.riftfall.worldgen.level;

import com.cyberday1.riftfall.RiftfallCore;
import com.cyberday1.riftfall.rift.RiftKeyItem;
import com.cyberday1.riftfall.worldgen.layout.RiftScale;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.valueproviders.ConstantInt;
import net.minecraft.world.level.biome.AmbientMoodSettings;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.biome.BiomeSpecialEffects;
import net.minecraft.world.level.biome.FixedBiomeSource;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;

import java.util.Optional;
import java.util.OptionalLong;

/**
 * The rift dimension: the world a generated dungeon is the only thing in.
 *
 * <p>
 * The datapack ships one, {@code riftfall:rift}, and every portal opens another built by
 * {@link #stem(RegistryAccess, long, int)} from the same parts. This is the place the generation passes
 * finally become something a player walks into, and everything about it is chosen to keep the
 * dungeon the only source of light, shape and orientation:
 *
 * <ul>
 * <li><b>No sky light and no ceiling.</b> A rift is not underground and not outdoors; it is a void
 * with rooms in it. Sky light would pour into every unroofed room, and a ceiling would make the
 * game light the whole volume as if it were the Nether.
 * <li><b>Time is fixed.</b> Nothing here has a horizon, so a day cycle would only change the ambient
 * tint for no reason a player could see a cause for.
 * <li><b>Beds and respawn anchors do not work.</b> A rift is entered and left, not lived in, and a
 * spawn point inside one would survive the rift being regenerated under a new seed.
 * <li><b>Nothing spawns.</b> Everything a player meets in a rift is meant to be something the
 * dungeon put there. See {@link #RIFT_BIOME}.
 * </ul>
 *
 * <h2>Height</h2>
 * The dimension type and the generator have to agree, because vanilla validates one against the
 * other at level load and refuses the world if they differ. Both are stated once here, so the
 * agreement is visible rather than a pair of numbers in two files that happen to match.
 *
 * <p>
 * The radius used to sit beside it as a second constant and has moved to {@link RiftScale}, which is
 * where the rest of the layout budget is now decided; this only passes it along. The height stays
 * here because it is the one of the three the dimension type is checked against.
 */
public final class RiftDimensions {

    /** Total build height, and with it the generator's {@code minY} at {@code -height/2}. */
    private static final int HEIGHT = 256;

    /**
     * The tier the shipped {@code riftfall:rift} dimension generates at.
     *
     * <p>
     * A datapack dimension is not opened by a key, so there is no key to read a tier off, and the
     * fixed rift is the one anyone reaches with {@code /execute in} rather than by earning it. The
     * first tier is the honest answer, and it is also the one that keeps this dimension generating
     * the rift it has always generated.
     */
    private static final int FIXED_TIER = RiftKeyItem.DEFAULT_TIER;

    /**
     * What everything that is not a room is made of.
     *
     * <p>
     * Bedrock, so that a rift is walked rather than tunnelled through: it backs every doorway the
     * layout left unmatched and it is the one block a survival player cannot get past. Stated here
     * beside the height and the radius because it is the same kind of decision, and because the
     * generator will take anything &mdash; swapping this for deepslate or obsidian changes the look
     * of every wall in the dimension, and a diggable choice gives up the guarantee.
     */
    private static final BlockState FILLER = Blocks.BEDROCK.defaultBlockState();

    /**
     * The floor under every rendered light level: no block in a rift is drawn darker than vanilla
     * light 8.
     *
     * <p>
     * {@code Level#buildBrightnessRamp} builds the table the renderer reads as
     * {@code lerp(ambientLight, f / (4 - 3f), 1)} with {@code f = level / 15}, so the entry for
     * light 0 <em>is</em> {@code ambientLight}, and vanilla light 8 with no ambient light comes to
     * {@code (8/15) / (4 - 3 * 8/15)} = 0.2222. Setting that here lifts the bottom of the ramp to
     * meet it and leaves the top alone: a torch still reads as brighter than the floor.
     *
     * <p>
     * This is brightness, not light. The block light level in an unlit room stays 0, so anything
     * that reads the light level rather than the brightness -- crops growing, undead burning -- is
     * unaffected. The one thing that mattered was mob spawning, and {@link #RIFT_BIOME} settles that
     * outright rather than by being bright enough. Real fixtures in the rooms are still owed; this
     * is the floor beneath them, not a substitute for them.
     *
     * <p>
     * Shader packs never read this ramp; their floor comes from the profile {@code RiftIrisDimension} picks.
     */
    private static final float AMBIENT_LIGHT = 0.2222F;

    public static final ResourceKey<DimensionType> RIFT_TYPE =
            ResourceKey.create(Registries.DIMENSION_TYPE, RiftfallCore.id("rift"));

    /**
     * The rift's own biome, and the reason it exists is that it contains nothing.
     *
     * <p>
     * A rift used {@code minecraft:the_void}, which is nearly right -- vanilla builds it with an
     * empty spawn list -- but it is nearly right in a way that another mod can undo. Biome modifiers
     * are how the loader lets a mod add spawns to somebody else's biome, they are matched by biome
     * tag, and every tag {@code the_void} is in is a tag some mod is targeting. A rift would then be
     * populated by whatever the modpack thought belonged in the overworld.
     *
     * <p>
     * A biome of our own, in no tags, is not a target. Nothing spawns here because the list is
     * empty, and the list stays empty because nothing can reach it. Its generation settings are
     * empty for a second reason: {@code the_void} carries the void start platform, a 33x33 slab of
     * stone laid near the origin of any world using it, which is a thing the rift was one unlucky
     * room placement away from finding in the middle of its entrance.
     *
     * <p>
     * The colours are the void's own, so this is a change of ownership rather than a change of
     * appearance.
     */
    public static final ResourceKey<Biome> RIFT_BIOME =
            ResourceKey.create(Registries.BIOME, RiftfallCore.id("rift"));

    public static final ResourceKey<LevelStem> RIFT_STEM =
            ResourceKey.create(Registries.LEVEL_STEM, RiftfallCore.id("rift"));

    private RiftDimensions() {
    }

    public static void dimensionTypes(BootstrapContext<DimensionType> context) {
        context.register(RIFT_TYPE, new DimensionType(
                OptionalLong.of(18000L), // fixed midnight: the dungeon lights itself
                false, // hasSkyLight
                false, // hasCeiling
                false, // ultraWarm
                false, // natural — no natural portal linking, no compass
                1.0,   // coordinateScale — a rift is its own place, not a scaled overworld
                false, // bedWorks
                false, // respawnAnchorWorks
                -HEIGHT / 2,
                HEIGHT,
                HEIGHT,
                BlockTags.INFINIBURN_OVERWORLD,
                BuiltinDimensionTypes.OVERWORLD_EFFECTS,
                AMBIENT_LIGHT,
                new DimensionType.MonsterSettings(false, false, ConstantInt.of(0), 0)));
    }

    public static void biomes(BootstrapContext<Biome> context) {
        context.register(RIFT_BIOME, new Biome.BiomeBuilder()
                .hasPrecipitation(false)
                .temperature(0.5F)
                .downfall(0.5F)
                .specialEffects(new BiomeSpecialEffects.Builder()
                        .waterColor(4159204)
                        .waterFogColor(329011)
                        .fogColor(12638463)
                        .skyColor(8103167) // OverworldBiomes.calculateSkyColor(0.5F), which is private
                        .ambientMoodSound(AmbientMoodSettings.LEGACY_CAVE_SETTINGS)
                        .build())
                .mobSpawnSettings(MobSpawnSettings.EMPTY)
                .generationSettings(BiomeGenerationSettings.EMPTY)
                .build());
    }

    public static void levelStems(BootstrapContext<LevelStem> context) {
        context.register(RIFT_STEM, stem(context.lookup(Registries.DIMENSION_TYPE), context.lookup(Registries.BIOME),
                Optional.empty(), FIXED_TIER));
    }

    /**
     * The same stem the datapack ships, built against a live server's registries and given a seed and
     * a difficulty of its own.
     *
     * <p>
     * This is what a portal hands to a new rift level. It goes through the same factory as the
     * shipped {@code riftfall:rift} so that the two cannot drift: a rift opened from a portal is the
     * fixed dimension in every respect except which number its layout comes out of and how far that
     * layout is allowed to grow.
     */
    public static LevelStem stem(RegistryAccess access, long seed, int tier) {
        return stem(access, seed, tier, java.util.List.of());
    }

    /** As above, with the themes an attuned key asked for; empty rolls from every theme. */
    public static LevelStem stem(RegistryAccess access, long seed, int tier,
                                 java.util.List<net.minecraft.resources.ResourceKey<com.cyberday1.riftfall.theme.RiftTheme>> attuned) {
        return stem(access.lookupOrThrow(Registries.DIMENSION_TYPE), access.lookupOrThrow(Registries.BIOME),
                Optional.of(seed), tier, attuned);
    }

    private static LevelStem stem(HolderGetter<DimensionType> types, HolderGetter<Biome> biomes, Optional<Long> seed,
                                  int tier) {
        return stem(types, biomes, seed, tier, java.util.List.of());
    }

    private static LevelStem stem(HolderGetter<DimensionType> types, HolderGetter<Biome> biomes, Optional<Long> seed,
                                  int tier, java.util.List<net.minecraft.resources.ResourceKey<com.cyberday1.riftfall.theme.RiftTheme>> attuned) {
        // One biome everywhere, and it contributes nothing on purpose: no spawns, no features, no
        // carvers, no surface. Everything a player meets in a rift comes from the rooms.
        //
        // The envelope comes from RiftScale rather than from a constant here, because it is derived
        // from the depth the layout is allowed to reach and the two only mean anything together: an
        // envelope narrower than that reach clips the outermost branches, and a wider one is space no
        // branch can get to.
        // No theme: a rift rolls its own from its seed. Naming one here would give every rift the
        // mod opens the same walls.
        return new LevelStem(
                types.getOrThrow(RIFT_TYPE),
                new RiftChunkGenerator(new FixedBiomeSource(biomes.getOrThrow(RIFT_BIOME)),
                        RiftScale.forTier(tier).radiusSections(), HEIGHT, seed, FILLER, tier, Optional.empty(), attuned));
    }
}
