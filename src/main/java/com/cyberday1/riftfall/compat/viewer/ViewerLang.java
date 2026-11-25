package com.cyberday1.riftfall.compat.viewer;

import com.cyberday1.riftfall.RiftfallCore;
import com.cyberday1.riftfall.block.marker.NpcRole;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Every line the recipe viewer shows; the lang provider reads {@link #ENGLISH}. All of it is placeholder text awaiting
 * the voice pass, so {@code tools/lang_check.py} treats the prefix as draft (English in every locale for now).
 */
public final class ViewerLang {
    public static final String PREFIX = "viewer." + RiftfallCore.MODID + ".";

    public static final String CATEGORY = PREFIX + "category";
    public static final String CONVERSIONS = PREFIX + "conversions";
    public static final String SWAPS = PREFIX + "swaps";
    public static final String BADGE_PATH = PREFIX + "badge.path";
    public static final String SHARDS = PREFIX + "shards";
    public static final String ORDER_BOTH = PREFIX + "order.both";
    public static final String ORDER_RUNS = PREFIX + "order.runs";
    public static final String ORDER_MINUTES = PREFIX + "order.minutes";
    public static final String INSTANT = PREFIX + "instant";
    public static final String REPLACES = PREFIX + "replaces";
    public static final String SWAP_TITLE = PREFIX + "swap.title";
    public static final String SWAP_COST = PREFIX + "swap.cost";
    public static final String SWAP_OPEN = PREFIX + "swap.open";
    public static final String RESIDENT_TOOLTIP = PREFIX + "resident.tooltip";

    public static final String SALVAGE = PREFIX + "salvage";
    public static final String RETEMPER = PREFIX + "retemper";
    public static final String TOOL_WORK = PREFIX + "tool_work";
    public static final String RUNESTONES = PREFIX + "runestones";
    public static final String TIER_BUTTON = PREFIX + "tier";
    public static final String TIER_HINT = PREFIX + "tier.hint";
    public static final String LEVEL_BUTTON = PREFIX + "pick_level";
    public static final String LEVEL_HINT = PREFIX + "pick_level.hint";
    public static final String SALVAGE_TITLE = PREFIX + "salvage.title";
    public static final String SALVAGE_ANY = PREFIX + "salvage.any";
    public static final String SALVAGE_REFUSES = PREFIX + "salvage.refuses";
    public static final String SALVAGE_PROSPECTOR = PREFIX + "salvage.prospector";
    public static final String SALVAGE_BULK = PREFIX + "salvage.bulk";
    public static final String RETEMPER_TITLE = PREFIX + "retemper.title";
    public static final String RETEMPER_ANY = PREFIX + "retemper.any";
    public static final String RETEMPER_BAND = PREFIX + "retemper.band";
    public static final String RETEMPER_CAP = PREFIX + "retemper.cap";
    public static final String FORTUNE_TITLE = PREFIX + "fortune.title";
    public static final String FORTUNE_NOTE = PREFIX + "fortune.note";
    public static final String FORTUNE_TOOL = PREFIX + "fortune.tool";
    public static final String MODE_NOTE = PREFIX + "mode.note";
    public static final String RUNE_TITLE = PREFIX + "rune.title";
    public static final String RUNE_STRENGTH = PREFIX + "rune.strength";
    public static final String RUNE_FITS = PREFIX + "rune.fits";
    public static final String RUNE_EFFECT_YES = PREFIX + "rune.effect_yes";
    public static final String RUNE_EFFECT_NO = PREFIX + "rune.effect_no";

    public static final String INFO = PREFIX + "info.";
    public static final String RARITY_MATERIAL = INFO + "rarity_material";
    public static final String TEMPERING_STOCK = INFO + "tempering_stock";
    public static final String GEM_GRIT = INFO + "gem_grit";
    public static final String SOCKET_CHISEL = INFO + "socket_chisel";
    public static final String FACETED_CRYSTAL = INFO + "faceted_crystal";
    public static final String RUNESTONE = INFO + "runestone";
    public static final String CRYSTAL_ATTUNE = INFO + "crystal.attune";
    public static final String CRYSTAL_QUARRY = INFO + "crystal.quarry";
    public static final String RARE_CRYSTAL = INFO + "rare_crystal";
    public static final String RIFT_KEY = INFO + "rift_key";
    public static final String LOOTING_PICK = INFO + "looting_pick";
    public static final String WITHDRAWAL = INFO + "sigil_of_withdrawal";
    public static final String GEM_FUSED_SLATE = INFO + "gem_fused_slate";
    public static final String RIFT_SHARD = INFO + "rift_shard";
    public static final String RANK_LINE = INFO + "resident.rank";
    public static final String RANK_FIRST = INFO + "resident.rank_first";
    public static final String PATHS = INFO + "resident.paths";
    public static final String PATH_LINE = INFO + "resident.path";

    /** "weapons", "armour", "trinkets" for a re-temper title. */
    public static String kind(com.cyberday1.riftfall.home.npc.smith.SmithRules.Kind kind) { return PREFIX + "kind." + RetemperDisplay.id(kind); }

    /** A serviced resident's opening paragraph on its info page. */
    public static String intro(NpcRole role) { return INFO + "resident." + role.id(); }

    public static final Map<String, String> ENGLISH;

    static {
        Map<String, String> en = new LinkedHashMap<>();
        en.put(CATEGORY, "%s: %s");
        en.put(CONVERSIONS, "Conversions");
        en.put(SWAPS, "Crystal Swap");
        en.put(BADGE_PATH, "%s: %s");
        en.put(SHARDS, "Rift Shards");
        en.put(ORDER_BOTH, "Work order: %s run(s) or %s min online");
        en.put(ORDER_RUNS, "Work order: %s run(s)");
        en.put(ORDER_MINUTES, "Work order: %s min online");
        en.put(INSTANT, "Made on the spot");
        en.put(REPLACES, "Replaces the standard recipe on this path");
        en.put(SWAP_TITLE, "Swap into %s");
        en.put(SWAP_COST, "%s other theme crystals");
        en.put(SWAP_OPEN, "Surveyed, or its theme in your atlas");
        en.put(RESIDENT_TOOLTIP, "Home resident");

        en.put(SALVAGE, "Salvage");
        en.put(RETEMPER, "Re-temper");
        en.put(TOOL_WORK, "Tool Work");
        en.put(RUNESTONES, "Runestones");
        en.put(TIER_BUTTON, "Tier %s");
        en.put(TIER_HINT, "Rift tier. Click for the next, shift-click for the last.");
        en.put(LEVEL_BUTTON, "Level %s");
        en.put(LEVEL_HINT, "Looting pick level: each rune installed adds one. Click for the next, shift-click for the last.");
        en.put(SALVAGE_TITLE, "Salvage %s rift gear");
        en.put(SALVAGE_ANY, "Any rift gear of this rarity");
        en.put(SALVAGE_REFUSES, "Not starter, unique or socketed gear");
        en.put(SALVAGE_PROSPECTOR, "With a Prospector Quarry Specialist");
        en.put(SALVAGE_BULK, "Bulk scrap takes up to 27 at once");
        en.put(RETEMPER_TITLE, "Re-temper %s");
        en.put(RETEMPER_ANY, "Any rift gear of this kind, one tier below");
        en.put(RETEMPER_BAND, "Steps per item: %s, %s, then no limit");
        en.put(RETEMPER_CAP, "Never past your own tier cap");
        en.put(FORTUNE_TITLE, "Fortune %s");
        en.put(FORTUNE_NOTE, "One granted Fortune level up");
        en.put(FORTUNE_TOOL, "Anything Fortune fits, or the looting pick");
        en.put(MODE_NOTE, "Looting pick only");
        en.put(RUNE_TITLE, "Fit %s runestones");
        en.put(RUNE_STRENGTH, "Adds %s rank(s) to one effect");
        en.put(RUNE_FITS, "Fits %s of %s effects (hover the rune)");
        en.put(RUNE_EFFECT_YES, "%s (needs %s)");
        en.put(RUNE_EFFECT_NO, "Not %s (needs %s)");
        en.put(kind(com.cyberday1.riftfall.home.npc.smith.SmithRules.Kind.WEAPON), "weapons");
        en.put(kind(com.cyberday1.riftfall.home.npc.smith.SmithRules.Kind.ARMOUR), "armour");
        en.put(kind(com.cyberday1.riftfall.home.npc.smith.SmithRules.Kind.TRINKET), "trinkets");

        en.put(RARITY_MATERIAL, "Salvage %s rift gear at the Fitter to get this. The Quarry Specialist cuts it into runestones; the Fitter spends it on tool upgrades.");
        en.put(TEMPERING_STOCK, "Made by the Quarry Specialist from two theme crystals, and salvaged from rift gear at the Fitter. The Fitter spends it to re-temper gear.");
        en.put(GEM_GRIT, "The Jeweler dissolves gems into grit and spends it on purity steps. The Quarry Specialist binds it into gem-fused slate.");
        en.put(SOCKET_CHISEL, "Cut by the Quarry Specialist from Journeyman. The Jeweler uses one to add a socket.");
        en.put(FACETED_CRYSTAL, "A Quarry Specialist work order from Expert. The Jeweler spends it on high purity steps; the Fitter on Fortune VII and VIII.");
        en.put(RUNESTONE, "Cut by the Quarry Specialist. The Fitter fits it to a looting pick.");
        en.put(CRYSTAL_ATTUNE, "The Rift Cartographer attunes a Rift Key to %s rifts with this crystal.");
        en.put(CRYSTAL_QUARRY, "The Quarry Specialist surveys it for free; from Journeyman, spare crystals can be swapped into it.");
        en.put(RARE_CRYSTAL, "Rare crystal. The Fitter takes %s for Fortune V and %s for Fortune VI. Swapping into it costs double unless the Quarry Specialist is a Prospector.");
        en.put(RIFT_KEY, "Opens a rift. The Rift Cartographer attunes it to a theme from Journeyman.");
        en.put(LOOTING_PICK, "The Fitter hands out a free one, fits runestones to it and upgrades its Fortune and modes.");
        en.put(WITHDRAWAL, "Pays for unsetting a gem at the Jeweler instead of shards and crystals.");
        en.put(GEM_FUSED_SLATE, "Made by the Quarry Specialist from cobbled deepslate and gem grit.");
        en.put(RIFT_SHARD, "Found in rifts. Every home resident is paid in Rift Shards.");
        en.put(RANK_LINE, "%s: %s XP and a tier %s clear by anyone in the home");
        en.put(RANK_FIRST, "%s: from the start");
        en.put(PATHS, "At Master, choose one path:");
        en.put(PATH_LINE, "%s: %s; %s");
        en.put(intro(NpcRole.QUARRY_SPECIALIST), "Refines rarity materials and theme crystals into runestones, tempering stock, chisels and faceted crystals; surveys and swaps crystals.");
        en.put(intro(NpcRole.BLACKSMITH), "Salvages and re-tempers rift gear, fits runestones and upgrades looting picks.");
        en.put(intro(NpcRole.JEWELER), "Sets and unsets gems, adds sockets and refines gem purity.");
        en.put(intro(NpcRole.RIFT_CARTOGRAPHER), "Keeps your rift atlas and attunes Rift Keys to themes you have visited.");
        ENGLISH = Collections.unmodifiableMap(en);
    }

    /** Placeholder keys, in the order the voice pass lists them. */
    public static List<String> keys() { return List.copyOf(ENGLISH.keySet()); }

    private ViewerLang() {}
}
