package com.cyberday1.riftfall.compat.viewer;

import com.cyberday1.riftfall.RiftfallCore;
import com.cyberday1.riftfall.block.marker.NpcRole;
import com.cyberday1.riftfall.gear.RarityLadder;
import com.cyberday1.riftfall.home.npc.ResidentPaths;
import com.cyberday1.riftfall.home.npc.ResidentRank;
import com.cyberday1.riftfall.home.npc.cartographer.CartographerCrystals;
import com.cyberday1.riftfall.home.npc.depth.DepthLang;
import com.cyberday1.riftfall.home.npc.jeweler.JewelerService;
import com.cyberday1.riftfall.home.npc.smith.ToolWorkRules;
import com.cyberday1.riftfall.init.RiftfallTags;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.TreeMap;

/**
 * The viewer's information pages: what each resident material is for, and one page per serviced resident. Items are
 * named by id or tag; the viewer skips any whose mod is absent (the crystals and Apotheosis materials are other mods').
 */
public final class InfoPages {
    /** {@code items} and every member of {@code tag} share the page. */
    public record Page(List<ResourceLocation> items, Optional<TagKey<Item>> tag, List<Component> lines) {
        public Page { items = List.copyOf(items); lines = List.copyOf(lines); }
        static Page of(List<ResourceLocation> items, Component... lines) { return new Page(items, Optional.empty(), List.of(lines)); }
        static Page of(String item, Component... lines) { return of(List.of(ResourceLocation.parse(item)), lines); }
        static Page of(TagKey<Item> tag, Component... lines) { return new Page(List.of(), Optional.of(tag), List.of(lines)); }
    }

    public static final String APOTHEOSIS_SLATE = "apotheosis:gem_fused_slate";

    private InfoPages() {}

    public static List<Page> items() {
        var out = new ArrayList<Page>();
        for (String rarity : RarityLadder.RARITIES) {
            var id = ResourceLocation.parse(rarity);
            var tag = TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath(RiftfallCore.MODID, "rarity_material/" + id.getPath()));
            out.add(Page.of(tag, Component.translatable(ViewerLang.RARITY_MATERIAL, Component.translatable("rarity." + rarity))));
        }
        out.add(Page.of(mod("tempering_stock"), Component.translatable(ViewerLang.TEMPERING_STOCK)));
        out.add(Page.of(mod("gem_grit"), Component.translatable(ViewerLang.GEM_GRIT)));
        out.add(Page.of(mod("socket_chisel"), Component.translatable(ViewerLang.SOCKET_CHISEL)));
        out.add(Page.of(mod("faceted_crystal"), Component.translatable(ViewerLang.FACETED_CRYSTAL)));
        out.add(Page.of(List.of(rl("common_runestone"), rl("uncommon_runestone"), rl("rare_runestone"), rl("exotic_runestone")),
                Component.translatable(ViewerLang.RUNESTONE)));
        // Sorted by crystal so the page order is stable; each crystal names its own theme.
        var byCrystal = new TreeMap<ResourceLocation, ResourceLocation>();
        CartographerCrystals.BY_THEME.forEach((theme, crystal) -> byCrystal.put(crystal, theme));
        byCrystal.forEach((crystal, theme) -> out.add(Page.of(List.of(crystal),
                Component.translatable(ViewerLang.CRYSTAL_ATTUNE, Component.translatable("theme." + theme.getNamespace() + "." + theme.getPath())),
                Component.translatable(ViewerLang.CRYSTAL_QUARRY))));
        out.add(Page.of(RiftfallTags.RARE_CRYSTALS, Component.translatable(ViewerLang.RARE_CRYSTAL,
                ToolWorkRules.fortuneCrystals(5), ToolWorkRules.fortuneCrystals(6))));
        out.add(Page.of(mod("rift_key"), Component.translatable(ViewerLang.RIFT_KEY)));
        out.add(Page.of(mod("looting_pick"), Component.translatable(ViewerLang.LOOTING_PICK)));
        out.add(Page.of(List.of(JewelerService.WITHDRAWAL), Component.translatable(ViewerLang.WITHDRAWAL)));
        out.add(Page.of(APOTHEOSIS_SLATE, Component.translatable(ViewerLang.GEM_FUSED_SLATE)));
        out.add(Page.of(mod("rift_shard"), Component.translatable(ViewerLang.RIFT_SHARD)));
        return List.copyOf(out);
    }

    /** A serviced resident's page: what they do, the four ranks, then the Master paths. */
    public static List<Component> resident(NpcRole role) {
        var lines = new ArrayList<Component>();
        lines.add(Component.translatable(ViewerLang.intro(role)));
        for (var rank : ResidentRank.values()) {
            var name = Component.translatable(DepthLang.rank(rank));
            lines.add(rank.xp() == 0 && rank.gate() == 0 ? Component.translatable(ViewerLang.RANK_FIRST, name)
                    : Component.translatable(ViewerLang.RANK_LINE, name, rank.xp(), rank.gate()));
            String opens = DepthLang.opens(role, rank);
            if (DepthLang.ENGLISH.containsKey(opens)) lines.add(Component.translatable(opens));
        }
        var paths = ResidentPaths.of(role);
        if (!paths.isEmpty()) lines.add(Component.translatable(ViewerLang.PATHS));
        for (var path : paths)
            lines.add(Component.translatable(ViewerLang.PATH_LINE, Component.translatable(ResidentPaths.name(role, path)),
                    Component.translatable(ResidentPaths.line(role, path, 0)), Component.translatable(ResidentPaths.line(role, path, 1))));
        return List.copyOf(lines);
    }

    private static String mod(String path) { return RiftfallCore.MODID + ":" + path; }
    private static ResourceLocation rl(String path) { return ResourceLocation.fromNamespaceAndPath(RiftfallCore.MODID, path); }
}
