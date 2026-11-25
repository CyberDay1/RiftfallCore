package com.cyberday1.riftfall.rift;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * A group of players who mean to go into a rift together.
 *
 * <p>
 * A rift has always had a roster &mdash; {@link RiftRun}'s entrants, which is everybody who has been
 * through the frame &mdash; but that is a record of what happened rather than a statement of intent,
 * and it is written one player at a time as they walk in. There was no way to say who <em>meant</em>
 * to go in together, which is what a reward split, an entry gate and a wipe all need to be able to
 * ask about. This is that statement, and it exists before anybody is anywhere near a portal.
 *
 * <h2>The leader is the first name on the list, and is not a field</h2>
 * Membership is ordered, and whoever is at the front leads. That is not a shorthand: it is what
 * removes the entire class of bug where a party's leader is not one of its members, or where a leader
 * leaves and the party is left pointing at somebody who has gone. Leadership passes by itself when
 * the front of the list goes, and a party is never leaderless while it has anybody in it at all.
 *
 * <p>
 * The order is otherwise join order, which is the one order everybody involved already agrees on.
 *
 * <h2>Persisted, because a party survives a relog</h2>
 * The obvious design is the opposite &mdash; a party that evaporates when its members log off, so
 * there is never a stale one on disk &mdash; and it was proposed and turned down. A group that has to
 * be rebuilt every session is a group that stops being used after the second session. So this is
 * saved, and everything that could go stale about it is deliberately <em>not</em> saved with it: no
 * names, no rift, no run. A party is a list of players and nothing else, and every question about
 * where those players are is asked of the server at the moment it matters.
 *
 * <p>
 * There is no cap here either. The cap belongs to the thing that lets somebody in, because it is a
 * server's answer to how big a dungeon party should be and can be changed under a party that already
 * exists &mdash; an existing party over a lowered cap keeps its members and refuses new ones, which
 * is the only answer that does not delete somebody from a group they were already in.
 */
public final class RiftParty {

    private static final String ID = "id";
    private static final String MEMBERS = "members";

    private final UUID id;

    /** Join order, and the front of it leads. Never empty: an emptied party is deleted instead. */
    private final List<UUID> members = new ArrayList<>();

    RiftParty(UUID id, UUID leader) {
        this.id = id;
        members.add(leader);
    }

    private RiftParty(UUID id, List<UUID> members) {
        this.id = id;
        this.members.addAll(members);
    }

    /**
     * What this party is called on disk and in an invitation.
     *
     * <p>
     * A party is named by something of its own rather than by its leader, because leadership moves
     * and an invitation that outlived a leadership change would otherwise point at nothing. It is
     * never shown to a player: they see names.
     */
    public UUID id() {
        return id;
    }

    /** Whoever is at the front, which is the whole of what leading means here. */
    public UUID leader() {
        return members.getFirst();
    }

    public boolean isLeader(UUID player) {
        return leader().equals(player);
    }

    public boolean has(UUID player) {
        return members.contains(player);
    }

    public int size() {
        return members.size();
    }

    /** Everybody, leader first. */
    public List<UUID> members() {
        return Collections.unmodifiableList(members);
    }

    /**
     * Adds a player, and says whether that changed anything.
     *
     * <p>
     * Package-private, and the cap is not checked here, because a party cannot police its own
     * membership: {@link RiftParties} is the only thing that knows whether the player being added is
     * already in a different one, and a player in two parties is the one state that has no sensible
     * meaning at all.
     */
    boolean add(UUID player) {
        return !members.contains(player) && members.add(player);
    }

    /** Removes a player, and says whether that changed anything. */
    boolean remove(UUID player) {
        return members.remove(player);
    }

    boolean isEmpty() {
        return members.isEmpty();
    }

    CompoundTag save() {
        ListTag list = new ListTag();
        members.forEach(member -> list.add(NbtUtils.createUUID(member)));
        CompoundTag tag = new CompoundTag();
        tag.putUUID(ID, id);
        tag.put(MEMBERS, list);
        return tag;
    }

    /**
     * Reads a party back, or null if what was written is not one any more.
     *
     * <p>
     * A party with nobody in it is the one shape that cannot be loaded, because every other piece of
     * this class is written assuming there is a leader to be found at the front. It should not be
     * possible to write one &mdash; the last member leaving deletes the party rather than emptying it
     * &mdash; so this is the guard for a file edited by hand or half written by a crash.
     */
    @Nullable
    static RiftParty load(CompoundTag tag) {
        ListTag list = tag.getList(MEMBERS, Tag.TAG_INT_ARRAY);
        List<UUID> members = new ArrayList<>(list.size());
        for (int index = 0; index < list.size(); index++) {
            UUID member = NbtUtils.loadUUID(list.get(index));
            if (!members.contains(member)) {
                members.add(member);
            }
        }
        return members.isEmpty() ? null : new RiftParty(tag.getUUID(ID), members);
    }
}
