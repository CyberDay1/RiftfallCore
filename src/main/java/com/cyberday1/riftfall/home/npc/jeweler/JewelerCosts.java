package com.cyberday1.riftfall.home.npc.jeweler;

import com.cyberday1.riftfall.RiftfallCore;
import com.cyberday1.riftfall.home.npc.ResidentRank;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The Jeweler's prices, from {@code data/riftfall/riftfall/resident/jeweler.json}. Purity is an index:
 * cracked 0, chipped 1, flawed 2, normal 3, flawless 4, perfect 5. A file that breaks a rule keeps the defaults.
 */
public record JewelerCosts(Fee set, Fee unset, List<Integer> dissolve, List<Step> purity, int socketShards) {
    public JewelerCosts(Fee set, Fee unset, List<Integer> dissolve, List<Step> purity) { this(set, unset, dissolve, purity, SOCKET_SHARDS); }
    /** Add a socket: {@code socketShards x (sockets after) x band} shards and one socket chisel. */
    public static final int SOCKET_SHARDS = 4;

    public static final int PURITIES = 6;
    public static final ResourceLocation FILE = RiftfallCore.id("jeweler");

    /** Shards {@code base + perPurity * P}; one theme crystal from {@code themeFrom}, one rare crystal from {@code rareFrom}. */
    public record Fee(int base, int perPurity, int themeFrom, int rareFrom) {
        static final Codec<Fee> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("base").forGetter(Fee::base),
                Codec.INT.fieldOf("per_purity").forGetter(Fee::perPurity),
                Codec.INT.optionalFieldOf("theme_from", PURITIES).forGetter(Fee::themeFrom),
                Codec.INT.optionalFieldOf("rare_from", PURITIES).forGetter(Fee::rareFrom)).apply(i, Fee::new));
        public int shards(int purity) { return base + perPurity * purity; }
        public int theme(int purity) { return purity >= themeFrom ? 1 : 0; }
        public int rare(int purity) { return purity >= rareFrom ? 1 : 0; }
    }

    /**
     * One purity step up from {@code from}. With {@code order_runs} or {@code order_minutes} it can also be taken as a work
     * order for {@code order_shards}; {@code order_only} steps have no instant form.
     */
    public record Step(int from, ResidentRank rank, int grit, int theme, int rare, int faceted, int shards,
                       int orderRuns, int orderMinutes, int orderShards, boolean orderOnly) {
        public Step(int from, ResidentRank rank, int grit, int theme, int rare, int faceted, int shards) {
            this(from, rank, grit, theme, rare, faceted, shards, 0, 0, 0, false);
        }
        static final Codec<Step> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("from").forGetter(Step::from),
                ResidentRank.CODEC.fieldOf("rank").forGetter(Step::rank),
                Codec.INT.fieldOf("grit").forGetter(Step::grit),
                Codec.INT.optionalFieldOf("theme", 0).forGetter(Step::theme),
                Codec.INT.optionalFieldOf("rare", 0).forGetter(Step::rare),
                Codec.INT.optionalFieldOf("faceted", 0).forGetter(Step::faceted),
                Codec.INT.fieldOf("shards").forGetter(Step::shards),
                Codec.INT.optionalFieldOf("order_runs", 0).forGetter(Step::orderRuns),
                Codec.INT.optionalFieldOf("order_minutes", 0).forGetter(Step::orderMinutes),
                Codec.INT.optionalFieldOf("order_shards", 0).forGetter(Step::orderShards),
                Codec.BOOL.optionalFieldOf("order_only", false).forGetter(Step::orderOnly)).apply(i, Step::new));
        public boolean orderable() { return orderRuns > 0 || orderMinutes > 0; }
    }

    public static final Codec<JewelerCosts> CODEC = RecordCodecBuilder.create(i -> i.group(
            Fee.CODEC.fieldOf("set").forGetter(JewelerCosts::set),
            Fee.CODEC.fieldOf("unset").forGetter(JewelerCosts::unset),
            Codec.INT.listOf().fieldOf("dissolve").forGetter(JewelerCosts::dissolve),
            Step.CODEC.listOf().fieldOf("purity").forGetter(JewelerCosts::purity),
            Codec.INT.optionalFieldOf("socket_shards", SOCKET_SHARDS).forGetter(JewelerCosts::socketShards)).apply(i, JewelerCosts::new));

    /** The shipped file's numbers, so a missing file changes nothing. */
    public static final JewelerCosts DEFAULT = new JewelerCosts(new Fee(2, 2, 2, 4), new Fee(3, 2, 0, PURITIES),
            List.of(1, 2, 3, 5, 8, 12), List.of(
            new Step(0, ResidentRank.APPRENTICE, 2, 1, 0, 0, 3),
            new Step(1, ResidentRank.APPRENTICE, 4, 2, 0, 0, 6),
            new Step(2, ResidentRank.JOURNEYMAN, 8, 2, 1, 0, 12, 1, 60, 6, false),
            new Step(3, ResidentRank.EXPERT, 12, 0, 0, 1, 24, 1, 60, 24, true),
            new Step(4, ResidentRank.MASTER, 16, 0, 0, 2, 48, 2, 120, 48, true)), SOCKET_SHARDS);

    private static volatile JewelerCosts current = DEFAULT;

    public static JewelerCosts current() { return current; }

    public int dissolve(int purity) { return purity >= 0 && purity < dissolve.size() ? dissolve.get(purity) : 0; }

    public Optional<Step> step(int from) { return purity.stream().filter(s -> s.from() == from).findFirst(); }

    /** Every rule a tuned file must keep; empty when it is sound. */
    public List<String> problems() {
        var out = new ArrayList<String>();
        if (dissolve.size() != PURITIES) out.add("dissolve needs " + PURITIES + " yields");
        for (int p = 0; p < dissolve.size(); p++) if (dissolve.get(p) < 0) out.add("negative dissolve yield at " + p);
        if (socketShards < 0) out.add("negative socket_shards");
        for (var fee : List.of(set, unset))
            if (fee.base() < 0 || fee.perPurity() < 0) out.add("negative fee " + fee);
        for (var step : purity) {
            if (step.from() < 0 || step.from() >= PURITIES - 1) out.add("step from " + step.from() + " has no next purity");
            if (step.grit() < 0 || step.theme() < 0 || step.rare() < 0 || step.faceted() < 0 || step.shards() < 0
                    || step.orderRuns() < 0 || step.orderMinutes() < 0 || step.orderShards() < 0)
                out.add("negative cost in step " + step.from());
            if (step.orderOnly() && !step.orderable()) out.add("order-only step " + step.from() + " has no order clock");
            if (purity.stream().filter(s -> s.from() == step.from()).count() > 1) out.add("two steps from " + step.from());
            // No dissolve-and-upgrade loop may gain grit: dissolve(P+1) < grit(P -> P+1) + dissolve(P).
            if (step.from() >= 0 && step.from() < PURITIES - 1 && dissolve.size() == PURITIES
                    && dissolve(step.from() + 1) >= step.grit() + dissolve(step.from()))
                out.add("dissolving after step " + step.from() + " returns more grit than it cost");
        }
        return out;
    }

    static void install(JewelerCosts costs) { current = costs; }

    public static final class Loader extends SimpleJsonResourceReloadListener {
        public Loader() { super(new Gson(), "riftfall/resident"); }

        @Override protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
            var json = files.get(FILE);
            if (json == null) { install(DEFAULT); return; }
            var parsed = CODEC.parse(JsonOps.INSTANCE, json);
            var costs = parsed.result().orElse(null);
            var problems = costs == null ? List.of(parsed.error().map(e -> e.message()).orElse("unreadable")) : costs.problems();
            if (!problems.isEmpty()) {
                LogUtils.getLogger().error("Jeweler costs refused, keeping the defaults: {}", problems);
                install(DEFAULT);
                return;
            }
            install(costs);
        }
    }
}
