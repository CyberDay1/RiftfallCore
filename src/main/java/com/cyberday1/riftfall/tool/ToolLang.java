package com.cyberday1.riftfall.tool;

import com.cyberday1.riftfall.RiftfallCore;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Every line the looting pick, runestones and Blacksmith pages show; the lang provider reads {@link #ENGLISH}. */
public final class ToolLang {
    private static final String PICK = "tooltip." + RiftfallCore.MODID + ".looting_pick.";
    private static final String RUNE = "tooltip." + RiftfallCore.MODID + ".runestone.";
    private static final String SCREEN = "screen." + RiftfallCore.MODID + ".blacksmith.";
    static final String MESSAGE = "message." + RiftfallCore.MODID + ".blacksmith.";
    static final String STAT = "tooltip." + RiftfallCore.MODID + ".tool_stat.";
    static final String GRADE = "tooltip." + RiftfallCore.MODID + ".rune_grade.";
    static final String SKIN = "tooltip." + RiftfallCore.MODID + ".tool_appearance.";

    public static final String TITLE = "container." + RiftfallCore.MODID + ".blacksmith";

    public static final String PICK_LEVEL = PICK + "level";
    public static final String PICK_STAT = PICK + "stat";
    public static final String PICK_BARRELS = PICK + "barrels";
    public static final String PICK_APPEARANCE = PICK + "appearance";
    public static final String PICK_CHANGE = PICK + "change_appearance";
    public static final String PICK_INVENTORY_FULL = "message." + RiftfallCore.MODID + ".looting_pick.inventory_full";

    public static final String RUNE_GRADE = RUNE + "grade";
    public static final String RUNE_INSTALL = RUNE + "install";
    public static final String RUNE_CHOOSE = RUNE + "choose";
    public static final String RUNE_STAT = RUNE + "stat";

    public static final String APPEARANCE_UNLOCKED = MESSAGE + "appearance_unlocked";

    public static final String LANDING_RUNES = SCREEN + "landing.runestones";
    public static final String LANDING_RUNES_HINT = SCREEN + "landing.runestones_hint";
    public static final String LANDING_RUNES_NOTE = SCREEN + "landing.runestones_note";
    public static final String LANDING_LOOKS = SCREEN + "landing.appearance";
    public static final String LANDING_LOOKS_HINT = SCREEN + "landing.appearance_hint";
    public static final String LANDING_LOOKS_NOTE = SCREEN + "landing.appearance_note";
    public static final String LANDING_STARTER = SCREEN + "landing.starter";
    public static final String LANDING_CLAIM_FREE = SCREEN + "landing.claim_free";
    public static final String LANDING_CLAIM_CARRIED = SCREEN + "landing.claim_carried";
    public static final String LANDING_CLAIM_SLOT = SCREEN + "landing.claim_slot";

    public static final String RUNES_HEADER = SCREEN + "runes.header";
    public static final String RUNES_PICK = SCREEN + "runes.pick";
    public static final String RUNES_RUNE = SCREEN + "runes.rune";
    public static final String RUNES_SHARDS = SCREEN + "runes.shards";
    public static final String RUNES_INSTALL = SCREEN + "runes.install";
    public static final String RUNES_INSTALLING = SCREEN + "runes.installing";
    public static final String RUNES_STAT = SCREEN + "runes.stat";
    public static final String RUNES_STAT_INSTALL = SCREEN + "runes.stat_install";
    public static final String RUNES_STAT_REQUIRES = SCREEN + "runes.stat_requires";
    public static final String RUNES_INSTALL_HINT = SCREEN + "runes.install_hint";
    public static final String RUNES_WAITING = SCREEN + "runes.waiting";
    public static final String RUNES_NEED_SHARDS = SCREEN + "runes.need_shards";
    public static final String RUNES_NEEDS_GRADE = SCREEN + "runes.needs_grade";
    public static final String RUNES_READY = SCREEN + "runes.ready";
    public static final String RUNES_PREVIEW = SCREEN + "runes.preview";
    public static final String RUNES_PREVIEW_EMPTY = SCREEN + "runes.preview_empty";
    public static final String RUNES_COST = SCREEN + "runes.cost";
    public static final String RUNES_COST_EMPTY = SCREEN + "runes.cost_empty";

    public static final String LOOKS_HEADER = SCREEN + "looks.header";
    public static final String LOOKS_PREVIOUS = SCREEN + "looks.previous";
    public static final String LOOKS_NEXT = SCREEN + "looks.next";
    public static final String LOOKS_APPLY = SCREEN + "looks.apply";
    public static final String LOOKS_APPLYING = SCREEN + "looks.applying";
    public static final String LOOKS_STATUS_APPLYING = SCREEN + "looks.status_applying";
    public static final String LOOKS_STATUS_APPLIED = SCREEN + "looks.status_applied";
    public static final String LOOKS_STATUS_LOCKED = SCREEN + "looks.status_locked";
    public static final String LOOKS_STATUS_NO_PICK = SCREEN + "looks.status_no_pick";
    public static final String LOOKS_STATUS_FREE = SCREEN + "looks.status_free";
    public static final String LOOKS_STATUS_CURRENT = SCREEN + "looks.status_current";
    public static final String LOOKS_LOCKED = SCREEN + "looks.locked";
    public static final String LOOKS_UNLOCKED_HINT = SCREEN + "looks.unlocked_hint";
    public static final String LOOKS_LOCKED_HINT = SCREEN + "looks.locked_hint";
    public static final String LOOKS_APPLY_HINT = SCREEN + "looks.apply_hint";
    public static final String LOOKS_PREVIEW = SCREEN + "looks.preview";
    public static final String LOOKS_CURRENT = SCREEN + "looks.current";
    public static final String LOOKS_NO_PICK = SCREEN + "looks.no_pick";
    public static final String LOOKS_PAGE = SCREEN + "looks.page";
    public static final String LOOKS_COST = SCREEN + "looks.cost";

    public static final Map<String, String> ENGLISH;

    static {
        Map<String, String> english = new LinkedHashMap<>();
        english.put(TITLE, "Fitter");
        english.put(PICK_LEVEL, "Tool level %s | No durability");
        english.put(PICK_STAT, "%s: %s");
        english.put(PICK_BARRELS, "Break barrels to collect your personal loot.");
        english.put(PICK_APPEARANCE, "Appearance: %s");
        english.put(PICK_CHANGE, "Change appearance at the Fitter.");
        english.put(PICK_INVENTORY_FULL, "Inventory full. Remaining loot is saved in your barrel share.");
        english.put(RUNE_GRADE, "%s runestone");
        english.put(RUNE_INSTALL, "Install at the Fitter. Consumed on success.");
        english.put(RUNE_CHOOSE, "Choose one effect; pay with Rift Shards.");
        english.put(RUNE_STAT, "%s %s");
        english.put(APPEARANCE_UNLOCKED, "Fitter appearance unlocked: %s");

        english.put(LANDING_RUNES, "Runestones & stats");
        english.put(LANDING_RUNES_HINT, "Inspect your pick's bonuses, then preview and install a runestone.");
        english.put(LANDING_RUNES_NOTE, "Permanent bonuses");
        english.put(LANDING_LOOKS, "Tool appearance");
        english.put(LANDING_LOOKS_HINT, "Preview unlocked and undiscovered looks. Applying an unlocked appearance is free.");
        english.put(LANDING_LOOKS_NOTE, "New look, same tool");
        english.put(LANDING_STARTER, "Get starter pick");
        english.put(LANDING_CLAIM_FREE, "Free starter pick. Requires an empty hand.");
        english.put(LANDING_CLAIM_CARRIED, "You already carry a Riftfall Looting Pick.");
        english.put(LANDING_CLAIM_SLOT, "Select an empty hotbar slot to claim a pick.");

        english.put(RUNES_HEADER, "Runestones");
        english.put(RUNES_PICK, "Pick");
        english.put(RUNES_RUNE, "Rune");
        english.put(RUNES_SHARDS, "Shards");
        english.put(RUNES_INSTALL, "Install rune");
        english.put(RUNES_INSTALLING, "Installing...");
        english.put(RUNES_STAT, "%s: %s");
        // Stat, current bonus, then the per-install bonus or required grade, then the stat's description.
        english.put(RUNES_STAT_INSTALL, "%1$s: %2$s. Install: %3$s. %4$s");
        english.put(RUNES_STAT_REQUIRES, "%1$s: %2$s. Requires %3$s+ rune. %4$s");
        english.put(RUNES_INSTALL_HINT, "%s. %s. Shards come from the slot first, then your inventory.");
        english.put(RUNES_WAITING, "Waiting for the Fitter...");
        english.put(RUNES_NEED_SHARDS, "Need %s more Rift Shards");
        english.put(RUNES_NEEDS_GRADE, "%s needs %s+");
        english.put(RUNES_READY, "%s rune | Choose an effect");
        english.put(RUNES_PREVIEW, "%s: %s -> %s | Lv %s -> %s");
        english.put(RUNES_PREVIEW_EMPTY, "Insert materials to preview the result");
        english.put(RUNES_COST, "1 rune + %s shards (%s held)");
        english.put(RUNES_COST_EMPTY, "Cost shown with pick + rune");

        english.put(LOOKS_HEADER, "Appearance");
        english.put(LOOKS_PREVIOUS, "Prev");
        english.put(LOOKS_NEXT, "Next");
        english.put(LOOKS_APPLY, "Apply look");
        english.put(LOOKS_APPLYING, "Applying...");
        english.put(LOOKS_STATUS_APPLYING, "Applying appearance...");
        english.put(LOOKS_STATUS_APPLIED, "Appearance applied. Level and bonuses kept.");
        english.put(LOOKS_STATUS_LOCKED, "Locked: find the original %s");
        english.put(LOOKS_STATUS_NO_PICK, "Insert your pick to apply this look");
        english.put(LOOKS_STATUS_FREE, "Unlocked appearance | Free to apply");
        english.put(LOOKS_STATUS_CURRENT, "This appearance is already on your pick");
        english.put(LOOKS_LOCKED, "Locked: %s");
        english.put(LOOKS_UNLOCKED_HINT, "%s: unlocked. Cosmetic only; no stats are copied.");
        english.put(LOOKS_LOCKED_HINT, "%s: find the original and bring it into your inventory. Previewing does not unlock or consume it.");
        english.put(LOOKS_APPLY_HINT, "%s. No materials consumed. Only the appearance changes.");
        english.put(LOOKS_PREVIEW, "Preview: %s");
        english.put(LOOKS_CURRENT, "Current: %s");
        english.put(LOOKS_NO_PICK, "no pick inserted");
        english.put(LOOKS_PAGE, "Page %s / %s | Cosmetic only");
        english.put(LOOKS_COST, "Cost: free");

        for (ToolStat stat : ToolStat.values()) {
            english.put(stat.nameKey(), stat.label);
            english.put(stat.bonusKey(), stat.bonusFormat);
            english.put(stat.descriptionKey(), stat.description);
        }
        for (RuneGrade grade : RuneGrade.values()) {
            english.put(grade.key(), grade.label);
        }
        for (ToolUpgrades.Result result : ToolUpgrades.Result.values()) {
            english.put(result.key(), result.message);
        }
        for (ToolAppearances.Skin skin : ToolAppearances.ALL) {
            english.put(skin.key(), skin.name());
        }
        ENGLISH = Collections.unmodifiableMap(english);
    }

    private ToolLang() {
    }

    static String lower(Enum<?> value) {
        return value.name().toLowerCase(Locale.ROOT);
    }
}
