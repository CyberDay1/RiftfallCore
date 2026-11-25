package com.cyberday1.riftfall.compat.viewer;

import com.cyberday1.riftfall.block.marker.NpcRole;
import com.cyberday1.riftfall.home.npc.ResidentPaths;
import com.cyberday1.riftfall.home.npc.ResidentRank;
import com.cyberday1.riftfall.home.npc.conversion.ResidentConversionRecipe;
import com.cyberday1.riftfall.home.npc.conversion.ResidentConversions;
import com.cyberday1.riftfall.home.npc.depth.DepthLang;
import com.cyberday1.riftfall.init.RiftfallItems;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.common.crafting.SizedIngredient;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * One resident conversion as the viewer shows it, built from the synced recipe. Viewer-neutral: the JEI category
 * reads it, and an EMI one could.
 */
public record ConversionDisplay(ResourceLocation id, NpcRole role, ResidentRank rank, Optional<String> path,
                                List<SizedIngredient> inputs, Optional<Ingredient> catalyst, int shards, ItemStack result,
                                Optional<ResidentConversionRecipe.WorkOrder> order, Optional<ResourceLocation> replaces) {
    public ConversionDisplay {
        inputs = List.copyOf(inputs);
        result = result.copy();
    }

    public static ConversionDisplay of(RecipeHolder<ResidentConversionRecipe> holder) {
        var r = holder.value();
        return new ConversionDisplay(holder.id(), r.role(), r.minRank(), r.specialisation(), r.inputs(), r.catalyst(), r.shards(),
                r.result(), r.ordered() ? r.workOrder() : Optional.empty(), r.replaces());
    }

    /** What the server would ever make: no banned result, no shard input, no recipe waiting on research. */
    public static boolean shown(ResidentConversionRecipe recipe) {
        return ResidentConversions.permittedResult(recipe.result()) && !ResidentConversions.shardInput(recipe) && !recipe.deferred();
    }

    /** Every rank and path at once (no per-player filter), ordered as the service page orders them: rank, then id. */
    public static List<RecipeHolder<ResidentConversionRecipe>> all(Collection<RecipeHolder<ResidentConversionRecipe>> recipes) {
        return recipes.stream().filter(h -> shown(h.value()))
                .sorted(Comparator.<RecipeHolder<ResidentConversionRecipe>>comparingInt(h -> h.value().minRank().ordinal())
                        .thenComparing(h -> h.id().toString()))
                .toList();
    }

    /** The stacks a slot cycles through, at the recipe's count; a tag lists every member. */
    public static List<ItemStack> stacks(SizedIngredient input) { return List.of(input.getItems()); }

    public ItemStack shardStack() { return shardStack(shards); }

    /** The Rift Shard cost as a slot's stack; the wallet pays it, but it reads like any input. */
    public static ItemStack shardStack(int count) { return new ItemStack(RiftfallItems.RIFT_SHARD.get(), Math.max(1, count)); }

    /** "Expert", or "Master: Runecutter" on a path recipe. */
    public Component badge() { return badge(role, rank, path); }

    public static Component badge(NpcRole role, ResidentRank rank, Optional<String> path) {
        var name = Component.translatable(DepthLang.rank(rank));
        return path.<Component>map(p -> Component.translatable(ViewerLang.BADGE_PATH, name, Component.translatable(ResidentPaths.name(role, p))))
                .orElse(name);
    }

    /** The work-order line, or "made on the spot". */
    public Component orderLine() {
        if (order.isEmpty()) return Component.translatable(ViewerLang.INSTANT);
        var o = order.get();
        if (o.runs() > 0 && o.minutes() > 0) return Component.translatable(ViewerLang.ORDER_BOTH, o.runs(), o.minutes());
        return o.runs() > 0 ? Component.translatable(ViewerLang.ORDER_RUNS, o.runs()) : Component.translatable(ViewerLang.ORDER_MINUTES, o.minutes());
    }

    /** A path recipe that stands in for another on its path. */
    public boolean variant() { return path.isPresent() && replaces.isPresent(); }
}
