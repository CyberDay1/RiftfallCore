package com.cyberday1.riftfall.rift;

import com.cyberday1.riftfall.RiftfallConfig;
import com.cyberday1.riftfall.RiftfallCore;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * The party verbs, shared by {@code /riftfall party} ({@link PartyCommand}) and the Party bookmark's
 * Expedition page ({@code SocialService}), so the two can never disagree.
 *
 * <p>
 * The rule, verbatim: a party forms through the create command or through the in-game menu,
 * and somebody invited gets a chat message they can click to accept, or accepts it from a pending
 * invitations list in the UI. Both faces call these methods; neither has its own copy of a rule.
 *
 * <p>
 * Inviting somebody while in no party starts one too, which was added afterwards. So there are
 * three ways in and {@code create} is the only one that is nothing but that &mdash; it is for the
 * player who wants to be in a party before deciding who else is, and for the menu button that says
 * so.
 *
 * <h2>Everything here is permission 0</h2>
 * A party is a thing players do with each other. It makes and unmakes no world, hands out no items,
 * and touches nothing an operator would have to be trusted with, so it hangs off {@code /riftfall}
 * beside {@code claim} rather than behind the operator gate the rest of the tree sits behind.
 *
 * <h2>Who may do what, and the one place it is not obvious</h2>
 * Any member may invite; only the leader may remove somebody or take the party apart. That split is
 * on purpose. A party is a group of people who agreed to play together, and making the leader a
 * gatekeeper for who else may be asked adds a role nobody requested; removing a person is the
 * opposite kind of act, and it needs one obvious answer to "who did that". Leaving is always your
 * own to do.
 *
 * <p>
 * Nobody is made a leader. The front of the member list leads, so leadership passes by itself when
 * the leader goes &mdash; see {@link RiftParty}.
 *
 * <h2>What this does not yet do</h2>
 * Nothing here reaches a rift. A party is currently a group you can form, see and leave; the rules
 * that make it matter &mdash; that it locks while somebody is inside a dungeon, that a rift admits
 * only the party of whoever entered first, that leaving one while inside puts you out of the door
 * &mdash; are the next slice, and they are rules about runs rather than about parties.
 */
public final class PartyActions {

    private static final String PREFIX = "message." + RiftfallCore.MODID + ".party_";

    private static final String CREATED = PREFIX + "created";
    private static final String ALREADY_IN = PREFIX + "already_in";
    private static final String NOT_IN = PREFIX + "not_in";
    private static final String FULL = PREFIX + "full";
    private static final String INVITE_SELF = PREFIX + "invite_self";
    private static final String INVITE_TAKEN = PREFIX + "invite_taken";
    private static final String INVITE_SENT = PREFIX + "invite_sent";
    private static final String INVITE_SENT_NEW = PREFIX + "invite_sent_new";
    private static final String INVITE_OFFER = PREFIX + "invite_offer";
    private static final String INVITE_ACCEPT = PREFIX + "invite_accept";
    private static final String INVITE_DECLINE = PREFIX + "invite_decline";
    private static final String INVITE_ACCEPT_HOVER = PREFIX + "invite_accept_hover";
    private static final String INVITE_DECLINE_HOVER = PREFIX + "invite_decline_hover";
    private static final String INVITE_NONE = PREFIX + "invite_none";
    private static final String INVITE_EXPIRED = PREFIX + "invite_expired";
    private static final String INVITE_GONE = PREFIX + "invite_gone";
    private static final String INVITES_NONE = PREFIX + "invites_none";
    private static final String INVITES_HEADER = PREFIX + "invites_header";
    private static final String JOINED = PREFIX + "joined";
    private static final String JOINED_OTHERS = PREFIX + "joined_others";
    private static final String DECLINED = PREFIX + "declined";
    private static final String DECLINED_OTHERS = PREFIX + "declined_others";
    private static final String LEFT = PREFIX + "left";
    private static final String LEFT_OTHERS = PREFIX + "left_others";
    private static final String LEADS_NOW = PREFIX + "leads_now";
    private static final String NOT_LEADER = PREFIX + "not_leader";
    private static final String NOT_MEMBER = PREFIX + "not_member";
    private static final String REMOVE_SELF = PREFIX + "remove_self";
    private static final String REMOVED = PREFIX + "removed";
    private static final String REMOVED_OTHERS = PREFIX + "removed_others";
    private static final String DISBANDED = PREFIX + "disbanded";
    private static final String HEADER = PREFIX + "header";
    private static final String MEMBER = PREFIX + "member";
    private static final String MEMBER_LEADER = PREFIX + "member_leader";
    private static final String MEMBER_OFFLINE = PREFIX + "member_offline";

    private PartyActions() {
    }

    /** The configured party size cap. A method so a test can stand in for the loaded config. */
    public static int cap() {
        return RiftfallConfig.MAX_PARTY_SIZE.get();
    }

    /** Starts a party of one, which is what everything else here is said to. */
    public static int create(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        RiftParty party = RiftParties.of(source.getServer()).create(player.getUUID());
        if (party == null) {
            source.sendFailure(Component.translatable(ALREADY_IN));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable(CREATED), false);
        return 1;
    }

    /**
     * Asks somebody to join the caller's party, starting one if they have not got one.
     *
     * <p>
     * Each refusal is its own sentence rather than one general failure, because the three of them
     * want three different next actions: pick somebody else, wait for a slot, or stop inviting
     * yourself.
     *
     * <p>
     * Inviting while in no party is the third way a party forms, and a design call. Asking somebody
     * to join you <em>is</em> the decision {@code create} exists to record, so requiring it first only
     * adds a command to be told about after being refused. It does not happen silently: the reply says
     * a party was started, so nobody ends up leading a group without being told they are.
     *
     * <p>
     * The cap is checked against the party that would exist rather than the one that does, so a server
     * that has set it to one refuses before anything is made. No party is left behind by a command that
     * then failed.
     */
    public static int invite(CommandSourceStack source, ServerPlayer invitee) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        MinecraftServer server = source.getServer();
        RiftParties parties = RiftParties.of(server);

        if (invitee.getUUID().equals(player.getUUID())) {
            source.sendFailure(Component.translatable(INVITE_SELF));
            return 0;
        }
        if (parties.of(invitee.getUUID()) != null) {
            source.sendFailure(Component.translatable(INVITE_TAKEN, invitee.getDisplayName()));
            return 0;
        }

        RiftParty existing = parties.of(player.getUUID());
        int cap = cap();
        if ((existing == null ? 1 : existing.size()) >= cap) {
            source.sendFailure(Component.translatable(FULL, cap));
            return 0;
        }
        boolean started = existing == null;
        // create cannot refuse here: the only thing it refuses is a player already in a party, and
        // this is the branch in which they are not.
        RiftParty party = started ? parties.create(player.getUUID()) : existing;

        RiftInvites.offer(server.getTickCount(), party.id(), player.getUUID(), invitee.getUUID());
        invitee.sendSystemMessage(offer(player));
        source.sendSuccess(() -> Component.translatable(started ? INVITE_SENT_NEW : INVITE_SENT,
                invitee.getDisplayName()), false);
        return 1;
    }

    /**
     * The invitation itself: a line of text with the two answers to it attached.
     *
     * <p>
     * {@code RUN_COMMAND} rather than {@code SUGGEST_COMMAND}, so that clicking answers rather than
     * typing the answer into the box for you. That is what the design describes &mdash; a message you
     * can click to accept &mdash; and it is safe to do here because the command it runs is the same
     * one the player could type, at the permission level they already have.
     *
     * <p>
     * Both buttons name the inviter rather than the party, because a party has no name a person could
     * read and a command line is a thing a person may end up looking at.
     */
    private static Component offer(ServerPlayer inviter) {
        String name = inviter.getGameProfile().getName();
        return Component.translatable(INVITE_OFFER, inviter.getDisplayName())
                .withStyle(ChatFormatting.GRAY)
                .append(button(INVITE_ACCEPT, INVITE_ACCEPT_HOVER, ChatFormatting.GREEN,
                        "/riftfall party accept " + name, inviter.getDisplayName()))
                .append(Component.literal(" "))
                .append(button(INVITE_DECLINE, INVITE_DECLINE_HOVER, ChatFormatting.RED,
                        "/riftfall party decline " + name, inviter.getDisplayName()));
    }

    private static MutableComponent button(String label, String hover, ChatFormatting colour,
                                           String command, Component inviter) {
        return Component.translatable(label).withStyle(style -> style
                .withColor(colour)
                .withBold(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        Component.translatable(hover, inviter))));
    }

    /**
     * Accepts or declines an invitation, named by anybody in the party that sent it.
     *
     * <p>
     * Naming a person rather than a party is the only thing a player could type, and naming
     * <em>any</em> member rather than the one who asked is what makes the two people who both invited
     * you into one invitation instead of a puzzle. {@link RiftInvites} explains why the offer is
     * addressed to the group in the first place.
     */
    public static int answer(CommandSourceStack source, ServerPlayer from, boolean accepting)
            throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        MinecraftServer server = source.getServer();
        RiftParties parties = RiftParties.of(server);

        RiftParty party = parties.of(from.getUUID());
        if (party == null) {
            source.sendFailure(Component.translatable(INVITE_NONE, from.getDisplayName()));
            return 0;
        }
        RiftInvites.Invite invite = RiftInvites.answer(party.id(), player.getUUID());
        if (invite == null) {
            source.sendFailure(Component.translatable(INVITE_NONE, from.getDisplayName()));
            return 0;
        }
        if (invite.expires() <= server.getTickCount()) {
            source.sendFailure(Component.translatable(INVITE_EXPIRED, from.getDisplayName()));
            return 0;
        }

        ServerPlayer inviter = server.getPlayerList().getPlayer(invite.inviter());
        if (!accepting) {
            source.sendSuccess(() -> Component.translatable(DECLINED, from.getDisplayName()), false);
            if (inviter != null) {
                inviter.sendSystemMessage(Component.translatable(DECLINED_OTHERS, player.getDisplayName())
                        .withStyle(ChatFormatting.GRAY));
            }
            return 1;
        }

        // Re-checked at the moment of joining rather than trusted from when the offer was made. Both
        // facts move while an invitation stands: the invitee may have joined somebody else's party,
        // and the inviting party may have filled up or been asked for by more people than it has room
        // for. An offer is permission to ask again, not a reservation.
        if (parties.of(player.getUUID()) != null) {
            source.sendFailure(Component.translatable(ALREADY_IN));
            return 0;
        }
        int cap = cap();
        if (party.size() >= cap) {
            source.sendFailure(Component.translatable(FULL, cap));
            return 0;
        }
        if (!parties.join(party.id(), player.getUUID())) {
            source.sendFailure(Component.translatable(INVITE_GONE));
            return 0;
        }

        source.sendSuccess(() -> Component.translatable(JOINED), false);
        tell(server, party, player.getUUID(),
                Component.translatable(JOINED_OTHERS, player.getDisplayName()));
        return 1;
    }

    /** Everything standing that the caller has been asked to join, for the days chat has scrolled. */
    public static int invites(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        MinecraftServer server = source.getServer();
        List<RiftInvites.Invite> pending = RiftInvites.pending(server.getTickCount(), player.getUUID());
        if (pending.isEmpty()) {
            source.sendSuccess(() -> Component.translatable(INVITES_NONE), false);
            return 0;
        }
        source.sendSuccess(() -> Component.translatable(INVITES_HEADER, pending.size()), false);
        for (RiftInvites.Invite invite : pending) {
            ServerPlayer inviter = server.getPlayerList().getPlayer(invite.inviter());
            // An inviter who has logged off since takes their invitation with them, so the list can
            // only hold people who are here. Skipping rather than naming them from the profile cache
            // keeps that true instead of showing an offer that cannot be accepted.
            if (inviter != null) {
                source.sendSuccess(() -> offer(inviter), false);
            }
        }
        return pending.size();
    }

    /**
     * Takes the caller out of their party.
     *
     * <p>
     * Always allowed, including for the leader: leadership is the front of a list rather than an
     * office, so a leader leaving passes it to whoever joined next and the party carries on without
     * needing to be handed over first. A leader who wanted the group to stop existing has
     * {@code disband} for that.
     */
    public static int leave(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        MinecraftServer server = source.getServer();
        RiftParties parties = RiftParties.of(server);

        RiftParty leaving = parties.of(player.getUUID());
        if (leaving == null) {
            source.sendFailure(Component.translatable(NOT_IN));
            return 0;
        }
        // Asked before the removal, because afterwards the front of the list is somebody else and
        // there is no way left to tell a leader walking out from an ordinary member doing it.
        boolean led = leaving.isLeader(player.getUUID());
        RiftParty party = parties.leave(player.getUUID());
        source.sendSuccess(() -> Component.translatable(LEFT), false);
        tell(server, party, player.getUUID(),
                Component.translatable(LEFT_OTHERS, player.getDisplayName()));
        if (led && !party.isEmpty()) {
            tell(server, party, null,
                    Component.translatable(LEADS_NOW, name(server, party.leader())));
        }
        return 1;
    }

    /**
     * Removes somebody else from the caller's party.
     *
     * <p>
     * Takes a profile rather than an online player, because the member being removed may well be
     * offline: a party survives a relog, so the party you want to tidy up on a Tuesday is full of
     * people who last played on Sunday. That is also why it is the one verb here whose target cannot
     * be a selector for somebody in the world.
     */
    public static int remove(CommandSourceStack source, Collection<GameProfile> profiles)
            throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        MinecraftServer server = source.getServer();
        RiftParties parties = RiftParties.of(server);

        RiftParty party = parties.of(player.getUUID());
        if (party == null) {
            source.sendFailure(Component.translatable(NOT_IN));
            return 0;
        }
        if (!party.isLeader(player.getUUID())) {
            source.sendFailure(Component.translatable(NOT_LEADER));
            return 0;
        }

        int removed = 0;
        for (GameProfile profile : profiles) {
            UUID target = profile.getId();
            if (target.equals(player.getUUID())) {
                source.sendFailure(Component.translatable(REMOVE_SELF));
                continue;
            }
            if (!party.has(target)) {
                source.sendFailure(Component.translatable(NOT_MEMBER, profile.getName()));
                continue;
            }
            parties.leave(target);
            removed++;
            ServerPlayer gone = server.getPlayerList().getPlayer(target);
            if (gone != null) {
                gone.sendSystemMessage(Component.translatable(REMOVED).withStyle(ChatFormatting.GRAY));
            }
            tell(server, party, null, Component.translatable(REMOVED_OTHERS, profile.getName()));
        }
        return removed;
    }

    /** Ends the party for everybody in it, which only the leader may do. */
    public static int disband(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        MinecraftServer server = source.getServer();
        RiftParties parties = RiftParties.of(server);

        RiftParty party = parties.of(player.getUUID());
        if (party == null) {
            source.sendFailure(Component.translatable(NOT_IN));
            return 0;
        }
        if (!party.isLeader(player.getUUID())) {
            source.sendFailure(Component.translatable(NOT_LEADER));
            return 0;
        }
        // Told before it is taken apart, because afterwards there is no list of who to tell.
        tell(server, party, null, Component.translatable(DISBANDED).withStyle(ChatFormatting.GRAY));
        parties.disband(party);
        return 1;
    }

    /**
     * Lists the caller's party, leader first, saying who is here and who is not.
     *
     * <p>
     * Whether a member is online is the fact this list is usually being read for, so it is on every
     * line rather than left to be worked out from the tab list. Names come from the profile cache for
     * anybody who is away, which is the game's own answer to the same question and means an offline
     * member is a name rather than a UUID.
     */
    public static int show(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        MinecraftServer server = source.getServer();
        RiftParty party = RiftParties.of(server).of(player.getUUID());
        if (party == null) {
            source.sendFailure(Component.translatable(NOT_IN));
            return 0;
        }

        source.sendSuccess(() -> Component.translatable(HEADER, party.size(),
                cap()), false);
        for (UUID member : party.members()) {
            MutableComponent named = Component.translatable(
                    party.isLeader(member) ? MEMBER_LEADER : MEMBER, name(server, member));
            MutableComponent line = server.getPlayerList().getPlayer(member) != null
                    ? named
                    : Component.translatable(MEMBER_OFFLINE, named).withStyle(ChatFormatting.DARK_GRAY);
            source.sendSuccess(() -> line, false);
        }
        return party.size();
    }

    /**
     * Says something to a whole party, optionally leaving one member out.
     *
     * <p>
     * {@code except} is the person the thing happened to, who has already been told in the first
     * person by the command they ran. Offline members are simply not reachable and are not queued for:
     * these are announcements about a group changing shape, and the shape is what they will see when
     * they get back.
     */
    private static void tell(MinecraftServer server, RiftParty party, @Nullable UUID except,
                             Component message) {
        for (UUID member : party.members()) {
            if (member.equals(except)) {
                continue;
            }
            ServerPlayer player = server.getPlayerList().getPlayer(member);
            if (player != null) {
                player.sendSystemMessage(message);
            }
        }
    }

    /** A player's name, from the server if they are here and from the profile cache if they are not. */
    private static String name(MinecraftServer server, UUID player) {
        ServerPlayer online = server.getPlayerList().getPlayer(player);
        if (online != null) {
            return online.getGameProfile().getName();
        }
        return server.getProfileCache() == null ? player.toString()
                : server.getProfileCache().get(player)
                        .map(GameProfile::getName)
                        .orElseGet(player::toString);
    }
}
