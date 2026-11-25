package com.cyberday1.riftfall.home;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import com.cyberday1.riftfall.network.SocialViewPayload;
import xaero.pac.common.server.api.OpenPACServerAPI;
import xaero.pac.common.server.parties.party.api.IServerPartyAPI;
import xaero.pac.common.server.claims.protection.override.api.*;
import xaero.pac.common.server.player.config.api.v2.IPlayerConfigAPI;
import xaero.pac.common.server.claims.action.listener.api.IClaimActionListenerAPI;
import xaero.pac.common.server.claims.action.listener.override.api.*;
import xaero.pac.common.server.claims.api.IServerClaimsManagerAPI;
import xaero.pac.common.claims.action.api.ClaimingAction;
import java.util.*;

/** Only loaded with OpenPAC present. Its persistent parties are Riftfall teams. */
final class HomeTeams {
    private HomeTeams() {}
    static UUID homeOwner(MinecraftServer server, UUID player) {
        var team = OpenPACServerAPI.get(server).getPartyManager().getPartyByMember(player);
        return team == null ? player : home(server, team);
    }

    private static UUID home(MinecraftServer server, IServerPartyAPI team) {
        return HomeAccessData.of(server).teamHome(team.getId(), team.getOwner().getUUID());
    }

    static Set<UUID> residents(MinecraftServer server, UUID owner) {
        Set<UUID> result = new HashSet<>();
        result.add(owner);
        OpenPACServerAPI.get(server).getPartyManager().getAllStream()
                .filter(team -> home(server, team).equals(owner))
                .forEach(team -> team.getMemberInfoStream().forEach(member -> result.add(member.getUUID())));
        return result;
    }

    static boolean isAllied(MinecraftServer server, UUID owner, UUID visitor) {
        var manager = OpenPACServerAPI.get(server).getPartyManager();
        var guestTeam = manager.getPartyByMember(visitor);
        return guestTeam != null && manager.getAllStream()
                .anyMatch(team -> home(server, team).equals(owner) && team.isAlly(guestTeam.getId()));
    }

    static int command(CommandSourceStack source, String command) throws CommandSyntaxException {
        // Execute as the original player: OPAC enforces permissions, caps and invitation rules,
        // persists changes and updates its clients/claim permission caches.
        int result = source.getServer().getCommands().getDispatcher().execute("oparties " + command, source);
        var team = OpenPACServerAPI.get(source.getServer()).getPartyManager()
                .getPartyByMember(source.getPlayerOrException().getUUID());
        if (team != null) home(source.getServer(), team);
        return result;
    }

    static UUID teamId(MinecraftServer server, UUID member) throws CommandSyntaxException {
        var team = OpenPACServerAPI.get(server).getPartyManager().getPartyByMember(member);
        if (team == null) throw HomeCommands.error(HomeCommands.NO_TEAM);
        return team.getId();
    }

    /** The viewer's team for the Party bookmark, in OPAC-free terms; null when on none. */
    static HomeSocial.Team team(MinecraftServer server, UUID viewer) {
        var team = OpenPACServerAPI.get(server).getPartyManager().getPartyByMember(viewer);
        if (team == null) return null;
        var members = new ArrayList<SocialViewPayload.Member>();
        team.getMemberInfoStream().forEach(member -> members.add(new SocialViewPayload.Member(member.getUUID(),
                SocialViewPayload.name(member.getUsername()), server.getPlayerList().getPlayer(member.getUUID()) != null,
                rank(member))));
        members.sort(Comparator.comparingInt(SocialViewPayload.Member::rank).reversed()
                .thenComparing(SocialViewPayload.Member::name, String.CASE_INSENSITIVE_ORDER));
        var me = team.getMemberInfo(viewer);
        var allied = new ArrayList<SocialViewPayload.Named>();
        var alliedIds = new HashSet<UUID>();
        team.getAllyPartiesStream().forEach(ally -> {
            var other = OpenPACServerAPI.get(server).getPartyManager().getPartyById(ally.getPartyId());
            if (other == null) return;
            alliedIds.add(other.getId());
            allied.add(new SocialViewPayload.Named(other.getOwner().getUUID(),
                    SocialViewPayload.name(other.getOwner().getUsername())));
        });
        allied.sort(Comparator.comparing(SocialViewPayload.Named::name, String.CASE_INSENSITIVE_ORDER));
        return new HomeSocial.Team(team.getId(), members, me == null ? SocialViewPayload.MEMBER : rank(me), allied,
                Set.copyOf(alliedIds));
    }

    private static int rank(xaero.pac.common.parties.party.member.api.IPartyMemberAPI member) {
        return member.isOwner() ? SocialViewPayload.OWNER : member.getRank().ordinal();
    }

    /** Teams that have invited the player, by owner. */
    static List<SocialViewPayload.Invite> invitesFor(MinecraftServer server, UUID player) {
        return OpenPACServerAPI.get(server).getPartyManager().getAllStream()
                .filter(team -> team.isInvited(player) && team.getMemberInfo(player) == null)
                .map(team -> new SocialViewPayload.Invite(team.getOwner().getUUID(),
                        SocialViewPayload.name(team.getOwner().getUsername()), team.getMemberCount()))
                .sorted(Comparator.comparing(SocialViewPayload.Invite::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    static UUID teamOf(MinecraftServer server, UUID player) {
        var team = OpenPACServerAPI.get(server).getPartyManager().getPartyByMember(player);
        return team == null ? null : team.getId();
    }

    static void install(MinecraftServer server) {
        var api = OpenPACServerAPI.get(server);
        api.getPartyManager().getAllStream().forEach(team -> home(server, team));
        api.getServerClaimsManager().getChunkAccessOverriderManager().register(new IChunkAccessOverriderAPI() {
            @Override public String getName() { return "Riftfall homes"; }
            @Override public ChunkAccessOverride overrideChunkAccess(ResourceLocation dimension, int x, int z,
                    IPlayerConfigAPI config, Entity accessor, UUID accessorId, MinecraftServer currentServer,
                    ChunkAccessOverride current) {
                UUID owner = HomeAccess.owner(dimension);
                if (owner == null) return current;
                return new ChunkAccessOverride(HomeAccess.canInteract(currentServer, owner, accessorId)
                        ? ChunkAccessOverrideType.ALLOW : ChunkAccessOverrideType.PROTECT);
            }
        });
        // A visitor must not be able to claim a host's home or block its future expansion.
        api.getServerClaimsManager().getActionListenerManager().register(new IClaimActionListenerAPI() {
            @Override public String getName() { return "Riftfall home ownership"; }
            @Override public ClaimActionPermissionOverride overrideClaimingActionPermission(UUID player,
                    ResourceLocation dimension, int x, int z, ClaimingAction action,
                    IServerClaimsManagerAPI claims, ClaimActionPermissionOverride current, MinecraftServer currentServer) {
                UUID owner = HomeAccess.owner(dimension);
                if (owner == null || owner.equals(player)) return current;
                return new ClaimActionPermissionOverride(ClaimActionPermissionOverrideType.FORBID,
                        Component.translatable(HomeCommands.CLAIMS_OWNER_ONLY));
            }
            @Override public void handleSuccessfulClaimingAction(UUID player, ResourceLocation dimension,
                    int x, int z, ClaimingAction action, IServerClaimsManagerAPI claims, MinecraftServer currentServer) {}
        });
    }
}
