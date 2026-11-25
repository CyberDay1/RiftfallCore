package com.cyberday1.riftfall.home;

import com.cyberday1.riftfall.network.SocialViewPayload;
import com.mojang.authlib.GameProfile;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The home team and home allies facts the Party bookmark draws. Every OPAC call is in {@link HomeTeams},
 * reached only when {@link HomeAccess#teamsAvailable()}, so a server without OPAC never loads it.
 */
public final class HomeSocial {

    private HomeSocial() {
    }

    /** The viewer's team: id, members (owner first), the viewer's rank, and allied teams by owner. */
    public record Team(UUID id, List<SocialViewPayload.Member> members, int rank, List<SocialViewPayload.Named> allied,
                       Set<UUID> alliedIds) {
    }

    public record Facts(boolean teams, @Nullable Team team, List<SocialViewPayload.Invite> invites, boolean homeOwner,
                        List<SocialViewPayload.Named> allies) {
        public @Nullable UUID teamId() {
            return team == null ? null : team.id();
        }
    }

    public static Facts facts(MinecraftServer server, UUID viewer) {
        boolean teams = HomeAccess.teamsAvailable();
        Team team = teams ? HomeTeams.team(server, viewer) : null;
        List<SocialViewPayload.Invite> invites = teams && team == null ? HomeTeams.invitesFor(server, viewer) : List.of();
        UUID owner = HomeAccess.homeOwner(server, viewer);
        var allies = HomeAccessData.of(server).allies(owner).stream()
                .map(id -> new SocialViewPayload.Named(id, SocialViewPayload.name(name(server, id))))
                .sorted(Comparator.comparing(SocialViewPayload.Named::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
        return new Facts(teams, team, invites, owner.equals(viewer), allies);
    }

    /** Team invitations waiting for the player, for the bookmark badge. */
    public static int teamInvites(MinecraftServer server, UUID player) {
        return HomeAccess.teamsAvailable() && HomeTeams.teamOf(server, player) == null
                ? HomeTeams.invitesFor(server, player).size() : 0;
    }

    /** What {@code other} already is to the viewer, as {@link SocialViewPayload} team and ally flags. */
    public static int flags(MinecraftServer server, UUID viewer, Facts facts, UUID other) {
        int flags = 0;
        if (facts.teams()) {
            UUID theirs = HomeTeams.teamOf(server, other);
            if (theirs != null) {
                flags |= SocialViewPayload.ON_TEAM;
                if (theirs.equals(facts.teamId())) flags |= SocialViewPayload.MY_TEAM;
                if (facts.team() != null && facts.team().alliedIds().contains(theirs)) {
                    flags |= SocialViewPayload.ALLIED_TEAM;
                }
            }
        }
        if (HomeAccessData.of(server).allied(HomeAccess.homeOwner(server, viewer), other)) {
            flags |= SocialViewPayload.HOME_ALLY;
        }
        return flags;
    }

    /** A profile for a player the server knows: online, else the profile cache; null when neither has them. */
    public static @Nullable GameProfile profile(MinecraftServer server, UUID id) {
        var online = server.getPlayerList().getPlayer(id);
        if (online != null) return online.getGameProfile();
        var cache = server.getProfileCache();
        return cache == null ? null : cache.get(id).orElse(null);
    }

    static String name(MinecraftServer server, UUID id) {
        var profile = profile(server, id);
        return profile == null || profile.getName() == null || profile.getName().isEmpty()
                ? id.toString().substring(0, 8) : profile.getName();
    }
}
