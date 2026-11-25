package com.cyberday1.riftfall.home.npc.depth.commission;

import com.cyberday1.riftfall.RiftfallCore;
import com.cyberday1.riftfall.block.marker.NpcRole;
import com.cyberday1.riftfall.init.RiftfallRegistries;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.tags.TagKey;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.Item;

import java.util.*;

/**
 * Every resident's request pool, from {@code data/<ns>/riftfall/commission/<role>/*.json}, and the generator that
 * posts from it. The generator never posts a request nothing can fulfil: the theme must be registered, the tag
 * must hold something, and a delivery may name only {@code #riftfall:commission_deliverable} items.
 */
public final class CommissionPool {
    public record Entry(ResourceLocation id, CommissionDef def) {}

    /** Items a delivery request may ask for: rift-only material, never anything craftable. */
    public static final TagKey<Item> DELIVERABLE = TagKey.create(Registries.ITEM, RiftfallCore.id("commission_deliverable"));

    private static volatile Map<NpcRole, List<Entry>> current = Map.of();

    private CommissionPool() {}

    public static List<Entry> of(NpcRole role) { return current.getOrDefault(role, List.of()); }
    public static Optional<Entry> find(ResourceLocation id) {
        return current.values().stream().flatMap(List::stream).filter(e -> e.id().equals(id)).findFirst();
    }
    public static Map<NpcRole, List<Entry>> snapshot() { return current; }
    /** Replaces the pool; the reload listener and the probe only. */
    public static void install(Map<NpcRole, List<Entry>> pool) {
        var copy = new EnumMap<NpcRole, List<Entry>>(NpcRole.class);
        pool.forEach((role, list) -> copy.put(role, List.copyOf(list)));
        current = Collections.unmodifiableMap(copy);
    }

    /** Turns a definition into a request on a live server, or empty when it cannot be fulfilled now. */
    @FunctionalInterface public interface Resolver { Optional<CommissionBoard.Request> resolve(Entry entry, Random random); }

    /**
     * Weighted, without repeats, posted at {@code homeTier}. Retrain requests only when {@code retrainOpen}.
     * Definitions the resolver refuses are skipped, so a board may come up short but never unfulfillable.
     */
    public static List<CommissionBoard.Request> generate(List<Entry> pool, int homeTier, int slots, boolean retrainOpen,
                                                         Random random, Resolver resolver) {
        var open = new ArrayList<Entry>();
        for (var e : pool) {
            var d = e.def();
            if (homeTier >= d.minTier() && homeTier <= d.maxTier() && (!d.retrain() || retrainOpen)) open.add(e);
        }
        var out = new ArrayList<CommissionBoard.Request>();
        while (out.size() < slots && !open.isEmpty()) {
            int total = 0;
            for (var e : open) total += e.def().weight();
            int roll = random.nextInt(total);
            Entry chosen = open.getLast();
            for (var e : open) { roll -= e.def().weight(); if (roll < 0) { chosen = e; break; } }
            open.remove(chosen);
            resolver.resolve(chosen, random).ifPresent(out::add);
        }
        return out;
    }

    /** The live resolver: registered themes, non-empty tags, deliverable items only. */
    public static Resolver live(RegistryAccess registries) { return live(registries, Set.of()); }

    /** As above; a fresh theme request avoids {@code clearedLastCycle} unless that leaves nothing. */
    public static Resolver live(RegistryAccess registries, Set<ResourceLocation> clearedLastCycle) {
        return (entry, random) -> {
            var d = entry.def();
            var themes = registries.registry(RiftfallRegistries.RIFT_THEMES)
                    .map(r -> r.registryKeySet().stream().map(k -> k.location()).sorted(Comparator.comparing(ResourceLocation::toString)).toList())
                    .orElse(List.of());
            Optional<ResourceLocation> theme = d.theme();
            if (theme.isPresent() && !themes.contains(theme.get())) return Optional.empty();
            if (theme.isEmpty() && d.kind() == CommissionDef.Kind.THEME) {
                var pick = themes;
                if (d.fresh()) {
                    var fresh = themes.stream().filter(t -> !clearedLastCycle.contains(t)).toList();
                    if (!fresh.isEmpty()) pick = fresh;
                }
                if (pick.isEmpty()) return Optional.empty();
                theme = Optional.of(pick.get(random.nextInt(pick.size())));
            }
            Optional<ResourceLocation> tag = d.kind() == CommissionDef.Kind.DELIVERY ? d.itemTag() : d.entityTag();
            if (d.kind() == CommissionDef.Kind.DELIVERY) {
                var items = BuiltInRegistries.ITEM.getTag(TagKey.create(Registries.ITEM, tag.orElseThrow()));
                if (items.isEmpty() || items.get().size() == 0
                        || items.get().stream().anyMatch(h -> !h.is(DELIVERABLE))) return Optional.empty();
            } else if (tag.isPresent()) {
                var types = BuiltInRegistries.ENTITY_TYPE.getTag(CommissionBoard.entityTag(tag.get()));
                if (types.isEmpty() || types.get().size() == 0) return Optional.empty();
            }
            return Optional.of(new CommissionBoard.Request(entry.id(), d.kind(), theme, tag, d.minRunTier(),
                    d.tierOffset(), d.minutes(), d.count(), d.xp(), d.retrain()));
        };
    }

    /** Parses one loaded file set; bad files are logged and left out. Package-visible for tests. */
    static Map<NpcRole, List<Entry>> parse(Map<ResourceLocation, JsonElement> files) {
        var pool = new EnumMap<NpcRole, List<Entry>>(NpcRole.class);
        var ids = new HashMap<String, NpcRole>();
        for (var role : NpcRole.values()) ids.put(role.id(), role);
        files.entrySet().stream().sorted(Map.Entry.comparingByKey(Comparator.comparing(ResourceLocation::toString))).forEach(file -> {
            var path = file.getKey().getPath();
            int slash = path.indexOf('/');
            var role = slash < 0 ? null : ids.get(path.substring(0, slash));
            if (role == null) { LogUtils.getLogger().error("Commission {} names no role folder", file.getKey()); return; }
            var parsed = CommissionDef.CODEC.parse(JsonOps.INSTANCE, file.getValue());
            var def = parsed.result().orElse(null);
            var problems = def == null ? List.of(parsed.error().map(e -> e.message()).orElse("unreadable")) : def.problems();
            if (!problems.isEmpty()) { LogUtils.getLogger().error("Commission {} refused: {}", file.getKey(), problems); return; }
            pool.computeIfAbsent(role, r -> new ArrayList<>()).add(new Entry(file.getKey(), def));
        });
        return pool;
    }

    public static final class Loader extends SimpleJsonResourceReloadListener {
        public Loader() { super(new Gson(), "riftfall/commission"); }

        @Override protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
            var pool = parse(files);
            install(pool);
            LogUtils.getLogger().info("Loaded {} resident commission(s)", pool.values().stream().mapToInt(List::size).sum());
        }
    }
}
