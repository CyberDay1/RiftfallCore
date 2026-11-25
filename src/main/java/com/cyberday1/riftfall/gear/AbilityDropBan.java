package com.cyberday1.riftfall.gear;

import com.cyberday1.riftfall.RiftfallCore;
import com.cyberday1.riftfall.rift.RiftLevels;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.JsonOps;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import org.slf4j.Logger;

import java.util.Map;
import java.util.TreeMap;
import java.util.function.Predicate;

/**
 * Strips castable third-party abilities from what a mob drops when it dies.
 *
 * <p>
 * Ruled 2026-10-06: only Riftfall-granted skills are player-accessible, from anywhere. Rift mobs
 * casting Iron's spells is intended; dropping the scroll, spell book or imbued weapon is the leak,
 * in a rift or out of one (a vanilla evoker's spell book included). Runs on
 * {@code MixinLootTable}, after every loot modifier, because Iron's bosses spawn their drops with
 * {@code ServerLevel#addFreshEntity} and {@code LivingDropsEvent} never sees them.
 */
public final class AbilityDropBan {

    /** Where the ban applies. EVERYWHERE is the 2026-10-06 ruling: "we are the only source of abilities". */
    public enum Scope { RIFTS, EVERYWHERE }

    public static final Scope SCOPE = Scope.EVERYWHERE;

    public static final TagKey<Item> GRANTS_ABILITY =
            TagKey.create(Registries.ITEM, RiftfallCore.id("grants_ability"));

    static final ResourceLocation SPELL_CONTAINER =
            ResourceLocation.fromNamespaceAndPath("irons_spellbooks", "spell_container");

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final Map<String, Integer> STRIPPED = new TreeMap<>();
    private static boolean warnedUnreadable;
    private static final Predicate<Entity> NOBODY = entity -> false;
    private static volatile Predicate<Entity> probeExempt = NOBODY;

    private AbilityDropBan() {
    }

    public static boolean inScope(ServerLevel level) {
        return SCOPE == Scope.EVERYWHERE || RiftLevels.isRiftWorld(level);
    }

    /** A death roll: a living, non-player THIS_ENTITY and a damage source. Barrels and blocks are not. */
    static boolean isDeathLoot(LootContext context) {
        Entity dying = context.getParamOrNull(LootContextParams.THIS_ENTITY);
        return dying instanceof LivingEntity && !(dying instanceof Player)
                && context.hasParam(LootContextParams.DAMAGE_SOURCE);
    }

    public static void strip(ObjectArrayList<ItemStack> loot, LootContext context) {
        ServerLevel level = context.getLevel();
        if (!inScope(level) || !isDeathLoot(context)
                || probeExempt.test(context.getParamOrNull(LootContextParams.THIS_ENTITY))) {
            return;
        }
        loot.removeIf(stack -> {
            if (!grantsAbility(stack, level)) {
                return false;
            }
            synchronized (STRIPPED) {
                STRIPPED.merge(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(), stack.getCount(), Integer::sum);
            }
            return true;
        });
    }

    public static boolean grantsAbility(ItemStack stack, ServerLevel level) {
        if (stack.isEmpty()) {
            return false;
        }
        if (stack.is(GRANTS_ABILITY)) {
            return true;
        }
        int spells = spellCount(stack, level);
        return spells > 0 || spells == -2;
    }

    /** Spells in the stack's Iron's container: -1 for no container, -2 if it would not encode. */
    public static int spellCount(ItemStack stack, ServerLevel level) {
        DataComponentType<?> type = BuiltInRegistries.DATA_COMPONENT_TYPE.get(SPELL_CONTAINER);
        if (type == null || !stack.has(type)) {
            return -1;
        }
        JsonElement encoded = encode(type, stack, level);
        int spells = encoded == null ? -2 : spellCount(encoded);
        if (spells == -2 && !warnedUnreadable) {
            warnedUnreadable = true;
            LOGGER.warn("Ability drop ban: {} carries a spell container that will not encode; treating it as an ability.",
                    BuiltInRegistries.ITEM.getKey(stack.getItem()));
        }
        return spells;
    }

    /** Iron's SpellContainer codec writes its spells as a {@code data} list. */
    static int spellCount(JsonElement encoded) {
        if (!(encoded instanceof JsonObject object)) {
            return -2;
        }
        JsonElement data = object.get("data");
        if (data == null) {
            return 0;
        }
        return data.isJsonArray() ? data.getAsJsonArray().size() : -2;
    }

    private static <T> JsonElement encode(DataComponentType<T> type, ItemStack stack, ServerLevel level) {
        if (type.codec() == null) {
            return null;
        }
        T value = stack.get(type);
        return type.codec().encodeStart(level.registryAccess().createSerializationContext(JsonOps.INSTANCE), value)
                .result().orElse(null);
    }

    /** What the ban has removed so far, by item id. Read by the probe. */
    public static Map<String, Integer> stripped() {
        synchronized (STRIPPED) {
            return Map.copyOf(STRIPPED);
        }
    }

    /**
     * Probe use: deaths of entities {@code exempt} accepts keep their loot, so the rig's control arm
     * can show what the ban would have removed. Null puts it back to nobody. No command reaches it.
     */
    public static void exemptForProbe(Predicate<Entity> exempt) {
        probeExempt = exempt == null ? NOBODY : exempt;
    }

    public static void forgetStripped() {
        synchronized (STRIPPED) {
            STRIPPED.clear();
        }
    }
}
