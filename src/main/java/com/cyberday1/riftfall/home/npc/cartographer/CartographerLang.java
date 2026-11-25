package com.cyberday1.riftfall.home.npc.cartographer;

import com.cyberday1.riftfall.RiftfallCore;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Every line the Cartographer's page shows; the lang provider reads {@link #ENGLISH}. */
public final class CartographerLang {
    private static final String SCREEN = "screen." + RiftfallCore.MODID + ".cartographer.";
    private static final String MESSAGE = "message." + RiftfallCore.MODID + ".cartographer.";

    public static final String LANDING_ATLAS = SCREEN + "landing.atlas";
    public static final String LANDING_ATLAS_HINT = SCREEN + "landing.atlas_hint";
    public static final String LANDING_ATLAS_NOTE = SCREEN + "landing.atlas_note";

    public static final String SLOT_KEY = SCREEN + "slot.key";
    public static final String SLOT_CRYSTAL = SCREEN + "slot.crystal";
    public static final String SLOT_SHARDS = SCREEN + "slot.shards";
    public static final String ROW_THEME = SCREEN + "row.theme";
    public static final String ROW_UNCHARTED = SCREEN + "row.uncharted";
    public static final String ROW_BOSS = SCREEN + "row.boss";
    public static final String INFO_THEME = SCREEN + "info.theme";
    public static final String INFO_UNCHARTED = SCREEN + "info.uncharted";
    public static final String INFO_BOSS = SCREEN + "info.boss";
    public static final String COST = SCREEN + "cost";
    public static final String COST_SHARDS = SCREEN + "cost_shards";
    public static final String SCROLL_UP = SCREEN + "scroll_up";
    public static final String SCROLL_DOWN = SCREEN + "scroll_down";
    public static final String CONFIRM = SCREEN + "confirm";
    public static final String WAITING = SCREEN + "waiting";

    public static final String ATTUNED = MESSAGE + "attuned";
    public static final String CHANGED = MESSAGE + "changed";

    public static String status(CartographerService.Status status) { return SCREEN + "status." + status.id(); }

    public static final Map<String, String> ENGLISH;

    static {
        Map<String, String> en = new LinkedHashMap<>();
        en.put(LANDING_ATLAS, "Atlas & attunement");
        en.put(LANDING_ATLAS_HINT, "Your atlas of rift themes and the bosses met in them. From Journeyman, attune a Rift Key to a theme you have visited.");
        en.put(LANDING_ATLAS_NOTE, "Keys keep their tier");

        en.put(SLOT_KEY, "Key");
        en.put(SLOT_CRYSTAL, "Crystal");
        en.put(SLOT_SHARDS, "Shards");
        en.put(ROW_THEME, "%s rifts");
        en.put(ROW_UNCHARTED, "%s rifts (uncharted)");
        en.put(ROW_BOSS, "%s: %s rifts");
        en.put(INFO_THEME, "%s visits, %s clears, best tier %s, %s bosses");
        en.put(INFO_UNCHARTED, "Uncharted: run a %s rift first");
        en.put(INFO_BOSS, "%s: %s kills in %s rifts");
        en.put(COST, "%s shards (%s held) + %s %s");
        en.put(COST_SHARDS, "%s shards (%s held)");
        en.put(SCROLL_UP, "Up");
        en.put(SCROLL_DOWN, "Down");
        en.put(CONFIRM, "Attune");
        en.put(WAITING, "Waiting for the Rift Cartographer...");

        en.put(status(CartographerService.Status.READY), "Ready");
        en.put(status(CartographerService.Status.NOTHING), "Choose a theme");
        en.put(status(CartographerService.Status.RANK_LOCKED), "Attuning: themes at Journeyman, bosses at Expert");
        en.put(status(CartographerService.Status.NO_KEY), "Place a Rift Key in the key slot");
        en.put(status(CartographerService.Status.UNVISITED), "Run a rift of this theme first");
        en.put(status(CartographerService.Status.OFF_ROSTER), "That boss cannot appear at this key's tier");
        en.put(status(CartographerService.Status.SAME), "The key is already attuned this way");
        en.put(status(CartographerService.Status.NO_CRYSTAL), "Place a crystal of this theme in the crystal slot");
        en.put(status(CartographerService.Status.MISSING_SHARDS), "Not enough Rift Shards");

        en.put(ATTUNED, "Key attuned");
        en.put(CHANGED, "Inputs changed. Review the new quote.");
        ENGLISH = Collections.unmodifiableMap(en);
    }

    private CartographerLang() {}
}
