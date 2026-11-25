package com.cyberday1.riftfall.gear;

import com.cyberday1.riftfall.loot.RiftArmsEntry;
import com.cyberday1.riftfall.skill.SkillService;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.TagsUpdatedEvent;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;

/**
 * Weapon rolls weighted toward the opener's class: {@link #CLASS_SHARE} of them are drawn from the class's families,
 * the rest are the roll the table would have made anyway. Families are data, {@code data/<ns>/class_loot/<class>.json},
 * cut from {@link GearPool#weapons()} (plus TaCZ guns through {@code rift_arms}). No class, no file, or no family with
 * anything in it: the open roll, untouched, and no randomness spent.
 */
public final class ClassLoot {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Ruled 2026-10-11: 65% class-appropriate, 35% anything. */
    public static final double CLASS_SHARE = 0.65D;

    public static final String DIRECTORY = "class_loot";

    /** TaCZ has one gun item and tells guns apart by data, so this id is the whole gun family's membership. */
    public static final String TACZ_GUN = "tacz:modern_kinetic_gun";

    /**
     * One family: a weapon is in it if it matches any of {@code slots}, {@code words}, {@code items} or {@code tags},
     * and none of {@code exclude}. {@code guns} makes it the TaCZ gun table instead.
     */
    public record Family(String name, int weight, Set<GearPool.Slot> slots, Set<String> words, Set<String> exclude,
                         Set<String> items, Set<String> tags, boolean guns) {

        /** {@code slot} is the weapon's bucket before the fold; {@code tagged} answers "is it in this tag". */
        public boolean matches(String id, @Nullable GearPool.Slot slot, Predicate<String> tagged) {
            if (guns) {
                return TACZ_GUN.equals(id);
            }
            Set<String> tokens = tokens(id);
            for (String word : exclude) {
                if (tokens.contains(word)) {
                    return false;
                }
            }
            if (items.contains(id) || (slot != null && slots.contains(slot))) {
                return true;
            }
            for (String word : words) {
                if (tokens.contains(word)) {
                    return true;
                }
            }
            for (String tag : tags) {
                if (tagged.test(tag)) {
                    return true;
                }
            }
            return false;
        }
    }

    public record Spec(String skillClass, List<Family> families) {

        /** Whether gun parts are this class's own: an attachment roll is then not a weapon roll. */
        public boolean ownsGuns() {
            return families.stream().anyMatch(Family::guns);
        }

        public boolean matches(String id, @Nullable GearPool.Slot slot, Predicate<String> tagged) {
            return families.stream().anyMatch(f -> f.matches(id, slot, tagged));
        }
    }

    public record Loaded(Map<String, Spec> specs, List<String> refusals) {
        public static final Loaded EMPTY = new Loaded(Map.of(), List.of());
    }

    private static volatile Loaded loaded = Loaded.EMPTY;

    /** Per class, each family's weapons. Built on first use, dropped on a reload of either input. */
    private static final Map<String, Map<Family, List<Item>>> CANDIDATES = new ConcurrentHashMap<>();

    /** Probe counters: classed weapon rolls, and how many of them the class picked. */
    private static final AtomicLong ROLLS = new AtomicLong();
    private static final AtomicLong PICKED = new AtomicLong();

    private ClassLoot() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener((TagsUpdatedEvent event) -> CANDIDATES.clear());
    }

    public static Loaded loaded() {
        return loaded;
    }

    /** Puts {@code parsed} in force as a reload does; the tests install the shipped files through it. */
    public static void install(Loaded parsed) {
        loaded = parsed;
        CANDIDATES.clear();
    }

    public static @Nullable Spec spec(@Nullable String skillClass) {
        return skillClass == null ? null : loaded.specs().get(skillClass);
    }

    // ---- the roll ----

    /**
     * A weapon roll for the context's player: the class's draw, or null to keep the open roll. Null without spending
     * randomness when there is no class to weight toward.
     */
    public static @Nullable List<ItemStack> weaponRoll(LootContext context, boolean boss) {
        Spec spec = spec(classOf(context.getParamOrNull(LootContextParams.THIS_ENTITY)));
        return spec == null ? null : classRoll(spec, context, boss);
    }

    /** {@code rift_arms}' hook: a gun is a weapon roll, an attachment is one only for a class without guns. */
    public static @Nullable List<ItemStack> armsRoll(LootContext context, RiftArms.Kind kind, boolean boss) {
        Spec spec = spec(classOf(context.getParamOrNull(LootContextParams.THIS_ENTITY)));
        if (spec == null || (kind == RiftArms.Kind.ATTACHMENT && spec.ownsGuns())) {
            return null;
        }
        return classRoll(spec, context, boss);
    }

    private static @Nullable List<ItemStack> classRoll(Spec spec, LootContext context, boolean boss) {
        ROLLS.incrementAndGet();
        RandomSource random = context.getRandom();
        if (!classPick(random.nextDouble())) {
            return null;
        }
        ServerLevel level = context.getLevel();
        Map<Family, List<Item>> candidates = candidates(spec, boss);
        List<Family> open = new ArrayList<>();
        for (Family family : spec.families()) {
            boolean stocked = family.guns()
                    ? RiftArmsEntry.available(RiftArms.Kind.GUN, boss, level) > 0
                    : !candidates.getOrDefault(family, List.of()).isEmpty();
            if (stocked) {
                open.add(family);
            }
        }
        Family family = pick(open, random);
        if (family == null) {
            return null;
        }
        List<ItemStack> drawn;
        if (family.guns()) {
            drawn = RiftArmsEntry.roll(RiftArms.Kind.GUN, boss, context);
        } else {
            List<Item> items = candidates.get(family);
            drawn = List.of(new ItemStack(items.get(random.nextInt(items.size()))));
        }
        if (drawn.isEmpty()) {
            return null;
        }
        PICKED.incrementAndGet();
        return drawn;
    }

    /** The 65/35 split on one uniform draw. */
    static boolean classPick(double uniform) {
        return uniform < CLASS_SHARE;
    }

    /** A family by weight, or null when none is stocked. */
    static @Nullable Family pick(List<Family> open, RandomSource random) {
        int total = open.stream().mapToInt(Family::weight).sum();
        if (total <= 0) {
            return null;
        }
        return pickAt(open, random.nextInt(total));
    }

    static @Nullable Family pickAt(List<Family> open, int roll) {
        int remaining = roll;
        for (Family family : open) {
            remaining -= family.weight();
            if (remaining < 0) {
                return family;
            }
        }
        return null;
    }

    /** The class of a classed player, or null for anyone else (a mob, the console, a player who has not chosen). */
    public static @Nullable String classOf(@Nullable Entity entity) {
        return entity instanceof ServerPlayer player ? SkillService.characterClass(player) : null;
    }

    // ---- the families against the live pool ----

    public static Map<Family, List<Item>> candidates(Spec spec) {
        return candidates(spec, false);
    }

    private static Map<Family, List<Item>> candidates(Spec spec, boolean boss) {
        Map<Family, List<Item>> all = CANDIDATES.computeIfAbsent(spec.skillClass(), ignored -> build(spec));
        if (boss) return all;
        Map<Family, List<Item>> barrels = new LinkedHashMap<>();
        all.forEach((family, items) -> barrels.put(family, items.stream().filter(BarrelTools::allowed).toList()));
        return barrels;
    }

    private static Map<Family, List<Item>> build(Spec spec) {
        Map<Family, List<Item>> out = new LinkedHashMap<>();
        Map<Item, GearPool.Slot> weapons = GearPool.weapons();
        for (Family family : spec.families()) {
            List<Item> items = new ArrayList<>();
            if (!family.guns()) {
                weapons.forEach((item, slot) -> {
                    // Unique weapons are boss drops only.
                    if (UniqueWeapons.barrelDrawable(item) && family.matches(BuiltInRegistries.ITEM.getKey(item).toString(), slot, tagged(item))) {
                        items.add(item);
                    }
                });
                items.sort((a, b) -> BuiltInRegistries.ITEM.getKey(a).compareTo(BuiltInRegistries.ITEM.getKey(b)));
            }
            out.put(family, List.copyOf(items));
        }
        return out;
    }

    /** Whether a dropped stack is in {@code spec}'s families: the probe's measure of the class share. */
    public static boolean inFamilies(Spec spec, ItemStack stack) {
        Item item = stack.getItem();
        return spec.matches(BuiltInRegistries.ITEM.getKey(item).toString(), GearPool.weaponSlot(item), tagged(item));
    }

    /** Whether a dropped stack is a weapon roll's output: a pool weapon or a TaCZ gun. */
    public static boolean isWeapon(ItemStack stack) {
        return GearPool.weaponSlot(stack.getItem()) != null
                || TACZ_GUN.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
    }

    private static Predicate<String> tagged(Item item) {
        Holder<Item> holder = BuiltInRegistries.ITEM.wrapAsHolder(item);
        return tag -> {
            ResourceLocation id = ResourceLocation.tryParse(tag);
            return id != null && holder.is(TagKey.create(Registries.ITEM, id));
        };
    }

    /** Probe use: {classed weapon rolls, of them picked by the class}, then zeroed. */
    public static long[] takeCounters() {
        return new long[]{ROLLS.getAndSet(0), PICKED.getAndSet(0)};
    }

    // ---- parsing ----

    /** The path split on {@code _ / .}: a word must be a whole token, so {@code sai} is not in {@code saint}. */
    static Set<String> tokens(String id) {
        String path = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
        Set<String> out = new TreeSet<>();
        for (String token : path.toLowerCase(Locale.ROOT).split("[_/.]")) {
            if (!token.isEmpty()) {
                out.add(token);
            }
        }
        return out;
    }

    public static Loaded parse(Map<ResourceLocation, JsonElement> files) {
        Map<String, Spec> specs = new TreeMap<>();
        List<String> refusals = new ArrayList<>();
        files.forEach((file, json) -> {
            try {
                Spec spec = parse(json.getAsJsonObject());
                if (specs.putIfAbsent(spec.skillClass(), spec) != null) {
                    refusals.add(file + ": a second file for " + spec.skillClass());
                }
            } catch (RuntimeException bad) {
                refusals.add(file + ": " + bad.getMessage());
            }
        });
        return new Loaded(Map.copyOf(specs), List.copyOf(refusals));
    }

    static Spec parse(JsonObject json) {
        String skillClass = json.get("class").getAsString();
        List<Family> families = new ArrayList<>();
        for (JsonElement element : json.getAsJsonArray("families")) {
            JsonObject f = element.getAsJsonObject();
            String name = f.get("name").getAsString();
            int weight = f.has("weight") ? f.get("weight").getAsInt() : 1;
            boolean guns = f.has("guns") && f.get("guns").getAsBoolean();
            Set<GearPool.Slot> slots = EnumSet.noneOf(GearPool.Slot.class);
            for (String slot : strings(f, "slots")) {
                GearPool.Slot parsed = GearPool.Slot.valueOf(slot.toUpperCase(Locale.ROOT));
                if (!GearPool.isWeapon(parsed)) {
                    throw new IllegalArgumentException(name + ": " + slot + " is not a weapon bucket");
                }
                slots.add(parsed);
            }
            Family family = new Family(name, weight, slots, strings(f, "words"), strings(f, "exclude"),
                    strings(f, "items"), strings(f, "tags"), guns);
            if (weight <= 0) {
                throw new IllegalArgumentException(name + ": weight " + weight);
            }
            if (!guns && slots.isEmpty() && family.words().isEmpty() && family.items().isEmpty()
                    && family.tags().isEmpty()) {
                throw new IllegalArgumentException(name + " matches nothing");
            }
            families.add(family);
        }
        if (families.isEmpty()) {
            throw new IllegalArgumentException(skillClass + " has no families");
        }
        return new Spec(skillClass, List.copyOf(families));
    }

    private static Set<String> strings(JsonObject json, String key) {
        Set<String> out = new TreeSet<>();
        if (json.has(key)) {
            json.getAsJsonArray(key).forEach(e -> out.add(e.getAsString().toLowerCase(Locale.ROOT)));
        }
        return Set.copyOf(out);
    }

    /** Reads {@code data/<ns>/class_loot/*.json} on every reload. */
    public static final class Loader extends SimpleJsonResourceReloadListener {

        public Loader() {
            super(new Gson(), DIRECTORY);
        }

        @Override
        protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
            Loaded parsed = parse(files);
            parsed.refusals().forEach(refusal -> LOGGER.error("Class loot refused {}", refusal));
            LOGGER.info("Class loot: {} classes, {} refused.", parsed.specs().size(), parsed.refusals().size());
            install(parsed);
        }
    }
}
