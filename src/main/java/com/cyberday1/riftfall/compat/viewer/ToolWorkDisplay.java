package com.cyberday1.riftfall.compat.viewer;

import com.cyberday1.riftfall.block.marker.NpcRole;
import com.cyberday1.riftfall.home.npc.ResidentPaths;
import com.cyberday1.riftfall.home.npc.ResidentRank;
import com.cyberday1.riftfall.home.npc.smith.ToolWorkLang;
import com.cyberday1.riftfall.home.npc.smith.ToolWorkRules;
import com.cyberday1.riftfall.tool.ToolMode;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * One Fitter tool-work row: a Fortune step to {@code target}, or learning a looting-pick {@code mode}; standard, or
 * Toolwright where that changes the price. Every number comes from {@link ToolWorkRules}.
 */
public record ToolWorkDisplay(int target, Optional<ToolMode> mode, boolean toolwright) {
    /** {@code ToolWorkService.quote} has no rank check on modes. */
    public static final ResidentRank MODE_RANK = ResidentRank.APPRENTICE;

    public static List<ToolWorkDisplay> all() {
        var out = new ArrayList<ToolWorkDisplay>();
        for (int t = 1; t <= ToolWorkRules.FORTUNE_MAX; t++) {
            out.add(new ToolWorkDisplay(t, Optional.empty(), false));
            if (ToolWorkRules.fortuneShards(t, true) != ToolWorkRules.fortuneShards(t, false))
                out.add(new ToolWorkDisplay(t, Optional.empty(), true));
        }
        for (var mode : ToolMode.values()) {
            out.add(new ToolWorkDisplay(0, Optional.of(mode), false));
            if (ToolWorkRules.modeShards(true) != ToolWorkRules.modeShards(false)) out.add(new ToolWorkDisplay(0, Optional.of(mode), true));
        }
        return List.copyOf(out);
    }

    public boolean fortune() { return mode.isEmpty(); }

    public int shards() { return fortune() ? ToolWorkRules.fortuneShards(target, toolwright) : ToolWorkRules.modeShards(toolwright); }

    /** Index into {@code RarityLadder.RARITIES}. */
    public int rarity() { return fortune() ? ToolWorkRules.fortuneRarity(target) : ToolWorkRules.MODE_RARITY; }

    public int material() { return fortune() ? ToolWorkRules.fortuneMaterial(target) : ToolWorkRules.MODE_MATERIAL; }

    public int crystals() { return fortune() ? ToolWorkRules.fortuneCrystals(target) : 0; }

    public int faceted() { return fortune() ? ToolWorkRules.fortuneFaceted(target) : 0; }

    public ResidentRank rank() {
        if (toolwright) return ResidentRank.MASTER;
        return fortune() ? ToolWorkRules.fortuneRank(target) : MODE_RANK;
    }

    public Optional<String> path() { return toolwright ? Optional.of(ResidentPaths.TOOLWRIGHT) : Optional.empty(); }

    public Component badge() { return ConversionDisplay.badge(NpcRole.BLACKSMITH, rank(), path()); }

    /** "Fortune VIII", or "Shovel mode". */
    public Component title() {
        return mode.<Component>map(m -> Component.translatable(ToolWorkLang.MODE_STATE, Component.translatable(ToolWorkLang.mode(m))))
                .orElseGet(() -> Component.translatable(ViewerLang.FORTUNE_TITLE, Component.translatable("enchantment.level." + target)));
    }

    public List<Component> footer() {
        return List.of(Component.translatable(fortune() ? ViewerLang.FORTUNE_NOTE : ViewerLang.MODE_NOTE), Component.translatable(ViewerLang.INSTANT));
    }
}
