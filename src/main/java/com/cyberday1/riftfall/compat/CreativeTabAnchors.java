package com.cyberday1.riftfall.compat;

import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Predicate;

/**
 * Better Weaponry takes the vanilla bow, crossbow and shield out of the combat tab; Weapons Expanded then inserts
 * after the bow and crossbow, and an insert after a missing item throws, so the creative screen crashed. FML posts
 * each priority to every mod in order, so Better Weaponry's NORMAL turn ends by putting the missing anchors back at
 * their vanilla places, and Weapons Expanded's LOWEST takes them out again: the removal stands, the inserts land.
 */
public final class CreativeTabAnchors {
    public static final String MOD = "weaponsexpanded";
    public static final String REMOVER = "better_weaponry";
    /** {@code -Driftfall.tab_anchors=off} leaves the tab alone, the probe's before arm. */
    public static final boolean ENABLED = !"off".equals(System.getProperty("riftfall.tab_anchors"));

    /** The vanilla combat items Weapons Expanded 1.9.3 inserts after or before. */
    static final List<Item> ANCHORS = List.of(Items.BOW, Items.CROSSBOW, Items.SPECTRAL_ARROW, Items.WOODEN_SWORD,
            Items.NETHERITE_SWORD, Items.WOODEN_AXE, Items.NETHERITE_AXE);
    /** Vanilla's combat order around them, to put an anchor back where it was. */
    static final List<Item> VANILLA_ORDER = List.of(Items.WOODEN_SWORD, Items.STONE_SWORD, Items.IRON_SWORD,
            Items.GOLDEN_SWORD, Items.DIAMOND_SWORD, Items.NETHERITE_SWORD, Items.WOODEN_AXE, Items.STONE_AXE,
            Items.IRON_AXE, Items.GOLDEN_AXE, Items.DIAMOND_AXE, Items.NETHERITE_AXE, Items.TRIDENT, Items.MACE,
            Items.SHIELD, Items.SNOWBALL, Items.EGG, Items.WIND_CHARGE, Items.BOW, Items.CROSSBOW, Items.ARROW,
            Items.SPECTRAL_ARROW);

    private static final Map<BuildCreativeModeTabContentsEvent, List<Restored>> RESTORED =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static int restoredTotal;
    private static int removedTotal;
    private static int combatBuilds;
    private static boolean listening;

    record Restored(Item item, CreativeModeTab.TabVisibility visibility) {
    }

    private CreativeTabAnchors() {
    }

    public static void register(IEventBus riftfall) {
        if (!ENABLED) return;
        // After every constructor, so ours follows Better Weaponry's own NORMAL listener on its bus.
        riftfall.addListener(FMLCommonSetupEvent.class, e -> e.enqueueWork(CreativeTabAnchors::listen));
    }

    private static void listen() {
        IEventBus remover = bus(REMOVER);
        IEventBus inserter = bus(MOD);
        if (remover == null || inserter == null) return;
        remover.addListener(EventPriority.NORMAL, BuildCreativeModeTabContentsEvent.class, CreativeTabAnchors::before);
        inserter.addListener(EventPriority.LOWEST, BuildCreativeModeTabContentsEvent.class, CreativeTabAnchors::after);
        listening = true;
    }

    private static IEventBus bus(String mod) {
        return ModList.get().getModContainerById(mod).map(ModContainer::getEventBus).orElse(null);
    }

    static void before(BuildCreativeModeTabContentsEvent event) {
        if (!CreativeModeTabs.COMBAT.equals(event.getTabKey())) return;
        combatBuilds++;
        List<Restored> added = new ArrayList<>();
        restore(event, CreativeModeTab.TabVisibility.PARENT_TAB_ONLY, s -> event.getParentEntries().contains(s), added);
        restore(event, CreativeModeTab.TabVisibility.SEARCH_TAB_ONLY, s -> event.getSearchEntries().contains(s), added);
        if (!added.isEmpty()) RESTORED.put(event, added);
        restoredTotal += added.size();
    }

    static void after(BuildCreativeModeTabContentsEvent event) {
        List<Restored> added = RESTORED.remove(event);
        if (added == null) return;
        for (Restored r : added) {
            event.remove(r.item().getDefaultInstance(), r.visibility());
            removedTotal++;
        }
    }

    static void restore(BuildCreativeModeTabContentsEvent event, CreativeModeTab.TabVisibility visibility,
                        Predicate<ItemStack> present, List<Restored> added) {
        for (Item anchor : ANCHORS) {
            ItemStack stack = anchor.getDefaultInstance();
            if (present.test(stack)) continue;
            Placement at = place(anchor, item -> present.test(item.getDefaultInstance()));
            switch (at.how()) {
                case AFTER -> event.insertAfter(at.neighbour().getDefaultInstance(), stack, visibility);
                case BEFORE -> event.insertBefore(at.neighbour().getDefaultInstance(), stack, visibility);
                case END -> event.accept(stack, visibility);
            }
            added.add(new Restored(anchor, visibility));
        }
    }

    enum How { AFTER, BEFORE, END }

    record Placement(How how, Item neighbour) {
    }

    /** After the nearest earlier vanilla item still listed, else before the nearest later one, else at the end. */
    static Placement place(Item anchor, Predicate<Item> listed) {
        int i = VANILLA_ORDER.indexOf(anchor);
        for (int j = i - 1; j >= 0; j--) {
            if (listed.test(VANILLA_ORDER.get(j))) return new Placement(How.AFTER, VANILLA_ORDER.get(j));
        }
        for (int j = i + 1; j < VANILLA_ORDER.size(); j++) {
            if (listed.test(VANILLA_ORDER.get(j))) return new Placement(How.BEFORE, VANILLA_ORDER.get(j));
        }
        return new Placement(How.END, null);
    }

    public static int restoredTotal() {
        return restoredTotal;
    }

    public static int removedTotal() {
        return removedTotal;
    }

    public static boolean listening() {
        return listening;
    }

    public static int combatBuilds() {
        return combatBuilds;
    }
}
