package com.cyberday1.riftfall.home;

import com.cyberday1.riftfall.RiftfallCore;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.valueproviders.ConstantInt;
import net.minecraft.world.level.biome.AmbientMoodSettings;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.biome.BiomeSpecialEffects;
import net.minecraft.world.level.biome.FixedBiomeSource;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;

import java.util.OptionalLong;

/**
 * Dimension type and stem for a player's shattered home: a persistent void with a starter island.
 * Unlike rifts, beds and respawn anchors work here — this is where a player lives between runs.
 */
public final class ShatteredHomeDimensions {
    public static final int GRASS_COLOR = 0x79C05A;

    private static final int HEIGHT = 256;

    public static final ResourceKey<DimensionType> HOME_TYPE =
            ResourceKey.create(Registries.DIMENSION_TYPE, RiftfallCore.id("shattered_home"));

    public static final ResourceKey<Biome> HOME_BIOME =
            ResourceKey.create(Registries.BIOME, RiftfallCore.id("shattered_home"));

    private ShatteredHomeDimensions() {
    }

    public static LevelStem stem(RegistryAccess access) {
        HolderGetter<DimensionType> types = access.lookupOrThrow(Registries.DIMENSION_TYPE);
        HolderGetter<Biome> biomes = access.lookupOrThrow(Registries.BIOME);
        return new LevelStem(
                types.getOrThrow(HOME_TYPE),
                new ShatteredHomeChunkGenerator(new FixedBiomeSource(biomes.getOrThrow(HOME_BIOME))));
    }

    /**
     * The dimension type for a shattered home. Noon-fixed, sky light, beds and respawn anchors
     * enabled. Height matches the chunk generator's 256 / minY -64.
     */
    public static DimensionType dimensionType() {
        return new DimensionType(
                OptionalLong.of(6000L), // fixed noon
                true,  // hasSkyLight: shader packs light the island from it
                false, // hasCeiling
                false, // ultraWarm
                true,  // natural — compass works, beds work
                1.0,   // coordinateScale
                true,  // bedWorks
                true,  // respawnAnchorWorks
                -HEIGHT / 2,
                HEIGHT,
                HEIGHT,
                BlockTags.INFINIBURN_OVERWORLD,
                RiftfallCore.id("shattered_home"),
                0.0F,  // ambientLight — dark void, player places their own lights
                new DimensionType.MonsterSettings(false, false, ConstantInt.of(0), 0));
    }

    /** A void biome with no spawns and no features. The island comes from the chunk generator. */
    public static Biome biome() {
        return new Biome.BiomeBuilder()
                .hasPrecipitation(false)
                .temperature(0.5F)
                .downfall(0.5F)
                .specialEffects(new BiomeSpecialEffects.Builder()
                        .grassColorOverride(GRASS_COLOR)
                        .waterColor(4159204)
                        .waterFogColor(329011)
                        .fogColor(0x000000)
                        .skyColor(0x000000)
                        .ambientMoodSound(AmbientMoodSettings.LEGACY_CAVE_SETTINGS)
                        .build())
                .mobSpawnSettings(MobSpawnSettings.EMPTY)
                .generationSettings(BiomeGenerationSettings.EMPTY)
                .build();
    }
}
