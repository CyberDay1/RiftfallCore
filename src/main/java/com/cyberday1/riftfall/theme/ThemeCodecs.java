package com.cyberday1.riftfall.theme;

import com.cyberday1.riftfall.block.processor.ProcessorSlot;
import com.cyberday1.riftfall.block.processor.ProcessorVariant;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Shared codecs for the theme system.
 *
 * <p>
 * These live here rather than on the enums themselves so {@link ProcessorVariant} and
 * {@link ProcessorSlot} stay free of DFU and Minecraft types and remain directly unit-testable.
 */
public final class ThemeCodecs {

    private static final Map<String, ProcessorVariant> VARIANTS_BY_NAME = buildVariantNames();

    /**
     * Variant by lowercase name: {@code "slab"}, {@code "fence_gate"}, {@code "directional_pillar"}.
     * {@code "base"} parses too, though family files never carry it — the base block is the key.
     */
    public static final Codec<ProcessorVariant> VARIANT = Codec.STRING.comapFlatMap(
            name -> {
                ProcessorVariant variant = VARIANTS_BY_NAME.get(name.toLowerCase(Locale.ROOT));
                return variant == null
                        ? DataResult.error(() -> "Unknown processor variant '" + name + "'")
                        : DataResult.success(variant);
            },
            ThemeCodecs::variantName);

    /**
     * Slot by its number as a string, {@code "1"} .. {@code "15"} — matching the block ids the
     * builders already type, so a theme author never has to learn a second naming scheme.
     */
    public static final Codec<ProcessorSlot> SLOT = Codec.STRING.comapFlatMap(
            text -> {
                int number;
                try {
                    number = Integer.parseInt(text.trim());
                } catch (NumberFormatException e) {
                    return DataResult.error(() -> "Material slot key '" + text + "' is not a number 1-15");
                }
                if (number < 1 || number > ProcessorSlot.ALL.length) {
                    return DataResult.error(() -> "Material slot " + number + " out of range (1-"
                            + ProcessorSlot.ALL.length + ")");
                }
                return DataResult.success(ProcessorSlot.byNumber(number));
            },
            slot -> String.valueOf(slot.number()));

    /**
     * A blockstate written the way a command writes it: {@code "minecraft:lantern[hanging=true]"}.
     *
     * <p>
     * The spec's decorator examples use this form, and it is the only form a theme author would
     * think to type. Vanilla's {@code BlockState.CODEC} accepts the {@code {"Name":..,"Properties":..}}
     * object form instead, so it is offered as an alternative rather than replaced — an existing
     * object-form file still parses.
     */
    public static final Codec<BlockState> BLOCK_STATE_STRING = Codec.STRING.comapFlatMap(
            text -> {
                try {
                    return DataResult.success(
                            BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK.asLookup(), text, false)
                                    .blockState());
                } catch (CommandSyntaxException e) {
                    String message = "Could not parse blockstate '" + text + "': " + e.getMessage();
                    return DataResult.error(() -> message);
                }
            },
            BlockStateParser::serialize);

    /** {@link #BLOCK_STATE_STRING} first, falling back to vanilla's object form. */
    public static final Codec<BlockState> BLOCK_STATE = Codec.withAlternative(BLOCK_STATE_STRING, BlockState.CODEC);

    /** Block ids are kept unresolved so a missing block can be reported and skipped, not thrown. */
    public static final Codec<ResourceLocation> BLOCK_ID = ResourceLocation.CODEC;

    private ThemeCodecs() {
    }

    /** e.g. {@code "fence_gate"}. The inverse of what {@link #VARIANT} parses. */
    public static String variantName(ProcessorVariant variant) {
        return variant.name().toLowerCase(Locale.ROOT);
    }

    private static Map<String, ProcessorVariant> buildVariantNames() {
        Map<String, ProcessorVariant> map = new HashMap<>();
        for (ProcessorVariant variant : ProcessorVariant.values()) {
            map.put(variantName(variant), variant);
        }
        return Map.copyOf(map);
    }
}
