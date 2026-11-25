package com.cyberday1.riftfall.gear;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Predicate;

/**
 * One class's starter kit as data: {@code data/<ns>/starter_kit/<class>.json}, a list of entries, each
 * an item for one place on the player with a vanilla fallback. Pure: parsing and choosing between an
 * entry's item and its fallback need no game, so the tests can hold every shipped file to the rules.
 */
public record StarterKit(String skillClass, List<Entry> entries, List<Entry> shared) {

    /** The data directory the loader reads, under every namespace. */
    public static final String DIRECTORY = "starter_kit";

    /** The file whose entries every class kit also gives (ruled 2026-10-11); never a class of its own. */
    public static final String COMMON = "common";

    public StarterKit(String skillClass, List<Entry> entries) {
        this(skillClass, entries, List.of());
    }

    /** The class's own main-hand item, the class weapon a kit starts with; null if it names none. */
    public @Nullable ResourceLocation mainhand() {
        return entries.stream().filter(e -> e.place() == Place.MAINHAND).map(Entry::item).findFirst().orElse(null);
    }

    /** The class's own entries, then the common set's. */
    public List<Entry> all() {
        List<Entry> all = new ArrayList<>(entries);
        all.addAll(shared);
        return all;
    }

    /** Where an entry goes. The six worn/held places are gear and are stamped; inventory is not. */
    public enum Place {
        HEAD, CHEST, LEGS, FEET, MAINHAND, OFFHAND, INVENTORY;

        public boolean gear() {
            return this != INVENTORY;
        }

        static @Nullable Place parse(String name) {
            for (Place place : values()) {
                if (place.name().toLowerCase(Locale.ROOT).equals(name)) {
                    return place;
                }
            }
            return null;
        }
    }

    /**
     * One stack. {@code components} is the item-stack component patch as JSON (the same shape as
     * {@code /give}'s), decoded at grant time against the live registries, for the entry's own item;
     * {@code fallbackComponents} is the same for the fallback.
     *
     * <p>
     * {@code ownStats} lets a worn or held piece through that the rifts do not own, at its own mod's
     * numbers and without a floor. It exists for the Gunner's TaCZ sidearm, which no rift drops and the
     * floors cannot rewrite; every other gear entry is refused for its fallback unless the rifts own it.
     *
     * <p>
     * {@code install} names upgrades to put inside the item (a backpack's upgrade slots); one that cannot
     * be installed is given loose.
     */
    public record Entry(Place place, ResourceLocation item, @Nullable ResourceLocation fallback, int count,
                        @Nullable JsonObject components, @Nullable JsonObject fallbackComponents,
                        boolean ownStats, List<ResourceLocation> install) {

        public Entry {
            install = install == null ? List.of() : List.copyOf(install);
        }

        public Entry(Place place, ResourceLocation item, @Nullable ResourceLocation fallback, int count,
                @Nullable JsonObject components, @Nullable JsonObject fallbackComponents, boolean ownStats) {
            this(place, item, fallback, count, components, fallbackComponents, ownStats, List.of());
        }

        public Entry(Place place, ResourceLocation item, @Nullable ResourceLocation fallback, int count,
                @Nullable JsonObject components) {
            this(place, item, fallback, count, components, null, false);
        }

        /** The component patch for whichever id was chosen. */
        public @Nullable JsonObject componentsFor(ResourceLocation id) {
            return id.equals(item) ? components : fallbackComponents;
        }

        /**
         * The id this entry gives: its own item when {@code usable} accepts it, else the fallback when
         * {@code fallbackUsable} accepts that, else nothing.
         */
        public @Nullable ResourceLocation choose(Predicate<ResourceLocation> usable,
                Predicate<ResourceLocation> fallbackUsable) {
            if (usable.test(item)) {
                return item;
            }
            return fallback != null && fallbackUsable.test(fallback) ? fallback : null;
        }
    }

    /** Every kit that parsed, by class id, and a line per refused file or entry. */
    public record Loaded(Map<String, StarterKit> kits, List<String> refusals) {
        public static final Loaded EMPTY = new Loaded(Map.of(), List.of());
    }

    static final int MAX_COUNT = 64;

    /**
     * Parses every file. A file whose class is not a word, or whose {@code items} is missing, is
     * refused whole; a bad entry is refused alone and the rest of its kit still loads. The
     * {@link #COMMON} file is not a kit: its entries ride along in every class kit's {@code shared}.
     */
    public static Loaded parse(Map<ResourceLocation, JsonElement> files) {
        Map<String, StarterKit> kits = new TreeMap<>();
        List<String> refusals = new ArrayList<>();
        for (Map.Entry<ResourceLocation, JsonElement> file : files.entrySet()) {
            String name = file.getKey().toString();
            if (!file.getValue().isJsonObject()) {
                refusals.add(name + ": not an object");
                continue;
            }
            JsonObject root = file.getValue().getAsJsonObject();
            String skillClass = root.has("class") ? root.get("class").getAsString() : file.getKey().getPath();
            if (!skillClass.matches("[a-z0-9_]+")) {
                refusals.add(name + ": class '" + skillClass + "' is not a lower-case id");
                continue;
            }
            if (!root.has("items") || !root.get("items").isJsonArray()) {
                refusals.add(name + ": no items array");
                continue;
            }
            List<Entry> entries = new ArrayList<>();
            int index = 0;
            for (JsonElement element : root.getAsJsonArray("items")) {
                String where = name + " item " + index++;
                try {
                    Entry entry = entry(element.getAsJsonObject());
                    if (entry.place() != Place.INVENTORY
                            && entries.stream().anyMatch(e -> e.place() == entry.place())) {
                        refusals.add(where + ": a second " + entry.place() + " entry");
                        continue;
                    }
                    entries.add(entry);
                } catch (RuntimeException bad) {
                    refusals.add(where + ": " + bad.getMessage());
                }
            }
            if (kits.containsKey(skillClass)) {
                refusals.add(name + ": a second kit for " + skillClass + ", ignored");
                continue;
            }
            kits.put(skillClass, new StarterKit(skillClass, Collections.unmodifiableList(entries)));
        }
        StarterKit common = kits.remove(COMMON);
        if (common != null) {
            if (common.entries().stream().anyMatch(e -> e.place() != Place.INVENTORY)) {
                refusals.add(COMMON + ": the common set goes to the inventory, never a worn or held place");
            }
            List<Entry> shared = common.entries().stream().filter(e -> e.place() == Place.INVENTORY).toList();
            kits.replaceAll((id, kit) -> new StarterKit(id, kit.entries(), shared));
        }
        return new Loaded(Collections.unmodifiableMap(kits), List.copyOf(refusals));
    }

    private static Entry entry(JsonObject json) {
        String slot = json.has("slot") ? json.get("slot").getAsString() : "inventory";
        Place place = Place.parse(slot);
        if (place == null) {
            throw new IllegalArgumentException("unknown slot '" + slot + "'");
        }
        if (!json.has("item")) {
            throw new IllegalArgumentException("no item");
        }
        ResourceLocation item = ResourceLocation.parse(json.get("item").getAsString());
        ResourceLocation fallback = json.has("fallback")
                ? ResourceLocation.parse(json.get("fallback").getAsString()) : null;
        int count = json.has("count") ? json.get("count").getAsInt() : 1;
        if (count < 1 || count > MAX_COUNT) {
            throw new IllegalArgumentException("count " + count + " is outside 1.." + MAX_COUNT);
        }
        if (place.gear() && count != 1) {
            throw new IllegalArgumentException(place + " holds one item, not " + count);
        }
        JsonObject components = json.has("components") ? json.getAsJsonObject("components") : null;
        JsonObject fallbackComponents = json.has("fallback_components")
                ? json.getAsJsonObject("fallback_components") : null;
        boolean ownStats = json.has("own_stats") && json.get("own_stats").getAsBoolean();
        if (ownStats && !place.gear()) {
            throw new IllegalArgumentException("own_stats only means something on a worn or held piece");
        }
        List<ResourceLocation> install = new ArrayList<>();
        if (json.has("install")) {
            json.getAsJsonArray("install").forEach(id -> install.add(ResourceLocation.parse(id.getAsString())));
        }
        return new Entry(place, item, fallback, count, components, fallbackComponents, ownStats, install);
    }
}
