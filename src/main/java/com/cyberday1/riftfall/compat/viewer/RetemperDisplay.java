package com.cyberday1.riftfall.compat.viewer;

import com.cyberday1.riftfall.block.marker.NpcRole;
import com.cyberday1.riftfall.home.npc.ResidentPaths;
import com.cyberday1.riftfall.home.npc.ResidentRank;
import com.cyberday1.riftfall.home.npc.smith.SmithRules;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * One Fitter re-temper page: one gear kind, standard or on a Master path that changes its price. The target tier is
 * picked on the page; every cost comes from {@link SmithRules} for that tier.
 */
public record RetemperDisplay(SmithRules.Kind kind, Optional<String> path) {
    /** Past the stock edges, where only the shard cost still grows. */
    private static final int[] HIGH = {25, 50, 100};
    /** The last target whose stock edges are listed one by one. */
    private static final int EDGES_TO = 20;
    public static final List<Integer> TIERS = tiers();

    public static List<RetemperDisplay> all() {
        var out = new ArrayList<RetemperDisplay>();
        for (var kind : SmithRules.Kind.values()) {
            out.add(new RetemperDisplay(kind, Optional.empty()));
            for (var path : ResidentPaths.of(NpcRole.BLACKSMITH)) {
                var row = new RetemperDisplay(kind, Optional.of(path));
                if (TIERS.stream().anyMatch(t -> row.shards(t) != row.standardShards(t) || row.stock(t) != row.standardStock(t))) out.add(row);
            }
        }
        return List.copyOf(out);
    }

    /** Targets from 2 (an item has tier 1 at least): each edge where the stock cost steps, then {@link #HIGH}. */
    static List<Integer> tiers() {
        var standard = Optional.<String>empty();
        var out = new ArrayList<Integer>();
        out.add(2);
        for (int t = 3; t <= EDGES_TO; t++)
            if (SmithRules.stock(t, SmithRules.Kind.WEAPON, standard) != SmithRules.stock(t - 1, SmithRules.Kind.WEAPON, standard)) {
                out.add(t - 1);
                out.add(t);
            }
        out.add(EDGES_TO);
        for (int t : HIGH) out.add(t);
        return List.copyOf(out.stream().distinct().sorted().toList());
    }

    /** The lowest rank the Fitter re-tempers this kind at, or Master on a path. */
    public ResidentRank rank() {
        if (path.isPresent()) return ResidentRank.MASTER;
        for (var rank : ResidentRank.values()) if (SmithRules.rankAllows(kind, rank)) return rank;
        return ResidentRank.MASTER;
    }

    public int shards(int target) { return SmithRules.shards(target, kind, path); }

    public int stock(int target) { return SmithRules.stock(target, kind, path); }

    private int standardShards(int target) { return SmithRules.shards(target, kind, Optional.empty()); }

    private int standardStock(int target) { return SmithRules.stock(target, kind, Optional.empty()); }

    public Component title() {
        return Component.translatable(ViewerLang.RETEMPER_TITLE, Component.translatable(ViewerLang.kind(kind)));
    }

    public Component badge() { return ConversionDisplay.badge(NpcRole.BLACKSMITH, rank(), path); }

    /** The lifetime limit by rank, then the tier cap. */
    public List<Component> footer() {
        return List.of(Component.translatable(ViewerLang.RETEMPER_BAND, SmithRules.band(ResidentRank.JOURNEYMAN), SmithRules.band(ResidentRank.EXPERT)),
                Component.translatable(ViewerLang.RETEMPER_CAP));
    }

    static String id(SmithRules.Kind kind) { return kind.name().toLowerCase(Locale.ROOT); }
}
