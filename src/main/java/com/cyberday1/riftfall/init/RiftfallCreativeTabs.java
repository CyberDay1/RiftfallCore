package com.cyberday1.riftfall.init;

import com.cyberday1.riftfall.RiftfallCore;
import com.cyberday1.riftfall.block.marker.MarkerKind;
import com.cyberday1.riftfall.block.processor.ProcessorSlot;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Creative tabs for Riftfall Core. */
public final class RiftfallCreativeTabs {

    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, RiftfallCore.MODID);

    /**
     * The eight rift markers, then the four POI anchors. Their own drawer rather than a corner of
     * the placeholder tab: a placeholder is a material the theme processor will replace, a marker is
     * a fixture that survives into the finished room, and mixing them invites a builder to reach for
     * the wrong one.
     *
     * <p>
     * The anchors come after the markers because they are the odd ones out even here — they survive
     * into nothing, and are the only blocks in the mod a player is guaranteed never to see.
     *
     * <p>
     * Appended after both: the one-way wall, which is deliberately not a {@link MarkerKind} — a
     * marker kind is read and taken back out during generation and this one is permanent — but is a
     * fixture that survives into the finished room by exactly the description above, so this is the
     * drawer a room author will go looking in.
     *
     * <p>
     * ⚠ <strong>Declared first on purpose, and the order is load-bearing.</strong> Spawn eggs
     * declare no ordering of their own, so the sort hands them an automatic edge to whichever modded
     * tab registers first — and a tab holding that edge cannot also ask to come before spawn eggs
     * without closing a loop and killing mod loading outright. This tab wants to sit after vanilla
     * anyway, so it takes that edge harmlessly and leaves {@link #PROCESSOR_BLOCKS} free to reach
     * forward. Moving these two declarations past each other reintroduces the cycle.
     */
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MARKER_BLOCKS =
            TABS.register("marker_blocks", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.riftfall.marker_blocks"))
                    .icon(() -> new ItemStack(RiftfallBlocks.MARKERS.get(MarkerKind.RIFT_CHEST).get()))
                    .withTabsBefore(CreativeModeTabs.SPAWN_EGGS)
                    .displayItems((params, output) -> {
                        RiftfallItems.MARKER_ITEMS.forEach(item -> output.accept(item.get()));
                        RiftfallItems.POI_ANCHOR_ITEMS.forEach(item -> output.accept(item.get()));
                        RiftfallItems.NPC_SPAWN_MARKER_ITEMS.forEach(item -> output.accept(item.get()));
                        output.accept(RiftfallItems.ONE_WAY_GLASS.get());
                        output.accept(RiftfallItems.NOTE_MARKER.get());
                        output.accept(RiftfallItems.GEM_SEAM.get());
                        output.accept(RiftfallItems.RIFTSTONE.get());
                    })
                    .build());

    /**
     * All 180 placeholders in one tab. They are build-time tools rather than survival content, so
     * they get their own drawer instead of being scattered through the vanilla building-blocks
     * tab; entries come out grouped by material slot, then by shape.
     *
     * <p>
     * Pinned between the last two vanilla tabs so it lands on the creative menu's first page. A page
     * holds exactly ten tabs and vanilla fills all ten, so this necessarily costs the tenth its slot:
     * spawn eggs move to page two. That is the whole trade — there is no eleventh slot to take.
     */
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> PROCESSOR_BLOCKS =
            TABS.register("processor_blocks", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.riftfall.processor_blocks"))
                    .icon(() -> new ItemStack(
                            RiftfallBlocks.PROCESSOR_FAMILIES.get(ProcessorSlot.SLOT_1.number() - 1).base().get()))
                    // Both ends named: one edge alone leaves the sort free to float us to the front.
                    .withTabsBefore(CreativeModeTabs.INGREDIENTS)
                    .withTabsAfter(CreativeModeTabs.SPAWN_EGGS)
                    .displayItems((params, output) -> RiftfallItems.PROCESSOR_ITEMS
                            .forEach(item -> output.accept(item.get())))
                    .build());

    /**
     * The things a player is meant to hold: the forge, the shards it eats, the key it makes, and the
     * anchor that gets them back out of what the key opened.
     *
     * <p>
     * Neither of the other two tabs would take them. Those are both authoring drawers — a
     * placeholder is a material the theme processor replaces and a marker is a fixture a room author
     * places — and these are neither. They are what survival is supposed to see.
     *
     * <p>
     * Keys are offered at the first tier of each look band and at the top look, not at every tier:
     * that is enough to see the ladder, and the forge is where a number gets chosen. The shard is in
     * the tab for the same reason it is {@code /give}-able: nothing drops it yet.
     */
    /** The first tier of each look band (copper .. void), then the top look. */
    private static final int[] SHOWN_KEY_TIERS = {1, 4, 7, 10, 13, 16, 19, 22};

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> RIFT =
            TABS.register("rift", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.riftfall.rift"))
                    .icon(() -> new ItemStack(RiftfallItems.SHATTERED_WORLD_SHARD.get()))
                    .withTabsBefore(MARKER_BLOCKS.getKey())
                    .displayItems((params, output) -> {
                        output.accept(RiftfallBlocks.KEY_FORGE.get());
                        output.accept(RiftfallItems.RIFT_SHARD.get());
                        output.accept(RiftfallItems.SHATTERED_WORLD_SHARD.get());
                        for (int tier : SHOWN_KEY_TIERS) {
                            ItemStack key = new ItemStack(RiftfallItems.RIFT_KEY.get());
                            key.set(RiftfallDataComponents.RIFT_TIER.get(), tier);
                            output.accept(key);
                        }
                        output.accept(RiftfallItems.RIFT_ANCHOR.get());
                        output.accept(RiftfallItems.RIFT_FLARE.get());
                        output.accept(RiftfallItems.LOOTING_PICK.get());
                        output.accept(RiftfallItems.GEM_GRIT.get());
                        output.accept(RiftfallItems.SOCKET_CHISEL.get());
                        output.accept(RiftfallItems.FACETED_CRYSTAL.get());
                        output.accept(RiftfallItems.TEMPERING_STOCK.get());
                        for (var grade : com.cyberday1.riftfall.tool.RuneGrade.values()) output.accept(grade.item());
                        output.accept(RiftfallItems.WORLD_BREAKER.get());
                    })
                    .build());

    private RiftfallCreativeTabs() {
    }

    public static void register(IEventBus modEventBus) {
        TABS.register(modEventBus);
    }
}
