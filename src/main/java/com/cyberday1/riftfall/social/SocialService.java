package com.cyberday1.riftfall.social;

import com.cyberday1.riftfall.RiftfallCore;
import com.cyberday1.riftfall.home.HomeCommands;
import com.cyberday1.riftfall.home.HomeSocial;
import com.cyberday1.riftfall.network.SocialBadgePayload;
import com.cyberday1.riftfall.network.SocialReplyPayload;
import com.cyberday1.riftfall.network.SocialRequestPayload;
import com.cyberday1.riftfall.network.SocialRequestPayload.Kind;
import com.cyberday1.riftfall.network.SocialViewPayload;
import com.cyberday1.riftfall.rift.PartyActions;
import com.cyberday1.riftfall.rift.RiftInvites;
import com.cyberday1.riftfall.rift.RiftParties;
import com.cyberday1.riftfall.rift.RiftParty;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.util.FakePlayer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The Party bookmark's server half. A request runs the same method its command runs ({@link PartyActions},
 * {@link HomeCommands}), as the player, at the player's own permission level, so every check a typed command
 * makes is made here too. The only checks added are about the packet: a verb that names a player must name
 * one, and one whose command takes an online player must name one who is online.
 */
public final class SocialService {

    private static final String PREFIX = "message." + RiftfallCore.MODID + ".social.";
    static final String NO_TARGET = PREFIX + "no_target";
    static final String OFFLINE = PREFIX + "offline";

    /** The provider reads this. */
    public static final Map<String, String> ENGLISH = Map.of(
            NO_TARGET, "Pick a player first.",
            OFFLINE, "That player is not online.");

    private SocialService() {
    }

    public static void handle(ServerPlayer player, SocialRequestPayload request) {
        switch (request.kind()) {
            case VIEW -> SocialSync.watch(player);
            case CLOSE -> SocialSync.unwatch(player);
            default -> {
                SocialSync.send(player, act(player, request));
                SocialSync.push(player);
            }
        }
    }

    /** Runs one verb as the player and returns what it said. */
    static SocialReplyPayload act(ServerPlayer player, SocialRequestPayload request) {
        Capture capture = new Capture(player);
        CommandSourceStack source = player.createCommandSourceStack().withSource(capture);
        boolean ok;
        try {
            ok = run(source, player.server, request) > 0;
        } catch (CommandSyntaxException refused) {
            capture.sendSystemMessage(ComponentUtils.fromMessage(refused.getRawMessage()).copy()
                    .withStyle(ChatFormatting.RED));
            ok = false;
        }
        return new SocialReplyPayload(capture.lines, ok);
    }

    /** The command each verb is; the payload's target, resolved, is what the command's argument would be. */
    static int run(CommandSourceStack source, MinecraftServer server, SocialRequestPayload request)
            throws CommandSyntaxException {
        Kind kind = request.kind();
        UUID target = request.target().orElse(null);
        if (kind.targeted() && target == null) {
            throw error(NO_TARGET);
        }
        return switch (kind) {
            case VIEW, CLOSE -> 0;
            case PARTY_CREATE -> PartyActions.create(source);
            case PARTY_INVITE -> PartyActions.invite(source, online(server, target));
            case PARTY_ACCEPT -> PartyActions.answer(source, online(server, target), true);
            case PARTY_DECLINE -> PartyActions.answer(source, online(server, target), false);
            case PARTY_LEAVE -> PartyActions.leave(source);
            case PARTY_REMOVE -> PartyActions.remove(source, List.of(known(server, target)));
            case PARTY_DISBAND -> PartyActions.disband(source);
            case TEAM_CREATE -> HomeCommands.teamVerb(source, "create");
            case TEAM_LEAVE -> HomeCommands.teamVerb(source, "leave");
            case TEAM_DISBAND -> HomeCommands.teamVerb(source, "disband");
            case TEAM_INVITE -> HomeCommands.teamTarget(source, "invite", online(server, target).getGameProfile());
            case TEAM_JOIN -> HomeCommands.teamTarget(source, "join", known(server, target));
            case TEAM_KICK -> HomeCommands.teamTarget(source, "kick", known(server, target));
            case TEAM_ALLY -> HomeCommands.teamTarget(source, "ally", online(server, target).getGameProfile());
            case TEAM_UNALLY -> HomeCommands.teamTarget(source, "unally", known(server, target));
            case HOME_ALLY -> HomeCommands.setAlly(source, online(server, target).getGameProfile(), true);
            case HOME_UNALLY -> HomeCommands.setAlly(source, known(server, target), false);
        };
    }

    /** The verbs whose command takes an online player. */
    static ServerPlayer online(MinecraftServer server, UUID id) throws CommandSyntaxException {
        ServerPlayer player = server.getPlayerList().getPlayer(id);
        if (player == null) {
            throw error(OFFLINE);
        }
        return player;
    }

    /**
     * The verbs whose target may be offline (a member, an ally). An uncached name still removes the right
     * person: the party and ally commands act on the id and only print the name.
     */
    static GameProfile known(MinecraftServer server, UUID id) {
        GameProfile profile = HomeSocial.profile(server, id);
        return profile != null ? profile : new GameProfile(id, id.toString().substring(0, 8));
    }

    static CommandSyntaxException error(String key) {
        return new SimpleCommandExceptionType(Component.translatable(key)).create();
    }

    // -- what the page draws ------------------------------------------------------------------------

    public static SocialViewPayload view(ServerPlayer viewer) {
        MinecraftServer server = viewer.server;
        UUID me = viewer.getUUID();
        RiftParties parties = RiftParties.of(server);
        RiftParty party = parties.of(me);
        List<SocialViewPayload.Member> members = new ArrayList<>();
        if (party != null) {
            for (UUID id : party.members()) {
                members.add(new SocialViewPayload.Member(id, name(server, id),
                        server.getPlayerList().getPlayer(id) != null,
                        party.isLeader(id) ? SocialViewPayload.LEADER : SocialViewPayload.MEMBER));
            }
        }
        HomeSocial.Facts home = HomeSocial.facts(server, me);
        List<SocialViewPayload.Online> online = new ArrayList<>();
        for (ServerPlayer other : server.getPlayerList().getPlayers()) {
            if (other.getUUID().equals(me) || other instanceof FakePlayer) {
                continue;
            }
            int flags = HomeSocial.flags(server, me, home, other.getUUID());
            if (parties.of(other.getUUID()) != null) flags |= SocialViewPayload.IN_PARTY;
            if (party != null && party.has(other.getUUID())) flags |= SocialViewPayload.MY_PARTY;
            online.add(new SocialViewPayload.Online(other.getUUID(),
                    SocialViewPayload.name(other.getGameProfile().getName()), flags));
        }
        online.sort(Comparator.comparing(SocialViewPayload.Online::name, String.CASE_INSENSITIVE_ORDER));
        HomeSocial.Team team = home.team();
        return new SocialViewPayload(PartyActions.cap(), members, partyInvites(server, me), home.teams(),
                team == null ? List.of() : team.members(), team == null ? SocialViewPayload.NO_RANK : team.rank(),
                home.invites(), team == null ? List.of() : team.allied(), home.homeOwner(), home.allies(), online);
    }

    /**
     * Standing expedition invitations, by inviter, as {@code /riftfall party invites} lists them: only inviters
     * still here and still in the party that asked, since accepting names the inviter's party.
     */
    static List<SocialViewPayload.Invite> partyInvites(MinecraftServer server, UUID me) {
        RiftParties parties = RiftParties.of(server);
        List<SocialViewPayload.Invite> invites = new ArrayList<>();
        for (RiftInvites.Invite invite : RiftInvites.pending(server.getTickCount(), me)) {
            ServerPlayer inviter = server.getPlayerList().getPlayer(invite.inviter());
            RiftParty asking = parties.of(invite.inviter());
            if (inviter != null && asking != null && asking.id().equals(invite.party())) {
                invites.add(new SocialViewPayload.Invite(invite.inviter(),
                        SocialViewPayload.name(inviter.getGameProfile().getName()), asking.size()));
            }
        }
        return invites;
    }

    static SocialBadgePayload badge(ServerPlayer player) {
        return new SocialBadgePayload(partyInvites(player.server, player.getUUID()).size(),
                HomeSocial.teamInvites(player.server, player.getUUID()));
    }

    private static String name(MinecraftServer server, UUID id) {
        GameProfile profile = HomeSocial.profile(server, id);
        return SocialViewPayload.name(profile == null || profile.getName() == null || profile.getName().isEmpty()
                ? id.toString().substring(0, 8) : profile.getName());
    }

    /** The player as a command source, keeping a copy of every line said to them for the page. */
    static final class Capture implements CommandSource {
        private final ServerPlayer player;
        final List<Component> lines = new ArrayList<>();

        Capture(ServerPlayer player) {
            this.player = player;
        }

        @Override
        public void sendSystemMessage(Component message) {
            lines.add(message);
            player.sendSystemMessage(message);
        }

        @Override
        public boolean acceptsSuccess() {
            return true;
        }

        @Override
        public boolean acceptsFailure() {
            return true;
        }

        @Override
        public boolean shouldInformAdmins() {
            return false;
        }
    }
}
