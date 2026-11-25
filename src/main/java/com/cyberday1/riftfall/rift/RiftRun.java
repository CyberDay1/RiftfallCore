package com.cyberday1.riftfall.rift;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * One trip into one rift: the dungeon, the frame it was entered through, and everyone who has been
 * through that frame since it was lit.
 *
 * <p>
 * A run is the thing that makes a rift finite. Without one a frame is a permanent door &mdash; the
 * binding from frame to level was deliberately transient, so a closed rift left the frame leading
 * nowhere and the next player through built a fresh dungeon behind it, and one key bought unlimited
 * rifts. The run owns that binding now, and it is the run that ends rather than the level: ending it
 * puts the frame out and takes the level down together, so there is no state in which one has
 * happened and the other has not.
 *
 * <h2>Persisted, with its level</h2>
 * Ruled 2026-10-11: a paused solo run has no time limit and survives a restart. So a run still
 * going when the server stops is written by {@link RiftRunStore}, and its level stays on disk;
 * {@link #save} and {@link #load} are the whole record. A run that ended is gone, as before.
 *
 * <h2>The roster is the lockout</h2>
 * Being handed a destination is what puts a player on the roster, and being on it is what stops them
 * walking in again. The two are the same set because they are the same fact: you have been through
 * this portal. A player who dies inside is still on it, which is the point &mdash; there is no going
 * back for your things &mdash; and the roster dies with the run, so a frame relit with a fresh key is
 * open to everybody again. It is a lock on the run, not a ban from the frame.
 *
 * <p>
 * Handing out the destination is very nearly a safe commitment point: in 1.21.1
 * {@code Entity#handlePortal} calls {@code getPortalDestination} and then teleports &mdash;
 * {@code isLevelEnabled} and {@code canChangeDimensions} both return true for everything, and a
 * passenger never reaches the call at all because {@code canUsePortal} rejects it first. The one
 * thing left that can refuse is another mod: {@code Entity#changeDimension} opens by returning null
 * if {@code CommonHooks.onTravelToDimension} is cancelled, which would leave a player on the roster
 * who never arrived. Nothing here defends against that, because there is no hook after the arrival
 * to write the roster from instead; it is recorded rather than guarded. The anchor, which teleports
 * itself, does check the return and keeps itself unspent.
 */
public final class RiftRun {

    /**
     * How a run finished. A run always ends the same way, by emptying; this is why it emptied.
     *
     * <p>
     * {@code CLEARED} is written by {@code RiftBosses#openTheWayHome} when a tracked boss dies, and
     * by nothing else. It was unreachable when it was first written and stayed that way until that
     * one call landed, which is why the rig has an arm whose whole job is to prove a boss death
     * still reaches it.
     */
    public enum Verdict {

        /** Nobody got out. */
        WIPED,

        /** Somebody walked out under their own power. */
        ESCAPED,

        /** The dungeon was beaten. */
        CLEARED
    }

    /**
     * How long a run may stand empty before it has ever held anybody, in ticks.
     *
     * <p>
     * This is the grace window, and it exists because a rift is made empty: {@link RiftLevels#create}
     * returns a level with nobody in it, and the player who caused it arrives afterwards. A plain
     * "nobody is in here" test would therefore destroy every rift at the instant it was built. The
     * run is not allowed to end until it has seen somebody in it at least once.
     *
     * <p>
     * Thirty seconds is far longer than the gap it covers &mdash; the arrival is in the same tick,
     * because {@code handlePortal} teleports immediately after asking for the destination and this is
     * counted on {@code ServerTickEvent.Post} once every level has ticked. The window is wide because
     * it is not really a timeout for the arrival, it is the backstop for a rift nobody ever reaches,
     * which is the only way one could otherwise leak until the server stops.
     */
    static final int GRACE_TICKS = 600;

    /**
     * A lit frame, named by the level it stands in and the bottom-left corner of its opening.
     *
     * <p>
     * The level is part of the name because runs are tracked for the whole server while
     * {@link RiftPortals} is per level: two frames at the same coordinates in two dimensions are two
     * frames, and only the pair tells them apart.
     */
    public record Frame(ResourceKey<Level> level, BlockPos corner) {

        public Frame {
            corner = corner.immutable();
        }
    }

    private final ResourceKey<Level> rift;

    /** Null once the frame has been broken out from under the run; see {@link #frameBroken()}. */
    @Nullable
    private Frame frame;

    private final Map<UUID, RiftEntry> entrants = new HashMap<>();

    /** What each entrant was carrying on the way in; see {@link #carriedIn}. */
    private final Map<UUID, List<ItemStack>> kits = new HashMap<>();

    /** Who reached the far side of a way out; see {@link #verdict}. */
    private final Set<UUID> escaped = new HashSet<>();

    /** Who died in here; see {@link #died(UUID)}. */
    private final Set<UUID> fallen = new HashSet<>();

    /** Shared kill credits earned while each entrant participated; see {@link #partyKilled}. */
    private final Map<UUID, Integer> kills = new HashMap<>();

    private boolean cleared;

    /** Set when the dungeon was beaten and no way home could be opened; see {@link #finishing()}. */
    private boolean finishing;

    private boolean reached;
    private boolean locked;
    private int waiting;

    /** How long this run has been going, in ticks; see {@link #advance}. */
    private int elapsed;

    private boolean expired;

    /** Entrants who logged out inside and have not come back; see {@link #advance}. */
    private final Set<UUID> away = new HashSet<>();

    /** Who was holding the run on the last tick anybody was; see {@link #paused}. */
    private Set<UUID> present = Set.of();

    /** XP already banked per entrant before the ending; see {@link #settled}. */
    private final Map<UUID, Long> paid = new HashMap<>();

    /** The key's tier, which prices the clear and the kills. */
    private final int tier;

    /** The theme the generator rendered, for the settle report; null when it could not be read. */
    @Nullable
    private ResourceLocation theme;

    /** Kill credits by entity type, alongside {@link #kills}; read as tag counts by the settle report. */
    private final Map<UUID, Map<ResourceLocation, Integer>> typeKills = new HashMap<>();

    /** Bosses credited to each player, the same players {@code RiftBossLoot} rolls for. */
    private final Map<UUID, List<ResourceLocation>> bosses = new HashMap<>();

    /** What each entrant found, counted for the recap; see {@link RiftRecaps}. */
    private final Map<UUID, RiftHaul> hauls = new HashMap<>();

    RiftRun(ResourceKey<Level> rift, Frame frame) {
        this(rift, frame, RiftKeyItem.DEFAULT_TIER);
    }

    RiftRun(ResourceKey<Level> rift, Frame frame, int tier) {
        this.rift = rift;
        this.frame = frame;
        this.tier = Math.max(RiftKeyItem.DEFAULT_TIER, tier);
    }

    /** The tier this run was opened at. */
    public int tier() {
        return tier;
    }

    public ResourceKey<Level> rift() {
        return rift;
    }

    @Nullable
    public Frame frame() {
        return frame;
    }

    /**
     * Whether anybody has actually stood in this rift yet.
     *
     * <p>
     * Only worth asking for logging and for tests: the answer is folded into {@link #advance}, which
     * is the one place it decides anything.
     */
    public boolean reached() {
        return reached;
    }

    /** Whether this player has already been through the frame on this run. */
    public boolean hasEntered(UUID player) {
        return entrants.containsKey(player);
    }

    /** Everybody who has been through the frame, which is who a verdict is answered for. */
    public Set<UUID> entrants() {
        return Collections.unmodifiableSet(entrants.keySet());
    }

    /**
     * Where this player came from, or null if they have never been through the frame.
     *
     * <p>
     * The roster doubles as the address book because everybody who is entitled to a way out is on
     * it and nobody else is. A mob that followed the party in has no record here and so has no way
     * out, which is the answer we want rather than one that has to be tested for separately.
     */
    @Nullable
    public RiftEntry entry(UUID player) {
        return entrants.get(player);
    }

    /**
     * What a player was carrying when they walked in, which is empty for anybody the roster does not
     * know.
     *
     * <p>
     * The snapshot is taken at the door because the door is the only moment it can be taken: once
     * somebody is inside, what they are holding is a mixture of what they brought and what the dungeon
     * gave them, and nothing afterwards can tell the two apart. It stays a picture of the inventory at
     * that instant however the real one is spent, damaged or thrown away in the hour that follows.
     *
     * <p>
     * {@link RiftLoot} reads it, and reads only half of it: it is the allowance for everything that
     * stacks, which is the half of a party's kit that cannot be told apart item by item. Anything that
     * does not stack is marked on the way in instead, so this list is a count rather than a manifest.
     * It lives on the run and dies with it, because it describes one trip and a trip is what a run is.
     */
    public List<ItemStack> carriedIn(UUID player) {
        return Collections.unmodifiableList(kits.getOrDefault(player, List.of()));
    }

    /**
     * Puts a player on the roster, along with what they brought.
     *
     * <p>
     * The first entry is the one kept, for both halves. A second call is a player who has walked in,
     * been sent home by something that is not this run ending, and walked in again &mdash; not a case
     * the portal allows, but if it ever became one the doorstep worth remembering is still the first
     * one, which is the only position known to be outside every rift, and so is the kit, which is the
     * only one that had not already been through a dungeon.
     *
     * <p>
     * The stacks are copied here rather than at the call site, so that the record is a picture no
     * matter who takes it: the caller is handing over an inventory's own stacks, which are about to be
     * worn down and eaten and thrown away, and one copy in the one place that keeps them is easier to
     * be sure of than a rule every future caller has to remember.
     */
    void entering(UUID player, RiftEntry entry, List<ItemStack> carried) {
        if (entrants.putIfAbsent(player, entry) == null) {
            kits.put(player, carried.stream().map(ItemStack::copy).toList());
        }
    }

    /**
     * Records that a player got out alive. Written at the far end of the trip rather than at the point
     * they asked for it, so a way out that something else refuses does not count as one taken.
     */
    void escaped(UUID player) {
        escaped.add(player);
    }

    /** Who got out alive, for anything that wants the names rather than the verdict. */
    public Set<UUID> escaped() {
        return Collections.unmodifiableSet(escaped);
    }

    /**
     * Records that a player died in here, if the roster knows them.
     *
     * <p>
     * The roster is the gate, and it is the gate for {@link #killed} too, so an operator who
     * teleported into somebody else's dungeon neither accrues anything nor loses anything. Stated
     * once here rather than at the listener, so there is one spelling of "is this run yours".
     */
    void died(UUID player) {
        if (entrants.containsKey(player)) {
            fallen.add(player);
        }
    }

    /**
     * Who died in here, which is who a payout is quartered for.
     *
     * <p>
     * ⚠ Dying is not the same as not getting out. A player who logged out inside is on neither
     * this set nor {@link #escaped()}, and is paid as somebody who simply did not bring the run
     * home &mdash; the full cost is in the stat-earning model.
     */
    public Set<UUID> died() {
        return Collections.unmodifiableSet(fallen);
    }

    /** Records one recipient's kill credit. Live deaths use {@link #partyKilled}. */
    void killed(UUID player) {
        killed(player, null);
    }

    void killed(UUID player, @Nullable ResourceLocation type) {
        if (entrants.containsKey(player)) {
            kills.merge(player, 1, Integer::sum);
            if (type != null) {
                typeKills.computeIfAbsent(player, key -> new HashMap<>()).merge(type, 1, Integer::sum);
            }
        }
    }

    /** Kill credits by entity type for one player. */
    public Map<ResourceLocation, Integer> typeKills(UUID player) {
        return Collections.unmodifiableMap(typeKills.getOrDefault(player, Map.of()));
    }

    /** Credits a boss to the players standing in the rift, the boss-loot rule. Roster members only. */
    void bossKilled(ResourceLocation type, Collection<UUID> participants) {
        for (UUID player : new HashSet<>(participants)) {
            if (entrants.containsKey(player) && !fallen.contains(player) && !escaped.contains(player)) {
                bosses.computeIfAbsent(player, key -> new ArrayList<>()).add(type);
            }
        }
    }

    public List<ResourceLocation> bosses(UUID player) {
        return Collections.unmodifiableList(bosses.getOrDefault(player, List.of()));
    }

    /** What this entrant has found so far; empty for anybody the roster does not know. */
    public RiftHaul haul(UUID player) {
        return hauls.getOrDefault(player, RiftHaul.EMPTY);
    }

    /** Replaces an entrant's haul. Roster members only, as with {@link #killed}. */
    void haul(UUID player, RiftHaul haul) {
        if (entrants.containsKey(player)) {
            hauls.put(player, haul);
        }
    }

    @Nullable
    public ResourceLocation theme() {
        return theme;
    }

    void theme(@Nullable ResourceLocation theme) {
        this.theme = theme;
    }

    /**
     * A roster member's kill pays a full credit to every living participant currently in the rift.
     * The caller supplies the live, non-spectating occupants; the run excludes departed and fallen
     * entrants as well. Credits remain per player so exits, deaths and saves retain their exact
     * earned totals. Joining or reconnecting never grants kills from before that moment.
     */
    void partyKilled(UUID killer, Collection<UUID> participants) {
        partyKilled(killer, participants, null);
    }

    void partyKilled(UUID killer, Collection<UUID> participants, @Nullable ResourceLocation type) {
        if (!entrants.containsKey(killer)) {
            return;
        }
        for (UUID player : new HashSet<>(participants)) {
            if (!fallen.contains(player) && !escaped.contains(player) && !away.contains(player)) {
                killed(player, type);
            }
        }
    }

    /** How many kills this run credits to a player, which for anybody it does not know is none. */
    public int kills(UUID player) {
        return kills.getOrDefault(player, 0);
    }

    /** Records that the dungeon was beaten. */
    void cleared() {
        this.cleared = true;
    }

    /**
     * Whether this run is to be wound up on the next sweep rather than when its rift empties.
     *
     * <p>
     * Set only by {@link RiftRuns#cleared}, for the one state a beaten run must not be left in: an
     * arena with no way home standing in it. Read beside {@link #advance} in {@code RiftRuns#tick},
     * so the ending happens where every other ending happens and nothing tears a level down from
     * inside the boss's own death.
     */
    public boolean finishing() {
        return finishing;
    }

    /** Says the run is to be wound up. Sticky, as the flag it accompanies is. */
    void finish() {
        this.finishing = true;
    }

    /**
     * Whether this run's roster is closed, which is what stops a stranger walking into a dungeon
     * already under way.
     *
     * <p>
     * Ruled 2026-09-21: <em>"Rift parties should be the runs roster, which is locked once someone
     * leaves the portal room"</em>. {@link RiftRuns#pastThePortalRoom} is what "left" is measured
     * as, and {@link RiftPortalBlock#enter} is where the refusal happens.
     */
    public boolean locked() {
        return locked;
    }

    /**
     * Closes the roster, for good. Sticky because the ruling is about an event rather than a state:
     * a party that regroups at the tear has still started, and a roster that reopened behind them
     * would let a stranger into a half-cleared dungeon.
     */
    void lock() {
        this.locked = true;
    }

    /**
     * Why this run emptied.
     *
     * <p>
     * Read at the end, from what the ways out wrote down on their way past. Clearing outranks escaping
     * because a party that beats a boss then walks home has done both and only one of them is the
     * headline; escaping outranks wiping because one survivor is not a wipe.
     */
    public Verdict verdict() {
        if (cleared) {
            return Verdict.CLEARED;
        }
        return escaped.isEmpty() ? Verdict.WIPED : Verdict.ESCAPED;
    }

    /**
     * Forgets the frame, because somebody has broken it while the run is still going.
     *
     * <p>
     * The run carries on. Breaking the frame from the outside is not one of the two things that end a
     * run, and a party is not stranded by it either: when the run does end the level comes down and
     * {@link RiftLevels#close} sends whoever is left home. There is simply no longer a frame to put
     * out at the end, and it is already dark.
     */
    void frameBroken() {
        frame = null;
    }

    /**
     * How long this run has been going, in ticks. Counted from the moment the dungeon was built
     * rather than from the first arrival, because the limit is a property of the run.
     */
    public int elapsed() {
        return elapsed;
    }

    /**
     * Whether this run ended because its time ran out, rather than because it emptied. Read at the
     * ending: expiry is a loss for everybody who had not already walked out.
     */
    public boolean expired() {
        return expired;
    }

    /**
     * Advances the run by one tick and says whether it is over, for any of the three reasons.
     *
     * <p>
     * ⛔ {@link #advance} is on the left of the {@code ||} and must stay there: it counts the tick,
     * so short-circuiting past it would stop the clock for a run that was winding up anyway. The
     * third reason is {@link #finishing()}, which nothing else can reach.
     */
    boolean over(Set<UUID> holders, int limitTicks) {
        return advance(holders, limitTicks) || finishing;
    }

    /** {@link #over(Set, int)} for holders nobody knows by name, who can never pause a run. */
    boolean over(int holders, int limitTicks) {
        return over(anonymous(holders), limitTicks);
    }

    /**
     * Advances the run by one tick and says whether its rift has emptied or its clock has run out.
     *
     * <p>
     * {@code holders} is who is holding the rift open this tick. A wipe and everybody walking out
     * both arrive here as empty and end the run; {@link #verdict} tells them apart afterwards.
     *
     * <p>
     * ⛔⛔ An empty rift whose last holders <em>logged out</em> is not over: it is paused (R-4,
     * R-13) with no time limit (ruled 2026-10-11), and the clock stops with it. Decided by who
     * left last, not by who is away: an entrant who logged out earlier does not hold open a run the
     * rest then died in (R-14).
     *
     * <p>
     * {@code limitTicks} is how long the run may last, or zero for no limit. Running out ends the
     * run whatever the count says, so it is asked before the count.
     */
    boolean advance(Set<UUID> holders, int limitTicks) {
        if (holders.isEmpty() && paused()) {
            return false;
        }
        elapsed++;
        if (limitTicks > 0 && elapsed >= limitTicks) {
            expired = true;
            return true;
        }
        if (!holders.isEmpty()) {
            reached = true;
            if (!present.equals(holders)) {
                present = Set.copyOf(holders);
            }
            return false;
        }
        return reached || ++waiting > GRACE_TICKS;
    }

    /** {@link #advance(Set, int)} for holders nobody knows by name, who can never pause a run. */
    boolean advance(int holders, int limitTicks) {
        return advance(anonymous(holders), limitTicks);
    }

    private static Set<UUID> anonymous(int count) {
        Set<UUID> holders = new HashSet<>();
        for (int index = 0; index < count; index++) {
            holders.add(UUID.randomUUID());
        }
        return holders;
    }

    /**
     * Whether this run stands empty because the last people holding it logged out. Only meaningful
     * on a tick with nobody holding it, which is the only place {@link #advance} asks.
     */
    public boolean paused() {
        return reached && !Collections.disjoint(present, away);
    }

    /** Who was holding the run the last time anybody was. */
    public Set<UUID> present() {
        return present;
    }

    /**
     * Records that an entrant logged out while standing in the rift. Ignored for anybody not still
     * in the run, so a stranger, a corpse or somebody already out cannot hold a run open by leaving.
     */
    void loggedOut(UUID player) {
        if (entrants.containsKey(player) && !fallen.contains(player) && !escaped.contains(player)) {
            away.add(player);
        }
    }

    /** Records that an entrant is back. */
    void loggedIn(UUID player) {
        away.remove(player);
    }

    /** A saved occupant cannot already be online during startup, even after an unclean stop. */
    void pauseForRestart() {
        present.forEach(this::loggedOut);
    }

    /** Entrants who logged out inside and have not come back. */
    public Set<UUID> away() {
        return Collections.unmodifiableSet(away);
    }

    /** XP already banked for this entrant before the run ended. */
    public long settled(UUID player) {
        return paid.getOrDefault(player, 0L);
    }

    /** Records XP banked for an entrant ahead of the ending, so the ending pays only the rest. */
    void settle(UUID player, long xp) {
        if (xp > 0L) {
            paid.merge(player, xp, Long::sum);
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Persistence (G9): everything a run knows, so a restart hands back the same run.
    // ---------------------------------------------------------------------------------------------

    private static final String RIFT = "rift";
    private static final String FRAME_LEVEL = "frameLevel";
    private static final String FRAME_CORNER = "frameCorner";
    private static final String TIER = "tier";
    private static final String ENTRANTS = "entrants";
    private static final String PLAYER = "player";
    private static final String ENTRY = "entry";
    private static final String KIT = "kit";
    private static final String KILLS = "kills";
    private static final String PAID = "paid";
    private static final String ESCAPED = "escaped";
    private static final String FALLEN = "fallen";
    private static final String AWAY = "away";
    private static final String PRESENT = "present";
    private static final String CLEARED = "cleared";
    private static final String FINISHING = "finishing";
    private static final String REACHED = "reached";
    private static final String LOCKED = "locked";
    private static final String WAITING = "waiting";
    private static final String ELAPSED = "elapsed";
    private static final String EXPIRED = "expired";
    private static final String THEME = "theme";
    private static final String TYPE_KILLS = "typeKills";
    private static final String BOSSES = "bosses";
    private static final String HAUL_BARRELS = "haulBarrels";
    private static final String HAUL_SHARDS = "haulShards";
    private static final String HAUL_GEAR = "haulGear";
    private static final String HAUL_RARITY = "haulRarity";

    CompoundTag save(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putString(RIFT, rift.location().toString());
        if (frame != null) {
            tag.putString(FRAME_LEVEL, frame.level().location().toString());
            tag.put(FRAME_CORNER, NbtUtils.writeBlockPos(frame.corner()));
        }
        tag.putInt(TIER, tier);
        ListTag roster = new ListTag();
        entrants.forEach((player, entry) -> {
            CompoundTag record = new CompoundTag();
            record.putUUID(PLAYER, player);
            record.put(ENTRY, entry.save());
            ListTag kit = new ListTag();
            for (ItemStack stack : kits.getOrDefault(player, List.of())) {
                // ItemStack#save refuses an empty, and would take the whole file with it.
                if (!stack.isEmpty()) {
                    kit.add(stack.save(registries));
                }
            }
            record.put(KIT, kit);
            record.putInt(KILLS, kills.getOrDefault(player, 0));
            record.putLong(PAID, paid.getOrDefault(player, 0L));
            CompoundTag types = new CompoundTag();
            typeKills.getOrDefault(player, Map.of()).forEach((type, count) -> types.putInt(type.toString(), count));
            record.put(TYPE_KILLS, types);
            ListTag credited = new ListTag();
            bosses.getOrDefault(player, List.of()).forEach(type -> credited.add(net.minecraft.nbt.StringTag.valueOf(type.toString())));
            record.put(BOSSES, credited);
            RiftHaul haul = hauls.getOrDefault(player, RiftHaul.EMPTY);
            if (!haul.isEmpty()) {
                record.putInt(HAUL_BARRELS, haul.barrels());
                record.putInt(HAUL_SHARDS, haul.shards());
                record.putInt(HAUL_GEAR, haul.gear());
                record.putInt(HAUL_RARITY, haul.bestRarity());
            }
            roster.add(record);
        });
        tag.put(ENTRANTS, roster);
        tag.put(ESCAPED, uuids(escaped));
        tag.put(FALLEN, uuids(fallen));
        tag.put(AWAY, uuids(away));
        tag.put(PRESENT, uuids(present));
        tag.putBoolean(CLEARED, cleared);
        tag.putBoolean(FINISHING, finishing);
        tag.putBoolean(REACHED, reached);
        tag.putBoolean(LOCKED, locked);
        tag.putInt(WAITING, waiting);
        tag.putInt(ELAPSED, elapsed);
        tag.putBoolean(EXPIRED, expired);
        if (theme != null) {
            tag.putString(THEME, theme.toString());
        }
        return tag;
    }

    /** The run a {@link #save} wrote, or null if the record names no rift. */
    @Nullable
    static RiftRun load(CompoundTag tag, HolderLookup.Provider registries) {
        ResourceLocation riftId = ResourceLocation.tryParse(tag.getString(RIFT));
        if (riftId == null || tag.getString(RIFT).isEmpty()) {
            return null;
        }
        Frame frame = null;
        ResourceLocation frameLevel = ResourceLocation.tryParse(tag.getString(FRAME_LEVEL));
        if (frameLevel != null && tag.contains(FRAME_CORNER)) {
            frame = NbtUtils.readBlockPos(tag, FRAME_CORNER)
                    .map(corner -> new Frame(ResourceKey.create(Registries.DIMENSION, frameLevel), corner))
                    .orElse(null);
        }
        RiftRun run = new RiftRun(ResourceKey.create(Registries.DIMENSION, riftId), frame, tag.getInt(TIER));
        ListTag roster = tag.getList(ENTRANTS, Tag.TAG_COMPOUND);
        for (int index = 0; index < roster.size(); index++) {
            CompoundTag record = roster.getCompound(index);
            RiftEntry entry = RiftEntry.load(record.getCompound(ENTRY));
            if (entry == null) {
                continue;
            }
            UUID player = record.getUUID(PLAYER);
            List<ItemStack> kit = new ArrayList<>();
            ListTag items = record.getList(KIT, Tag.TAG_COMPOUND);
            for (int slot = 0; slot < items.size(); slot++) {
                // A stack that will not parse is dropped, as RiftTolls drops one.
                ItemStack.parse(registries, items.getCompound(slot)).ifPresent(kit::add);
            }
            run.entrants.put(player, entry);
            run.kits.put(player, List.copyOf(kit));
            if (record.getInt(KILLS) > 0) {
                run.kills.put(player, record.getInt(KILLS));
            }
            if (record.getLong(PAID) > 0L) {
                run.paid.put(player, record.getLong(PAID));
            }
            CompoundTag types = record.getCompound(TYPE_KILLS);
            for (String key : types.getAllKeys()) {
                ResourceLocation type = ResourceLocation.tryParse(key);
                if (type != null && types.getInt(key) > 0) {
                    run.typeKills.computeIfAbsent(player, k -> new HashMap<>()).put(type, types.getInt(key));
                }
            }
            if (record.contains(HAUL_BARRELS)) {
                run.hauls.put(player, new RiftHaul(record.getInt(HAUL_BARRELS), record.getInt(HAUL_SHARDS),
                        record.getInt(HAUL_GEAR), record.getInt(HAUL_RARITY)));
            }
            ListTag credited = record.getList(BOSSES, Tag.TAG_STRING);
            for (int b = 0; b < credited.size(); b++) {
                ResourceLocation type = ResourceLocation.tryParse(credited.getString(b));
                if (type != null) {
                    run.bosses.computeIfAbsent(player, k -> new ArrayList<>()).add(type);
                }
            }
        }
        run.escaped.addAll(uuids(tag.getList(ESCAPED, Tag.TAG_INT_ARRAY)));
        run.fallen.addAll(uuids(tag.getList(FALLEN, Tag.TAG_INT_ARRAY)));
        run.away.addAll(uuids(tag.getList(AWAY, Tag.TAG_INT_ARRAY)));
        run.present = Set.copyOf(uuids(tag.getList(PRESENT, Tag.TAG_INT_ARRAY)));
        run.cleared = tag.getBoolean(CLEARED);
        run.finishing = tag.getBoolean(FINISHING);
        run.reached = tag.getBoolean(REACHED);
        run.locked = tag.getBoolean(LOCKED);
        run.waiting = tag.getInt(WAITING);
        run.elapsed = tag.getInt(ELAPSED);
        run.expired = tag.getBoolean(EXPIRED);
        run.theme = tag.contains(THEME) ? ResourceLocation.tryParse(tag.getString(THEME)) : null;
        return run;
    }

    private static ListTag uuids(Collection<UUID> ids) {
        ListTag list = new ListTag();
        for (UUID id : ids) {
            list.add(NbtUtils.createUUID(id));
        }
        return list;
    }

    private static List<UUID> uuids(ListTag list) {
        List<UUID> ids = new ArrayList<>(list.size());
        for (Tag entry : list) {
            ids.add(NbtUtils.loadUUID(entry));
        }
        return ids;
    }
}
