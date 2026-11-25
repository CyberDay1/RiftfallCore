package com.cyberday1.riftfall.compat.viewer;

import com.cyberday1.riftfall.block.marker.NpcRole;
import com.cyberday1.riftfall.home.npc.ResidentPaths;
import com.cyberday1.riftfall.home.npc.ResidentRank;
import com.cyberday1.riftfall.tool.RuneGrade;
import com.cyberday1.riftfall.tool.ToolStat;
import com.cyberday1.riftfall.tool.ToolUpgrades;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * One Fitter runestone page: one rune grade, standard or Toolwright. The pick's level is picked on the page, since each
 * install raises the next fee; every fee comes from {@link ToolUpgrades#fee}.
 */
public record RuneDisplay(RuneGrade grade, boolean toolwright) {
    /** {@code BlacksmithMenu} quotes runes with no rank check. */
    public static final ResidentRank RANK = ResidentRank.APPRENTICE;
    /** Pick levels the page steps through; a fresh pick is level 1 and every install adds one. */
    public static final List<Integer> LEVELS = List.of(1, 2, 3, 5, 10, 25);

    public static List<RuneDisplay> all() {
        var out = new ArrayList<RuneDisplay>();
        for (var grade : RuneGrade.values()) {
            var standard = new RuneDisplay(grade, false);
            var toolwright = new RuneDisplay(grade, true);
            out.add(standard);
            if (LEVELS.stream().anyMatch(l -> toolwright.fee(l) != standard.fee(l)) || !toolwright.fits().equals(standard.fits()))
                out.add(toolwright);
        }
        return List.copyOf(out);
    }

    /** Shards for one install on a pick at {@code level}. */
    public int fee(int level) { return Math.toIntExact(ToolUpgrades.fee(level, grade, toolwright)); }

    /** The effects this grade may install, Toolwright's grade up included. */
    public List<ToolStat> fits() {
        return Arrays.stream(ToolStat.values()).filter(s -> ToolUpgrades.supports(grade, s, toolwright)).toList();
    }

    public Optional<String> path() { return toolwright ? Optional.of(ResidentPaths.TOOLWRIGHT) : Optional.empty(); }

    public Component badge() { return ConversionDisplay.badge(NpcRole.BLACKSMITH, toolwright ? ResidentRank.MASTER : RANK, path()); }

    public Component title() { return Component.translatable(ViewerLang.RUNE_TITLE, grade.displayName()); }

    public List<Component> footer() {
        return List.of(Component.translatable(ViewerLang.RUNE_STRENGTH, grade.strength),
                Component.translatable(ViewerLang.RUNE_FITS, fits().size(), ToolStat.values().length));
    }

    /** The rune slot's tooltip: each effect and the grade it needs, marked when this page's rune fits it. */
    public List<Component> effectLines() {
        var fits = fits();
        return Arrays.stream(ToolStat.values()).<Component>map(s -> Component.translatable(
                fits.contains(s) ? ViewerLang.RUNE_EFFECT_YES : ViewerLang.RUNE_EFFECT_NO, s.displayName(), s.minimum.displayName())).toList();
    }
}
