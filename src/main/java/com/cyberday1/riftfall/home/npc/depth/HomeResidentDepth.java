package com.cyberday1.riftfall.home.npc.depth;

import com.cyberday1.riftfall.RiftfallCore;
import com.cyberday1.riftfall.block.marker.NpcRole;
import com.cyberday1.riftfall.home.npc.ResidentRank;
import com.cyberday1.riftfall.home.npc.depth.commission.CommissionBoard;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.*;

/**
 * Per-home resident depth: XP, rank, specialisation, per-home-run caps and request boards for every role, plus the
 * home's run count and best clear. Kept on the overworld, keyed by home owner, because runs settle while the home
 * level may not be loaded. The resident markers stay in {@code HomeResidentData}.
 */
public final class HomeResidentDepth extends SavedData {
    private static final String FILE = RiftfallCore.MODID + "_resident_depth";

    public enum Source { SERVICE, DELIVERY, COMMISSION, MILESTONE }

    public static final int SERVICE_CAP = 5, DELIVERY_CAP = 20, MILESTONE_XP = 25, RESPEC_LOCK_RUNS = 10, RESPEC_SHARDS_PER_TIER = 20;
    /** Station level 1 (Journeyman) to 3 (Master): gem-fused slate and shards, paid once per home. */
    public static final int[] STATION_SLATE = {0, 4, 8, 16}, STATION_SHARDS = {0, 4, 8, 16};

    /** XP multiplier for work at a tier: {@code 1 + (tier - 1) / 5}. */
    public static int band(int tier) { return 1 + (Math.max(1, tier) - 1) / 5; }

    public static final class Role {
        private int xp;
        private ResidentRank rank = ResidentRank.APPRENTICE;
        private String specialisation;
        private int respecLockUntil;
        private boolean retrainReady, unlocked;
        private int capRun = -1, serviceXp, deliveryXp, completed, stationFunded;
        private CommissionBoard board = new CommissionBoard();

        public int xp() { return xp; }
        public ResidentRank rank() { return rank; }
        public Optional<String> specialisation() { return Optional.ofNullable(specialisation); }
        public boolean unlocked() { return unlocked; }
        public boolean retrainReady() { return retrainReady; }
        public int completed() { return completed; }
        public CommissionBoard board() { return board; }
        public int respecLockUntil() { return respecLockUntil; }
        /** Station levels paid for; overlays go up to min(this, the rank's level). */
        public int stationFunded() { return stationFunded; }
    }

    public record Award(int granted, ResidentRank before, ResidentRank after) {
        public boolean promoted() { return after.ordinal() > before.ordinal(); }
    }
    public record Settled(boolean counted, boolean newBest, int tierBefore, List<NpcRole> promoted) {}
    public enum PathResult { DONE, NOT_OWNER, NOT_MASTER, ALREADY_CHOSEN, NONE_CHOSEN, NO_RETRAIN, LOCKED, SAME }
    public enum StationResult { DONE, RANK, BUILT, NOT_MEMBER }

    public static final class Home {
        private int homeRuns, bestCleared, themeCycle = -1;
        private final EnumMap<NpcRole, Role> roles = new EnumMap<>(NpcRole.class);
        // Themes cleared by counted runs in board cycle themeCycle, and in the cycle before it.
        private final Set<net.minecraft.resources.ResourceLocation> cycleThemes = new TreeSet<>(), lastCycleThemes = new TreeSet<>();

        public int homeRuns() { return homeRuns; }
        public int bestCleared() { return bestCleared; }
        /** The home's tier: its best clear, at least 1. */
        public int homeTier() { return Math.max(1, bestCleared); }
        /** A run counts for the home's boards, caps and orders only at or above {@code home tier - 2}. */
        public boolean counts(int runTier) { return runTier >= homeTier() - 2; }
        public Role role(NpcRole role) { return roles.computeIfAbsent(role, r -> new Role()); }
        public Map<NpcRole, Role> roles() { return Collections.unmodifiableMap(roles); }

        public boolean unlock(NpcRole role) {
            var r = role(role);
            if (r.unlocked) return false;
            r.unlocked = true;
            return true;
        }

        /** Grants XP under the per-home-run caps and promotes if the gate allows. */
        public Award award(NpcRole role, Source source, int amount) {
            var r = role(role);
            var before = r.rank;
            if (r.capRun != homeRuns) { r.capRun = homeRuns; r.serviceXp = 0; r.deliveryXp = 0; }
            int granted = Math.max(0, amount);
            if (source == Source.SERVICE) { granted = Math.min(granted, SERVICE_CAP - r.serviceXp); r.serviceXp += granted; }
            if (source == Source.DELIVERY) { granted = Math.min(granted, DELIVERY_CAP - r.deliveryXp); r.deliveryXp += granted; }
            r.xp = (int) Math.min(Integer.MAX_VALUE, (long) r.xp + granted);
            promote(r);
            return new Award(granted, before, r.rank);
        }

        private boolean promote(Role r) {
            var earned = ResidentRank.earned(r.xp, bestCleared);
            if (earned.ordinal() <= r.rank.ordinal()) return false;
            r.rank = earned;
            return true;
        }

        /** Themes this home cleared in the board cycle before {@code generation}; a fresh request avoids them. */
        public Set<net.minecraft.resources.ResourceLocation> clearedLastCycle(int generation) {
            if (themeCycle == generation - 1) return Collections.unmodifiableSet(cycleThemes);
            if (themeCycle == generation) return Collections.unmodifiableSet(lastCycleThemes);
            return Set.of();
        }

        public Settled settle(int runTier, boolean cleared) { return settle(runTier, cleared, Optional.empty()); }

        /** One settled run with at least one member of this home in it. */
        public Settled settle(int runTier, boolean cleared, Optional<net.minecraft.resources.ResourceLocation> theme) {
            int tierBefore = homeTier();
            boolean counted = counts(runTier);
            if (counted) {
                int cycle = homeRuns / CommissionBoard.REFRESH_RUNS;
                if (cycle != themeCycle) {
                    var previous = cycle == themeCycle + 1 ? Set.copyOf(cycleThemes) : Set.<net.minecraft.resources.ResourceLocation>of();
                    lastCycleThemes.clear(); lastCycleThemes.addAll(previous);
                    cycleThemes.clear();
                    themeCycle = cycle;
                }
                if (cleared) theme.ifPresent(cycleThemes::add);
                homeRuns++;
            }
            boolean newBest = cleared && runTier > bestCleared;
            var promoted = new ArrayList<NpcRole>();
            if (newBest) {
                bestCleared = runTier;
                for (var entry : roles.entrySet()) {
                    var r = entry.getValue();
                    if (r.unlocked) r.xp = (int) Math.min(Integer.MAX_VALUE, (long) r.xp + MILESTONE_XP);
                }
            }
            for (var entry : roles.entrySet()) if (promote(entry.getValue())) promoted.add(entry.getKey());
            return new Settled(counted, newBest, tierBefore, promoted);
        }

        public void completed(NpcRole role, boolean retrain) {
            var r = role(role);
            r.completed++;
            if (retrain) r.retrainReady = true;
        }

        public int respecShards() { return RESPEC_SHARDS_PER_TIER * homeTier(); }

        /** Whether the next station level can be paid for; {@code top} is the highest level with an overlay. */
        public StationResult canFundStation(NpcRole role, int top, boolean member) {
            var r = role(role);
            int next = r.stationFunded + 1;
            if (!member) return StationResult.NOT_MEMBER;
            if (next > Math.min(3, top)) return StationResult.BUILT;
            if (r.rank.ordinal() < next) return StationResult.RANK;
            return StationResult.DONE;
        }

        /** Records the next level paid; the caller has taken the slate and shards. */
        public void fundStation(NpcRole role) { role(role).stationFunded++; }

        /** The owner's one-way choice at Master. */
        public PathResult choose(NpcRole role, String path, boolean owner) {
            var r = role(role);
            if (!owner) return PathResult.NOT_OWNER;
            if (r.rank != ResidentRank.MASTER) return PathResult.NOT_MASTER;
            if (r.specialisation != null) return PathResult.ALREADY_CHOSEN;
            r.specialisation = path;
            return PathResult.DONE;
        }

        /** What a respec would answer now; the caller pays {@link #respecShards} only on DONE. */
        public PathResult canRespec(NpcRole role, String path, boolean owner) {
            var r = role(role);
            if (!owner) return PathResult.NOT_OWNER;
            if (r.rank != ResidentRank.MASTER) return PathResult.NOT_MASTER;
            if (r.specialisation == null) return PathResult.NONE_CHOSEN;
            if (r.specialisation.equals(path)) return PathResult.SAME;
            if (!r.retrainReady) return PathResult.NO_RETRAIN;
            if (homeRuns < r.respecLockUntil) return PathResult.LOCKED;
            return PathResult.DONE;
        }

        public PathResult respec(NpcRole role, String path, boolean owner) {
            var result = canRespec(role, path, owner);
            if (result != PathResult.DONE) return result;
            var r = role(role);
            r.specialisation = path;
            r.retrainReady = false;
            r.respecLockUntil = homeRuns + RESPEC_LOCK_RUNS;
            return PathResult.DONE;
        }

        CompoundTag save() {
            var tag = new CompoundTag();
            tag.putInt("homeRuns", homeRuns);
            tag.putInt("bestCleared", bestCleared);
            tag.putInt("themeCycle", themeCycle);
            tag.put("cycleThemes", strings(cycleThemes));
            tag.put("lastCycleThemes", strings(lastCycleThemes));
            var list = new ListTag();
            roles.forEach((role, r) -> {
                var e = new CompoundTag();
                e.putString("role", role.id());
                e.putInt("xp", r.xp);
                e.putString("rank", r.rank.getSerializedName());
                if (r.specialisation != null) e.putString("specialisation", r.specialisation);
                e.putInt("respecLockUntil", r.respecLockUntil);
                e.putBoolean("retrainReady", r.retrainReady);
                e.putBoolean("unlocked", r.unlocked);
                e.putInt("capRun", r.capRun);
                e.putInt("serviceXp", r.serviceXp);
                e.putInt("deliveryXp", r.deliveryXp);
                e.putInt("completed", r.completed);
                e.putInt("stationFunded", r.stationFunded);
                e.put("board", r.board.save());
                list.add(e);
            });
            tag.put("roles", list);
            return tag;
        }

        static Home load(CompoundTag tag) {
            var home = new Home();
            home.homeRuns = tag.getInt("homeRuns");
            home.bestCleared = tag.getInt("bestCleared");
            home.themeCycle = tag.contains("themeCycle") ? tag.getInt("themeCycle") : -1;
            readStrings(tag.getList("cycleThemes", Tag.TAG_STRING), home.cycleThemes);
            readStrings(tag.getList("lastCycleThemes", Tag.TAG_STRING), home.lastCycleThemes);
            var list = tag.getList("roles", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                var e = list.getCompound(i);
                NpcRole role = null;
                for (var candidate : NpcRole.values()) if (candidate.id().equals(e.getString("role"))) role = candidate;
                if (role == null) continue;
                var r = home.role(role);
                r.xp = Math.max(0, e.getInt("xp"));
                for (var rank : ResidentRank.values()) if (rank.getSerializedName().equals(e.getString("rank"))) r.rank = rank;
                r.specialisation = e.contains("specialisation") ? e.getString("specialisation") : null;
                r.respecLockUntil = e.getInt("respecLockUntil");
                r.retrainReady = e.getBoolean("retrainReady");
                r.unlocked = e.getBoolean("unlocked");
                r.capRun = e.contains("capRun") ? e.getInt("capRun") : -1;
                r.serviceXp = e.getInt("serviceXp");
                r.deliveryXp = e.getInt("deliveryXp");
                r.completed = e.getInt("completed");
                // Saved before stations cost slate: the rank's levels were built free, so they count as paid.
                r.stationFunded = e.contains("stationFunded") ? e.getInt("stationFunded") : r.rank.ordinal();
                r.board = CommissionBoard.load(e.getCompound("board"));
            }
            return home;
        }
    }

    private static ListTag strings(Set<net.minecraft.resources.ResourceLocation> ids) {
        var list = new ListTag();
        for (var id : ids) list.add(net.minecraft.nbt.StringTag.valueOf(id.toString()));
        return list;
    }

    private static void readStrings(ListTag list, Set<net.minecraft.resources.ResourceLocation> into) {
        for (var entry : list) {
            var id = net.minecraft.resources.ResourceLocation.tryParse(entry.getAsString());
            if (id != null) into.add(id);
        }
    }

    private final Map<UUID, Home> homes = new HashMap<>();

    public HomeResidentDepth() {}

    public static HomeResidentDepth of(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(HomeResidentDepth::new, HomeResidentDepth::load), FILE);
    }

    /** The home's record, created on first use. Callers that change it must {@link #setDirty}. */
    public Home home(UUID owner) { return homes.computeIfAbsent(owner, o -> new Home()); }
    public Optional<Home> peek(UUID owner) { return Optional.ofNullable(homes.get(owner)); }
    /** Probe cleanup only. */
    public void forget(UUID owner) { if (homes.remove(owner) != null) setDirty(); }
    public ResidentRank rank(UUID owner, NpcRole role) {
        var home = homes.get(owner);
        var r = home == null ? null : home.roles.get(role);
        return r == null ? ResidentRank.APPRENTICE : r.rank;
    }

    public static HomeResidentDepth load(CompoundTag tag, HolderLookup.Provider registries) {
        var data = new HomeResidentDepth();
        var list = tag.getList("homes", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            var e = list.getCompound(i);
            if (e.hasUUID("owner")) data.homes.put(e.getUUID("owner"), Home.load(e));
        }
        return data;
    }

    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        var list = new ListTag();
        homes.forEach((owner, home) -> {
            var e = home.save();
            e.putUUID("owner", owner);
            list.add(e);
        });
        tag.put("homes", list);
        return tag;
    }
}
