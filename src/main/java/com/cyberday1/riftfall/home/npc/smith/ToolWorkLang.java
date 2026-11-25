package com.cyberday1.riftfall.home.npc.smith;

import com.cyberday1.riftfall.RiftfallCore;
import com.cyberday1.riftfall.tool.ToolMode;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Every line the Blacksmith's tool upgrade page and the pick's mode tooltip show; the lang provider reads {@link #ENGLISH}. */
public final class ToolWorkLang {
    private static final String SCREEN = "screen." + RiftfallCore.MODID + ".smith_tools.";
    static final String MESSAGE = "message." + RiftfallCore.MODID + ".smith_tools.";

    public static final String LANDING = SCREEN + "landing";
    public static final String LANDING_HINT = SCREEN + "landing_hint";
    public static final String LANDING_NOTE = SCREEN + "landing_note";

    public static final String SLOT_TOOL = SCREEN + "slot.tool";
    public static final String SLOT_MATERIAL = SCREEN + "slot.material";
    public static final String SLOT_CRYSTAL = SCREEN + "slot.crystal";
    public static final String SLOT_SHARDS = SCREEN + "slot.shards";
    public static final String ACTION_FORTUNE = SCREEN + "action.fortune";
    public static final String ACTION_FORTUNE_HINT = SCREEN + "action.fortune_hint";
    public static final String ACTION_MODE_HINT = SCREEN + "action.mode_hint";
    public static final String CONFIRM = SCREEN + "confirm";
    public static final String WAITING = SCREEN + "waiting";
    public static final String FORTUNE_LEVEL = SCREEN + "fortune_level";
    public static final String FORTUNE_TOP = SCREEN + "fortune_top";
    public static final String MODE_STATE = SCREEN + "mode_state";
    public static final String NONE = SCREEN + "none";
    public static final String COST = SCREEN + "cost";
    public static final String COST_CRYSTAL = SCREEN + "cost_crystal";
    public static final String COST_FACETED = SCREEN + "cost_faceted";
    public static final String PICK_MODES = "tooltip." + RiftfallCore.MODID + ".looting_pick.modes";
    public static final String FORTUNE_UNEARNED = "tooltip." + RiftfallCore.MODID + ".fortune_unearned";

    public static String mode(ToolMode mode) { return "tooltip." + RiftfallCore.MODID + ".tool_mode." + mode.name().toLowerCase(Locale.ROOT); }

    public static final Map<String, String> ENGLISH;

    static {
        Map<String, String> en = new LinkedHashMap<>();
        en.put(LANDING, "Tool upgrades");
        en.put(LANDING_HINT, "Fortune up to VIII on mining tools, and shovel, axe or hoe work for the Looting Pick.");
        en.put(LANDING_NOTE, "Fortune, tool modes");

        en.put(SLOT_TOOL, "Tool");
        en.put(SLOT_MATERIAL, "Material");
        en.put(SLOT_CRYSTAL, "Crystal");
        en.put(SLOT_SHARDS, "Shards");
        en.put(ACTION_FORTUNE, "Fortune");
        en.put(ACTION_FORTUNE_HINT, "One Fortune level per visit, up to VIII. I-II at Journeyman, III at Expert, IV-VIII at Master. Only the Fitter gives Fortune.");
        en.put(ACTION_MODE_HINT, "The Looting Pick also works as a %s: digging speed, drops and right-click use. Once per pick.");
        en.put(CONFIRM, "Confirm");
        en.put(WAITING, "Waiting for the Fitter...");
        en.put(FORTUNE_LEVEL, "Fortune %s -> %s");
        en.put(FORTUNE_TOP, "Fortune %s, the most the Fitter gives");
        en.put(MODE_STATE, "%s mode");
        en.put(NONE, "Place a tool in the tool slot");
        en.put(COST, "%s shards + %s x%s (%s held)");
        en.put(COST_CRYSTAL, "%s shards + %s x%s + %s rare crystal(s) (%s held)");
        en.put(COST_FACETED, "%s shards + %s x%s + %s faceted crystal(s) (%s held)");
        en.put(PICK_MODES, "Also works as: %s");
        en.put(FORTUNE_UNEARNED, "Fortune here does nothing: only the Fitter's applies");
        en.put(mode(ToolMode.SHOVEL), "Shovel");
        en.put(mode(ToolMode.AXE), "Axe");
        en.put(mode(ToolMode.HOE), "Hoe");

        en.put(ToolWorkService.Result.READY.key(), "Ready");
        en.put(ToolWorkService.Result.NO_ITEM.key(), "Place one tool in the tool slot");
        en.put(ToolWorkService.Result.NOT_SINGLE.key(), "One item at a time");
        en.put(ToolWorkService.Result.BANNED.key(), "The Fitter will not touch this item");
        en.put(ToolWorkService.Result.NOT_SUPPORTED.key(), "Fortune does not go on this item");
        en.put(ToolWorkService.Result.STARTER.key(), "Starter kit gear cannot be upgraded");
        en.put(ToolWorkService.Result.SILK_TOUCH.key(), "Fortune cannot share a tool with Silk Touch");
        en.put(ToolWorkService.Result.AT_CAP.key(), "This tool already has Fortune VIII");
        en.put(ToolWorkService.Result.RANK.key(), "The Fitter needs a higher rank for this Fortune level");
        en.put(ToolWorkService.Result.NOT_PICK.key(), "Tool modes go on the Looting Pick only");
        en.put(ToolWorkService.Result.ALREADY.key(), "This pick already has that mode");
        en.put(ToolWorkService.Result.MISSING_MATERIAL.key(), "Not enough of the rarity material");
        en.put(ToolWorkService.Result.MISSING_CRYSTAL.key(), "Not enough rare theme crystals");
        en.put(ToolWorkService.Result.MISSING_FACETED.key(), "Not enough faceted crystals");
        en.put(ToolWorkService.Result.MISSING_SHARDS.key(), "Not enough Rift Shards");
        en.put(ToolWorkService.Result.REFUSED.key(), "The Fitter will not work on this item");
        en.put(ToolWorkService.Result.CHANGED.key(), "Inputs changed. Review the new quote.");
        en.put(ToolWorkService.Result.RAISED.key(), "Fortune raised");
        en.put(ToolWorkService.Result.LEARNT.key(), "Tool mode added");
        ENGLISH = Collections.unmodifiableMap(en);
    }

    private ToolWorkLang() {}
}
