package com.cyberday1.riftfall.story;

import com.cyberday1.riftfall.RiftfallCore;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * The campaign's record: what each player has proved and what each home holds. Overworld storage, like
 * {@code RiftStats}, so it outlives rifts, restarts and an offline player. Storage only; {@link StoryBridge} acts.
 *
 * <p>
 * Ids are kept as strings, so a milestone, resident or receipt this jar does not know survives a round trip. A
 * record from a newer version, or one that will not parse, is carried back out untouched rather than dropped:
 * {@code SavedData} writes back what it holds, so a skipped record would be deleted at the next autosave.
 */
public final class StoryState extends SavedData {
    public static final int VERSION = 1;
    public static final String FILE = RiftfallCore.MODID + "_story";
    private static final String PLAYERS = "players", HOMES = "homes", VERSION_KEY = "version", PLAYER = "player",
            OWNER = "owner", MILESTONES = "milestones", RESIDENTS = "residents", RECEIPTS = "receipts", TILES = "tiles";

    /** One player's facts: milestone id to its evidence (a run id, a role, or empty). */
    private static final class Personal {
        final Map<String, String> milestones = new LinkedHashMap<>();
        final Set<String> residents = new LinkedHashSet<>();
        final Set<String> receipts = new LinkedHashSet<>();
    }

    private static final class Home {
        final Set<String> milestones = new LinkedHashSet<>();
        int tiles;
    }

    private final Map<UUID, Personal> players = new HashMap<>();
    private final Map<UUID, Home> homes = new HashMap<>();
    private final List<CompoundTag> foreignPlayers = new ArrayList<>();
    private final List<CompoundTag> foreignHomes = new ArrayList<>();

    StoryState() {
    }

    public static StoryState of(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(StoryState::new, StoryState::load), FILE);
    }

    /** Never throws: a record it cannot read is kept as it was. */
    public static StoryState load(CompoundTag tag, HolderLookup.Provider registries) {
        StoryState state = new StoryState();
        ListTag people = tag.getList(PLAYERS, Tag.TAG_COMPOUND);
        for (int i = 0; i < people.size(); i++) {
            CompoundTag record = people.getCompound(i);
            if (!state.readPlayer(record)) state.foreignPlayers.add(record.copy());
        }
        ListTag places = tag.getList(HOMES, Tag.TAG_COMPOUND);
        for (int i = 0; i < places.size(); i++) {
            CompoundTag record = places.getCompound(i);
            if (!state.readHome(record)) state.foreignHomes.add(record.copy());
        }
        return state;
    }

    private boolean readPlayer(CompoundTag record) {
        int version = record.getInt(VERSION_KEY);
        if (version < 1 || version > VERSION || !record.hasUUID(PLAYER)) return false;
        try {
            Personal personal = new Personal();
            CompoundTag milestones = record.getCompound(MILESTONES);
            for (String key : milestones.getAllKeys()) personal.milestones.put(key, milestones.getString(key));
            strings(record.getList(RESIDENTS, Tag.TAG_STRING), personal.residents);
            strings(record.getList(RECEIPTS, Tag.TAG_STRING), personal.receipts);
            players.put(record.getUUID(PLAYER), personal);
            return true;
        } catch (RuntimeException unreadable) {
            return false;
        }
    }

    private boolean readHome(CompoundTag record) {
        int version = record.getInt(VERSION_KEY);
        if (version < 1 || version > VERSION || !record.hasUUID(OWNER)) return false;
        try {
            Home home = new Home();
            strings(record.getList(MILESTONES, Tag.TAG_STRING), home.milestones);
            home.tiles = Math.max(0, record.getInt(TILES));
            homes.put(record.getUUID(OWNER), home);
            return true;
        } catch (RuntimeException unreadable) {
            return false;
        }
    }

    private static void strings(ListTag list, Set<String> into) {
        for (int i = 0; i < list.size(); i++) into.add(list.getString(i));
    }

    private static ListTag list(Set<String> values) {
        ListTag list = new ListTag();
        values.forEach(value -> list.add(StringTag.valueOf(value)));
        return list;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag people = new ListTag();
        players.forEach((player, personal) -> {
            CompoundTag record = new CompoundTag();
            record.putInt(VERSION_KEY, VERSION);
            record.putUUID(PLAYER, player);
            CompoundTag milestones = new CompoundTag();
            personal.milestones.forEach(milestones::putString);
            record.put(MILESTONES, milestones);
            record.put(RESIDENTS, list(personal.residents));
            record.put(RECEIPTS, list(personal.receipts));
            people.add(record);
        });
        foreignPlayers.forEach(record -> people.add(record.copy()));
        ListTag places = new ListTag();
        homes.forEach((owner, home) -> {
            CompoundTag record = new CompoundTag();
            record.putInt(VERSION_KEY, VERSION);
            record.putUUID(OWNER, owner);
            record.put(MILESTONES, list(home.milestones));
            record.putInt(TILES, home.tiles);
            places.add(record);
        });
        foreignHomes.forEach(record -> places.add(record.copy()));
        tag.put(PLAYERS, people);
        tag.put(HOMES, places);
        return tag;
    }

    private Personal personal(UUID player) { return players.computeIfAbsent(player, key -> new Personal()); }

    private Home home(UUID owner) { return homes.computeIfAbsent(owner, key -> new Home()); }

    /** Records a personal milestone once. Returns whether this call recorded it; the first evidence is kept. */
    public boolean record(UUID player, StoryMilestone milestone, String evidence) {
        if (milestone.owner() != StoryMilestone.Owner.PLAYER) throw new IllegalArgumentException(milestone + " is not personal");
        if (personal(player).milestones.putIfAbsent(milestone.id(), evidence == null ? "" : evidence) != null) return false;
        setDirty();
        return true;
    }

    public boolean has(UUID player, StoryMilestone milestone) {
        Personal personal = players.get(player);
        return personal != null && personal.milestones.containsKey(milestone.id());
    }

    public Optional<String> evidence(UUID player, StoryMilestone milestone) {
        Personal personal = players.get(player);
        return personal == null ? Optional.empty() : Optional.ofNullable(personal.milestones.get(milestone.id()));
    }

    /** The personal milestones this jar knows, in declaration order. */
    public Set<StoryMilestone> milestones(UUID player) {
        Set<StoryMilestone> known = EnumSet.noneOf(StoryMilestone.class);
        Personal personal = players.get(player);
        if (personal != null) {
            for (String id : personal.milestones.keySet()) {
                StoryMilestone milestone = StoryMilestone.byId(id);
                if (milestone != null) known.add(milestone);
            }
        }
        return known;
    }

    /** Records a home milestone once. */
    public boolean recordHome(UUID owner, StoryMilestone milestone) {
        if (milestone.owner() != StoryMilestone.Owner.HOME) throw new IllegalArgumentException(milestone + " is not a home milestone");
        if (!home(owner).milestones.add(milestone.id())) return false;
        setDirty();
        return true;
    }

    public Set<StoryMilestone> homeMilestones(UUID owner) {
        Set<StoryMilestone> known = EnumSet.noneOf(StoryMilestone.class);
        Home home = homes.get(owner);
        if (home != null) {
            for (String id : home.milestones) {
                StoryMilestone milestone = StoryMilestone.byId(id);
                if (milestone != null) known.add(milestone);
            }
        }
        return known;
    }

    /** The highest tile count the story has seen for this home; never goes down. */
    public void seeTiles(UUID owner, int tiles) {
        Home home = home(owner);
        if (tiles > home.tiles) {
            home.tiles = tiles;
            setDirty();
        }
    }

    public int tiles(UUID owner) {
        Home home = homes.get(owner);
        return home == null ? 0 : home.tiles;
    }

    /** Records that this player has spoken to a resident of this role. Returns whether it is the first time. */
    public boolean meet(UUID player, String role) {
        if (!personal(player).residents.add(role)) return false;
        setDirty();
        return true;
    }

    public Set<String> residentsMet(UUID player) {
        Personal personal = players.get(player);
        return personal == null ? Set.of() : Collections.unmodifiableSet(personal.residents);
    }

    /**
     * A one-time delivery: true exactly once per player and receipt, across restarts. Call it in the same tick as
     * the effect it guards, and only deliver on true.
     */
    public boolean firstTime(UUID player, String receipt) {
        if (!personal(player).receipts.add(receipt)) return false;
        setDirty();
        return true;
    }

    public boolean delivered(UUID player, String receipt) {
        Personal personal = players.get(player);
        return personal != null && personal.receipts.contains(receipt);
    }

    public int foreignCount() { return foreignPlayers.size() + foreignHomes.size(); }

    /** Drops one player's and one home's records: probe identities only, so each rig run starts fresh. */
    public void forget(UUID id) {
        if (players.remove(id) != null | homes.remove(id) != null) setDirty();
    }
}
