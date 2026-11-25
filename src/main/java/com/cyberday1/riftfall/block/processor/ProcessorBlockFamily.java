package com.cyberday1.riftfall.block.processor;

import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.registries.DeferredBlock;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * One material slot's worth of placeholders: all twelve shapes of {@code processor_block_&lt;N&gt;}.
 *
 * <p>
 * This is the "give me slot 6's stairs" handle that later passes (theme processor, structure
 * tooling, JEI grouping) ask for, so they never have to concatenate a suffix onto a string and
 * hope the registry has it.
 *
 * @param slot     which of the fifteen material slots this family paints
 * @param variants every shape, keyed by variant; always complete (all twelve present)
 */
public record ProcessorBlockFamily(ProcessorSlot slot,
        Map<ProcessorVariant, DeferredBlock<? extends Block>> variants) {

    public ProcessorBlockFamily {
        EnumMap<ProcessorVariant, DeferredBlock<? extends Block>> copy = new EnumMap<>(ProcessorVariant.class);
        copy.putAll(variants);
        for (ProcessorVariant variant : ProcessorVariant.values()) {
            if (!copy.containsKey(variant)) {
                throw new IllegalArgumentException(
                        "Processor family " + slot.familyId() + " is missing variant " + variant);
            }
        }
        variants = Collections.unmodifiableMap(copy);
    }

    /** The {@link DeferredBlock} holder for one shape. Never null. */
    public DeferredBlock<? extends Block> holder(ProcessorVariant variant) {
        return variants.get(variant);
    }

    /**
     * The live {@link Block} for one shape. Only safe once registration has run — use
     * {@link #holder(ProcessorVariant)} if you need something you can hold at class-init time.
     */
    public Block block(ProcessorVariant variant) {
        return holder(variant).get();
    }

    public DeferredBlock<? extends Block> base() {
        return holder(ProcessorVariant.BASE);
    }

    public DeferredBlock<? extends Block> slab() {
        return holder(ProcessorVariant.SLAB);
    }

    public DeferredBlock<? extends Block> stairs() {
        return holder(ProcessorVariant.STAIRS);
    }

    public DeferredBlock<? extends Block> wall() {
        return holder(ProcessorVariant.WALL);
    }

    public DeferredBlock<? extends Block> fence() {
        return holder(ProcessorVariant.FENCE);
    }

    public DeferredBlock<? extends Block> button() {
        return holder(ProcessorVariant.BUTTON);
    }

    public DeferredBlock<? extends Block> trapdoor() {
        return holder(ProcessorVariant.TRAPDOOR);
    }

    public DeferredBlock<? extends Block> glass() {
        return holder(ProcessorVariant.GLASS);
    }
}
