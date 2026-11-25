package com.cyberday1.riftfall.client;

import com.cyberday1.riftfall.network.SocialViewPayload;
import com.cyberday1.riftfall.network.SocialViewPayload.Invite;
import com.cyberday1.riftfall.network.SocialViewPayload.Member;
import com.cyberday1.riftfall.network.SocialViewPayload.Online;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

import static com.cyberday1.riftfall.network.SocialViewPayload.*;

/**
 * Which Party page buttons are live, and the line that says why one is not. Each answer is the refusal the
 * matching command would give ({@code PartyActions}, {@code HomeCommands}, OPAC's own rank checks), read off
 * the view. These only grey buttons: the server runs the command either way and its answer is the one that
 * counts. Null means allowed; otherwise a line name on the social page.
 */
final class SocialRules {

    private SocialRules() {
    }

    static boolean leads(SocialViewPayload view, UUID me) {
        return view.inParty() && view.party().getFirst().id().equals(me);
    }

    // -- expedition party ---------------------------------------------------------------------------

    static @Nullable String partyCreate(SocialViewPayload view) {
        return view.inParty() ? "why_in_party" : null;
    }

    /** Any member may invite; inviting with no party starts one. */
    static @Nullable String partyInvite(SocialViewPayload view, Online target) {
        if (target.is(IN_PARTY)) return "why_target_party";
        int size = view.inParty() ? view.party().size() : 1;
        return size >= view.cap() ? "why_full" : null;
    }

    static @Nullable String partyAccept(SocialViewPayload view, Invite invite) {
        if (view.inParty()) return "why_in_party";
        return invite.size() >= view.cap() ? "why_their_full" : null;
    }

    static @Nullable String partyLeave(SocialViewPayload view) {
        return view.inParty() ? null : "why_no_party";
    }

    /** Remove and disband are the leader's. */
    static @Nullable String partyLead(SocialViewPayload view, UUID me) {
        if (!view.inParty()) return "why_no_party";
        return leads(view, me) ? null : "why_not_leader";
    }

    // -- home team (OPAC) ---------------------------------------------------------------------------

    private static @Nullable String teams(SocialViewPayload view) {
        return view.teams() ? null : "no_opac";
    }

    private static @Nullable String onTeam(SocialViewPayload view) {
        if (!view.teams()) return "no_opac";
        return view.onTeam() ? null : "why_no_team";
    }

    /** Invite, kick and alliances need Moderator or higher, as OPAC's commands require. */
    private static @Nullable String staff(SocialViewPayload view) {
        String why = onTeam(view);
        if (why != null) return why;
        return view.teamRank() >= MODERATOR ? null : "why_rank";
    }

    static @Nullable String teamCreate(SocialViewPayload view) {
        String why = teams(view);
        if (why != null) return why;
        return view.onTeam() ? "why_on_team" : null;
    }

    static @Nullable String teamJoin(SocialViewPayload view) {
        return teamCreate(view);
    }

    static @Nullable String teamInvite(SocialViewPayload view, Online target) {
        String why = staff(view);
        if (why != null) return why;
        return target.is(ON_TEAM) ? "why_target_team" : null;
    }

    /** OPAC refuses the owner leaving: the owner disbands instead. */
    static @Nullable String teamLeave(SocialViewPayload view) {
        String why = onTeam(view);
        if (why != null) return why;
        return view.teamRank() == OWNER ? "why_owner_leave" : null;
    }

    static @Nullable String teamKick(SocialViewPayload view, Member member) {
        String why = staff(view);
        if (why != null) return why;
        boolean below = member.rank() != OWNER && (view.teamRank() == OWNER || member.rank() < view.teamRank());
        return below ? null : "why_rank_target";
    }

    static @Nullable String teamDisband(SocialViewPayload view) {
        String why = onTeam(view);
        if (why != null) return why;
        return view.teamRank() == OWNER ? null : "why_not_owner";
    }

    static @Nullable String teamAlly(SocialViewPayload view, Online target) {
        String why = staff(view);
        if (why != null) return why;
        if (!target.is(ON_TEAM)) return "why_target_no_team";
        return target.is(ALLIED_TEAM) ? "why_allied" : null;
    }

    static @Nullable String teamUnally(SocialViewPayload view) {
        return staff(view);
    }

    // -- home allies --------------------------------------------------------------------------------

    static @Nullable String homeAlly(SocialViewPayload view, Online target) {
        if (!view.homeOwner()) return "why_not_home_owner";
        return target.is(HOME_ALLY) ? "why_ally_already" : null;
    }

    static @Nullable String homeUnally(SocialViewPayload view) {
        return view.homeOwner() ? null : "why_not_home_owner";
    }
}
