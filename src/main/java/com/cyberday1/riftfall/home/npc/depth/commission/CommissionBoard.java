package com.cyberday1.riftfall.home.npc.depth.commission;

import com.cyberday1.riftfall.rift.RiftRunReport;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;

import java.util.*;

/**
 * One resident's request board in one home. Run-counted: it refreshes every {@link #REFRESH_RUNS} home runs,
 * and requests left open expire then. One free reroll per refresh, then {@code home tier} shards each.
 */
public final class CommissionBoard {
    public static final int REFRESH_RUNS = 5, MAX_PER_RUN = 2;

    /** A posted request, its terms resolved at posting so a reload cannot change it under the player. */
    public static final class Request {
        public final ResourceLocation def;
        public final CommissionDef.Kind kind;
        /** For boss and mob: an entity tag; for delivery: an item tag. */
        public final Optional<ResourceLocation> theme, tag;
        public final int minRunTier, tierOffset, minutes, target, xp;
        public final boolean retrain;
        private int progress;
        private final Set<UUID> contributors = new HashSet<>();
        /** Variety: the themes already counted. */
        private final Set<ResourceLocation> themes = new TreeSet<>(Comparator.comparing(ResourceLocation::toString));

        public Request(ResourceLocation def, CommissionDef.Kind kind, Optional<ResourceLocation> theme,
                       Optional<ResourceLocation> tag, int minRunTier, int tierOffset, int minutes, int target, int xp,
                       boolean retrain) {
            this.def = def; this.kind = kind; this.theme = theme; this.tag = tag; this.minRunTier = minRunTier;
            this.tierOffset = tierOffset; this.minutes = minutes; this.target = Math.max(1, target); this.xp = xp;
            this.retrain = retrain;
        }

        public int progress() { return progress; }
        public boolean done() { return progress >= target; }
        public Set<UUID> contributors() { return Collections.unmodifiableSet(contributors); }
        public Set<ResourceLocation> themes() { return Collections.unmodifiableSet(themes); }
        /** Variety: records a counted theme. */
        public void counted(Optional<ResourceLocation> theme) { if (kind == CommissionDef.Kind.VARIETY) theme.ifPresent(themes::add); }

        /** How far one member's run takes this request; 0 when it does not qualify. */
        public int delta(RiftRunReport run, int homeTier) {
            if (done() || run.tier() < minRunTier) return 0;
            boolean themed = theme.isEmpty() || run.theme().equals(theme);
            return switch (kind) {
                case THEME -> run.cleared() && themed ? 1 : 0;
                case BOSS -> themed ? run.bosses(tag.map(CommissionBoard::entityTag).orElse(null)) : 0;
                case MOB -> themed && tag.isPresent() ? run.kills(entityTag(tag.get())) : 0;
                case TIER -> run.cleared() && run.tier() >= homeTier + tierOffset ? 1 : 0;
                case CLEAN -> run.cleared() && !run.partyDied() ? 1 : 0;
                case SPEED -> run.cleared() && run.elapsedTicks() <= minutes * 1200 ? 1 : 0;
                case DELIVERY -> 0;
                case VARIETY -> run.cleared() && run.theme().isPresent() && !themes.contains(run.theme().get()) ? 1 : 0;
            };
        }

        /** Adds progress, capped at the target. True if this call completed it. */
        public boolean advance(int amount, Collection<UUID> members) {
            if (done() || amount <= 0) return false;
            progress = Math.min(target, progress + amount);
            contributors.addAll(members);
            return done();
        }

        CompoundTag save() {
            var out = new CompoundTag();
            out.putString("def", def.toString());
            out.putString("kind", kind.getSerializedName());
            theme.ifPresent(t -> out.putString("theme", t.toString()));
            tag.ifPresent(t -> out.putString("tag", t.toString()));
            out.putInt("minRunTier", minRunTier);
            out.putInt("tierOffset", tierOffset);
            out.putInt("minutes", minutes);
            out.putInt("target", target);
            out.putInt("xp", xp);
            out.putBoolean("retrain", retrain);
            out.putInt("progress", progress);
            var who = new ListTag();
            for (var id : contributors) who.add(NbtUtils.createUUID(id));
            out.put("contributors", who);
            if (!themes.isEmpty()) {
                var seen = new ListTag();
                for (var t : themes) seen.add(net.minecraft.nbt.StringTag.valueOf(t.toString()));
                out.put("themes", seen);
            }
            return out;
        }

        static Request load(CompoundTag in) {
            var def = ResourceLocation.tryParse(in.getString("def"));
            var kind = Arrays.stream(CommissionDef.Kind.values())
                    .filter(k -> k.getSerializedName().equals(in.getString("kind"))).findFirst().orElse(null);
            if (def == null || kind == null) return null;
            var request = new Request(def, kind, optional(in, "theme"), optional(in, "tag"), in.getInt("minRunTier"),
                    in.getInt("tierOffset"), in.getInt("minutes"), in.getInt("target"), in.getInt("xp"),
                    in.getBoolean("retrain"));
            request.progress = Math.min(request.target, Math.max(0, in.getInt("progress")));
            for (var entry : in.getList("contributors", Tag.TAG_INT_ARRAY)) request.contributors.add(NbtUtils.loadUUID(entry));
            for (var entry : in.getList("themes", Tag.TAG_STRING)) {
                var t = ResourceLocation.tryParse(entry.getAsString());
                if (t != null) request.themes.add(t);
            }
            return request;
        }

        private static Optional<ResourceLocation> optional(CompoundTag in, String key) {
            return in.contains(key) ? Optional.ofNullable(ResourceLocation.tryParse(in.getString(key))) : Optional.empty();
        }
    }

    static TagKey<EntityType<?>> entityTag(ResourceLocation id) {
        return TagKey.create(Registries.ENTITY_TYPE, id);
    }

    private int generation = -1;
    private boolean freeRerollUsed;
    private final List<Request> requests = new ArrayList<>();

    public List<Request> requests() { return Collections.unmodifiableList(requests); }
    public int generation() { return generation; }
    public boolean freeRerollUsed() { return freeRerollUsed; }

    /** Due when the home's run count has moved into a new block of {@link #REFRESH_RUNS}. */
    public boolean due(int homeRuns) { return generation != homeRuns / REFRESH_RUNS; }

    public void refresh(int homeRuns, List<Request> fresh) {
        generation = homeRuns / REFRESH_RUNS;
        freeRerollUsed = false;
        requests.clear();
        requests.addAll(fresh);
    }

    /** Shards the next reroll costs: the first after a refresh is free. */
    public int rerollCost(int homeTier) { return freeRerollUsed ? Math.max(1, homeTier) : 0; }

    /** Replaces the open requests; completed ones stay. Call only after the cost is paid. */
    public void reroll(List<Request> fresh) {
        freeRerollUsed = true;
        requests.removeIf(r -> !r.done());
        requests.addAll(fresh);
    }

    public CompoundTag save() {
        var tag = new CompoundTag();
        tag.putInt("generation", generation);
        tag.putBoolean("freeRerollUsed", freeRerollUsed);
        var list = new ListTag();
        for (var request : requests) list.add(request.save());
        tag.put("requests", list);
        return tag;
    }

    public static CommissionBoard load(CompoundTag tag) {
        var board = new CommissionBoard();
        board.generation = tag.contains("generation") ? tag.getInt("generation") : -1;
        board.freeRerollUsed = tag.getBoolean("freeRerollUsed");
        var list = tag.getList("requests", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            var request = Request.load(list.getCompound(i));
            if (request != null) board.requests.add(request);
        }
        return board;
    }
}
