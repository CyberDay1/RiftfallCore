package com.cyberday1.riftfall.home.npc;

import com.cyberday1.riftfall.RiftfallCore;
import com.cyberday1.riftfall.block.marker.NpcRole;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The Master specialisations each role offers (SP1/SP2). Ids are stored in {@code HomeResidentDepth}; a path changes
 * rates and options only, never a cap. Each path has a name and two effect lines for the choice screen.
 */
public final class ResidentPaths {
    public static final String RUNECUTTER = "runecutter", PROSPECTOR = "prospector", CRYSTALWRIGHT = "crystalwright";
    public static final String WEAPONSMITH = "weaponsmith", ARMOURER = "armourer", TOOLWRIGHT = "toolwright";
    public static final String SETTER = "setter", CUTTER = "cutter";
    public static final String PATHFINDER = "pathfinder", BOUNTY_SCOUT = "bounty_scout";
    public static final int LINES = 2;

    private static final String KEY = "resident." + RiftfallCore.MODID + ".";

    private ResidentPaths() {}

    public static List<String> of(NpcRole role) {
        return switch (role) {
            case QUARRY_SPECIALIST -> List.of(RUNECUTTER, PROSPECTOR, CRYSTALWRIGHT);
            case BLACKSMITH -> List.of(WEAPONSMITH, ARMOURER, TOOLWRIGHT);
            case JEWELER -> List.of(SETTER, CUTTER);
            case RIFT_CARTOGRAPHER -> List.of(PATHFINDER, BOUNTY_SCOUT);
            default -> List.of();
        };
    }

    public static boolean valid(NpcRole role, String path) { return of(role).contains(path); }

    /** Index in {@link #of}, or -1; what the landing syncs. */
    public static int index(NpcRole role, String path) { return path == null ? -1 : of(role).indexOf(path); }

    public static String name(NpcRole role, String path) { return KEY + role.id() + ".path." + path; }
    public static String line(NpcRole role, String path, int line) { return KEY + role.id() + ".path." + path + "." + line; }

    public static final Map<String, String> ENGLISH;

    static {
        Map<String, String> en = new LinkedHashMap<>();
        var q = NpcRole.QUARRY_SPECIALIST;
        en.put(name(q, RUNECUTTER), "Runecutter");
        en.put(line(q, RUNECUTTER, 0), "Runestone batches yield one extra per four");
        en.put(line(q, RUNECUTTER, 1), "Rare runestones instant; exotic orders one run");
        en.put(name(q, PROSPECTOR), "Prospector");
        en.put(line(q, PROSPECTOR, 0), "Swaps into rare crystals cost no extra");
        en.put(line(q, PROSPECTOR, 1), "Fitter salvage yields 25%% more tempering stock");
        en.put(name(q, CRYSTALWRIGHT), "Crystalwright");
        en.put(line(q, CRYSTALWRIGHT, 0), "Crystal swaps at 2:1 instead of 3:1");
        en.put(line(q, CRYSTALWRIGHT, 1), "Chisels and faceted crystals take one crystal less");
        var b = NpcRole.BLACKSMITH;
        en.put(name(b, WEAPONSMITH), "Weaponsmith");
        en.put(line(b, WEAPONSMITH, 0), "Re-tempering weapons costs 40%% less");
        en.put(line(b, WEAPONSMITH, 1), "Never above your tier cap");
        en.put(name(b, ARMOURER), "Armorer");
        en.put(line(b, ARMOURER, 0), "Re-tempering armor costs 40%% less");
        en.put(line(b, ARMOURER, 1), "Never above your tier cap");
        en.put(name(b, TOOLWRIGHT), "Toolwright");
        en.put(line(b, TOOLWRIGHT, 0), "Rune fees and tool upgrade shards cost 30%% less");
        en.put(line(b, TOOLWRIGHT, 1), "Runes fit effects one grade up");
        var j = NpcRole.JEWELER;
        en.put(name(j, SETTER), "Setter");
        en.put(line(j, SETTER, 0), "Unsetting costs no shards, only the crystal");
        en.put(line(j, SETTER, 1), "Setting a gem costs half the shards");
        en.put(name(j, CUTTER), "Cutter");
        en.put(line(j, CUTTER, 0), "Purity work orders take one run less");
        en.put(line(j, CUTTER, 1), "Purity steps take 25%% less grit");
        var c = NpcRole.RIFT_CARTOGRAPHER;
        en.put(name(c, PATHFINDER), "Pathfinder");
        en.put(line(c, PATHFINDER, 0), "Attunement costs half the shards");
        en.put(line(c, PATHFINDER, 1), "A key may carry two themes; the rift rolls between them");
        en.put(name(c, BOUNTY_SCOUT), "Bounty Scout");
        en.put(line(c, BOUNTY_SCOUT, 0), "Boss attunement is free");
        en.put(line(c, BOUNTY_SCOUT, 1), "Boss requests pay 50%% more resident XP at every resident");
        ENGLISH = Collections.unmodifiableMap(en);
    }
}
