package com.cyberday1.riftfall.rift;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Who has been asked to join which party, and has not yet said.
 *
 * <p>
 * An invitation is an offer to go and do something now. That is what decides everything else about
 * this class: it is held in memory, it is not written to disk, it does not survive a restart, and it
 * is thrown away the moment either end of it logs off. None of that is a simplification of a richer
 * design &mdash; a mail system would be a different feature, and an offer that arrives from somebody
 * who left the game an hour ago is worse than no offer at all.
 *
 * <p>
 * It is kept apart from {@link RiftParties} for the same reason, structurally rather than by
 * discipline. That class is a {@code SavedData} whose whole job is to reach the disk; this one must
 * never reach it. Two classes cannot get that wrong by forgetting a field.
 *
 * <h2>Addressed to a party, answered by a name</h2>
 * An invitation names the party rather than the person who sent it, because the party is what is
 * being joined and because two members of one group inviting the same player is one invitation, not
 * two. But nobody can type a party's id, so accepting is done by naming <em>anybody</em> in the
 * inviting party: the command resolves the name to a party and asks whether that party has an
 * invitation out to the caller. Both of the people who asked you therefore work, which is what
 * somebody reading their chat log would expect.
 *
 * <p>
 * The inviter is still recorded, because a player who is looking at a list of pending invitations
 * wants to know who asked, and because a declined invitation should be reported back to the person
 * who sent it rather than to whoever happens to lead the group.
 */
public final class RiftInvites {

    /**
     * How long an invitation stands, in ticks.
     *
     * <p>
     * Two minutes. Long enough to be read at the end of a fight rather than during one, and short
     * enough that an offer you ignored cannot pull you into a group later in the evening when
     * somebody finally clicks accept on your behalf &mdash; which is the failure that matters, because
     * being in a party is the thing that decides where your loot goes when you die.
     *
     * <p>
     * Not a config entry. It is a number with an argument behind it, and the pack-facing question
     * about parties is how big they are allowed to be, not how quickly a chat message goes stale.
     */
    static final int LIFETIME_TICKS = 2400;

    /**
     * The clock is passed in rather than read from a server.
     *
     * <p>
     * Every method here that cares what time it is takes the tick count as an argument, which the
     * command reads off the server it already has. That is the same trick {@link RiftClaims#handBack}
     * uses to take a function instead of an inventory, and for the same reason: it is the one
     * dependency that would otherwise make this class need a running game to be tested at all, and
     * expiry is exactly the behaviour worth a test.
     */
    private RiftInvites() {
    }

    /** Invitee, then party, then when that offer runs out. Insertion-ordered, so a list reads in order. */
    private static final Map<UUID, Map<UUID, Invite>> PENDING = new HashMap<>();

    /** One standing offer: which party, who asked, and the tick it expires on. */
    public record Invite(UUID party, UUID inviter, long expires) {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(RiftInvites::onLogout);
        NeoForge.EVENT_BUS.addListener(RiftInvites::forgetAll);
    }

    /**
     * Records an offer, replacing any this party already had out to this player.
     *
     * <p>
     * Replacing rather than refusing, so that asking again is how you renew an invitation somebody
     * has not got round to. There is nothing to be gained by telling the inviter that their previous
     * message is still technically valid for another forty seconds.
     */
    public static void offer(long now, UUID party, UUID inviter, UUID invitee) {
        PENDING.computeIfAbsent(invitee, key -> new LinkedHashMap<>())
                .put(party, new Invite(party, inviter, now + LIFETIME_TICKS));
    }

    /**
     * Takes back an offer and hands it over, or null if that party never made one.
     *
     * <p>
     * The same call serves accepting and declining, because the effect here is identical either way:
     * the offer is spent. What happens next is the command's business.
     *
     * <p>
     * An offer that has run out still comes back, rather than being reported as absent. The caller has
     * the clock and the {@link Invite} carries its own deadline, so it can tell the two apart &mdash;
     * and it needs to, because "nobody asked you" is a wrong and slightly rude thing to say to
     * somebody who is looking at the message that asked them, and it sends them off to check a
     * spelling that was correct.
     */
    @Nullable
    public static Invite answer(UUID party, UUID invitee) {
        Map<UUID, Invite> offers = PENDING.get(invitee);
        if (offers == null) {
            return null;
        }
        Invite invite = offers.remove(party);
        if (offers.isEmpty()) {
            PENDING.remove(invitee);
        }
        return invite;
    }

    /**
     * Every offer still standing for this player, oldest first.
     *
     * <p>
     * Expiry is applied here rather than on a timer. There is nothing an expired invitation does
     * between running out and being looked at, so a tick listener sweeping them would be work done to
     * keep a map tidy that nobody is reading; and the two things that would leave one behind for good
     * &mdash; the invitee logging off, or the inviter doing so &mdash; both clear it outright.
     */
    public static List<Invite> pending(long now, UUID invitee) {
        Map<UUID, Invite> offers = PENDING.get(invitee);
        if (offers == null) {
            return List.of();
        }
        offers.values().removeIf(invite -> invite.expires() <= now);
        if (offers.isEmpty()) {
            PENDING.remove(invitee);
            return List.of();
        }
        return List.copyOf(offers.values());
    }

    /**
     * Drops every offer a player is on either end of.
     *
     * <p>
     * Both directions, because an invitation is between two people who are both here. Theirs go
     * because there is nobody to deliver an acceptance to, and the ones they sent go because the
     * group they were speaking for may be a different shape by the time they are back.
     */
    public static void forget(UUID player) {
        PENDING.remove(player);
        List<UUID> emptied = new ArrayList<>();
        PENDING.forEach((invitee, offers) -> {
            offers.values().removeIf(invite -> invite.inviter().equals(player));
            if (offers.isEmpty()) {
                emptied.add(invitee);
            }
        });
        emptied.forEach(PENDING::remove);
    }

    private static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            forget(player.getUUID());
        }
    }

    /**
     * Drops everything when the server stops.
     *
     * <p>
     * The map is static and a singleplayer client starts a fresh server for every world, so without
     * this the second world of a session would open holding the first world's invitations &mdash; the
     * same reason {@link RiftRuns} drops its own static maps there, and the same failure it prevents.
     */
    private static void forgetAll(ServerStoppingEvent event) {
        PENDING.clear();
    }
}
