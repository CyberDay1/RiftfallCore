package com.cyberday1.riftfall.worldgen.ore;

import com.cyberday1.riftfall.RiftfallCore;
import com.cyberday1.riftfall.block.ore.RiftstoneBlock;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The ore a rift places: gem-vein seams and the riftstone boulders of ore rooms. Pools are Java defaults,
 * so one place holds every weight; a theme picks only its home crystal and stone variant.
 */
public final class RiftOres {

    /** {@code -Driftfall.rift_ores=off}: no gem veins, no ore rooms (the rig's "before" arm). */
    public static final String PROPERTY = "riftfall.rift_ores";
    public static final boolean ENABLED = !"off".equals(System.getProperty(PROPERTY));

    /** The only blocks a player may break in a rift (plus barrels, by the Looting Pick). */
    public static final TagKey<Block> MINEABLE = TagKey.create(Registries.BLOCK, RiftfallCore.id("rift_mineable"));

    /** Tier at which every tier-scaled number reaches its top value. */
    public static final int TOP_TIER = 16;

    private static final String CC = "crystalcraft_unlimited_java:";
    private static final String DD = "deeperdarker:";

    private RiftOres() {
    }

    /** 0 at tier 1, 1 at {@link #TOP_TIER} and above. */
    public static double tierFactor(int tier) {
        return Mth.clamp((tier - 1) / (double) (TOP_TIER - 1), 0.0, 1.0);
    }

    static double lerp(double low, double high, int tier) {
        return low + (high - low) * tierFactor(tier);
    }

    public static boolean mineable(BlockState state) {
        return state.is(MINEABLE);
    }

    // -------------------------------------------------------------------------------------------
    // Entries
    // -------------------------------------------------------------------------------------------

    /**
     * One weighted pick. The weight runs from {@code base} at tier 1 to {@code top} at {@link #TOP_TIER}.
     * The first registered id wins; an entry with none drops out and the rest renormalise.
     */
    public record Entry(String name, List<String> ids, double base, double top) {
        public double weight(int tier) {
            return lerp(base, top, tier);
        }

        public Optional<BlockState> state() {
            for (String id : ids) {
                Optional<Block> block = block(id);
                if (block.isPresent()) return Optional.of(placed(block.get()));
            }
            return Optional.empty();
        }
    }

    private static final Map<String, Optional<Block>> BLOCKS = new ConcurrentHashMap<>();

    static Optional<Block> block(String id) {
        return BLOCKS.computeIfAbsent(id, key -> {
            ResourceLocation location = ResourceLocation.tryParse(key);
            return location == null ? Optional.empty() : BuiltInRegistries.BLOCK.getOptional(location);
        });
    }

    /** Clusters stand up: facing up is the one facing rotation and mirroring leave alone. */
    static BlockState placed(Block block) {
        BlockState state = block.defaultBlockState();
        if (state.hasProperty(BlockStateProperties.FACING)) state = state.setValue(BlockStateProperties.FACING,
                net.minecraft.core.Direction.UP);
        return state;
    }

    /** Weighted draw; empty when nothing in the list is registered. */
    public static Optional<BlockState> pick(List<Entry> entries, int tier, RandomSource random) {
        return pick(entries, tier, random, Entry::state);
    }

    /** As above with the resolution supplied, so tests can stand vanilla blocks in for modded ones. */
    public static Optional<BlockState> pick(List<Entry> entries, int tier, RandomSource random,
            java.util.function.Function<Entry, Optional<BlockState>> resolve) {
        double total = 0;
        List<BlockState> states = new ArrayList<>(entries.size());
        List<Double> weights = new ArrayList<>(entries.size());
        for (Entry entry : entries) {
            Optional<BlockState> state = resolve.apply(entry);
            double weight = entry.weight(tier);
            if (state.isEmpty() || weight <= 0) continue;
            states.add(state.get());
            weights.add(weight);
            total += weight;
        }
        if (states.isEmpty()) return Optional.empty();
        double roll = random.nextDouble() * total;
        for (int i = 0; i < states.size(); i++) {
            roll -= weights.get(i);
            if (roll < 0) return Optional.of(states.get(i));
        }
        return Optional.of(states.getLast());
    }

    // -------------------------------------------------------------------------------------------
    // Gems (the gem vein)
    // -------------------------------------------------------------------------------------------

    /** The rock a crystal ore sits in; a theme picks one ({@code host} on its gem_vein and ore_chunk). */
    public enum Host implements net.minecraft.util.StringRepresentable {
        STONE("stone"), DEEPSLATE("deepslate");

        @Override
        public String getSerializedName() {
            return id;
        }

        public static final com.mojang.serialization.Codec<Host> CODEC = com.mojang.serialization.Codec.STRING
                .comapFlatMap(name -> {
                    for (Host h : values()) if (h.id.equals(name)) return com.mojang.serialization.DataResult.success(h);
                    return com.mojang.serialization.DataResult.error(() -> "Unknown crystal host '" + name + "'");
                }, h -> h.id);

        final String id;

        Host(String id) {
            this.id = id;
        }
    }

    /**
     * Each theme crystal's ore on each host, never end stone or netherrack. Crystalcraft's where it has one;
     * Riftfall's own where it does not (its fire opal and sunstone are netherrack, maradonyx and moonstone
     * end stone, and it has no deepslate onyx or ice opal).
     */
    public static final Map<String, Map<Host, String>> CRYSTAL_HOSTS = crystalHosts();
    /** Each theme crystal's stone-hosted ore; the key set is the twelve crystals. */
    public static final Map<String, String> CRYSTAL_ORES = crystalOres();
    /** Onyx, fire opal and maradonyx: {@code #riftfall:rare_crystals}. */
    public static final Set<String> RARE_CRYSTALS = Set.of("onyx", "fire_opal", "maradonyx");

    public static final double HOME_BASE = 40, HOME_TOP = 30;
    public static final double COMMON_BASE = 3, COMMON_TOP = 3;
    public static final double RARE_BASE = 1, RARE_TOP = 3;
    public static final Entry DIAMOND = new Entry("diamond", List.of(DD + "gloomslate_diamond_ore"), 4, 8);
    public static final Entry EMERALD = new Entry("emerald", List.of(DD + "gloomslate_emerald_ore"), 3, 5);
    /** Share of gem seams that hold a gem; the rest take the stone beside them. */
    public static final double GEM_FILL_BASE = 0.5, GEM_FILL_TOP = 0.85;
    /** Share of cluster seams that grow an amethyst cluster; the rest stay air. */
    public static final double CLUSTER_BASE = 0.5, CLUSTER_TOP = 0.8;

    private static Map<String, Map<Host, String>> crystalHosts() {
        Map<String, Map<Host, String>> out = new LinkedHashMap<>();
        String rf = RiftfallCore.MODID + ":";
        java.util.function.BiConsumer<String, String[]> put = (crystal, ids) -> out.put(crystal,
                Map.of(Host.STONE, ids[0], Host.DEEPSLATE, ids[1]));
        for (String crystal : List.of("alexandrite", "ametrine", "aquamarine", "citrine", "peridot")) {
            put.accept(crystal, new String[]{CC + crystal + "_ore", CC + "deepslate_" + crystal + "_ore"});
        }
        put.accept("grandidierite", new String[]{CC + "grandidierite_ore", CC + "deepslate_grand_ore"});
        put.accept("onyx", new String[]{CC + "onyx_ore", rf + "deepslate_onyx_ore"});
        for (String crystal : List.of("fire_opal", "maradonyx", "moonstone", "sunstone")) {
            put.accept(crystal, new String[]{rf + crystal + "_ore", rf + "deepslate_" + crystal + "_ore"});
        }
        put.accept("ice_opal", new String[]{rf + "ice_opal_ore", rf + "deepslate_ice_opal_ore"});
        Map<String, Map<Host, String>> sorted = new java.util.TreeMap<>(out);
        return java.util.Collections.unmodifiableMap(new LinkedHashMap<>(sorted));
    }

    private static Map<String, String> crystalOres() {
        Map<String, String> out = new LinkedHashMap<>();
        CRYSTAL_HOSTS.forEach((crystal, hosts) -> out.put(crystal, hosts.get(Host.STONE)));
        return java.util.Collections.unmodifiableMap(out);
    }

    public static String crystalOre(String crystal, Host host) {
        return CRYSTAL_HOSTS.get(crystal).get(host);
    }

    /**
     * The gem pool for a theme. {@code home} is the theme's crystal (null for cave, which has none: its
     * home weight is shared by all twelve).
     */
    public static List<Entry> gemPool(String home) {
        return gemPool(home, Host.STONE);
    }

    /** As above, each crystal on {@code host}. */
    public static List<Entry> gemPool(String home, Host host) {
        List<Entry> out = new ArrayList<>();
        boolean homed = home != null && CRYSTAL_ORES.containsKey(home);
        double spread = homed ? 0 : HOME_BASE / CRYSTAL_ORES.size();
        double spreadTop = homed ? 0 : HOME_TOP / CRYSTAL_ORES.size();
        for (String name : CRYSTAL_ORES.keySet()) {
            List<String> ids = List.of(crystalOre(name, host));
            if (name.equals(home)) out.add(new Entry(name, ids, HOME_BASE, HOME_TOP));
            else if (RARE_CRYSTALS.contains(name)) out.add(new Entry(name, ids, RARE_BASE + spread, RARE_TOP + spreadTop));
            else out.add(new Entry(name, ids, COMMON_BASE + spread, COMMON_TOP + spreadTop));
        }
        out.add(DIAMOND);
        out.add(EMERALD);
        return List.copyOf(out);
    }

    public static double gemFill(int tier) {
        return lerp(GEM_FILL_BASE, GEM_FILL_TOP, tier);
    }

    public static double clusterChance(int tier) {
        return lerp(CLUSTER_BASE, CLUSTER_TOP, tier);
    }

    public static BlockState amethystCluster() {
        return Blocks.AMETHYST_CLUSTER.defaultBlockState().setValue(AmethystClusterBlock.FACING,
                net.minecraft.core.Direction.UP);
    }

    // -------------------------------------------------------------------------------------------
    // Ore rooms
    // -------------------------------------------------------------------------------------------

    /** Which stone the trace diamond sits in. Deeper Darker's, because vanilla's appear in templates. */
    public enum Variant {
        GLOOMSLATE("gloomslate"), SCULK_STONE("sculk_stone");

        public static final com.mojang.serialization.Codec<Variant> CODEC = com.mojang.serialization.Codec.STRING
                .comapFlatMap(name -> {
                    for (Variant v : values()) if (v.prefix.equals(name)) return com.mojang.serialization.DataResult.success(v);
                    return com.mojang.serialization.DataResult.error(() -> "Unknown ore variant '" + name + "'");
                }, v -> v.prefix);

        final String prefix;

        Variant(String prefix) {
            this.prefix = prefix;
        }

        String ore(String mineral) {
            return DD + prefix + "_" + mineral + "_ore";
        }
    }

    /** The filler a boulder is mostly made of. */
    public static final String RIFTSTONE = "riftfall:riftstone";
    /** Target share of an ore room's mass that is ore; the rest is riftstone. */
    public static final double ORE_SHARE = 0.33;
    /** Cells per small vein. */
    public static final int VEIN_MIN = 3, VEIN_MAX = 6;
    /** Share of small veins seeded on the mass's surface, so the room reads as an ore room. */
    public static final double EXPOSED_VEINS = 0.5;
    /** Share of the ore laid as long seams, and a seam's length in cells. */
    public static final double SEAM_SHARE = 0.3;
    public static final int SEAM_MIN = 8, SEAM_MAX = 16;
    /** The crystals' share of vein weight; split by the gem-vein crystal weights. */
    public static final double CRYSTAL_BASE = 70, CRYSTAL_TOP = 66;
    /** Chance a boulder's top cell grows a certus cluster. */
    public static final double CROWN_BASE = 0.04, CROWN_TOP = 0.12;
    public static final String CERTUS_CLUSTER = "ae2:quartz_cluster";
    /**
     * The share of an ore room's crystal veins actually laid, by tier: 20% of the full mix at tier 1 to 40% at
     * {@link #TOP_TIER}. The cells left out stay riftstone. {@code OreChunkDecorator#crystalShare} multiplies
     * this into the theme's {@code crystal_scale}.
     */
    public static final double CRYSTAL_SCALE_BASE = 0.2, CRYSTAL_SCALE_TOP = 0.4;
    /** Chance a riftstone block drops a runestone; no Fortune. */
    public static final double RUNE_BASE = 0.001, RUNE_TOP = 0.002;

    /**
     * The vein pool for a theme: every crystal (the home heaviest), the pack's tech ores, a trace of
     * diamond. One pick per vein.
     */
    public static List<Entry> roomPool(String home, Variant variant) {
        return roomPool(home, variant, Host.STONE);
    }

    /** As above, each crystal on {@code host}. */
    public static List<Entry> roomPool(String home, Variant variant, Host host) {
        List<Entry> crystals = gemPool(home, host).stream().filter(e -> CRYSTAL_ORES.containsKey(e.name())).toList();
        double base = crystals.stream().mapToDouble(Entry::base).sum();
        double top = crystals.stream().mapToDouble(Entry::top).sum();
        List<Entry> out = new ArrayList<>();
        for (Entry e : crystals) {
            out.add(new Entry(e.name(), e.ids(), e.base() * CRYSTAL_BASE / base, e.top() * CRYSTAL_TOP / top));
        }
        out.add(new Entry("zinc", List.of("create:deepslate_zinc_ore"), 14, 12));
        out.add(new Entry("uraninite_poor", List.of("powah:deepslate_uraninite_ore_poor"), 6, 4));
        out.add(new Entry("uraninite", List.of("powah:deepslate_uraninite_ore"), 4, 5));
        out.add(new Entry("uraninite_dense", List.of("powah:deepslate_uraninite_ore_dense"), 1, 3));
        out.add(new Entry("diamond", List.of(variant.ore("diamond")), 2, 4));
        return List.copyOf(out);
    }

    public static double crystalCurve(int tier) {
        return lerp(CRYSTAL_SCALE_BASE, CRYSTAL_SCALE_TOP, tier);
    }

    private static volatile Set<Block> crystalOres;

    /** Whether a state is one of the crystal ores the pools place (any host stone). */
    public static boolean isCrystalOre(BlockState state) {
        Set<Block> set = crystalOres;
        if (set == null) {
            Set<Block> built = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
            for (String id : crystalOreIds()) block(id).ifPresent(built::add);
            crystalOres = set = java.util.Collections.unmodifiableSet(built);
        }
        return state != null && set.contains(state.getBlock());
    }

    /** Every crystal ore id any pool can place. */
    public static List<String> crystalOreIds() {
        List<String> out = new ArrayList<>();
        CRYSTAL_HOSTS.values().forEach(hosts -> out.addAll(hosts.values()));
        return out.stream().distinct().sorted().toList();
    }

    public static double crownChance(int tier) {
        return lerp(CROWN_BASE, CROWN_TOP, tier);
    }

    public static double runeChance(int tier) {
        return lerp(RUNE_BASE, RUNE_TOP, tier);
    }

    /** A certus cluster standing up, if AE2 is in the pack. */
    public static Optional<BlockState> crown() {
        return block(CERTUS_CLUSTER).map(RiftOres::placed);
    }

    /** Worldgen riftstone on stone: the natural state, the only one whose break can drop a rune. */
    public static Optional<BlockState> riftstone() {
        return riftstone(Host.STONE);
    }

    /** Worldgen riftstone looking like {@code host}'s rock, natural. */
    public static Optional<BlockState> riftstone(Host host) {
        return block(RIFTSTONE).map(b -> {
            BlockState s = b.defaultBlockState();
            if (s.hasProperty(RiftstoneBlock.NATURAL)) s = s.setValue(RiftstoneBlock.NATURAL, true);
            if (s.hasProperty(RiftstoneBlock.HOST)) s = s.setValue(RiftstoneBlock.HOST, host);
            return s;
        });
    }

    /** Every id any pool can place, for the tag and the known-ids checks. */
    public static List<String> allIds() {
        List<String> out = new ArrayList<>();
        for (Host h : Host.values()) {
            for (Entry e : gemPool(null, h)) out.addAll(e.ids());
            for (Variant v : Variant.values()) {
                for (Entry e : roomPool(null, v, h)) out.addAll(e.ids());
            }
        }
        out.add(RIFTSTONE);
        out.add(CERTUS_CLUSTER);
        out.add("minecraft:amethyst_cluster");
        return out.stream().distinct().toList();
    }

    private static volatile Set<Block> placed;

    /** Whether a state is something these pools place: what later decorators must not overwrite. */
    public static boolean placedOre(BlockState state) {
        if (state == null || state.isAir()) return false;
        Set<Block> set = placed;
        if (set == null) {
            java.util.Set<Block> built = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
            for (String id : allIds()) block(id).ifPresent(built::add);
            placed = set = java.util.Collections.unmodifiableSet(built);
        }
        return set.contains(state.getBlock());
    }
}
