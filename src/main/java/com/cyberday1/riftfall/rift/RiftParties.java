package com.cyberday1.riftfall.rift;

import com.cyberday1.riftfall.RiftfallCore;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Every party on the server, and the rule that a player is in at most one of them.
 *
 * <p>
 * {@link RiftParty} is a list of players; this is the thing that owns those lists and answers the
 * only question anything else ever asks, which is "what party is this player in". Both directions of
 * that are kept &mdash; parties by their own id, and a player-to-party index &mdash; because the
 * lookup by player happens on a command, on a death, and eventually on every step through a portal,
 * and walking every party to find somebody would make the cost of asking depend on how many other
 * groups happen to be playing.
 *
 * <p>
 * The index is rebuilt on load rather than written. It is derived from the parties, and two
 * representations of one fact on disk is two chances to disagree about it: a saved index that has
 * drifted would put a player in a party whose member list has never heard of them.
 *
 * <h2>One party at a time, enforced here and nowhere else</h2>
 * Joining is refused for anybody already in a party, and that check lives here because this is the
 * only class that can make it. A party cannot see the others. The rule matters because everything
 * built on top of a party &mdash; who a wipe is, whose loot a split is between, who a rift lets in
 * &mdash; assumes one answer per player, and two would not be a smaller version of the same feature,
 * it would be a set of questions with no answer.
 *
 * <h2>Where it lives, and why not with the rifts</h2>
 * The overworld's data storage, for the third time in this package and for the same reason
 * {@link RiftReturns} and {@link RiftClaims} are there. A party has to outlive the rift it was made
 * for &mdash; it exists before there is one and carries on after it is deleted &mdash; and it has to
 * outlive the server, because the rule is that it survives a relog. Nothing about a party is
 * scoped to a level, so nothing about it belongs in one.
 */
public final class RiftParties extends SavedData {

    private static final String FILE = RiftfallCore.MODID + "_parties";
    private static final String PARTIES = "parties";

    private final Map<UUID, RiftParty> parties = new HashMap<>();

    /** Player to the party they are in, derived from {@link #parties} and never saved. */
    private final Map<UUID, UUID> membership = new HashMap<>();

    // Package-private, like RiftClaims' and RiftReturns', so the round trip can be tested without
    // a server standing behind it.
    RiftParties() {
    }

    public static RiftParties of(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(RiftParties::new, RiftParties::load), FILE);
    }

    static RiftParties load(CompoundTag tag, HolderLookup.Provider registries) {
        RiftParties all = new RiftParties();
        ListTag list = tag.getList(PARTIES, Tag.TAG_COMPOUND);
        for (int index = 0; index < list.size(); index++) {
            RiftParty party = RiftParty.load(list.getCompound(index));
            // A party that will not load is dropped rather than throwing, for the reason RiftClaims
            // gives: taking the whole file would take everybody else's party with it.
            if (party != null) {
                all.remember(party);
            }
        }
        return all;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        parties.values().forEach(party -> list.add(party.save()));
        tag.put(PARTIES, list);
        return tag;
    }

    /** The party this player is in, or null &mdash; which is most players, most of the time. */
    @Nullable
    public RiftParty of(UUID player) {
        UUID party = membership.get(player);
        return party == null ? null : parties.get(party);
    }

    /**
     * Starts a party with one member, or returns null because that player is already in one.
     *
     * <p>
     * A party of one is a real party and not a placeholder for the absence of one. It is the state
     * between deciding to play together and anybody having accepted, so there is somewhere for an
     * invitation to point; and it is what a player who has been left alone in a party is in, which
     * should not silently become a different thing from what they were in a moment earlier.
     */
    @Nullable
    public RiftParty create(UUID leader) {
        if (membership.containsKey(leader)) {
            return null;
        }
        RiftParty party = new RiftParty(UUID.randomUUID(), leader);
        remember(party);
        setDirty();
        return party;
    }

    /**
     * Puts a player into a party, and says whether it took.
     *
     * <p>
     * Refused for a player who is already in one, and for a party this instance does not know about
     * &mdash; which is a party that was disbanded while an invitation to it was still on somebody's
     * screen. That is a race worth failing rather than a case worth handling: the alternative is
     * resurrecting a group its own members have left.
     */
    public boolean join(UUID party, UUID player) {
        RiftParty joining = parties.get(party);
        if (joining == null || membership.containsKey(player) || !joining.add(player)) {
            return false;
        }
        membership.put(player, party);
        setDirty();
        return true;
    }

    /**
     * Takes a player out of whatever party they are in, and returns the party they left.
     *
     * <p>
     * The returned party may now be empty, in which case it has already been forgotten and what comes
     * back is a record of who they were with rather than a group that still exists. Callers want that
     * &mdash; the message to send is about the people who were there a moment ago &mdash; and
     * {@link RiftParty#isEmpty} is how to tell the two apart.
     */
    @Nullable
    public RiftParty leave(UUID player) {
        UUID id = membership.remove(player);
        if (id == null) {
            return null;
        }
        RiftParty party = parties.get(id);
        party.remove(player);
        if (party.isEmpty()) {
            parties.remove(id);
        }
        setDirty();
        return party;
    }

    /** Takes a whole party apart, and returns who was in it. */
    public RiftParty disband(RiftParty party) {
        parties.remove(party.id());
        party.members().forEach(membership::remove);
        setDirty();
        return party;
    }

    public boolean isEmpty() {
        return parties.isEmpty();
    }

    /**
     * Files a party and everybody in it, dropping anybody already spoken for.
     *
     * <p>
     * A member listed in two parties takes a hand-edited file to produce, and the first party loaded
     * keeps them. They are then removed from the second party's list rather than merely left out of
     * the index, because the two have to agree: an index that says one thing and a member list that
     * says another is a player who can be removed from a group and still be in it.
     */
    private void remember(RiftParty party) {
        party.members().stream()
                .filter(membership::containsKey)
                .toList()
                .forEach(party::remove);
        if (party.isEmpty()) {
            return;
        }
        parties.put(party.id(), party);
        party.members().forEach(member -> membership.put(member, party.id()));
    }
}
