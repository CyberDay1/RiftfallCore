package com.cyberday1.riftfall.theme;

import com.cyberday1.riftfall.block.processor.ProcessorSlot;
import com.cyberday1.riftfall.worldgen.decorator.RiftDecorator;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A theme: what the fifteen abstract material slots are made of, plus how the substitution noise
 * behaves and what dressing to add afterwards.
 *
 * <p>
 * Loaded from {@code data/<namespace>/riftfall/rift_theme/<name>.json}. The whole design target is
 * that this is <strong>the only file a theme author writes</strong>: fifteen weighted material
 * lists and nothing else. Slabs, stairs, walls, fences, gates, buttons, plates, trapdoors, panes
 * and pillars are all derived from the base block via the baked block-family table, so a theme
 * never enumerates them. For contrast, the reference implementation this replaces needs a
 * hand-written ~28 KB processor list per theme enumerating every placeholder-to-output pair.
 *
 * <p>
 * <strong>Omitted slots are legal.</strong> A theme that only cares about walls and floors writes
 * two entries; the rest fall back (see {@link ResolvedTheme}). That is what keeps a theme small.
 *
 * @param materials       slot -> weighted candidates; keys are the slot numbers as strings
 * @param fallback        block used for any slot with no surviving entries; defaults to
 *                        {@code minecraft:stone}
 * @param biome           optional biome to paint the room with. <em>Parsed and exposed but not yet
 *                        applied</em> — see {@code ThemeSubstitutionProcessor}
 * @param noiseScale      substitution noise frequency per axis
 * @param seedAdjustment  offsets the noise seed so two themes in one world do not blotch in
 *                        lockstep
 * @param decorators      post-substitution passes, run in declaration order
 */
public record RiftTheme(Map<ProcessorSlot, List<ThemeMaterial>> materials, Optional<ResourceLocation> fallback,
        Optional<ResourceKey<Biome>> biome, NoiseScale noiseScale, int seedAdjustment,
        List<RiftDecorator> decorators) {

    public static final Codec<RiftTheme> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.unboundedMap(ThemeCodecs.SLOT, ThemeMaterial.CODEC.listOf())
                    .optionalFieldOf("materials", Map.of()).forGetter(RiftTheme::materials),
            ThemeCodecs.BLOCK_ID.optionalFieldOf("fallback").forGetter(RiftTheme::fallback),
            ResourceKey.codec(Registries.BIOME).optionalFieldOf("biome").forGetter(RiftTheme::biome),
            NoiseScale.CODEC.optionalFieldOf("noise_scale", NoiseScale.DEFAULT).forGetter(RiftTheme::noiseScale),
            Codec.INT.optionalFieldOf("seed_adjustment", 0).forGetter(RiftTheme::seedAdjustment),
            RiftDecorator.CODEC.listOf().optionalFieldOf("decorators", List.of()).forGetter(RiftTheme::decorators)
    ).apply(instance, RiftTheme::new));

    public RiftTheme {
        materials = Map.copyOf(materials);
        decorators = List.copyOf(decorators);
    }
}
