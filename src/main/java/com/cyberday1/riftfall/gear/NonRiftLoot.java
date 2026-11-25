package com.cyberday1.riftfall.gear;

import com.cyberday1.riftfall.RiftfallCore;
import com.cyberday1.riftfall.init.RiftfallDataComponents;
import com.cyberday1.riftfall.rift.RiftLevels;
import com.cyberday1.riftfall.tool.BarrelLootBonuses;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Predicate;

/**
 * Loot rolled outside a rift loses modded gear, every Crystalcraft item, and Apotheosis affixed items and gems no
 * rift stamped, whichever mod put them in the table; mob death drops lose the affixed items and gems too.
 * Block drops are exempt: they return what was placed. {@code -Driftfall.non_rift_loot=off} turns it off.
 */
@EventBusSubscriber(modid = RiftfallCore.MODID)
public final class NonRiftLoot {
    public static final String PROPERTY = "riftfall.non_rift_loot";
    public static final boolean ENFORCED = enforced(System.getProperty(PROPERTY));

    /** Mods whose every item is rift loot: Crystalcraft is the rift palette. */
    public static final Set<String> RIFT_NAMESPACES = Set.of("crystalcraft_unlimited_java");

    private static final ResourceLocation GEM = ResourceLocation.fromNamespaceAndPath("apotheosis", "gem");

    private static final ThreadLocal<Boolean> SUSPENDED = ThreadLocal.withInitial(() -> false);
    private static final Map<String, LongAdder> STRIPPED = new ConcurrentHashMap<>();

    private NonRiftLoot() {
    }

    static boolean enforced(String property) {
        return property == null || !property.trim().equalsIgnoreCase("off");
    }

    /** The rule on one stack: from a rift-palette mod, or gear the rifts own. */
    public static boolean riftOwned(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        if (RIFT_NAMESPACES.contains(BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace())) {
            return true;
        }
        return GearPool.owned(stack.getItem().builtInRegistryHolder()) != null || unearnedApotheosis(stack);
    }

    /** An Apotheosis gem, or an affixed item, that no rift stamped with its tier. */
    public static boolean unearnedApotheosis(ItemStack stack) {
        if (stack.isEmpty() || stack.has(RiftfallDataComponents.RIFT_TIER.get())) {
            return false;
        }
        return GEM.equals(BuiltInRegistries.ITEM.getKey(stack.getItem())) || RiftLootRarity.affixed(stack);
    }

    /** Backstop for mobs dressed in affixed gear outside a rift (Apotheosis' own spawns). Players keep theirs. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void deathDrops(LivingDropsEvent event) {
        if (!ENFORCED || !(event.getEntity() instanceof Mob mob) || !(mob.level() instanceof ServerLevel level)
                || RiftLevels.isRiftWorld(level)) {
            return;
        }
        event.getDrops().removeIf(drop -> {
            if (!unearnedApotheosis(drop.getItem())) {
                return false;
            }
            count(drop.getItem());
            return true;
        });
    }

    private static void count(ItemStack stack) {
        STRIPPED.computeIfAbsent(BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace(),
                key -> new LongAdder()).add(stack.getCount());
    }

    public static void strip(List<ItemStack> loot, LootContext context) {
        strip(loot, context, NonRiftLoot::riftOwned);
    }

    static void strip(List<ItemStack> loot, LootContext context, Predicate<ItemStack> riftOwned) {
        if (!ENFORCED || SUSPENDED.get() || context.hasParam(LootContextParams.BLOCK_STATE)
                || RiftLevels.isRiftWorld(context.getLevel()) || BarrelLootBonuses.active()) {
            return;
        }
        loot.removeIf(stack -> {
            if (!riftOwned.test(stack)) {
                return false;
            }
            count(stack);
            return true;
        });
    }

    /** Runs {@code roll} with the strip off on this thread: the probe's "before" arm. */
    public static <T> T suspended(java.util.function.Supplier<T> roll) {
        boolean previous = SUSPENDED.get();
        SUSPENDED.set(true);
        try {
            return roll.get();
        } finally {
            SUSPENDED.set(previous);
        }
    }

    /** Items removed since the server started, by mod. Read by the probe. */
    public static Map<String, Long> stripped() {
        Map<String, Long> out = new TreeMap<>();
        STRIPPED.forEach((namespace, count) -> out.put(namespace, count.sum()));
        return out;
    }
}
