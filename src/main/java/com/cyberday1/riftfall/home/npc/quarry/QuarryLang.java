package com.cyberday1.riftfall.home.npc.quarry;

import com.cyberday1.riftfall.RiftfallCore;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Every line the Quarry Specialist's screens show; the lang provider reads {@link #ENGLISH}. */
public final class QuarryLang {
    private static final String SCREEN = "screen." + RiftfallCore.MODID + ".quarry.";
    private static final String MESSAGE = "message." + RiftfallCore.MODID + ".quarry.";

    public static final String LANDING_REFINE = SCREEN + "landing.refine";
    public static final String LANDING_REFINE_HINT = SCREEN + "landing.refine_hint";
    public static final String LANDING_REFINE_NOTE = SCREEN + "landing.refine_note";
    public static final String LANDING_SURVEY = SCREEN + "landing.survey";
    public static final String LANDING_SURVEY_HINT = SCREEN + "landing.survey_hint";
    public static final String LANDING_SURVEY_NOTE = SCREEN + "landing.survey_note";

    public static final String SLOT_INPUTS = SCREEN + "slot.inputs";
    public static final String SLOT_SHARDS = SCREEN + "slot.shards";
    public static final String SLOT_OUTPUT = SCREEN + "slot.output";
    public static final String ENTRY_SURVEY = SCREEN + "entry.survey";
    public static final String ENTRY_SWAP = SCREEN + "entry.swap";
    public static final String ENTRY_ORDER = SCREEN + "entry.order";
    public static final String ENTRY_SURVEYED = SCREEN + "entry.surveyed";
    public static final String ENTRY_CHARTED = SCREEN + "entry.charted";
    public static final String SCROLL_UP = SCREEN + "scroll_up";
    public static final String SCROLL_DOWN = SCREEN + "scroll_down";
    public static final String CONFIRM = SCREEN + "confirm";
    public static final String START_ORDER = SCREEN + "start_order";
    public static final String WAITING = SCREEN + "waiting";
    public static final String NEEDS = SCREEN + "needs";
    public static final String ORDER_TIME = SCREEN + "order_time";
    public static final String ORDER_SLOTS = SCREEN + "order_slots";
    public static final String COST_SHARDS = SCREEN + "cost_shards";
    public static final String SWAP_COST = SCREEN + "swap_cost";
    public static final String SURVEY_HINT = SCREEN + "survey_hint";

    public static final String SURVEYED = MESSAGE + "surveyed";
    public static final String SWAPPED = MESSAGE + "swapped";
    public static final String MADE = MESSAGE + "made";
    public static final String ORDERED = MESSAGE + "ordered";
    public static final String CHANGED = MESSAGE + "changed";

    public static String status(String status) { return SCREEN + "status." + status; }

    public static final Map<String, String> ENGLISH;

    static {
        Map<String, String> en = new LinkedHashMap<>();
        en.put(LANDING_REFINE, "Refine materials");
        en.put(LANDING_REFINE_HINT, "Turn rarity materials and theme crystals into runestones, tempering stock, chisels and faceted crystals.");
        en.put(LANDING_REFINE_NOTE, "Runestones and stock");
        en.put(LANDING_SURVEY, "Survey & swap");
        en.put(LANDING_SURVEY_HINT, "Survey a theme crystal to learn it, then swap spare crystals into it from Journeyman.");
        en.put(LANDING_SURVEY_NOTE, "Surveys are free");

        en.put(SLOT_INPUTS, "Materials");
        en.put(SLOT_SHARDS, "Shards");
        en.put(SLOT_OUTPUT, "Out");
        en.put(ENTRY_SURVEY, "Survey");
        en.put(ENTRY_SWAP, "Swap: %s");
        en.put(ENTRY_ORDER, "%s (order)");
        en.put(ENTRY_SURVEYED, "Surveyed");
        en.put(ENTRY_CHARTED, "In your atlas: swaps are open");
        en.put(SCROLL_UP, "Up");
        en.put(SCROLL_DOWN, "Down");
        en.put(CONFIRM, "Confirm");
        en.put(START_ORDER, "Start order");
        en.put(WAITING, "Waiting for the Quarry Specialist...");
        en.put(NEEDS, "Needs: %s");
        en.put(ORDER_TIME, "Ready after %s run(s) or %s min online");
        en.put(ORDER_SLOTS, "%s order slot(s) free");
        en.put(COST_SHARDS, "%s shards (%s held)");
        en.put(SWAP_COST, "%s other theme crystals and %s shard");
        en.put(SURVEY_HINT, "The surveyed crystal is not used up.");

        en.put(status("ready"), "Ready");
        en.put(status("no_match"), "The materials do not cover this recipe");
        en.put(status("wrong_role"), "This resident does not make that");
        en.put(status("rank_locked"), "The Quarry Specialist needs a higher rank for this");
        en.put(status("deferred"), "This recipe needs research that does not exist yet");
        en.put(status("banned_result"), "This recipe is refused");
        en.put(status("shard_input"), "This recipe is refused");
        en.put(status("unsafe_set"), "Refining is paused: a recipe set would multiply materials");
        en.put(status("missing_shards"), "Not enough Rift Shards");
        en.put(status("no_space"), "Empty the output slot");
        en.put(status("path_locked"), "Another specialization's recipe");
        en.put(status("tier_locked"), "Your home has not cleared a high enough tier");
        en.put(status("undiscovered"), "Survey this crystal or run a rift of its theme first");
        en.put(status("no_order_slot"), "No free work-order slot; collect or wait for one");
        en.put(status("survey_known"), "You have already surveyed this crystal");
        en.put(status("survey_not_crystal"), "Only theme crystals can be surveyed");
        en.put(status("survey_empty"), "Place a theme crystal in the first slot");
        en.put(status("nothing"), "Choose a recipe");

        en.put(SURVEYED, "Surveyed %s. Swaps into it are open.");
        en.put(SWAPPED, "Crystals swapped");
        en.put(MADE, "Made");
        en.put(ORDERED, "Work order started. Collect it from the Orders tab.");
        en.put(CHANGED, "Inputs changed. Review the new quote.");
        ENGLISH = Collections.unmodifiableMap(en);
    }

    private QuarryLang() {}
}
