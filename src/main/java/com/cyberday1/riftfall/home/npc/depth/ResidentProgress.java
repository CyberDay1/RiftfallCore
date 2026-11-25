package com.cyberday1.riftfall.home.npc.depth;

import com.cyberday1.riftfall.RiftfallCore;
import com.cyberday1.riftfall.block.marker.NpcRole;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.*;
import java.util.function.Predicate;

/**
 * Per-player resident record: runs settled, online time (the work-order fallback clock), the last run's facts for
 * dialogue, what each resident has already said, request rewards not yet handed over, Quarry surveys, the Cartographer's atlas
 * and the story notes read.
 * On the overworld, so a
 * run that settles while the player is offline still counts.
 */
public final class ResidentProgress extends SavedData {
    private static final String FILE = RiftfallCore.MODID + "_resident_progress";

    /** The last settled run, as dialogue reads it. {@code id} is the player's settled-run count at that run. */
    public record Last(int id, int tier, boolean cleared, boolean newBest, Optional<ResourceLocation> boss, boolean died) {}

    /** The line a resident is showing this player, kept until the next run so it does not change on reopen. */
    public record Line(ResidentDialogue.Trigger trigger, int variant, int arg, int at) {}

    /** One theme in a player's atlas: runs entered, clears, the best tier cleared and bosses killed there. */
    public static final class Chart {
        int visits, clears, best;
        final Map<ResourceLocation, Integer> bosses = new TreeMap<>(Comparator.comparing(ResourceLocation::toString));
        public int visits() { return visits; }
        public int clears() { return clears; }
        public int best() { return best; }
        public Map<ResourceLocation, Integer> bosses() { return Collections.unmodifiableMap(bosses); }
    }

    public static final class Seen {
        int report = -1, rank, completed, visit = -1;
        /** Recognition lines already said, a bit per line (ResidentStory). */
        int noted;
        Line line;
        public int noted() { return noted; }
    }

    public static final class Player {
        int runsSettled, deathStreak;
        long onlineTicks;
        Last last;
        final EnumMap<NpcRole, Seen> seen = new EnumMap<>(NpcRole.class);
        final List<ItemStack> pending = new ArrayList<>();
        final Set<ResourceLocation> discoveries = new TreeSet<>();
        final Map<ResourceLocation, Chart> atlas = new TreeMap<>(Comparator.comparing(ResourceLocation::toString));
        final Set<String> notes = new TreeSet<>();
        /** Worlds whose cores this player fed to the Core, oldest first: the atlas's list of the dead. */
        final List<com.cyberday1.riftfall.lore.ShardOrigin> fallen = new ArrayList<>();
        /** The last respawn line this player heard (RespawnLines key), so it is not said twice in a row. */
        String respawnLine = "";

        public int runsSettled() { return runsSettled; }
        public long onlineTicks() { return onlineTicks; }
        public int deathStreak() { return deathStreak; }
        public Optional<Last> last() { return Optional.ofNullable(last); }
        public Seen seen(NpcRole role) { return seen.computeIfAbsent(role, r -> new Seen()); }
        public List<ItemStack> pending() { return Collections.unmodifiableList(pending); }
        /** Materials this player has surveyed at the Quarry. */
        public Set<ResourceLocation> discoveries() { return Collections.unmodifiableSet(discoveries); }
        /** True when this is a new discovery. */
        public boolean discover(ResourceLocation id) { return discoveries.add(id); }

        /** Records one settled run and returns its id. */
        public int settled(int tier, boolean cleared, boolean newBest, Optional<ResourceLocation> boss, boolean died) {
            runsSettled++;
            deathStreak = died ? deathStreak + 1 : 0;
            last = new Last(runsSettled, tier, cleared, newBest, boss, died);
            return runsSettled;
        }

        public void online(long ticks) { onlineTicks += Math.max(0, ticks); }

        /** The Cartographer's atlas, by theme id. Filled from every settled run, whether or not the Cartographer is home yet. */
        public Map<ResourceLocation, Chart> atlas() { return Collections.unmodifiableMap(atlas); }
        /** Story notes this player has read (LoreNotes ids); use NoteFinds to record one. */
        public Set<String> notes() { return Collections.unmodifiableSet(notes); }
        /** True when this is the first read. */
        public boolean readNote(String id) { return notes.add(id); }

        public List<com.cyberday1.riftfall.lore.ShardOrigin> fallen() { return Collections.unmodifiableList(fallen); }
        public void fed(com.cyberday1.riftfall.lore.ShardOrigin origin) { fallen.add(origin); }
        public String respawnLine() { return respawnLine; }
        public void respawnLine(String key) { respawnLine = key; }

        public boolean visited(ResourceLocation theme) { var c = atlas.get(theme); return c != null && c.visits > 0; }

        /** Records one settled run in its theme's chart; a run with no theme records nothing. */
        public void chart(Optional<ResourceLocation> theme, int tier, boolean cleared, List<ResourceLocation> bosses) {
            if (theme.isEmpty()) return;
            var c = atlas.computeIfAbsent(theme.get(), t -> new Chart());
            c.visits++;
            if (cleared) { c.clears++; c.best = Math.max(c.best, tier); }
            for (var boss : bosses) c.bosses.merge(boss, 1, Integer::sum);
        }
    }

    private final Map<UUID, Player> players = new HashMap<>();

    public ResidentProgress() {}

    public static ResidentProgress of(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(ResidentProgress::new, ResidentProgress::load), FILE);
    }

    public Player player(UUID id) { return players.computeIfAbsent(id, k -> new Player()); }
    /** Probe cleanup only. */
    public void forget(UUID id) { if (players.remove(id) != null) setDirty(); }

    public void reward(UUID id, List<ItemStack> stacks) {
        for (var stack : stacks) if (!stack.isEmpty()) player(id).pending.add(stack.copy());
        setDirty();
    }

    /** Offers pending rewards in order; a partial acceptance keeps the remainder, as {@code RiftClaims#handBack}. */
    public int handBack(UUID id, Predicate<ItemStack> into) {
        var p = players.get(id);
        if (p == null || p.pending.isEmpty()) return 0;
        int given = 0;
        var left = new ArrayList<ItemStack>();
        for (var stack : p.pending) {
            if (into.test(stack) || stack.isEmpty()) given++;
            else left.add(stack);
        }
        p.pending.clear();
        p.pending.addAll(left);
        setDirty();
        return given;
    }

    public static ResidentProgress load(CompoundTag tag, HolderLookup.Provider registries) {
        var data = new ResidentProgress();
        var list = tag.getList("players", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            var e = list.getCompound(i);
            if (!e.hasUUID("id")) continue;
            var p = data.player(e.getUUID("id"));
            p.runsSettled = e.getInt("runsSettled");
            p.deathStreak = e.getInt("deathStreak");
            p.onlineTicks = e.getLong("onlineTicks");
            if (e.contains("last")) {
                var l = e.getCompound("last");
                p.last = new Last(l.getInt("id"), l.getInt("tier"), l.getBoolean("cleared"), l.getBoolean("newBest"),
                        l.contains("boss") ? Optional.ofNullable(ResourceLocation.tryParse(l.getString("boss"))) : Optional.empty(),
                        l.getBoolean("died"));
            }
            var seen = e.getList("seen", Tag.TAG_COMPOUND);
            for (int s = 0; s < seen.size(); s++) {
                var se = seen.getCompound(s);
                for (var role : NpcRole.values()) {
                    if (!role.id().equals(se.getString("role"))) continue;
                    var st = p.seen(role);
                    st.report = se.getInt("report"); st.rank = se.getInt("rank");
                    st.completed = se.getInt("completed"); st.visit = se.getInt("visit");
                    st.noted = se.getInt("noted");
                    if (se.contains("trigger")) {
                        var trigger = ResidentDialogue.Trigger.byId(se.getString("trigger"));
                        if (trigger != null) st.line = new Line(trigger, se.getInt("variant"), se.getInt("arg"), se.getInt("at"));
                    }
                }
            }
            for (var item : e.getList("pending", Tag.TAG_COMPOUND))
                ItemStack.parse(registries, (CompoundTag) item).ifPresent(p.pending::add);
            for (var found : e.getList("discoveries", Tag.TAG_STRING)) {
                var id = ResourceLocation.tryParse(found.getAsString());
                if (id != null) p.discoveries.add(id);
            }
            for (var note : e.getList("notes", Tag.TAG_STRING)) p.notes.add(note.getAsString());
            for (var dead : e.getList("fallen", Tag.TAG_COMPOUND))
                com.cyberday1.riftfall.lore.ShardOrigin.load((CompoundTag) dead).ifPresent(p.fallen::add);
            p.respawnLine = e.getString("respawnLine");
            for (var entry : e.getList("atlas", Tag.TAG_COMPOUND)) {
                var a = (CompoundTag) entry;
                var theme = ResourceLocation.tryParse(a.getString("theme"));
                if (theme == null) continue;
                var c = p.atlas.computeIfAbsent(theme, t -> new Chart());
                c.visits = a.getInt("visits"); c.clears = a.getInt("clears"); c.best = a.getInt("best");
                for (var b : a.getList("bosses", Tag.TAG_COMPOUND)) {
                    var bt = (CompoundTag) b;
                    var boss = ResourceLocation.tryParse(bt.getString("id"));
                    if (boss != null) c.bosses.put(boss, bt.getInt("kills"));
                }
            }
        }
        return data;
    }

    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        var list = new ListTag();
        players.forEach((id, p) -> {
            var e = new CompoundTag();
            e.putUUID("id", id);
            e.putInt("runsSettled", p.runsSettled);
            e.putInt("deathStreak", p.deathStreak);
            e.putLong("onlineTicks", p.onlineTicks);
            if (p.last != null) {
                var l = new CompoundTag();
                l.putInt("id", p.last.id()); l.putInt("tier", p.last.tier()); l.putBoolean("cleared", p.last.cleared());
                l.putBoolean("newBest", p.last.newBest()); l.putBoolean("died", p.last.died());
                p.last.boss().ifPresent(b -> l.putString("boss", b.toString()));
                e.put("last", l);
            }
            var seen = new ListTag();
            p.seen.forEach((role, st) -> {
                var se = new CompoundTag();
                se.putString("role", role.id());
                se.putInt("report", st.report); se.putInt("rank", st.rank);
                se.putInt("completed", st.completed); se.putInt("visit", st.visit);
                se.putInt("noted", st.noted);
                if (st.line != null) {
                    se.putString("trigger", st.line.trigger().id());
                    se.putInt("variant", st.line.variant()); se.putInt("arg", st.line.arg()); se.putInt("at", st.line.at());
                }
                seen.add(se);
            });
            e.put("seen", seen);
            var pending = new ListTag();
            for (var stack : p.pending) if (!stack.isEmpty()) pending.add(stack.save(registries));
            e.put("pending", pending);
            var found = new ListTag();
            for (var known : p.discoveries) found.add(net.minecraft.nbt.StringTag.valueOf(known.toString()));
            e.put("discoveries", found);
            var atlas = new ListTag();
            p.atlas.forEach((theme, c) -> {
                var a = new CompoundTag();
                a.putString("theme", theme.toString());
                a.putInt("visits", c.visits); a.putInt("clears", c.clears); a.putInt("best", c.best);
                var bosses = new ListTag();
                c.bosses.forEach((boss, kills) -> {
                    var bt = new CompoundTag();
                    bt.putString("id", boss.toString());
                    bt.putInt("kills", kills);
                    bosses.add(bt);
                });
                a.put("bosses", bosses);
                atlas.add(a);
            });
            e.put("atlas", atlas);
            var notes = new ListTag();
            for (var note : p.notes) notes.add(net.minecraft.nbt.StringTag.valueOf(note));
            e.put("notes", notes);
            var fallen = new ListTag();
            for (var dead : p.fallen) fallen.add(dead.save());
            e.put("fallen", fallen);
            if (!p.respawnLine.isEmpty()) e.putString("respawnLine", p.respawnLine);
            list.add(e);
        });
        tag.put("players", list);
        return tag;
    }
}
