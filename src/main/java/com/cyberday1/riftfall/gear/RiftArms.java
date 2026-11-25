package com.cyberday1.riftfall.gear;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Which TaCZ guns and attachments a rift of a given tier drops: {@code data/<ns>/rift_arms/*.json}, one file per
 * kind, each a list of tier bands. Pure apart from the loader, so the tests hold the shipped files to the rules.
 * A tier draws from every band that covers it. A boss draws only from the covering band that starts highest, unless
 * the table names a {@code boss_top_share}: then from every covering band, by {@link Table#pool}'s split.
 */
public final class RiftArms {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** The data directory the loader reads, under every namespace. */
    public static final String DIRECTORY = "rift_arms";

    public enum Kind {
        GUN, ATTACHMENT;

        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }

        static @Nullable Kind parse(String name) {
            for (Kind kind : values()) {
                if (kind.key().equals(name)) {
                    return kind;
                }
            }
            return null;
        }
    }

    public record Entry(String id, int weight) {
    }

    /**
     * {@code maxTier} is {@link Integer#MAX_VALUE} for an open-ended band. {@code damageScale} (guns only, default 1)
     * evens the bands' mean sustained DPS out, so tier alone decides how hard a gun hits.
     */
    public record Band(String name, int minTier, int maxTier, List<Entry> entries, double damageScale) {
        public Band(String name, int minTier, int maxTier, List<Entry> entries) {
            this(name, minTier, maxTier, entries, 1.0);
        }

        public boolean covers(int tier) {
            return tier >= minTier && tier <= maxTier;
        }

        public boolean lists(String id) {
            return entries.stream().anyMatch(e -> e.id().equals(id));
        }
    }

    /**
     * Gun tuning, read from the gun table's {@code grade} object; the one place gun damage and handling are tuned.
     * Every field is optional, and each ladder has one entry per rarity, common to mythic:
     * <ul>
     *   <li>{@code damage_multiplier}: the global re-baseline every TaCZ bullet is multiplied by (default 1.0).</li>
     *   <li>{@code rarity_factors}: bullet damage by rarity, above 0 ({@link GunGrade#DEFAULT_RARITY_FACTORS}).</li>
     *   <li>{@code fire_rate_bonus}: rpm up by this fraction, 0 to 4 ({@link GunGrade#DEFAULT_FIRE_RATE_BONUS}).</li>
     *   <li>{@code recoil_cut}, {@code reload_cut}, {@code ads_cut}, {@code spread_cut}: recoil (pitch and yaw),
     *   reload time, aim-down-sights time and spread down by this fraction, 0 to 0.9 (the {@code DEFAULT_*_CUT}s
     *   in {@link GunGrade}).</li>
     * </ul>
     */
    public record Grading(double damageMultiplier, double[] rarityFactors, double[] fireRateBonus, double[] recoilCut,
                          double[] reloadCut, double[] adsCut, double[] spreadCut) {
        public static final Grading NONE = new Grading(1.0, GunGrade.DEFAULT_RARITY_FACTORS);

        public Grading(double damageMultiplier, double[] rarityFactors) {
            this(damageMultiplier, rarityFactors, GunGrade.DEFAULT_FIRE_RATE_BONUS, GunGrade.DEFAULT_RECOIL_CUT,
                    GunGrade.DEFAULT_RELOAD_CUT, GunGrade.DEFAULT_ADS_CUT, GunGrade.DEFAULT_SPREAD_CUT);
        }

        /** A rarity's handling bonuses. */
        public GunGrade.Stats stats(int rarity) {
            return new GunGrade.Stats(step(fireRateBonus, rarity), step(recoilCut, rarity), step(reloadCut, rarity),
                    step(adsCut, rarity), step(spreadCut, rarity));
        }

        private static float step(double[] ladder, int rarity) {
            return (float) ladder[Math.clamp(rarity, 0, ladder.length - 1)];
        }
    }

    /**
     * {@code bossTopShare}: with a value in (0, 1], a boss reads every covering band. Bands that start at the same tier
     * form one group; the highest-starting group gets this share of the draws and the other groups split the rest
     * evenly, each group by its own weights. 0 keeps the old rule: the top group only.
     */
    public record Table(Kind kind, List<Band> bands, Grading grading, double bossTopShare) {

        /** Draws a boss pool is scaled to, so group shares survive as integer weights. */
        static final int BOSS_SCALE = 100_000;

        public Table(Kind kind, List<Band> bands, Grading grading) {
            this(kind, bands, grading, 0.0);
        }

        public Table(Kind kind, List<Band> bands) {
            this(kind, bands, Grading.NONE);
        }

        /** The band that lists {@code id}'s gun, for its damage scale; 1.0 for a gun no band lists. */
        public double damageScale(String id) {
            return bands.stream().filter(b -> b.lists(id)).findFirst().map(Band::damageScale).orElse(1.0);
        }

        /** The bands a draw at {@code tier} reads; for a boss without a top share only the highest-starting one(s). */
        public List<Band> bands(int tier, boolean boss) {
            List<Band> covering = bands.stream().filter(b -> b.covers(tier)).toList();
            if (!boss || covering.isEmpty() || bossTopShare > 0.0) {
                return covering;
            }
            int top = covering.stream().mapToInt(Band::minTier).max().orElseThrow();
            return covering.stream().filter(b -> b.minTier() == top).toList();
        }

        /** Every id a draw at {@code tier} can land on, weights summed across bands (a boss: by group share). */
        public Map<String, Integer> pool(int tier, boolean boss) {
            Map<String, Integer> pool = new LinkedHashMap<>();
            if (!boss || bossTopShare <= 0.0) {
                for (Band band : bands(tier, boss)) {
                    band.entries().forEach(e -> pool.merge(e.id(), e.weight(), Integer::sum));
                }
                return pool;
            }
            Map<Integer, Map<String, Integer>> groups = new java.util.TreeMap<>(java.util.Comparator.reverseOrder());
            for (Band band : bands(tier, true)) {
                Map<String, Integer> group = groups.computeIfAbsent(band.minTier(), k -> new LinkedHashMap<>());
                band.entries().forEach(e -> group.merge(e.id(), e.weight(), Integer::sum));
            }
            int index = 0;
            for (Map<String, Integer> group : groups.values()) {
                double share = groups.size() == 1 ? 1.0
                        : index == 0 ? bossTopShare : (1.0 - bossTopShare) / (groups.size() - 1);
                int total = group.values().stream().mapToInt(Integer::intValue).sum();
                group.forEach((id, w) -> {
                    int scaled = (int) Math.round(w * share * BOSS_SCALE / total);
                    if (scaled > 0) pool.merge(id, scaled, Integer::sum);
                });
                index++;
            }
            return pool;
        }

        public int total(int tier, boolean boss) {
            return pool(tier, boss).values().stream().mapToInt(Integer::intValue).sum();
        }

        /** The id {@code roll} lands on, {@code roll} in {@code [0, total)}; null when nothing covers the tier. */
        public @Nullable String pick(int tier, boolean boss, int roll) {
            int remaining = roll;
            for (Map.Entry<String, Integer> entry : pool(tier, boss).entrySet()) {
                remaining -= entry.getValue();
                if (remaining < 0) {
                    return entry.getKey();
                }
            }
            return null;
        }
    }

    public record Loaded(Map<Kind, Table> tables, List<String> refusals) {
        public static final Loaded EMPTY = new Loaded(Map.of(), List.of());
    }

    private static volatile Loaded loaded = Loaded.EMPTY;

    /** Probe use: the tier every draw reads instead of its level's, or null for the level's own. */
    private static volatile @Nullable Integer tierOverride;

    private RiftArms() {
    }

    /** A dropped gun's spare ammo is one magazine, at least this (a launcher holds one round) and at most a stack. */
    public static final int MIN_SPARE = 4;
    public static final int MAX_SPARE = 64;

    public static int spare(int magazine) {
        return Math.clamp(magazine, MIN_SPARE, MAX_SPARE);
    }

    public static @Nullable Table table(Kind kind) {
        return loaded.tables().get(kind);
    }

    public static Loaded loaded() {
        return loaded;
    }

    public static int tier(int levelTier) {
        Integer override = tierOverride;
        return override != null ? override : levelTier;
    }

    /** Probe use only; no command reaches it. */
    public static void overrideTierForProbe(@Nullable Integer tier) {
        tierOverride = tier;
    }

    /** Parses every file, refusing (with a reason) any that is malformed or repeats a kind. */
    public static Loaded parse(Map<ResourceLocation, JsonElement> files) {
        Map<Kind, Table> tables = new EnumMap<>(Kind.class);
        List<String> refusals = new ArrayList<>();
        files.forEach((file, json) -> {
            try {
                Table table = parse(json.getAsJsonObject());
                if (tables.putIfAbsent(table.kind(), table) != null) {
                    refusals.add(file + ": a second " + table.kind().key() + " table");
                }
            } catch (RuntimeException bad) {
                refusals.add(file + ": " + bad.getMessage());
            }
        });
        return new Loaded(Map.copyOf(tables), List.copyOf(refusals));
    }

    static Table parse(JsonObject json) {
        Kind kind = Kind.parse(json.get("kind").getAsString());
        if (kind == null) {
            throw new IllegalArgumentException("unknown kind " + json.get("kind"));
        }
        List<Band> bands = new ArrayList<>();
        for (JsonElement element : json.getAsJsonArray("bands")) {
            JsonObject band = element.getAsJsonObject();
            String name = band.get("name").getAsString();
            int min = band.get("min_tier").getAsInt();
            int max = band.has("max_tier") ? band.get("max_tier").getAsInt() : Integer.MAX_VALUE;
            if (min < 1 || max < min) {
                throw new IllegalArgumentException(name + ": tiers " + min + ".." + max);
            }
            List<Entry> entries = new ArrayList<>();
            for (Map.Entry<String, JsonElement> e : band.getAsJsonObject("entries").entrySet()) {
                int weight = e.getValue().getAsInt();
                if (weight <= 0 || ResourceLocation.tryParse(e.getKey()) == null) {
                    throw new IllegalArgumentException(name + ": " + e.getKey() + " weight " + weight);
                }
                entries.add(new Entry(e.getKey(), weight));
            }
            if (entries.isEmpty()) {
                throw new IllegalArgumentException(name + " is empty");
            }
            double scale = band.has("damage_scale") ? band.get("damage_scale").getAsDouble() : 1.0;
            if (!(scale > 0.0)) {
                throw new IllegalArgumentException(name + ": damage_scale " + scale);
            }
            bands.add(new Band(name, min, max, List.copyOf(entries), scale));
        }
        double top = json.has("boss_top_share") ? json.get("boss_top_share").getAsDouble() : 0.0;
        if (!(top >= 0.0 && top <= 1.0)) {
            throw new IllegalArgumentException("boss_top_share " + top);
        }
        return new Table(kind, List.copyOf(bands), grading(json), top);
    }

    static Grading grading(JsonObject json) {
        if (!json.has("grade")) {
            return Grading.NONE;
        }
        JsonObject grade = json.getAsJsonObject("grade");
        double multiplier = grade.has("damage_multiplier") ? grade.get("damage_multiplier").getAsDouble() : 1.0;
        if (!(multiplier > 0.0)) {
            throw new IllegalArgumentException("grade: damage_multiplier " + multiplier);
        }
        double[] factors = ladder(grade, "rarity_factors", GunGrade.DEFAULT_RARITY_FACTORS, Double.MIN_VALUE,
                Double.MAX_VALUE);
        double cut = GunGrade.Stats.MAX_CUT;
        return new Grading(multiplier, factors,
                ladder(grade, "fire_rate_bonus", GunGrade.DEFAULT_FIRE_RATE_BONUS, 0.0, GunGrade.Stats.MAX_BONUS),
                ladder(grade, "recoil_cut", GunGrade.DEFAULT_RECOIL_CUT, 0.0, cut),
                ladder(grade, "reload_cut", GunGrade.DEFAULT_RELOAD_CUT, 0.0, cut),
                ladder(grade, "ads_cut", GunGrade.DEFAULT_ADS_CUT, 0.0, cut),
                ladder(grade, "spread_cut", GunGrade.DEFAULT_SPREAD_CUT, 0.0, cut));
    }

    /** One per-rarity array of {@code grade}, each entry in {@code [min, max]}; {@code defaults} when absent. */
    private static double[] ladder(JsonObject grade, String key, double[] defaults, double min, double max) {
        if (!grade.has(key)) {
            return defaults;
        }
        var array = grade.getAsJsonArray(key);
        if (array.size() != RarityLadder.RARITIES.length) {
            throw new IllegalArgumentException("grade: " + key + " needs " + RarityLadder.RARITIES.length);
        }
        double[] values = new double[array.size()];
        for (int i = 0; i < values.length; i++) {
            values[i] = array.get(i).getAsDouble();
            if (!(values[i] >= min && values[i] <= max)) {
                throw new IllegalArgumentException("grade: " + key + " entry " + values[i]);
            }
        }
        return values;
    }

    /** The gun table's grading, or {@link Grading#NONE} without one. */
    public static Grading grading() {
        Table guns = table(Kind.GUN);
        return guns == null ? Grading.NONE : guns.grading();
    }

    /** The whole bullet multiplier for a gun of {@code gunId} carrying {@code grade}. */
    public static double gunFactor(String gunId, GunGrade grade) {
        Table guns = table(Kind.GUN);
        Grading grading = guns == null ? Grading.NONE : guns.grading();
        double scale = guns == null ? 1.0 : guns.damageScale(gunId);
        return grade.factor(grading.damageMultiplier(), scale, grading.rarityFactors());
    }

    /** Reads {@code data/<ns>/rift_arms/*.json} on every reload. */
    public static final class Loader extends SimpleJsonResourceReloadListener {

        public Loader() {
            super(new Gson(), DIRECTORY);
        }

        @Override
        protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
            Loaded parsed = parse(files);
            parsed.refusals().forEach(refusal -> LOGGER.error("Rift arms refused {}", refusal));
            LOGGER.info("Rift arms: {} tables ({}), {} refused.", parsed.tables().size(),
                    parsed.tables().keySet(), parsed.refusals().size());
            loaded = parsed;
        }
    }
}
