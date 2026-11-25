package com.cyberday1.riftfall.theme;

import com.cyberday1.riftfall.block.processor.ProcessorVariant;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;

/**
 * One candidate material in a slot's weighted list.
 *
 * <pre>
 * { "block": "minecraft:deepslate_bricks", "weight": 1.0,
 *   "variants": { "glass": "minecraft:tinted_glass" } }
 * </pre>
 *
 * @param block    id of the base block; kept unresolved so a typo can be reported and skipped
 *                 rather than thrown, and so a theme referencing one dead block still generates
 * @param weight   relative within the slot; the loader normalises, so numbers need not sum to 1
 * @param variants per-variant escape hatch that beats both the datapack family fragments and the
 *                 baked table — this is how a theme reaches modded blocks, and how it fixes
 *                 anything derivation gets wrong, without any Java
 */
public record ThemeMaterial(ResourceLocation block, double weight,
        Map<ProcessorVariant, ResourceLocation> variants) {

    /** Default when {@code weight} is omitted: every entry equally likely. */
    public static final double DEFAULT_WEIGHT = 1.0D;

    private static final Codec<ThemeMaterial> FULL = RecordCodecBuilder.create(instance -> instance.group(
            ThemeCodecs.BLOCK_ID.fieldOf("block").forGetter(ThemeMaterial::block),
            Codec.DOUBLE.optionalFieldOf("weight", DEFAULT_WEIGHT).forGetter(ThemeMaterial::weight),
            Codec.unboundedMap(ThemeCodecs.VARIANT, ThemeCodecs.BLOCK_ID)
                    .optionalFieldOf("variants", Map.of()).forGetter(ThemeMaterial::variants)
    ).apply(instance, ThemeMaterial::new));

    /**
     * Shorthand: a bare block id means weight 1 and no overrides, so the overwhelmingly common
     * single-material slot is {@code "6": ["minecraft:spruce_planks"]}.
     */
    private static final Codec<ThemeMaterial> SHORTHAND = ThemeCodecs.BLOCK_ID
            .xmap(id -> new ThemeMaterial(id, DEFAULT_WEIGHT, Map.of()), ThemeMaterial::block);

    public static final Codec<ThemeMaterial> CODEC = Codec.withAlternative(FULL, SHORTHAND);

    public ThemeMaterial {
        variants = Map.copyOf(variants);
    }
}
