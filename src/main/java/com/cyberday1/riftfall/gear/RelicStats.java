package com.cyberday1.riftfall.gear;

import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.datamaps.DataMapType;
import net.neoforged.neoforge.registries.datamaps.RegisterDataMapTypesEvent;

import com.cyberday1.riftfall.RiftfallCore;

/**
 * A silenced relic's fixed Riftfall stats: the data map {@code riftfall:relic_stats}, and the modifier id each
 * worn line gets. The lines are added on Curios' modifier event ({@code RelicCurios}), after the bridge the silence
 * empties, so they survive it. Names no Curios type, so the rule is testable.
 */
public final class RelicStats {

    /** One fixed line. Attributes are ids, resolved when worn, so a line for a missing mod is skipped, not fatal. */
    public record Line(ResourceLocation attribute, double amount, AttributeModifier.Operation operation,
                       boolean tiered) {
        static final Codec<Line> CODEC = RecordCodecBuilder.create(i -> i.group(
                ResourceLocation.CODEC.fieldOf("attribute").forGetter(Line::attribute),
                Codec.DOUBLE.fieldOf("amount").forGetter(Line::amount),
                AttributeModifier.Operation.CODEC.optionalFieldOf("operation", AttributeModifier.Operation.ADD_VALUE)
                        .forGetter(Line::operation),
                Codec.BOOL.optionalFieldOf("tiered", false).forGetter(Line::tiered)).apply(i, Line::new));
    }

    static final Codec<List<Line>> CODEC = Line.CODEC.listOf();

    /** Synced, not mandatory: the client's tooltip needs it, a client without Riftfall does not. */
    public static final DataMapType<Item, List<Line>> RELIC_STATS = DataMapType
            .builder(RiftfallCore.id("relic_stats"), Registries.ITEM, CODEC)
            .synced(CODEC, false)
            .build();

    private static final String FIXED = "relic/";
    private static final String TIERED_FIXED = "tiered/relic/";

    /** Probe-only: off while trinket-slots measures affixes alone, so a relic's own fixed line cannot blur them. */
    private static volatile boolean suspended;

    private RelicStats() {
    }

    public static <T> T withoutFixedLines(java.util.function.Supplier<T> measure) {
        boolean previous = suspended;
        suspended = true;
        try {
            return measure.get();
        } finally {
            suspended = previous;
        }
    }

    static boolean suspended() {
        return suspended;
    }

    public static void register(IEventBus modBus) {
        modBus.addListener((RegisterDataMapTypesEvent event) -> event.register(RELIC_STATS));
    }

    public static List<Line> lines(ItemStack stack) {
        if (stack.isEmpty()) return List.of();
        List<Line> lines = stack.getItemHolder().getData(RELIC_STATS);
        return lines == null ? List.of() : lines;
    }

    /**
     * {@code riftfall:relic/<ns>/<item>/<line>/<slot>_<index>}; health and toughness under {@code riftfall:tiered/}
     * so {@code TrinketCategory#scaleCurio} rides them. Slot and index are in it, so worn copies never collide.
     */
    public static ResourceLocation id(ResourceLocation item, int line, boolean tiered, String slot, int index) {
        return RiftfallCore.id((tiered ? TIERED_FIXED : FIXED) + item.getNamespace() + "/" + item.getPath()
                + "/" + line + "/" + slot + "_" + index);
    }

    /** Whether a modifier id is a fixed relic line (affix ids under {@code tiered/relic/} carry {@code _modifier_}). */
    public static boolean isFixed(ResourceLocation id) {
        if (!RiftfallCore.MODID.equals(id.getNamespace())) return false;
        String path = id.getPath();
        return path.startsWith(FIXED) || path.startsWith(TIERED_FIXED) && !path.contains("_modifier_");
    }
}
