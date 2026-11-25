package com.cyberday1.riftfall.home.npc.jeweler;

import com.cyberday1.riftfall.RiftfallCore;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Every line the Jeweler's screens show; the lang provider reads {@link #ENGLISH}. */
public final class JewelerLang {
    private static final String SCREEN = "screen." + RiftfallCore.MODID + ".jeweler.";
    static final String MESSAGE = "message." + RiftfallCore.MODID + ".jeweler.";

    public static final String LANDING_APPRAISE = SCREEN + "landing.appraise";
    public static final String LANDING_APPRAISE_HINT = SCREEN + "landing.appraise_hint";
    public static final String LANDING_APPRAISE_NOTE = SCREEN + "landing.appraise_note";
    public static final String LANDING_SOCKETS = SCREEN + "landing.sockets";
    public static final String LANDING_SOCKETS_HINT = SCREEN + "landing.sockets_hint";
    public static final String LANDING_SOCKETS_NOTE = SCREEN + "landing.sockets_note";
    public static final String LANDING_REFINE = SCREEN + "landing.refine";
    public static final String LANDING_REFINE_HINT = SCREEN + "landing.refine_hint";
    public static final String LANDING_REFINE_NOTE = SCREEN + "landing.refine_note";

    public static final String SLOT_ITEM = SCREEN + "slot.item";
    public static final String SLOT_GEM = SCREEN + "slot.gem";
    public static final String SLOT_GRIT = SCREEN + "slot.grit";
    public static final String SLOT_CRYSTALS = SCREEN + "slot.crystals";
    public static final String SLOT_SHARDS = SCREEN + "slot.shards";
    public static final String SLOT_MATERIAL = SCREEN + "slot.material";
    public static final String ACTION_SOCKET = SCREEN + "action.socket";
    public static final String ACTION_SOCKET_HINT = SCREEN + "action.socket_hint";
    public static final String ACTION_ORDER = SCREEN + "action.order";
    public static final String ACTION_ORDER_HINT = SCREEN + "action.order_hint";
    public static final String START_ORDER = SCREEN + "start_order";
    public static final String APPRAISE_CAP = SCREEN + "appraise.cap";
    public static final String COST_CHISEL = SCREEN + "cost.chisel";
    public static final String COST_WITHDRAWAL = SCREEN + "cost.withdrawal";
    public static final String ORDER_TIME = SCREEN + "order_time";

    public static final String ACTION_APPRAISE = SCREEN + "action.appraise";
    public static final String ACTION_SET = SCREEN + "action.set";
    public static final String ACTION_DISSOLVE = SCREEN + "action.dissolve";
    public static final String ACTION_REFINE = SCREEN + "action.refine";
    public static final String ACTION_UNSET = SCREEN + "action.unset";
    public static final String ACTION_SET_HINT = SCREEN + "action.set_hint";
    public static final String ACTION_DISSOLVE_HINT = SCREEN + "action.dissolve_hint";
    public static final String ACTION_REFINE_HINT = SCREEN + "action.refine_hint";
    public static final String ACTION_UNSET_HINT = SCREEN + "action.unset_hint";
    public static final String SOCKET_EMPTY = SCREEN + "socket_empty";
    public static final String CONFIRM = SCREEN + "confirm";
    public static final String WORKING = SCREEN + "working";
    public static final String WAITING = SCREEN + "waiting";

    public static final String APPRAISE_EMPTY = SCREEN + "appraise.empty";
    public static final String APPRAISE_SOCKETS = SCREEN + "appraise.sockets";
    public static final String APPRAISE_NO_SOCKETS = SCREEN + "appraise.no_sockets";
    public static final String APPRAISE_GEM = SCREEN + "appraise.gem";
    public static final String APPRAISE_GEM_TOP = SCREEN + "appraise.gem_top";

    public static final String COST_FREE = SCREEN + "cost.free";
    public static final String COST_SHARDS = SCREEN + "cost.shards";
    public static final String COST_GRIT = SCREEN + "cost.grit";
    public static final String COST_THEME = SCREEN + "cost.theme";
    public static final String COST_RARE = SCREEN + "cost.rare";
    public static final String COST_FACETED = SCREEN + "cost.faceted";
    public static final String COST_YIELD = SCREEN + "cost.yield";
    public static final String COST_HELD = SCREEN + "cost.held";
    public static final String PREVIEW_REFINE = SCREEN + "preview.refine";

    public static final Map<String, String> ENGLISH;

    static {
        Map<String, String> english = new LinkedHashMap<>();
        english.put(LANDING_APPRAISE, "Appraise");
        english.put(LANDING_APPRAISE_HINT, "Read an item's sockets, and a gem's purity and next step. Free; nothing changes.");
        english.put(LANDING_APPRAISE_NOTE, "Free reading");
        english.put(LANDING_SOCKETS, "Set & unset gems");
        english.put(LANDING_SOCKETS_HINT, "Set a gem in an empty socket, or take one out intact. Paid in Rift Shards and theme crystals.");
        english.put(LANDING_SOCKETS_NOTE, "Gems come out intact");
        english.put(LANDING_REFINE, "Refine & dissolve");
        english.put(LANDING_REFINE_HINT, "Dissolve unwanted gems into grit, then spend grit to raise a gem's purity.");
        english.put(LANDING_REFINE_NOTE, "Purity by rank");

        english.put(SLOT_ITEM, "Gear");
        english.put(SLOT_GEM, "Gem");
        english.put(SLOT_GRIT, "Grit");
        english.put(SLOT_CRYSTALS, "Crystals");
        english.put(SLOT_SHARDS, "Shards");
        english.put(SLOT_MATERIAL, "Tool");
        english.put(ACTION_SOCKET, "Add socket");
        english.put(ACTION_SOCKET_HINT, "Add one empty socket, up to the item's rarity: common and uncommon 1, rare 2, epic 3, mythic 4. Takes a socket chisel.");
        english.put(ACTION_ORDER, "Refine (order)");
        english.put(ACTION_ORDER_HINT, "Leave the gem with the Jeweler; it comes back one purity up after your runs or time online.");
        english.put(START_ORDER, "Start order");
        english.put(APPRAISE_CAP, "socket cap %s");
        english.put(COST_CHISEL, "1 socket chisel");
        english.put(COST_WITHDRAWAL, "1 Sigil of Withdrawal pays the unset");
        english.put(ORDER_TIME, "ready after %s run(s) or %s min online");

        english.put(ACTION_APPRAISE, "Appraise");
        english.put(ACTION_SET, "Set gem");
        english.put(ACTION_DISSOLVE, "Dissolve");
        english.put(ACTION_REFINE, "Refine");
        english.put(ACTION_UNSET, "Unset %s");
        english.put(ACTION_SET_HINT, "Set the gem in the gear's first empty socket.");
        english.put(ACTION_DISSOLVE_HINT, "Break a loose gem down into gem grit. Higher purity gives more grit.");
        english.put(ACTION_REFINE_HINT, "Raise the gem one purity, paid in grit, crystals and shards.");
        english.put(ACTION_UNSET_HINT, "Socket %s: %s. The gem returns to the gem slot; the socket stays.");
        english.put(SOCKET_EMPTY, "empty");
        english.put(CONFIRM, "Confirm");
        english.put(WORKING, "Working...");
        english.put(WAITING, "Waiting for the Jeweler...");

        english.put(APPRAISE_EMPTY, "Place gear or a gem to appraise it");
        english.put(APPRAISE_SOCKETS, "Sockets: %s of %s filled");
        english.put(APPRAISE_NO_SOCKETS, "This item has no sockets");
        english.put(APPRAISE_GEM, "%s gem | refines to %s");
        english.put(APPRAISE_GEM_TOP, "%s gem | highest purity");

        english.put(COST_FREE, "Free");
        english.put(COST_SHARDS, "%s shards");
        english.put(COST_GRIT, "%s grit");
        english.put(COST_THEME, "%s theme crystal(s)");
        english.put(COST_RARE, "%s rare crystal(s)");
        english.put(COST_FACETED, "%s faceted crystal(s)");
        english.put(COST_YIELD, "Returns %s grit");
        english.put(COST_HELD, "%s held");
        english.put(PREVIEW_REFINE, "Purity: %s -> %s");

        english.put(JewelerService.Result.READY.key(), "Ready");
        english.put(JewelerService.Result.APPRAISE.key(), "Appraisal is free. Choose a service to act.");
        english.put(JewelerService.Result.NO_ITEM.key(), "Place the gear in the gear slot");
        english.put(JewelerService.Result.NO_GEM.key(), "Place one loose gem in the gem slot");
        english.put(JewelerService.Result.NO_EMPTY_SOCKET.key(), "This item has no empty socket");
        english.put(JewelerService.Result.GEM_DOES_NOT_FIT.key(), "This gem does not fit this item");
        english.put(JewelerService.Result.EMPTY_SOCKET.key(), "That socket is empty");
        english.put(JewelerService.Result.GEM_SLOT_FULL.key(), "Empty the gem slot to receive the gem");
        english.put(JewelerService.Result.GRIT_SLOT_FULL.key(), "Make room in the grit slot");
        english.put(JewelerService.Result.MAX_PURITY.key(), "This gem is at its highest purity");
        english.put(JewelerService.Result.RANK.key(), "The Jeweler needs a higher rank for this purity");
        english.put(JewelerService.Result.MISSING_GRIT.key(), "Not enough gem grit");
        english.put(JewelerService.Result.MISSING_CRYSTALS.key(), "Not enough theme crystals");
        english.put(JewelerService.Result.MISSING_RARE.key(), "Needs a rare crystal: onyx, fire opal or maradonyx");
        english.put(JewelerService.Result.MISSING_FACETED.key(), "Needs faceted crystals");
        english.put(JewelerService.Result.MISSING_SHARDS.key(), "Not enough Rift Shards");
        english.put(JewelerService.Result.REFUSED.key(), "The Jeweler will not work on this item");
        english.put(JewelerService.Result.CHANGED.key(), "Inputs changed. Review the new quote.");
        english.put(JewelerService.Result.SET_DONE.key(), "Gem set");
        english.put(JewelerService.Result.UNSET_DONE.key(), "Gem removed; the socket stays");
        english.put(JewelerService.Result.DISSOLVE_DONE.key(), "Gem dissolved into grit");
        english.put(JewelerService.Result.REFINE_DONE.key(), "Gem refined");
        english.put(JewelerService.Result.ORDER_ONLY.key(), "This purity step is a work order: choose Refine (order)");
        english.put(JewelerService.Result.NO_ORDER.key(), "This purity step has no work order; refine it directly");
        english.put(JewelerService.Result.NO_ORDER_SLOT.key(), "No free work-order slot");
        english.put(JewelerService.Result.NOT_HOME.key(), "Work orders are taken in a Shattered Home");
        english.put(JewelerService.Result.NOT_SOCKETABLE.key(), "This item cannot take sockets");
        english.put(JewelerService.Result.AT_CAP.key(), "This item already has its rarity's most sockets");
        english.put(JewelerService.Result.STARTER.key(), "Starter kit gear cannot take more sockets");
        english.put(JewelerService.Result.MISSING_CHISEL.key(), "Place a socket chisel in the tool slot");
        english.put(JewelerService.Result.SOCKET_RANK.key(), "Adding sockets opens at Journeyman");
        english.put(JewelerService.Result.SOCKET_DONE.key(), "Socket added");
        english.put(JewelerService.Result.ORDER_STARTED.key(), "Purity order started. Collect it from the Orders tab.");
        ENGLISH = Collections.unmodifiableMap(english);
    }

    private JewelerLang() {}
}
