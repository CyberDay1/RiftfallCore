package com.cyberday1.riftfall.compat.viewer;

import com.cyberday1.riftfall.block.marker.NpcRole;
import com.cyberday1.riftfall.gear.RarityLadder;
import com.cyberday1.riftfall.home.npc.ResidentPaths;
import com.cyberday1.riftfall.home.npc.ResidentRank;
import com.cyberday1.riftfall.home.npc.smith.SmithRules;
import com.cyberday1.riftfall.home.npc.smith.SmithService;
import com.cyberday1.riftfall.home.npc.smith.ToolWorkService;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * One Fitter salvage page: one rarity, with or without a Prospector Quarry Specialist. The tier is picked on the page;
 * every count comes from {@link SmithRules} for that tier.
 */
public record SalvageDisplay(int rarity, boolean prospector) {
    /** {@code SmithService.quote} has no rank check on salvage. */
    public static final ResidentRank RANK = ResidentRank.APPRENTICE;
    /** Tiers past the material cap, where only the stock still grows. */
    private static final int[] HIGH = {25, 50, 100};
    public static final List<Integer> TIERS = tiers();
    /** The refusals every Fitter service shares, plus salvage's own; the page's tooltip lists these lines. */
    public static final List<SmithService.Result> REFUSALS = List.of(SmithService.Result.NO_TIER, SmithService.Result.STARTER,
            SmithService.Result.UNIQUE, SmithService.Result.SOCKETED);

    public static List<SalvageDisplay> all() {
        var out = new ArrayList<SalvageDisplay>();
        for (int r = 0; r < RarityLadder.RARITIES.length; r++) {
            out.add(new SalvageDisplay(r, false));
            out.add(new SalvageDisplay(r, true));
        }
        return List.copyOf(out);
    }

    /** Each edge of {@link SmithRules#tierBand} up to the material cap, then {@link #HIGH}. */
    static List<Integer> tiers() {
        var out = new ArrayList<Integer>();
        out.add(1);
        for (int t = 2; SmithRules.material(t - 1, 0) < SmithRules.MAX_MATERIAL; t++)
            if (SmithRules.tierBand(t) != SmithRules.tierBand(t - 1)) { out.add(t - 1); out.add(t); }
        for (int t : HIGH) if (t > out.getLast()) out.add(t);
        return List.copyOf(out.stream().distinct().toList());
    }

    public int material(int tier) { return SmithRules.material(tier, rarity); }

    public int stock(int tier) { return SmithRules.salvageStock(tier, rarity, prospector); }

    public TagKey<Item> materialTag() { return ToolWorkService.rarityTag(rarity); }

    public Component rarityName() { return Component.translatable("rarity." + RarityLadder.RARITIES[rarity]); }

    public Component title() { return Component.translatable(ViewerLang.SALVAGE_TITLE, rarityName()); }

    /** Prospector is the Quarry Specialist's path, so the badge names theirs. */
    public Component badge() {
        return prospector ? ConversionDisplay.badge(NpcRole.QUARRY_SPECIALIST, ResidentRank.MASTER, Optional.of(ResidentPaths.PROSPECTOR))
                : ConversionDisplay.badge(NpcRole.BLACKSMITH, RANK, Optional.empty());
    }

    public List<Component> footer() {
        var refuses = Component.translatable(ViewerLang.SALVAGE_REFUSES);
        return List.of(refuses, Component.translatable(prospector ? ViewerLang.SALVAGE_PROSPECTOR : ViewerLang.SALVAGE_BULK));
    }

    public static List<Component> refusalLines() {
        return REFUSALS.stream().<Component>map(r -> Component.translatable(r.key())).toList();
    }
}
