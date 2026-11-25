package com.cyberday1.riftfall.client;

import com.cyberday1.riftfall.block.marker.NpcRole;
import com.cyberday1.riftfall.home.npc.ResidentLandingMenu;
import com.cyberday1.riftfall.home.npc.ResidentLang;
import com.cyberday1.riftfall.home.npc.ResidentLanding;
import com.cyberday1.riftfall.home.npc.ResidentPaths;
import com.cyberday1.riftfall.home.npc.ResidentRank;
import com.cyberday1.riftfall.home.npc.depth.BoardLang;
import com.cyberday1.riftfall.home.npc.depth.DepthLang;
import com.cyberday1.riftfall.home.npc.depth.ResidentBoard;
import com.cyberday1.riftfall.home.npc.depth.ResidentDialogue;
import com.cyberday1.riftfall.network.ResidentBoardPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Every resident's native landing, in the Stellar workshop style: Services, Requests, Orders and (for roles with
 * paths) Path tabs over the rank panel. All text is a lang key resolved here; every number is the server's.
 * At 317 px it is taller than 1080p at auto GUI scale, so it shrinks to fit ({@link FittedContainerScreen}).
 */
public final class ResidentLandingScreen extends FittedContainerScreen<ResidentLandingMenu> {
    private static final int TILE_W = 139, TILE_H = 38, ACTION_H = 23, COLUMN = 147, ROW = 52, TOP = 52, PANEL_Y = 204;
    private static final int SERVICES = 0, REQUESTS = 1, ORDERS = 2, PATH = 3;
    /** Path tab: six rows of each path's effects from PATH_LINES, then its buttons, state lines and station row. */
    private static final int PATH_LINES = TOP + 26, PATH_LAST_ROW = PATH_LINES + 45, PATH_BUTTONS = 132, PATH_STATE = 150, STATION_Y = 186;
    /** Header room above the tabs: the name row, a services row when the name is long, three dialogue lines. */
    private static final int HEAD = 27, NAME_Y = 9, SAID_Y = 22, SAID_LONG_Y = 31, LINE_H = 9;
    private record Placed(int index, ResidentLanding.Entry entry, StellarWorkshop.Action button, int x, int y) {}
    private final List<Placed> placed = new ArrayList<>();
    private NpcRole builtFor;
    private int tab, builtVersion = -1, armed = -1;

    public ResidentLandingScreen(ResidentLandingMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 310; imageHeight = 290 + HEAD;
    }

    /** Top of everything below the header; the layout under it is unchanged from the one-line header. */
    private int body() { return topPos + HEAD; }

    private ResidentBoardPayload board() {
        var b = ResidentBoardClient.latest();
        return menu.role() != null && b.role() == menu.role().ordinal() ? b : ResidentBoardPayload.EMPTY;
    }

    @Override protected void init() {
        super.init();
        placed.clear();
        builtFor = menu.role();
        builtVersion = ResidentBoardClient.version();
        String[] tabs = {BoardLang.TAB_SERVICES, BoardLang.TAB_REQUESTS, BoardLang.TAB_ORDERS, BoardLang.TAB_PATH};
        int shown = builtFor != null && !ResidentPaths.of(builtFor).isEmpty() ? 4 : 3;
        for (int i = 0; i < shown; i++) {
            final int t = i;
            var b = addRenderableWidget(new StellarWorkshop.Action(leftPos + 12 + i * 74, body() + 33, 70, 14,
                    Component.translatable(tabs[i]), x -> { tab = t; armed = -1; rebuildWidgets(); }, false));
            b.selected(tab == i);
        }
        switch (tab) {
            case REQUESTS -> initRequests();
            case ORDERS -> initOrders();
            case PATH -> initPath();
            default -> initServices();
        }
        addRenderableWidget(new StellarWorkshop.Action(leftPos + 12, topPos + imageHeight - 27, 60, 18,
                Component.translatable(ResidentLang.CLOSE), b -> onClose(), false));
    }

    private void initServices() {
        var entries = menu.entries();
        int services = 0, servicesPlaced = 0, actions = 0;
        for (var entry : entries) if (entry.kind() == ResidentLanding.Kind.SERVICE) services++;
        int actionTop = TOP + (services + 1) / 2 * ROW + 2;
        for (int i = 0; i < entries.size(); i++) {
            var entry = entries.get(i);
            final int index = i;
            boolean service = entry.kind() == ResidentLanding.Kind.SERVICE;
            int slot = service ? servicesPlaced++ : actions++;
            int x = 12 + slot % 2 * COLUMN;
            int y = service ? TOP + slot / 2 * ROW : actionTop + slot / 2 * ROW;
            var button = addRenderableWidget(new StellarWorkshop.Action(leftPos + x, body() + y, TILE_W, service ? TILE_H : ACTION_H,
                    Component.translatable(entry.titleKey()), b -> {
                        send(entry.id());
                        if (entry.kind() == ResidentLanding.Kind.ACTION) b.active = false;
                    }, false));
            if (entry.hintKey() != null) button.setTooltip(Tooltip.create(Component.translatable(entry.hintKey())));
            var icon = entry.icon().get();
            if (service && icon != null) button.icon(new ItemStack(icon));
            placed.add(new Placed(index, entry, button, x, y));
        }
    }

    private void initRequests() {
        var b = board();
        for (int i = 0; i < b.requests().size() && i < 4; i++) {
            var r = b.requests().get(i);
            if (!"delivery".equals(r.kind()) || !b.member() || r.progress() >= r.target()) continue;
            int id = ResidentBoard.DELIVER + i;
            var button = addRenderableWidget(new StellarWorkshop.Action(leftPos + 212, body() + TOP + i * 34 + 11, 86, 14,
                    Component.translatable(BoardLang.DELIVER, r.deliverable()), x -> send(id), false));
            button.active = r.deliverable() > 0;
        }
        if (b.member() && b.role() >= 0) {
            var reroll = addRenderableWidget(new StellarWorkshop.Action(leftPos + 12, body() + 166, 120, 14,
                    b.rerollCost() == 0 ? Component.translatable(BoardLang.REROLL_FREE) : Component.translatable(BoardLang.REROLL_COST, b.rerollCost()),
                    x -> send(ResidentBoard.REROLL), false));
            reroll.active = b.requests().stream().anyMatch(r -> r.progress() < r.target()) || b.requests().isEmpty();
        }
    }

    private void initOrders() {
        var b = board();
        for (int i = 0; i < b.orders().size() && i < 4; i++) {
            var o = b.orders().get(i);
            if (!o.cancellable()) continue;
            int id = ResidentBoard.CANCEL + i;
            addRenderableWidget(new StellarWorkshop.Action(leftPos + 236, body() + TOP + i * 28 + 4, 62, 14,
                    Component.translatable(BoardLang.ORDER_CANCEL), x -> send(id), false));
        }
        var collect = addRenderableWidget(new StellarWorkshop.Action(leftPos + 12, body() + 166, 120, 14,
                Component.translatable(BoardLang.ORDER_COLLECT), x -> send(ResidentBoard.COLLECT), true));
        collect.active = b.orders().stream().anyMatch(ResidentBoardPayload.Order::ready);
    }

    private void initPath() {
        var role = menu.role();
        if (role == null) return;
        var b = board();
        var paths = ResidentPaths.of(role);
        int width = (imageWidth - 24) / Math.max(1, paths.size());
        boolean chosen = !b.path().isEmpty();
        for (int i = 0; i < paths.size(); i++) {
            String path = paths.get(i);
            if (chosen && path.equals(b.path())) continue;
            int id = (chosen ? ResidentBoard.RESPEC : ResidentBoard.CHOOSE) + i;
            boolean allowed = chosen ? b.respecState().isEmpty() : b.pathState().isEmpty();
            var label = armed == id ? Component.translatable(BoardLang.PATH_CONFIRM)
                    : Component.translatable(chosen ? BoardLang.PATH_RESPEC : BoardLang.PATH_CHOOSE);
            var button = addRenderableWidget(new StellarWorkshop.Action(leftPos + 12 + i * width, body() + PATH_BUTTONS, width - 6, 14, label, x -> {
                if (armed == id) { armed = -1; send(id); } else armed = id;
                rebuildWidgets();
            }, armed == id));
            button.active = allowed;
        }
        var station = b.station();
        if (station.top() > 0) {
            var build = addRenderableWidget(new StellarWorkshop.Action(leftPos + imageWidth - 82, body() + STATION_Y - 4, 70, 14,
                    Component.translatable(BoardLang.STATION_BUILD), x -> send(ResidentBoard.STATION), false));
            build.active = station.state().isEmpty();
        }
    }

    private void send(int id) {
        if (minecraft != null && minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
    }

    @Override protected void containerTick() {
        // The role arrives with the first data sync; the tabs' data with the server's payload.
        if (menu.role() != builtFor || tab != SERVICES && builtVersion != ResidentBoardClient.version()) rebuildWidgets();
    }

    @Override protected void renderFitted(GuiGraphics g, int x, int y, float partial) {
        for (var p : placed) {
            if (p.entry().kind() != ResidentLanding.Kind.ACTION) continue;
            p.button().active = menu.state(p.index()) == 0;
            String key = p.entry().stateKey(menu.state(p.index()));
            if (key != null) p.button().setTooltip(Tooltip.create(Component.translatable(key)));
        }
        super.renderFitted(g, x, y, partial);
        renderTooltip(g, x, y);
        int panel = body() + PANEL_Y;
        if (menu.rank().isPresent() && x >= leftPos + 12 && x < leftPos + imageWidth - 12 && y >= panel && y < panel + 16)
            atScreenScale(g, x, y, (sx, sy) -> g.renderTooltip(font, Component.translatable(DepthLang.SHARED), sx, sy));
        else if (tab == REQUESTS) requestTooltip(g, x, y);
        else if (tab == PATH) pathTooltip(g, x, y);
    }

    @Override protected void renderBg(GuiGraphics g, float partial, int x, int y) {
        StellarWorkshop.frame(g, leftPos, topPos, imageWidth, imageHeight);
        if (tab == ORDERS) {
            var b = board();
            for (int i = 0; i < b.orders().size() && i < 4; i++)
                g.renderItem(b.orders().get(i).show(), leftPos + 12, body() + TOP + i * 28 + 2);
        }
    }

    @Override protected void renderLabels(GuiGraphics g, int x, int y) {
        g.drawString(font, fit(title.getString(), imageWidth - 48), 36, NAME_Y, StellarWorkshop.INK, false);
        var role = menu.role();
        if (role == null) return;
        // A long "Name, Role" pushes the services subtitle to its own row rather than cutting either.
        String subtitle = I18n.get(ResidentLang.title(role));
        boolean beside = 36 + font.width(title) + 12 + font.width(subtitle) <= imageWidth - 12;
        int subY = beside ? NAME_Y : NAME_Y + LINE_H + 1;
        g.drawString(font, fit(subtitle, imageWidth - 24), imageWidth - 12 - Math.min(imageWidth - 24, font.width(subtitle)), subY,
                StellarWorkshop.GOLD, false);
        String said = line(role);
        if (said != null) {
            int top = beside ? SAID_Y : SAID_LONG_Y, rows = (HEAD + 31 - top) / LINE_H;
            var wrapped = font.getSplitter().splitLines(said, imageWidth - 24, net.minecraft.network.chat.Style.EMPTY).stream()
                    .map(net.minecraft.network.chat.FormattedText::getString).toList();
            for (int i = 0; i < wrapped.size() && i < rows; i++) {
                // A line past the room (never the shipped English) ends in "..." on the last row.
                String row = i == rows - 1 ? fit(String.join(" ", wrapped.subList(i, wrapped.size())), imageWidth - 24) : wrapped.get(i);
                g.drawString(font, row, 12, top + i * LINE_H, StellarWorkshop.INK, false);
            }
        }
        g.pose().pushPose();
        g.pose().translate(0, HEAD, 0);
        renderBody(g, role);
        g.pose().popPose();
    }

    private void renderBody(GuiGraphics g, NpcRole role) {
        switch (tab) {
            case REQUESTS -> labelsRequests(g);
            case ORDERS -> labelsOrders(g);
            case PATH -> labelsPath(g, role);
            default -> labelsServices(g);
        }
        menu.rank().ifPresent(rank -> renderNextRank(g, rank));
    }

    private void labelsServices(GuiGraphics g) {
        if (placed.isEmpty()) g.drawString(font, I18n.get(ResidentLang.NO_SERVICES), 12, TOP, StellarWorkshop.MUTED, false);
        for (var p : placed) {
            String line = p.entry().kind() == ResidentLanding.Kind.SERVICE
                    ? p.entry().noteKey() == null ? null : I18n.get(p.entry().noteKey())
                    : p.entry().stateKey(menu.state(p.index())) == null ? null : I18n.get(p.entry().stateKey(menu.state(p.index())));
            if (line == null) continue;
            boolean service = p.entry().kind() == ResidentLanding.Kind.SERVICE;
            int lineX = p.x() + (service ? 6 : 0);
            int lineY = p.y() + (service ? TILE_H + 3 : ACTION_H + 3);
            // A service note ends at its tile's edge, so a left tile's note never runs under the right one's.
            int room = service ? TILE_W - 6 : imageWidth - lineX - 12;
            g.drawString(font, fit(line, room), lineX, lineY, StellarWorkshop.MUTED, false);
        }
    }

    /** A request's own line when the pack ships one, else the line for its kind. */
    static String request(ResidentBoardPayload.Request r) {
        String key = ResidentBoardPayload.titleKey(r.def());
        if (!I18n.exists(key)) key = BoardLang.kind(r.kind());
        String themeKey = BoardLang.theme(r.theme());
        String theme = themeKey.isEmpty() ? "" : I18n.exists(themeKey) ? I18n.get(themeKey) : r.theme();
        return I18n.get(key, r.target(), theme, r.minRunTier(), r.minutes());
    }

    private void labelsRequests(GuiGraphics g) {
        var b = board();
        if (b.requests().isEmpty()) g.drawString(font, fit(I18n.get(BoardLang.REQUESTS_NONE), imageWidth - 24), 12, TOP, StellarWorkshop.MUTED, false);
        for (int i = 0; i < b.requests().size() && i < 4; i++) {
            var r = b.requests().get(i);
            int rowY = TOP + i * 34;
            boolean done = r.progress() >= r.target();
            String progress = progress(r);
            g.drawString(font, fit(request(r), requestRoom(progress)), 12, rowY, StellarWorkshop.INK, false);
            g.drawString(font, progress, imageWidth - 12 - font.width(progress), rowY, done ? StellarWorkshop.GOLD : StellarWorkshop.MUTED, false);
            int w = 190;
            g.fill(12, rowY + 14, 12 + w, rowY + 17, 0xFF1D2334);
            g.fill(12, rowY + 14, 12 + (int) (w * Math.min(1.0, r.progress() / (double) Math.max(1, r.target()))), rowY + 17, 0xFF867655);
        }
        if (b.role() >= 0 && !b.member()) g.drawString(font, fit(I18n.get(BoardLang.REQUESTS_GUEST), imageWidth - 24), 12, 186, StellarWorkshop.MUTED, false);
        else if (b.role() >= 0) g.drawString(font, fit(I18n.get(BoardLang.REQUESTS_REFRESH, b.runsToRefresh()), imageWidth - 152), 140, 169, StellarWorkshop.MUTED, false);
    }

    /** A request line's room: the row less its progress count on the right. */
    private int requestRoom(String progress) { return imageWidth - 30 - font.width(progress); }

    private static String progress(ResidentBoardPayload.Request r) {
        return r.progress() >= r.target() ? I18n.get(BoardLang.DONE) : I18n.get(BoardLang.PROGRESS, r.progress(), r.target());
    }

    /** The whole request on hover, when its row had to cut it. */
    private void requestTooltip(GuiGraphics g, int x, int y) {
        var b = board();
        for (int i = 0; i < b.requests().size() && i < 4; i++) {
            var r = b.requests().get(i);
            String text = request(r);
            int room = requestRoom(progress(r)), rowY = body() + TOP + i * 34;
            if (font.width(text) > room && x >= leftPos + 12 && x < leftPos + 12 + room && y >= rowY && y < rowY + 9)
                atScreenScale(g, x, y, (sx, sy) -> g.renderTooltip(font, font.split(Component.literal(text), 200), sx, sy));
        }
    }

    /** A path's effects in full on hover over its column, for a locale whose lines need more than the six rows. */
    private void pathTooltip(GuiGraphics g, int x, int y) {
        var role = menu.role();
        if (role == null) return;
        var paths = ResidentPaths.of(role);
        int width = (imageWidth - 24) / Math.max(1, paths.size());
        int top = body() + TOP + 14, bottom = body() + PATH_LAST_ROW + 9;
        if (y < top || y >= bottom || x < leftPos + 12) return;
        int i = (x - leftPos - 12) / width;
        if (i >= paths.size()) return;
        var lines = new ArrayList<Component>();
        lines.add(Component.translatable(ResidentPaths.name(role, paths.get(i))).withStyle(net.minecraft.ChatFormatting.GOLD));
        for (int l = 0; l < ResidentPaths.LINES; l++) lines.add(Component.translatable(ResidentPaths.line(role, paths.get(i), l)));
        atScreenScale(g, x, y, (sx, sy) -> g.renderComponentTooltip(font, lines, sx, sy));
    }

    private void labelsOrders(GuiGraphics g) {
        var b = board();
        if (b.orders().isEmpty()) g.drawString(font, fit(I18n.get(BoardLang.ORDERS_NONE), imageWidth - 24), 12, TOP, StellarWorkshop.MUTED, false);
        for (int i = 0; i < b.orders().size() && i < 4; i++) {
            var o = b.orders().get(i);
            int rowY = TOP + i * 28;
            String name = o.show().isEmpty() ? "?" : o.show().getHoverName().getString() + (o.show().getCount() > 1 ? " x" + o.show().getCount() : "");
            g.drawString(font, fit(name, 200), 32, rowY, StellarWorkshop.INK, false);
            String clock = o.ready() ? I18n.get(BoardLang.ORDER_READY)
                    : I18n.get(BoardLang.ORDER_RUNS, o.runsDone(), o.runs(), Math.min(o.minutesDone(), o.minutes()), o.minutes());
            g.drawString(font, fit(clock, 200), 32, rowY + 10, o.ready() ? StellarWorkshop.GOLD : StellarWorkshop.MUTED, false);
        }
        g.drawString(font, fit(I18n.get(BoardLang.ORDER_KEPT), imageWidth - 24), 12, 186, StellarWorkshop.MUTED, false);
    }

    private void labelsPath(GuiGraphics g, NpcRole role) {
        var b = board();
        var paths = ResidentPaths.of(role);
        String current = b.path().isEmpty() ? I18n.get(BoardLang.PATH_NONE)
                : I18n.get(BoardLang.PATH_CURRENT, I18n.get(ResidentPaths.name(role, b.path())));
        g.drawString(font, fit(current, imageWidth - 24), 12, TOP, StellarWorkshop.GOLD, false);
        int width = (imageWidth - 24) / Math.max(1, paths.size());
        for (int i = 0; i < paths.size(); i++) {
            String path = paths.get(i);
            int x = 12 + i * width;
            g.drawString(font, fit(I18n.get(ResidentPaths.name(role, path)), width - 6), x, TOP + 14,
                    path.equals(b.path()) ? StellarWorkshop.GOLD : StellarWorkshop.INK, false);
            int lineY = PATH_LINES;
            for (int l = 0; l < ResidentPaths.LINES; l++) {
                for (var part : font.split(Component.translatable(ResidentPaths.line(role, path, l)), width - 6)) {
                    if (lineY > PATH_LAST_ROW) break;
                    g.drawString(font, part, x, lineY, StellarWorkshop.MUTED, false);
                    lineY += 9;
                }
            }
        }
        String state = b.path().isEmpty() ? b.pathState() : b.respecState();
        if (!state.isEmpty()) g.drawString(font, fit(I18n.get(BoardLang.pathState(state)), imageWidth - 24), 12, PATH_STATE, StellarWorkshop.ERROR, false);
        if (!b.path().isEmpty()) g.drawString(font, fit(I18n.get(BoardLang.PATH_RESPEC_COST, b.respecShards()), imageWidth - 24), 12, PATH_STATE + 10, StellarWorkshop.MUTED, false);
        g.drawString(font, fit(I18n.get(BoardLang.PATH_ONE_WAY), imageWidth - 24), 12, PATH_STATE + 20, StellarWorkshop.MUTED, false);
        var station = b.station();
        if (station.top() > 0) {
            String line = station.state().isEmpty() || station.state().equals("NOT_MEMBER")
                    ? I18n.get(BoardLang.STATION_LINE, station.funded(), station.top(), station.slate(), station.shards())
                    : I18n.get(BoardLang.stationState(station.state()));
            g.drawString(font, fit(line, imageWidth - 104), 12, STATION_Y, StellarWorkshop.INK, false);
        }
    }

    /** This visit's line: the first key this client has, in ResidentDialogue#keys order. */
    private String line(NpcRole role) {
        int t = menu.trigger();
        if (t < 0 || t >= ResidentDialogue.Trigger.values().length) return null;
        var trigger = ResidentDialogue.Trigger.values()[t];
        String key = ResidentDialogue.keys(role, trigger, menu.variant(), menu.known()).stream()
                .filter(I18n::exists).findFirst().orElse(null);
        if (key == null) return null;
        Object arg = switch (trigger) {
            case BOSS -> BuiltInRegistries.ENTITY_TYPE.byId(menu.arg()).getDescription().getString();
            case TIER -> menu.arg();
            default -> "";
        };
        return I18n.get(key, arg);
    }

    /** Rank, an XP bar toward the next rank, the next gate, what it opens and the home's best clear. */
    private void renderNextRank(GuiGraphics g, int rankIndex) {
        var ranks = ResidentRank.values();
        var rank = ranks[Math.clamp(rankIndex, 0, ranks.length - 1)];
        var next = rank.next();
        int w = imageWidth - 24, y = PANEL_Y;
        g.drawString(font, I18n.get(DepthLang.rank(rank)), 12, y, StellarWorkshop.GOLD, false);
        String xp = next == null ? I18n.get(DepthLang.XP_TOP, menu.xp()) : I18n.get(DepthLang.XP, menu.xp(), next.xp());
        g.drawString(font, xp, imageWidth - 12 - font.width(xp), y, StellarWorkshop.INK, false);
        int from = rank.xp(), to = next == null ? from : next.xp();
        int filled = next == null ? w : (int) (w * Math.clamp((menu.xp() - from) / (double) Math.max(1, to - from), 0.0, 1.0));
        g.fill(12, y + 11, 12 + w, y + 15, 0xFF1D2334);
        g.fill(12, y + 11, 12 + filled, y + 15, 0xFF867655);
        g.drawString(font, fit(next == null ? I18n.get(DepthLang.TOP)
                : I18n.get(DepthLang.NEXT, I18n.get(DepthLang.rank(next)), next.xp(), next.gate()), w), 12, y + 19, StellarWorkshop.MUTED, false);
        var role = menu.role();
        int row = y + 29;
        if (next != null && role != null && I18n.exists(DepthLang.opens(role, next))) {
            g.drawString(font, fit(I18n.get(DepthLang.opens(role, next)), w), 12, row, StellarWorkshop.MUTED, false);
            row += 10;
        }
        g.drawString(font, fit(menu.best() > 0 ? I18n.get(DepthLang.BEST, menu.best()) : I18n.get(DepthLang.BEST_NONE), w),
                12, row, StellarWorkshop.MUTED, false);
    }

    private String fit(String text, int width) {
        if (width <= 0) return "";
        return font.width(text) <= width ? text : font.plainSubstrByWidth(text, Math.max(0, width - font.width("..."))) + "...";
    }
}
