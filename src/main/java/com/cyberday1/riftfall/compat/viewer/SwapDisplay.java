package com.cyberday1.riftfall.compat.viewer;

import com.cyberday1.riftfall.block.marker.NpcRole;
import com.cyberday1.riftfall.home.npc.ResidentPaths;
import com.cyberday1.riftfall.home.npc.ResidentRank;
import com.cyberday1.riftfall.home.npc.quarry.QuarryService;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * One Quarry crystal swap row: {@code cost} theme crystals other than the target, plus shards, for one target. Each target
 * gets a standard row and one per Master path whose cost differs. Every number comes from {@link QuarryService}.
 */
public record SwapDisplay(Item target, boolean rare, Optional<String> path, int cost, int shards, List<Item> from) {
    public static final ResidentRank RANK = ResidentRank.JOURNEYMAN;

    public SwapDisplay { from = List.copyOf(from); }

    /** Rows for every swap target, in the page's target order. */
    public static List<SwapDisplay> all(Collection<Item> crystals, Predicate<Item> rare) {
        var out = new ArrayList<SwapDisplay>();
        var targets = QuarryService.targets(crystals);
        for (var target : targets) {
            boolean isRare = rare.test(target);
            var from = targets.stream().filter(c -> c != target).toList();
            int standard = QuarryService.swapCost(isRare, Optional.empty());
            out.add(new SwapDisplay(target, isRare, Optional.empty(), standard, QuarryService.SWAP_SHARDS, from));
            for (var path : ResidentPaths.of(NpcRole.QUARRY_SPECIALIST)) {
                int cost = QuarryService.swapCost(isRare, Optional.of(path));
                if (cost != standard) out.add(new SwapDisplay(target, isRare, Optional.of(path), cost, QuarryService.SWAP_SHARDS, from));
            }
        }
        return List.copyOf(out);
    }

    /** The other crystals, each at the full cost so a slot can cycle through them. */
    public List<ItemStack> inputs() { return from.stream().map(i -> new ItemStack(i, cost)).toList(); }

    public ItemStack result() { return new ItemStack(target); }

    public ItemStack shardStack() { return ConversionDisplay.shardStack(shards); }

    public Component badge() { return ConversionDisplay.badge(NpcRole.QUARRY_SPECIALIST, path.isPresent() ? ResidentRank.MASTER : RANK, path); }

    public Component title() { return Component.translatable(ViewerLang.SWAP_TITLE, result().getHoverName()); }

    public Component costLine() { return Component.translatable(ViewerLang.SWAP_COST, cost); }

    public Component requirement() { return Component.translatable(ViewerLang.SWAP_OPEN); }
}
