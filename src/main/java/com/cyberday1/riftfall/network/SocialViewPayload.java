package com.cyberday1.riftfall.network;

import com.cyberday1.riftfall.RiftfallCore;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.List;
import java.util.UUID;

/**
 * Everything the Party bookmark draws, for one viewer: their expedition party, home team and home allies,
 * the invitations waiting for them, and who else is online to invite. Facts only; which buttons are live is
 * worked out from these by {@code SocialRules}, and the server re-checks every request regardless.
 *
 * @param cap          the expedition party size cap
 * @param party        the viewer's party, leader first; empty when in none
 * @param partyInvites expedition invitations to the viewer, by inviter
 * @param teams        whether Open Parties and Claims is on the server
 * @param team         the viewer's home team, owner first; empty when on none
 * @param teamRank     the viewer's rank on it ({@link #OWNER} for the owner), or {@link #NO_RANK}
 * @param teamInvites  teams that have invited the viewer, by owner
 * @param alliedTeams  teams the viewer's team is allied with, by owner
 * @param homeOwner    whether the viewer owns the home they live in (and so may change allies)
 * @param allies       individual grants to the viewer's home
 * @param online       everyone else online, with what they already are to the viewer
 */
public record SocialViewPayload(int cap, List<Member> party, List<Invite> partyInvites, boolean teams,
                                List<Member> team, int teamRank, List<Invite> teamInvites, List<Named> alliedTeams,
                                boolean homeOwner, List<Named> allies, List<Online> online)
        implements CustomPacketPayload {

    public static final Type<SocialViewPayload> TYPE = new Type<>(RiftfallCore.id("social_view"));

    /** A party member's rank: the leader leads, everyone else is a member. */
    public static final int MEMBER = 0;
    public static final int LEADER = 1;
    /** Team ranks follow OPAC's order (member, claimer, moderator, admin); the owner sits above them. */
    public static final int CLAIMER = 1;
    public static final int MODERATOR = 2;
    public static final int ADMIN = 3;
    public static final int OWNER = 4;
    public static final int NO_RANK = -1;

    /** {@link Online#flags()} bits. */
    public static final int IN_PARTY = 1;
    public static final int MY_PARTY = 2;
    public static final int ON_TEAM = 4;
    public static final int MY_TEAM = 8;
    public static final int ALLIED_TEAM = 16;
    public static final int HOME_ALLY = 32;

    static final int NAME = 16;
    static final int MAX_MEMBERS = 256;
    static final int MAX_INVITES = 64;
    static final int MAX_ONLINE = 512;

    public static final SocialViewPayload EMPTY = new SocialViewPayload(0, List.of(), List.of(), false, List.of(),
            NO_RANK, List.of(), List.of(), false, List.of(), List.of());

    public record Member(UUID id, String name, boolean online, int rank) {
        public static final StreamCodec<ByteBuf, Member> STREAM_CODEC = StreamCodec.composite(
                UUIDUtil.STREAM_CODEC, Member::id,
                ByteBufCodecs.stringUtf8(NAME), Member::name,
                ByteBufCodecs.BOOL, Member::online,
                ByteBufCodecs.VAR_INT, Member::rank,
                Member::new);
    }

    /** An invitation: {@code from} is the inviter (party) or the team's owner (team); {@code size} its members. */
    public record Invite(UUID from, String name, int size) {
        public static final StreamCodec<ByteBuf, Invite> STREAM_CODEC = StreamCodec.composite(
                UUIDUtil.STREAM_CODEC, Invite::from,
                ByteBufCodecs.stringUtf8(NAME), Invite::name,
                ByteBufCodecs.VAR_INT, Invite::size,
                Invite::new);
    }

    public record Named(UUID id, String name) {
        public static final StreamCodec<ByteBuf, Named> STREAM_CODEC = StreamCodec.composite(
                UUIDUtil.STREAM_CODEC, Named::id,
                ByteBufCodecs.stringUtf8(NAME), Named::name,
                Named::new);
    }

    public record Online(UUID id, String name, int flags) {
        public boolean is(int flag) {
            return (flags & flag) != 0;
        }

        public static final StreamCodec<ByteBuf, Online> STREAM_CODEC = StreamCodec.composite(
                UUIDUtil.STREAM_CODEC, Online::id,
                ByteBufCodecs.stringUtf8(NAME), Online::name,
                ByteBufCodecs.VAR_INT, Online::flags,
                Online::new);
    }

    public SocialViewPayload {
        party = List.copyOf(party);
        partyInvites = List.copyOf(partyInvites);
        team = List.copyOf(team);
        teamInvites = List.copyOf(teamInvites);
        alliedTeams = List.copyOf(alliedTeams);
        allies = List.copyOf(allies);
        online = List.copyOf(online);
    }

    /** A name cut to what the wire carries; Minecraft names are 16 at most, but OPAC and caches are not ours. */
    public static String name(String name) {
        return name == null ? "" : name.length() > NAME ? name.substring(0, NAME) : name;
    }

    public boolean inParty() {
        return !party.isEmpty();
    }

    public boolean onTeam() {
        return teamRank != NO_RANK;
    }

    public static final StreamCodec<ByteBuf, SocialViewPayload> STREAM_CODEC = new StreamCodec<>() {
        private final StreamCodec<ByteBuf, List<Member>> members = Member.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_MEMBERS));
        private final StreamCodec<ByteBuf, List<Invite>> invites = Invite.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_INVITES));
        private final StreamCodec<ByteBuf, List<Named>> named = Named.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_MEMBERS));
        private final StreamCodec<ByteBuf, List<Online>> online = Online.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_ONLINE));

        @Override
        public SocialViewPayload decode(ByteBuf buf) {
            return new SocialViewPayload(ByteBufCodecs.VAR_INT.decode(buf), members.decode(buf), invites.decode(buf),
                    buf.readBoolean(), members.decode(buf), ByteBufCodecs.VAR_INT.decode(buf), invites.decode(buf),
                    named.decode(buf), buf.readBoolean(), named.decode(buf), online.decode(buf));
        }

        @Override
        public void encode(ByteBuf buf, SocialViewPayload view) {
            ByteBufCodecs.VAR_INT.encode(buf, view.cap());
            members.encode(buf, view.party());
            invites.encode(buf, view.partyInvites());
            buf.writeBoolean(view.teams());
            members.encode(buf, view.team());
            ByteBufCodecs.VAR_INT.encode(buf, view.teamRank());
            invites.encode(buf, view.teamInvites());
            named.encode(buf, view.alliedTeams());
            buf.writeBoolean(view.homeOwner());
            named.encode(buf, view.allies());
            online.encode(buf, view.online());
        }
    };

    @Override
    public Type<SocialViewPayload> type() {
        return TYPE;
    }
}
