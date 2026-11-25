package com.cyberday1.riftfall.client;

import com.cyberday1.riftfall.network.SocialReplyPayload;
import com.cyberday1.riftfall.network.SocialRequestPayload;
import com.cyberday1.riftfall.network.SocialRequestPayload.Kind;
import com.cyberday1.riftfall.network.SocialViewPayload;
import com.cyberday1.riftfall.network.SocialViewPayload.Invite;
import com.cyberday1.riftfall.network.SocialViewPayload.Member;
import com.cyberday1.riftfall.network.SocialViewPayload.Named;
import com.cyberday1.riftfall.network.SocialViewPayload.Online;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * The Party bookmark: three sections, Expedition (the rift party), Home team (OPAC) and Allies (home access).
 * The left page is the group you are in and its buttons; the right page is invitations, then the players
 * online to invite. Every button sends a {@link SocialRequestPayload} that the server runs as the command.
 */
public class SocialScreen extends ProgressionScreen {

    enum Section { PARTY, TEAM, ALLIES }

    private static final int L = ProgressionFrame.LEFT;
    private static final int R = ProgressionFrame.RIGHT;
    private static final int T = ProgressionFrame.TOP;
    private static final int W = ProgressionFrame.PAGE_WIDTH;
    private static final int ROW = 12;
    private static final int LIST_TOP = T + 44;
    private static final int BUTTONS = T + 196;
    private static final int INVITE_ROWS = 4;
    private static final int PICK_HEAD = T + 12 + INVITE_ROWS * ROW + 6;
    private static final int PICK_TOP = PICK_HEAD + 12;
    /** Three lines of reply: a refusal with a player's name runs to three at the page's width. */
    private static final int REPLY_LINES = 3;
    private static final int REPLY_TOP = ProgressionFrame.BOTTOM - 10 * REPLY_LINES;
    private static final int PICK_BOTTOM = REPLY_TOP - 4;

    /** Remembered while the game runs, like a book left open at a page. */
    private static Section section = Section.PARTY;

    /** A destructive verb waiting for its second click. */
    private record Confirm(String title, String text, Object name, String yes, SocialRequestPayload request) {
    }

    private @Nullable Confirm confirm;
    private int scroll;

    public SocialScreen() {
        super(ProgressionTab.SOCIAL);
    }

    @Override
    protected void init() {
        super.init();
        var badge = SocialClient.badge();
        if (badge.partyInvites() > 0) section = Section.PARTY;
        else if (badge.teamInvites() > 0) section = Section.TEAM;
        SocialClient.asked();
        send(SocialRequestPayload.of(Kind.VIEW));
    }

    @Override
    public void removed() {
        send(SocialRequestPayload.of(Kind.CLOSE));
        super.removed();
    }

    private static void send(SocialRequestPayload request) {
        // removed() also runs while disconnecting, when there is no server left to tell.
        if (net.minecraft.client.Minecraft.getInstance().getConnection() != null) {
            PacketDistributor.sendToServer(request);
        }
    }

    private void act(Kind kind, @Nullable UUID target) {
        SocialClient.clearReply();
        send(target == null ? SocialRequestPayload.of(kind) : SocialRequestPayload.of(kind, target));
    }

    private MutableComponent line(String key, Object... args) {
        return Component.translatable(tab.key(key), args);
    }

    private UUID me() {
        return minecraft != null && minecraft.player != null ? minecraft.player.getUUID() : new UUID(0, 0);
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (key == 256 && confirm != null) {
            confirm = null;
            return true;
        }
        return super.keyPressed(key, scanCode, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mouseBookX >= R - 4) {
            scroll = Math.max(0, scroll - (int) Math.signum(scrollY));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    protected void renderPages(GuiGraphics graphics, float partialTick) {
        SocialViewPayload view = SocialClient.latest();
        sections(graphics, view);
        if (!SocialClient.answered()) {
            text(graphics, line("loading"), L, T + 17, CodexStyle.MUTED);
            return;
        }
        switch (section) {
            case PARTY -> party(graphics, view);
            case TEAM -> team(graphics, view);
            case ALLIES -> allies(graphics, view);
        }
        reply(graphics);
    }

    // -- the three sections' tabs -------------------------------------------------------------------

    private void sections(GuiGraphics graphics, SocialViewPayload view) {
        Section[] all = Section.values();
        // Sized to their labels, as the Skills page's tabs are; an equal share each only when that runs over.
        int natural = 2 * (all.length - 1);
        for (Section each : all) {
            Component label = line("tab_" + each.name().toLowerCase(java.util.Locale.ROOT));
            natural += font.width(each == section ? bold(label) : label) + 6;
        }
        int max = natural <= W ? W : (W - 2 * (all.length - 1)) / all.length;
        int x = L;
        for (Section each : all) {
            boolean current = each == section;
            Component label = line("tab_" + each.name().toLowerCase(java.util.Locale.ROOT));
            Component shown = fit(current ? bold(label) : label, max - 6);
            int w = Math.min(max, font.width(shown) + 6);
            hot(x, T - 2, x + w, T + 11, button -> {
                if (section != each) {
                    section = each;
                    confirm = null;
                    scroll = 0;
                    SocialClient.clearReply();
                }
                return true;
            });
            if (current) {
                graphics.fill(x, T - 2, x + w, T + 11, CodexStyle.ACCENT);
                text(graphics, shown, x + 3, T + 1, CodexStyle.ON_ACCENT);
            } else {
                frame(graphics, x, T - 1, x + w, T + 11, CodexStyle.TAB_IDLE);
                text(graphics, shown, x + 3, T + 1, CodexStyle.MUTED);
            }
            int waiting = switch (each) {
                case PARTY -> view.partyInvites().size();
                case TEAM -> view.teamInvites().size();
                case ALLIES -> 0;
            };
            if (waiting > 0) {
                graphics.fill(x + w - 4, T, x + w - 1, T + 3, current ? CodexStyle.ON_ACCENT : CodexStyle.ACCENT);
            }
            x += w + 2;
        }
        graphics.fill(L, T + 11, L + W, T + 12, CodexStyle.ACCENT);
    }

    // -- Expedition ---------------------------------------------------------------------------------

    private void party(GuiGraphics graphics, SocialViewPayload view) {
        UUID me = me();
        text(graphics, bold(line("party_title")), L, T + 17, CodexStyle.ACCENT);
        text(graphics, view.inParty() ? line("party_count", view.party().size(), view.cap()) : line("party_none"),
                L, T + 28, CodexStyle.MUTED);
        dotted(graphics, L, L + W, T + 39, CodexStyle.DOTS);
        if (confirm != null) {
            confirmation(graphics);
        } else {
            if (!view.inParty()) {
                wrapped(graphics, line("party_hint"), L, LIST_TOP, W, 10, 4, CodexStyle.MUTED);
            }
            String lead = SocialRules.partyLead(view, me);
            int shown = rows(view.party().size());
            for (int i = 0; i < shown; i++) {
                Member member = view.party().get(i);
                int y = LIST_TOP + i * ROW;
                int right = L + W;
                if (!member.id().equals(me)) {
                    right = link(graphics, "remove", right, y + 2, lead, true, () -> confirm = new Confirm(
                            "c_party_remove_t", "c_party_remove", member.name(), "remove",
                            SocialRequestPayload.of(Kind.PARTY_REMOVE, member.id())));
                    right -= 4;
                }
                Component tag = member.rank() == SocialViewPayload.LEADER ? line("leader") : null;
                person(graphics, L, member.id(), member.name(), member.online(), member.id().equals(me), tag, y,
                        right - L);
            }
            more(graphics, view.party().size() - shown, L, BUTTONS - 11);
            int third = (W - 8) / 3;
            boolean leads = SocialRules.leads(view, me) && view.party().size() > 1;
            act(graphics, L, BUTTONS, third, line("create"), SocialRules.partyCreate(view), view,
                    () -> act(Kind.PARTY_CREATE, null));
            act(graphics, L + third + 4, BUTTONS, third, line("leave"), SocialRules.partyLeave(view), view,
                    () -> confirm = new Confirm("c_party_leave_t", leads ? "c_party_leave_lead" : "c_party_leave", "",
                            "leave", SocialRequestPayload.of(Kind.PARTY_LEAVE)));
            act(graphics, L + 2 * (third + 4), BUTTONS, W - 2 * (third + 4), line("disband"),
                    SocialRules.partyLead(view, me), view, () -> confirm = new Confirm("c_party_disband_t",
                            "c_party_disband", "", "disband", SocialRequestPayload.of(Kind.PARTY_DISBAND)));
        }

        heading(graphics, line("invites"), view.partyInvites().size());
        if (view.partyInvites().isEmpty()) {
            text(graphics, line("invites_none"), R, T + 13, CodexStyle.DIM);
        }
        for (int i = 0; i < Math.min(INVITE_ROWS, view.partyInvites().size()); i++) {
            Invite invite = view.partyInvites().get(i);
            int y = T + 12 + i * ROW;
            int right = link(graphics, "decline", R + W, y + 2, null, true, () -> act(Kind.PARTY_DECLINE, invite.from()));
            right = link(graphics, "accept", right - 6, y + 2, SocialRules.partyAccept(view, invite), false,
                    () -> act(Kind.PARTY_ACCEPT, invite.from()));
            face(graphics, invite.from(), R, y + 1);
            text(graphics, fit(line("party_from", invite.name(), invite.size()), right - 4 - (R + 11)), R + 11, y + 2,
                    CodexStyle.INK);
        }
        dotted(graphics, R, R + W, PICK_HEAD - 4, CodexStyle.DOTS);
        picker(graphics, view, line("invite_title"), (online, y, right) -> {
            if (online.is(SocialViewPayload.MY_PARTY)) {
                textRight(graphics, line("in_your_party"), right, y + 2, CodexStyle.DIM);
                return right - font.width(line("in_your_party"));
            }
            return link(graphics, "invite", right, y + 2, SocialRules.partyInvite(view, online), false,
                    () -> act(Kind.PARTY_INVITE, online.id()));
        });
    }

    // -- Home team ----------------------------------------------------------------------------------

    private void team(GuiGraphics graphics, SocialViewPayload view) {
        UUID me = me();
        text(graphics, bold(line("team_title")), L, T + 17, CodexStyle.ACCENT);
        Component sub = !view.onTeam() ? line("team_none")
                : line(view.team().size() == 1 ? "team_count_one" : "team_count", view.team().size());
        text(graphics, fit(sub, W), L, T + 28, CodexStyle.MUTED);
        dotted(graphics, L, L + W, T + 39, CodexStyle.DOTS);
        if (confirm != null) {
            confirmation(graphics);
        } else {
            if (!view.teams()) {
                wrapped(graphics, line("no_opac"), L, LIST_TOP, W, 10, 4, CodexStyle.MUTED);
            } else if (!view.onTeam()) {
                wrapped(graphics, line("team_hint"), L, LIST_TOP, W, 10, 4, CodexStyle.MUTED);
            }
            int shown = rows(view.team().size());
            for (int i = 0; i < shown; i++) {
                Member member = view.team().get(i);
                int y = LIST_TOP + i * ROW;
                int right = L + W;
                if (!member.id().equals(me)) {
                    right = link(graphics, "remove", right, y + 2, SocialRules.teamKick(view, member), true,
                            () -> confirm = new Confirm("c_team_kick_t", "c_team_kick", member.name(), "remove",
                                    SocialRequestPayload.of(Kind.TEAM_KICK, member.id())));
                    right -= 4;
                }
                person(graphics, L, member.id(), member.name(), member.online(), member.id().equals(me),
                        line("rank_" + Math.clamp(member.rank(), 0, SocialViewPayload.OWNER)), y, right - L);
            }
            more(graphics, view.team().size() - shown, L, BUTTONS - 11);
            int third = (W - 8) / 3;
            act(graphics, L, BUTTONS, third, line("create"), SocialRules.teamCreate(view), view,
                    () -> act(Kind.TEAM_CREATE, null));
            act(graphics, L + third + 4, BUTTONS, third, line("leave"), SocialRules.teamLeave(view), view,
                    () -> confirm = new Confirm("c_team_leave_t", "c_team_leave", "", "leave",
                            SocialRequestPayload.of(Kind.TEAM_LEAVE)));
            act(graphics, L + 2 * (third + 4), BUTTONS, W - 2 * (third + 4), line("disband"),
                    SocialRules.teamDisband(view), view, () -> confirm = new Confirm("c_team_disband_t",
                            "c_team_disband", "", "disband", SocialRequestPayload.of(Kind.TEAM_DISBAND)));
        }

        heading(graphics, line("invites"), view.teamInvites().size());
        if (view.teamInvites().isEmpty()) {
            text(graphics, line("invites_none"), R, T + 13, CodexStyle.DIM);
        }
        for (int i = 0; i < Math.min(INVITE_ROWS, view.teamInvites().size()); i++) {
            Invite invite = view.teamInvites().get(i);
            int y = T + 12 + i * ROW;
            int right = link(graphics, "join", R + W, y + 2, SocialRules.teamJoin(view), false,
                    () -> act(Kind.TEAM_JOIN, invite.from()));
            face(graphics, invite.from(), R, y + 1);
            text(graphics, fit(line("team_from", invite.name(), invite.size()), right - 4 - (R + 11)), R + 11, y + 2,
                    CodexStyle.INK);
        }
        dotted(graphics, R, R + W, PICK_HEAD - 4, CodexStyle.DOTS);
        picker(graphics, view, line("invite_title"), (online, y, right) -> {
            if (online.is(SocialViewPayload.MY_TEAM)) {
                textRight(graphics, line("on_your_team"), right, y + 2, CodexStyle.DIM);
                return right - font.width(line("on_your_team"));
            }
            return link(graphics, "invite", right, y + 2, SocialRules.teamInvite(view, online), false,
                    () -> act(Kind.TEAM_INVITE, online.id()));
        });
    }

    // -- Allies -------------------------------------------------------------------------------------

    private void allies(GuiGraphics graphics, SocialViewPayload view) {
        text(graphics, bold(line("allies_title")), L, T + 17, CodexStyle.ACCENT);
        dotted(graphics, L, L + W, T + 39, CodexStyle.DOTS);
        if (confirm != null) {
            confirmation(graphics);
        } else {
            if (view.allies().isEmpty()) {
                text(graphics, line("allies_none"), L, LIST_TOP + 2, CodexStyle.DIM);
            }
            int shown = Math.min(view.allies().size(), (BUTTONS - 26 - LIST_TOP) / ROW);
            for (int i = 0; i < shown; i++) {
                Named ally = view.allies().get(i);
                int y = LIST_TOP + i * ROW;
                int right = link(graphics, "remove", L + W, y + 2, SocialRules.homeUnally(view), true,
                        () -> confirm = new Confirm("c_ally_remove_t", "c_ally_remove", ally.name(), "remove",
                                SocialRequestPayload.of(Kind.HOME_UNALLY, ally.id()))) - 4;
                person(graphics, L, ally.id(), ally.name(), isOnline(view, ally.id()), false, null, y, right - L);
            }
            more(graphics, view.allies().size() - shown, L, BUTTONS - 24);
            wrapped(graphics, line(view.homeOwner() ? "allies_hint" : "why_not_home_owner"), L, BUTTONS - 12, W, 10, 3,
                    view.homeOwner() ? CodexStyle.DIM : CodexStyle.MUTED);
        }

        heading(graphics, line("teams_title"), view.alliedTeams().size());
        if (!view.teams()) {
            wrapped(graphics, line("no_opac"), R, T + 13, W, 10, 3, CodexStyle.DIM);
        } else if (!view.onTeam()) {
            wrapped(graphics, line("teams_need_team"), R, T + 13, W, 10, 3, CodexStyle.DIM);
        } else if (view.alliedTeams().isEmpty()) {
            text(graphics, line("teams_none"), R, T + 13, CodexStyle.DIM);
        }
        for (int i = 0; i < Math.min(INVITE_ROWS, view.alliedTeams().size()); i++) {
            Named team = view.alliedTeams().get(i);
            int y = T + 12 + i * ROW;
            int right = link(graphics, "end", R + W, y + 2, SocialRules.teamUnally(view), true,
                    () -> confirm = new Confirm("c_team_unally_t", "c_team_unally", team.name(), "end",
                            SocialRequestPayload.of(Kind.TEAM_UNALLY, team.id())));
            face(graphics, team.id(), R, y + 1);
            text(graphics, fit(line("team_of", team.name()), right - 4 - (R + 11)), R + 11, y + 2, CodexStyle.INK);
        }
        dotted(graphics, R, R + W, PICK_HEAD - 4, CodexStyle.DOTS);
        picker(graphics, view, line("add_title"), (online, y, right) -> {
            if (view.teams() && view.onTeam() && !online.is(SocialViewPayload.MY_TEAM)) {
                right = link(graphics, "ally_team", right, y + 2, SocialRules.teamAlly(view, online), false,
                        () -> act(Kind.TEAM_ALLY, online.id())) - 6;
            }
            return link(graphics, "ally", right, y + 2, SocialRules.homeAlly(view, online), false,
                    () -> act(Kind.HOME_ALLY, online.id()));
        });
    }

    private static boolean isOnline(SocialViewPayload view, UUID id) {
        return view.online().stream().anyMatch(online -> online.id().equals(id));
    }

    // -- shared pieces ------------------------------------------------------------------------------

    /** Draws a picker row's links right-aligned from {@code right}; returns the x left of them. */
    @FunctionalInterface
    private interface RowLinks {
        int draw(Online online, int y, int right);
    }

    /** The right page's lower list: everyone else online, scrolled, each with its links. */
    private void picker(GuiGraphics graphics, SocialViewPayload view, Component title, RowLinks links) {
        List<Online> online = view.online();
        int rows = Math.max(1, (PICK_BOTTOM - PICK_TOP) / ROW);
        scroll = Math.max(0, Math.min(scroll, Math.max(0, online.size() - rows)));
        int shown = Math.min(rows, online.size() - scroll);
        Component more = scroll + shown < online.size() ? line("more", online.size() - scroll - shown) : null;
        text(graphics, fit(bold(title), more == null ? W : W - font.width(more) - 6), R, PICK_HEAD, CodexStyle.ACCENT);
        if (online.isEmpty()) {
            text(graphics, line("online_none"), R, PICK_TOP + 2, CodexStyle.DIM);
            return;
        }
        for (int i = 0; i < shown; i++) {
            Online each = online.get(scroll + i);
            int y = PICK_TOP + i * ROW;
            int left = links.draw(each, y, R + W);
            person(graphics, R, each.id(), each.name(), true, false, null, y, left - 4 - R);
        }
        if (more != null) {
            textRight(graphics, more, R + W, PICK_HEAD, CodexStyle.DIM);
        }
    }

    /** A right-page heading with its count. */
    private void heading(GuiGraphics graphics, Component title, int count) {
        text(graphics, bold(title), R, T, CodexStyle.ACCENT);
        if (count > 0) {
            textRight(graphics, Component.literal(Integer.toString(count)), R + W, T, CodexStyle.MUTED);
        }
    }

    /** Face, name and an optional tag ("Leader", a rank), from {@code left}, in {@code width}. */
    private void person(GuiGraphics graphics, int left, UUID id, String name, boolean online, boolean self,
                        @Nullable Component tag, int y, int width) {
        face(graphics, id, left, y + 1);
        int room = width - 11;
        Component suffix = self ? line("you") : online ? null : line("away");
        if (tag != null) {
            room -= font.width(tag) + 4;
        }
        if (suffix != null) {
            room -= font.width(suffix) + 4;
        }
        Component shown = fit(Component.literal(name), Math.max(12, room));
        int end = text(graphics, shown, left + 11, y + 2, online ? CodexStyle.INK : CodexStyle.DIM);
        if (suffix != null) {
            end = text(graphics, suffix, end + 4, y + 2, CodexStyle.DIM);
        }
        if (tag != null) {
            text(graphics, tag, end + 4, y + 2, CodexStyle.ACCENT);
        }
    }

    private void face(GuiGraphics graphics, UUID id, int x, int y) {
        var connection = minecraft == null ? null : minecraft.getConnection();
        PlayerInfo info = connection == null ? null : connection.getPlayerInfo(id);
        if (info != null) {
            PlayerFaceRenderer.draw(graphics, info.getSkin(), x, y, 8);
        } else {
            graphics.fill(x, y, x + 8, y + 8, CodexStyle.WELL_DARK);
        }
    }

    /** How many member rows fit above the buttons. */
    private static int rows(int count) {
        return Math.min(count, (BUTTONS - 14 - LIST_TOP) / ROW);
    }

    private void more(GuiGraphics graphics, int hidden, int x, int y) {
        if (hidden > 0) {
            text(graphics, line("more", hidden), x, y, CodexStyle.DIM);
        }
    }

    /**
     * A right-aligned text link ending at {@code right}; greyed with its reason on hover when {@code why} is set.
     * Returns its left edge.
     */
    private int link(GuiGraphics graphics, String key, int right, int y, @Nullable String why, boolean danger,
                     Runnable action) {
        Component label = line(key);
        int left = right - font.width(label);
        boolean live = why == null;
        boolean hover = live && hot(left - 1, y - 2, right + 1, y + 9, button -> {
            if (button != PRIMARY) {
                return false;
            }
            action.run();
            return true;
        });
        int colour = !live ? CodexStyle.DIM : hover ? CodexStyle.ACCENT_HOT : danger ? CodexStyle.WARN : CodexStyle.ACCENT;
        text(graphics, label, left, y, colour);
        if (hover) {
            graphics.fill(left, y + 9, right, y + 10, colour);
        }
        if (!live && over(left - 1, y - 2, right + 1, y + 9)) {
            tooltip(List.of(why(why, SocialClient.latest())));
        }
        return left;
    }

    /** A slab button, greyed with its reason on hover when {@code why} is set. */
    private void act(GuiGraphics graphics, int x, int y, int w, Component label, @Nullable String why,
                     SocialViewPayload view, Runnable action) {
        button(graphics, x, y, w, fit(label, w - 4), why == null, action);
        if (why != null && over(x, y, x + w, y + 18)) {
            tooltip(List.of(why(why, view)));
        }
    }

    private Component why(String key, SocialViewPayload view) {
        return key.equals("why_full") ? line(key, view.cap()) : line(key);
    }

    /** The second click for a destructive verb, in place of the left page's list and buttons. */
    private void confirmation(GuiGraphics graphics) {
        Confirm asked = confirm;
        int y = wrapped(graphics, bold(line(asked.title(), asked.name())), L, LIST_TOP, W, 10, 2, CodexStyle.ACCENT);
        wrapped(graphics, line(asked.text(), asked.name()), L, y + 4, W, 10, 5, CodexStyle.WARN);
        int half = (W - 4) / 2;
        button(graphics, L, BUTTONS, half, line(asked.yes()), true, () -> {
            confirm = null;
            SocialClient.clearReply();
            send(asked.request());
        });
        button(graphics, L + half + 4, BUTTONS, W - half - 4, line("back"), true, () -> confirm = null);
    }

    /** What the command said, at the foot of the right page: chat is hidden behind the book. */
    private void reply(GuiGraphics graphics) {
        SocialReplyPayload reply = SocialClient.reply();
        if (reply == null || reply.lines().isEmpty()) {
            return;
        }
        int colour = reply.ok() ? CodexStyle.GOOD : CodexStyle.WARN;
        int y = REPLY_TOP;
        int budget = REPLY_LINES;
        for (Component said : reply.lines()) {
            // The command's own colour (red for a refusal) gives way to the book's.
            List<FormattedCharSequence> lines = font.split(said.copy().setStyle(Style.EMPTY), W);
            for (int i = 0; i < lines.size() && budget > 0; i++, budget--) {
                graphics.drawString(font, lines.get(i), R, y, colour, false);
                y += 10;
            }
        }
    }
}
