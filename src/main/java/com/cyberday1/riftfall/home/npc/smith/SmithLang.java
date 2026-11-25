package com.cyberday1.riftfall.home.npc.smith;

import com.cyberday1.riftfall.RiftfallCore;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Every line the Blacksmith's re-temper and salvage page shows; the lang provider reads {@link #ENGLISH}. */
public final class SmithLang {
    private static final String SCREEN = "screen." + RiftfallCore.MODID + ".smith.";
    static final String MESSAGE = "message." + RiftfallCore.MODID + ".smith.";

    public static final String LANDING_TEMPER = SCREEN + "landing.temper";
    public static final String LANDING_TEMPER_HINT = SCREEN + "landing.temper_hint";
    public static final String LANDING_TEMPER_NOTE = SCREEN + "landing.temper_note";

    public static final String SLOT_GEAR = SCREEN + "slot.gear";
    public static final String SLOT_STOCK = SCREEN + "slot.stock";
    public static final String SLOT_SHARDS = SCREEN + "slot.shards";
    public static final String SLOT_OUT = SCREEN + "slot.out";
    public static final String ACTION_RETEMPER = SCREEN + "action.retemper";
    public static final String ACTION_SALVAGE = SCREEN + "action.salvage";
    public static final String ACTION_RETEMPER_HINT = SCREEN + "action.retemper_hint";
    public static final String ACTION_SALVAGE_HINT = SCREEN + "action.salvage_hint";
    public static final String CONFIRM = SCREEN + "confirm";
    public static final String WAITING = SCREEN + "waiting";
    public static final String TIER = SCREEN + "tier";
    public static final String TIER_NONE = SCREEN + "tier_none";
    public static final String BAND = SCREEN + "band";
    public static final String BAND_UNLIMITED = SCREEN + "band_unlimited";
    public static final String COST = SCREEN + "cost";
    public static final String YIELD = SCREEN + "yield";
    public static final String YIELD_STOCK = SCREEN + "yield_stock";

    public static final Map<String, String> ENGLISH;

    static {
        Map<String, String> en = new LinkedHashMap<>();
        en.put(LANDING_TEMPER, "Salvage & re-temper");
        en.put(LANDING_TEMPER_HINT, "Break rift gear into rarity materials and stock, or raise an item's tier up to your own.");
        en.put(LANDING_TEMPER_NOTE, "Rarity materials, tiers");

        en.put(SLOT_GEAR, "Gear");
        en.put(SLOT_STOCK, "Stock");
        en.put(SLOT_SHARDS, "Shards");
        en.put(SLOT_OUT, "Out");
        en.put(ACTION_RETEMPER, "Re-temper");
        en.put(ACTION_SALVAGE, "Salvage");
        en.put(ACTION_RETEMPER_HINT, "One tier up now, paid in shards and tempering stock.");
        en.put(ACTION_SALVAGE_HINT, "The item is consumed. Unset its gems at the Jeweler first.");
        en.put(CONFIRM, "Confirm");
        en.put(WAITING, "Waiting for the Fitter...");
        en.put(TIER, "Tier %s -> %s (cap %s)");
        en.put(TIER_NONE, "Place a rift item in the gear slot");
        en.put(BAND, "%s/%s re-tempers");
        en.put(BAND_UNLIMITED, "%s re-tempers");
        en.put(COST, "%s shards + %s stock (%s held)");
        en.put(YIELD, "Returns %s x%s and %s stock");
        en.put(YIELD_STOCK, "Returns %s tempering stock");

        en.put(SmithService.Result.READY.key(), "Ready");
        en.put(SmithService.Result.NO_ITEM.key(), "Place one rift item in the gear slot");
        en.put(SmithService.Result.NOT_SINGLE.key(), "One item at a time");
        en.put(SmithService.Result.BANNED.key(), "The Fitter will not touch this item");
        en.put(SmithService.Result.LOOTING_PICK.key(), "The Looting Pick takes runestones, not tempering");
        en.put(SmithService.Result.NO_TIER.key(), "Only rift gear with a tier can be worked here");
        en.put(SmithService.Result.STARTER.key(), "Starter kit gear cannot be re-tempered or salvaged");
        en.put(SmithService.Result.UNIQUE.key(), "Boss uniques are not the Fitter's to rework");
        en.put(SmithService.Result.SOCKETED.key(), "Unset this item's gems at the Jeweler first");
        en.put(SmithService.Result.RANK.key(), "The Fitter needs a higher rank for this item");
        en.put(SmithService.Result.BAND.key(), "Re-tempered as far as this rank allows");
        en.put(SmithService.Result.CAP.key(), "That would pass your own tier cap");
        en.put(SmithService.Result.MISSING_STOCK.key(), "Not enough tempering stock");
        en.put(SmithService.Result.MISSING_SHARDS.key(), "Not enough Rift Shards");
        en.put(SmithService.Result.OUTPUT_FULL.key(), "Empty the output slots");
        en.put(SmithService.Result.REFUSED.key(), "The Fitter will not work on this item");
        en.put(SmithService.Result.CHANGED.key(), "Inputs changed. Review the new quote.");
        en.put(SmithService.Result.RETEMPERED.key(), "Re-tempered");
        en.put(SmithService.Result.SALVAGED.key(), "Salvaged");
        ENGLISH = Collections.unmodifiableMap(en);
    }

    private SmithLang() {}
}
