package com.cyberday1.riftfall.home.npc.depth;

import com.cyberday1.riftfall.RiftfallCore;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Lines for the landing's Requests, Orders and Path tabs, the shipped request titles and the rift theme names they
 * use. Request lines take {@code %1$s} target, {@code %2$s} theme, {@code %3$s} lowest run tier, {@code %4$s} minutes.
 */
public final class BoardLang {
    private static final String SCREEN = "screen." + RiftfallCore.MODID + ".resident.";
    private static final String MESSAGE = "message." + RiftfallCore.MODID + ".resident.";

    public static final String TAB_SERVICES = SCREEN + "tab.services";
    public static final String TAB_REQUESTS = SCREEN + "tab.requests";
    public static final String TAB_ORDERS = SCREEN + "tab.orders";
    public static final String TAB_PATH = SCREEN + "tab.path";

    public static final String REQUESTS_NONE = SCREEN + "requests.none";
    public static final String REQUESTS_REFRESH = SCREEN + "requests.refresh";
    public static final String REQUESTS_GUEST = SCREEN + "requests.guest";
    public static final String PROGRESS = SCREEN + "requests.progress";
    public static final String DONE = SCREEN + "requests.done";
    public static final String DELIVER = SCREEN + "requests.deliver";
    public static final String REROLL_FREE = SCREEN + "requests.reroll_free";
    public static final String REROLL_COST = SCREEN + "requests.reroll_cost";

    public static final String ORDERS_NONE = SCREEN + "orders.none";
    public static final String ORDER_RUNS = SCREEN + "orders.runs";
    public static final String ORDER_READY = SCREEN + "orders.ready";
    public static final String ORDER_CANCEL = SCREEN + "orders.cancel";
    public static final String ORDER_COLLECT = SCREEN + "orders.collect";
    public static final String ORDER_KEPT = SCREEN + "orders.kept";

    public static final String PATH_CURRENT = SCREEN + "path.current";
    public static final String PATH_NONE = SCREEN + "path.none";
    public static final String PATH_CHOOSE = SCREEN + "path.choose";
    public static final String PATH_RESPEC = SCREEN + "path.respec";
    public static final String PATH_CONFIRM = SCREEN + "path.confirm";
    public static final String PATH_ONE_WAY = SCREEN + "path.one_way";
    public static final String PATH_RESPEC_COST = SCREEN + "path.respec_cost";
    public static final String STATION_LINE = SCREEN + "station.line";
    public static final String STATION_BUILD = SCREEN + "station.build";
    public static final String STATION_SHORT = MESSAGE + "station_short";
    public static final String STATION_FUNDED = MESSAGE + "station_funded";

    public static final String DELIVERED = MESSAGE + "delivered";
    public static final String REROLLED = MESSAGE + "rerolled";
    public static final String REROLL_SHORT = MESSAGE + "reroll_short";
    public static final String CANCELLED = MESSAGE + "order_cancelled";
    public static final String CANCEL_REFUSED = MESSAGE + "order_cancel_refused";
    public static final String PATH_CHOSEN = MESSAGE + "path_chosen";
    public static final String RESPEC_SHORT = MESSAGE + "respec_short";

    public static String kind(String kind) { return SCREEN + "request." + kind; }
    public static String pathState(String state) { return SCREEN + "path.state." + state.toLowerCase(java.util.Locale.ROOT); }
    public static String stationState(String state) { return SCREEN + "station.state." + state.toLowerCase(java.util.Locale.ROOT); }
    public static String theme(String themeId) {
        var id = net.minecraft.resources.ResourceLocation.tryParse(themeId);
        return id == null ? "" : "theme." + id.getNamespace() + "." + id.getPath();
    }

    /** The shipped rift themes, for request lines. */
    public static final List<String> THEMES = List.of("catacombs", "cave", "celestial", "default", "ender", "foundry", "glacial",
            "infernal", "mycelial", "ruins", "solar", "tidal", "void");

    public static final Map<String, String> ENGLISH;

    static {
        Map<String, String> en = new LinkedHashMap<>();
        en.put(TAB_SERVICES, "Services");
        en.put(TAB_REQUESTS, "Requests");
        en.put(TAB_ORDERS, "Orders");
        en.put(TAB_PATH, "Path");

        en.put(REQUESTS_NONE, "No requests yet. New ones come as the home runs rifts.");
        en.put(REQUESTS_REFRESH, "New requests in %s home runs");
        en.put(REQUESTS_GUEST, "Only members of this home can take its requests.");
        en.put(PROGRESS, "%s / %s");
        en.put(DONE, "Done");
        en.put(DELIVER, "Deliver %s");
        en.put(REROLL_FREE, "Reroll (free)");
        en.put(REROLL_COST, "Reroll (%s shards)");

        en.put(ORDERS_NONE, "No work orders here. Start one from a service page.");
        en.put(ORDER_RUNS, "%s / %s runs | %s / %s min");
        en.put(ORDER_READY, "Ready");
        en.put(ORDER_CANCEL, "Cancel");
        en.put(ORDER_COLLECT, "Collect ready");
        en.put(ORDER_KEPT, "Recipe orders can be canceled only before progress.");

        en.put(PATH_CURRENT, "Specialization: %s");
        en.put(PATH_NONE, "No specialization yet. The home owner picks at Master.");
        en.put(PATH_CHOOSE, "Choose");
        en.put(PATH_RESPEC, "Retrain");
        en.put(PATH_CONFIRM, "Confirm?");
        en.put(PATH_ONE_WAY, "Retraining needs its request, shards and ten home runs.");
        en.put(PATH_RESPEC_COST, "Retraining costs %s shards");
        en.put(STATION_LINE, "Station %s/%s. Next: %s slate + %s shards");
        en.put(STATION_BUILD, "Build");
        en.put(stationState("rank"), "Opens at the next rank");
        en.put(stationState("built"), "The station is fully built");
        en.put(stationState("not_member"), "Only home members pay for the station");
        en.put(pathState("not_owner"), "Only the home owner chooses a specialization");
        en.put(pathState("not_master"), "Specializations open at Master");
        en.put(pathState("already_chosen"), "A path is chosen");
        en.put(pathState("none_chosen"), "No path to retrain from");
        en.put(pathState("no_retrain"), "Complete this resident's retrain request first");
        en.put(pathState("locked"), "Retrained too recently; wait for more home runs");
        en.put(pathState("same"), "That is the current path");

        en.put(DELIVERED, "Delivered %s");
        en.put(REROLLED, "New requests posted");
        en.put(REROLL_SHORT, "A reroll costs %s Rift Shards");
        en.put(CANCELLED, "Work order canceled; what it held is back in your inventory");
        en.put(CANCEL_REFUSED, "This order has progressed and can no longer be canceled");
        en.put(PATH_CHOSEN, "%s is now a %s.");
        en.put(RESPEC_SHORT, "Retraining costs %s Rift Shards");
        en.put(STATION_SHORT, "The next station level costs %s gem-fused slate and %s Rift Shards");
        en.put(STATION_FUNDED, "Station level paid; it builds over the next few moments");

        en.put(kind("theme"), "Clear a %2$s rift at tier %3$s or higher");
        en.put(kind("boss"), "Rift bosses to defeat: %1$s");
        en.put(kind("mob"), "Defeat %1$s marked enemies");
        en.put(kind("tier"), "Clear a rift at your home's tier or higher");
        en.put(kind("clean"), "Clear a rift with no party deaths");
        en.put(kind("speed"), "Clear a rift inside %4$s minutes");
        en.put(kind("delivery"), "Bring %1$s rift materials");
        en.put(kind("variety"), "Clear rifts of %1$s different themes");

        en.put("theme.riftfall.catacombs", "catacombs");
        en.put("theme.riftfall.cave", "cave");
        en.put("theme.riftfall.celestial", "celestial");
        en.put("theme.riftfall.default", "stone");
        en.put("theme.riftfall.ender", "ender");
        en.put("theme.riftfall.foundry", "foundry");
        en.put("theme.riftfall.glacial", "glacial");
        en.put("theme.riftfall.infernal", "infernal");
        en.put("theme.riftfall.mycelial", "mycelial");
        en.put("theme.riftfall.ruins", "ruins");
        en.put("theme.riftfall.solar", "solar");
        en.put("theme.riftfall.tidal", "tidal");
        en.put("theme.riftfall.void", "void");

        String q = "commission.riftfall.quarry_specialist.", b = "commission.riftfall.blacksmith.", j = "commission.riftfall.jeweler.";
        en.put(q + "deliver_crystals", "Bring %1$s theme crystals for the sorting bins");
        en.put(q + "deliver_crystals_large", "Bring %1$s theme crystals for a faceting run");
        en.put(q + "clear_theme", "Survey: clear a %2$s rift at tier %3$s+");
        en.put(q + "elites_mycelial", "Clear %1$s elites from mycelial rifts");
        en.put(q + "elites_cave", "Clear %1$s elites from cave rifts");
        en.put(q + "retrain", "Retrain: clear a rift at home tier or higher");
        en.put(b + "clean_clear", "Clear a rift with no party deaths");
        en.put(b + "tier_clear", "Clear a rift at your home's tier or higher");
        en.put(b + "boss", "Rift bosses to bring down: %1$s");
        en.put(b + "speed", "Clear a rift inside %4$s minutes");
        en.put(b + "retrain", "Retrain: bring down %1$s rift bosses");
        en.put(j + "deliver_crystals", "Bring %1$s theme crystals to cut");
        en.put(j + "boss", "Rift bosses to bring down, for their gems: %1$s");
        en.put(j + "clear_theme", "Clear a %2$s rift at tier %3$s or higher");
        en.put(j + "clean_clear", "Clear a rift with no party deaths");
        en.put(j + "retrain", "Retrain: clear a rift with no party deaths");
        en.put(j + "withdrawal", "Sigil of Withdrawal: a tier %3$s+ %2$s rift");
        String c = "commission.riftfall.rift_cartographer.";
        en.put(c + "fresh_theme", "Chart new ground: clear a %2$s rift");
        en.put(c + "three_themes", "Clear rifts of %1$s different themes");
        en.put(c + "boss", "Rift bosses to bring down for the atlas: %1$s");
        en.put(c + "retrain", "Retrain: clear rifts of %1$s different themes");
        ENGLISH = Collections.unmodifiableMap(en);
    }

    private BoardLang() {}
}
