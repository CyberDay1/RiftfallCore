package com.cyberday1.riftfall.gear;

import com.cyberday1.riftfall.block.barrel.BarrelTier;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Empties the spells out of what a rift barrel hands over, keeping the item.
 *
 * <p>
 * Ruled 2026-10-06, "we are the only source of abilities". Iron's presets its spell weapons and
 * unique books in the {@code ItemStack} constructor, so {@code riftfall:gear} draws them loaded.
 * Separate from {@link AbilityDropBan}, which deliberately leaves barrels alone; this one touches
 * nothing but the four barrel tables. A stack it cannot empty is removed.
 */
public final class BarrelAbilityStrip {

    static final Set<ResourceLocation> BARREL_TABLES = Stream.of(BarrelTier.ALL)
            .map(tier -> tier.lootTable().location()).collect(Collectors.toUnmodifiableSet());

    private static final Map<String, Integer> EMPTIED = new TreeMap<>();
    private static final Map<String, Integer> REFUSED = new TreeMap<>();
    private static volatile boolean probeExempt;

    private BarrelAbilityStrip() {
    }

    /** Run last on every loot roll; acts only when {@code table} is a barrel tier's. */
    public static void apply(ObjectArrayList<ItemStack> loot, @Nullable ResourceLocation table, LootContext context) {
        // Null-guarded: an immutable set's contains(null) throws, and a table built in code has no id.
        if (table == null || !BARREL_TABLES.contains(table) || probeExempt) {
            return;
        }
        DataComponentType<?> type = BuiltInRegistries.DATA_COMPONENT_TYPE.get(AbilityDropBan.SPELL_CONTAINER);
        apply(loot, type, context.getLevel().registryAccess().createSerializationContext(JsonOps.INSTANCE));
    }

    static void apply(ObjectArrayList<ItemStack> loot, @Nullable DataComponentType<?> type, DynamicOps<JsonElement> ops) {
        loot.removeIf(stack -> {
            Outcome outcome = disarm(stack, type, ops);
            if (outcome == Outcome.KEPT) {
                return false;
            }
            String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            Map<String, Integer> tally = outcome == Outcome.EMPTIED ? EMPTIED : REFUSED;
            synchronized (EMPTIED) {
                tally.merge(id, stack.getCount(), Integer::sum);
            }
            return outcome == Outcome.REFUSED;
        });
    }

    enum Outcome { KEPT, EMPTIED, REFUSED }

    /** Empties {@code stack} in place if it carries spells; REFUSED means it must not be handed out. */
    static Outcome disarm(ItemStack stack, @Nullable DataComponentType<?> type, DynamicOps<JsonElement> ops) {
        if (stack.isEmpty()) {
            return Outcome.KEPT;
        }
        if (type == null || !stack.has(type)) {
            // Named as an ability, with no container to empty: nothing here can make it safe.
            return stack.is(AbilityDropBan.GRANTS_ABILITY) ? Outcome.REFUSED : Outcome.KEPT;
        }
        int spells = spells(stack, type, ops);
        if (spells == 0) {
            return Outcome.KEPT;
        }
        if (spells > 0 && empty(stack, type, ops) && spells(stack, type, ops) == 0) {
            return Outcome.EMPTIED;
        }
        return Outcome.REFUSED;
    }

    private static <T> int spells(ItemStack stack, DataComponentType<T> type, DynamicOps<JsonElement> ops) {
        JsonElement encoded = encode(stack, type, ops);
        return encoded == null ? -2 : AbilityDropBan.spellCount(encoded);
    }

    /** Re-decodes the container with an empty {@code data} list: same slots and flags, no spells. */
    private static <T> boolean empty(ItemStack stack, DataComponentType<T> type, DynamicOps<JsonElement> ops) {
        if (!(encode(stack, type, ops) instanceof JsonObject cached)) {
            return false;
        }
        // A copy: Iron's type caches its encoding, and editing the cached object empties every later read.
        JsonObject encoded = cached.deepCopy();
        encoded.add("data", new JsonArray());
        Optional<T> emptied = type.codec().parse(ops, encoded).result();
        emptied.ifPresent(value -> stack.set(type, value));
        return emptied.isPresent();
    }

    private static <T> @Nullable JsonElement encode(ItemStack stack, DataComponentType<T> type, DynamicOps<JsonElement> ops) {
        if (type.codec() == null) {
            return null;
        }
        return type.codec().encodeStart(ops, stack.get(type)).result().orElse(null);
    }

    /** What has been emptied so far, by item id. Read by the probe. */
    public static Map<String, Integer> emptied() {
        synchronized (EMPTIED) {
            return Map.copyOf(EMPTIED);
        }
    }

    /** What has been removed because it could not be emptied, by item id. */
    public static Map<String, Integer> refused() {
        synchronized (EMPTIED) {
            return Map.copyOf(REFUSED);
        }
    }

    public static void forget() {
        synchronized (EMPTIED) {
            EMPTIED.clear();
            REFUSED.clear();
        }
    }

    /** Probe use only, for the rig's control arm. No command reaches it. */
    public static void exemptForProbe(boolean exempt) {
        probeExempt = exempt;
    }
}
