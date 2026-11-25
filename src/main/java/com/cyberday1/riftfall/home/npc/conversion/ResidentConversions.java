package com.cyberday1.riftfall.home.npc.conversion;

import com.cyberday1.riftfall.block.marker.NpcRole;
import com.cyberday1.riftfall.gear.GearRecipeBan;
import com.cyberday1.riftfall.home.npc.ResidentRank;
import com.cyberday1.riftfall.home.npc.ShardWallet;
import com.cyberday1.riftfall.init.RiftfallItems;
import com.cyberday1.riftfall.init.RiftfallRecipes;
import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Quote and commit for a resident conversion: inputs and shards out, one result in, all in one tick or not at all.
 * A work-order recipe consumes the same way and records an order instead of filling the output.
 */
public final class ResidentConversions {
    public enum Status { READY, NO_MATCH, WRONG_ROLE, RANK_LOCKED, DEFERRED, BANNED_RESULT, SHARD_INPUT, UNSAFE_SET, MISSING_SHARDS, NO_SPACE,
        PATH_LOCKED, TIER_LOCKED, UNDISCOVERED, NO_ORDER_SLOT }

    /** {@code take} is per input slot. Value-comparable, so it can serve as a menu's confirm terms. */
    public record Quote(ResourceLocation recipe, Status status, List<Integer> take, int shards, int balance, ItemStack result,
                        boolean ordered, int runs, int minutes) {
        public Quote(ResourceLocation recipe, Status status, List<Integer> take, int shards, int balance, ItemStack result) {
            this(recipe, status, take, shards, balance, result, false, 0, 0);
        }
        public boolean ready() { return status == Status.READY; }
        public List<Object> terms() {
            return List.of(String.valueOf(recipe), status, take, shards, balance,
                    BuiltInRegistries.ITEM.getKey(result.getItem()), result.getCount(), ordered, runs, minutes);
        }
    }

    /**
     * What the home and player bring to a quote: the resident's rank and Master path, the home tier for
     * {@code requires.min_tier}, the player's surveys for {@code requires.discovery}, and free order slots.
     */
    public record Context(NpcRole role, ResidentRank rank, Optional<String> path, int homeTier, Set<ResourceLocation> discoveries,
                          int freeOrders, boolean safe) {
        public Context { discoveries = Set.copyOf(discoveries); }
        /** No path, no surveys, no order slot, any home tier: what a plain instant recipe needs. */
        public static Context of(NpcRole role, ResidentRank rank, boolean safe) {
            return new Context(role, rank, Optional.empty(), Integer.MAX_VALUE, Set.of(), 0, safe);
        }
    }

    /** Starts a work order: records it with what was consumed, then runs {@code consume} in the same tick. */
    @FunctionalInterface public interface OrderStart {
        boolean start(RecipeHolder<ResidentConversionRecipe> recipe, List<ItemStack> consumed, Runnable consume);
    }

    /** Where the service keeps its slots. Inputs are {@code [first, first + count)} of {@code slots}. */
    public record Slots(Container slots, int first, int count, int payment, int output, Container inventory) {
        List<ItemStack> inputs() {
            var list = new ArrayList<ItemStack>(count);
            for (int i = 0; i < count; i++) list.add(slots.getItem(first + i));
            return list;
        }
    }

    private static final Map<RecipeManager, Boolean> SAFE = Collections.synchronizedMap(new WeakHashMap<>());
    private ResidentConversions() {}

    /** Never Rift Shards, Shattered World Shards, gear or anything the recipe ban removes. */
    public static boolean permittedResult(ItemStack result) {
        return !result.isEmpty() && !result.is(RiftfallItems.RIFT_SHARD.get())
                && !result.is(RiftfallItems.SHATTERED_WORLD_SHARD.get())
                && !result.isDamageableItem() && !GearRecipeBan.bannedResult(result);
    }

    public static List<RecipeHolder<ResidentConversionRecipe>> forRole(RecipeManager manager, NpcRole role) {
        return manager.getAllRecipesFor(RiftfallRecipes.RESIDENT_CONVERSION.get()).stream()
                .filter(h -> h.value().role() == role).toList();
    }

    /**
     * The recipes a page lists for this rank and path, in a stable order both sides compute alike: rank, then id.
     * A path recipe hides the recipe it replaces; another path's recipes and research recipes are not listed.
     */
    public static List<RecipeHolder<ResidentConversionRecipe>> visible(Collection<RecipeHolder<ResidentConversionRecipe>> all,
                                                                     NpcRole role, ResidentRank rank, Optional<String> path) {
        var shown = new ArrayList<RecipeHolder<ResidentConversionRecipe>>();
        var replaced = new HashSet<ResourceLocation>();
        for (var h : all) {
            var r = h.value();
            if (r.role() != role || !rank.atLeast(r.minRank()) || r.deferred()) continue;
            if (r.specialisation().isPresent() && !r.specialisation().equals(path)) continue;
            shown.add(h);
            if (r.specialisation().isPresent()) r.replaces().ifPresent(replaced::add);
        }
        shown.removeIf(h -> replaced.contains(h.id()));
        shown.sort(Comparator.<RecipeHolder<ResidentConversionRecipe>>comparingInt(h -> h.value().minRank().ordinal())
                .thenComparing(h -> h.id().toString()));
        return List.copyOf(shown);
    }

    /** The loaded set has no gain cycle under live tags. Checked once per recipe reload. */
    public static boolean safe(RecipeManager manager) {
        return SAFE.computeIfAbsent(manager, m -> {
            var recipes = m.getAllRecipesFor(RiftfallRecipes.RESIDENT_CONVERSION.get()).stream()
                    .collect(Collectors.toMap(RecipeHolder::id, RecipeHolder::value, (a, b) -> a, LinkedHashMap::new));
            var cycle = ConversionGraph.of(recipes, tag -> BuiltInRegistries.ITEM.getTag(tag)
                    .map(set -> set.stream().map(h -> h.unwrapKey().orElseThrow().location()).collect(Collectors.toSet()))
                    .orElse(null)).gainCycle();
            cycle.ifPresent(c -> LogUtils.getLogger().error("Resident conversions disabled: a cycle multiplies material: {}", c));
            return cycle.isEmpty();
        });
    }

    /** Pure: reads the slots, changes nothing. {@code safe} comes from {@link #safe(RecipeManager)}. */
    public static Quote quote(RecipeHolder<ResidentConversionRecipe> holder, NpcRole role, ResidentRank rank, boolean safe, Slots slots) {
        return quote(holder, Context.of(role, rank, safe), slots);
    }

    public static Quote quote(RecipeHolder<ResidentConversionRecipe> holder, Context ctx, Slots slots) {
        var recipe = holder.value();
        int balance = ShardWallet.balance(slots.slots(), slots.payment(), slots.inventory());
        int[] take = recipe.allocate(slots.inputs());
        var requires = recipe.requires();
        boolean ordered = recipe.ordered();
        Status status;
        if (recipe.role() != ctx.role()) status = Status.WRONG_ROLE;
        else if (!ctx.safe()) status = Status.UNSAFE_SET;
        else if (!permittedResult(recipe.result())) status = Status.BANNED_RESULT;
        else if (shardInput(recipe)) status = Status.SHARD_INPUT;
        else if (recipe.deferred()) status = Status.DEFERRED;
        else if (recipe.specialisation().isPresent() && !recipe.specialisation().equals(ctx.path())) status = Status.PATH_LOCKED;
        else if (!ctx.rank().atLeast(recipe.minRank())) status = Status.RANK_LOCKED;
        else if (requires.isPresent() && ctx.homeTier() < requires.get().minTier()) status = Status.TIER_LOCKED;
        else if (requires.flatMap(ResidentConversionRecipe.Requirements::discovery).filter(d -> !ctx.discoveries().contains(d)).isPresent())
            status = Status.UNDISCOVERED;
        else if (take == null) status = Status.NO_MATCH;
        else if (balance < recipe.shards()) status = Status.MISSING_SHARDS;
        else if (ordered && ctx.freeOrders() <= 0) status = Status.NO_ORDER_SLOT;
        else if (!ordered && !fits(slots.slots().getItem(slots.output()), recipe.result())) status = Status.NO_SPACE;
        else status = Status.READY;
        var list = new ArrayList<Integer>();
        if (take != null) for (int n : take) list.add(n);
        var order = recipe.workOrder().orElse(null);
        return new Quote(holder.id(), status, List.copyOf(list), recipe.shards(), balance, recipe.result().copy(),
                ordered, order == null ? 0 : order.runs(), order == null ? 0 : order.minutes());
    }

    /** Re-quotes; any difference from {@code shown} refuses without touching a slot. */
    public static boolean commit(RecipeHolder<ResidentConversionRecipe> holder, NpcRole role, ResidentRank rank, boolean safe,
                                 Slots slots, Quote shown) {
        return commit(holder, Context.of(role, rank, safe), slots, shown, (r, consumed, consume) -> false);
    }

    /** An instant recipe fills the output; an ordered one goes to {@code orders} with what it consumed. */
    public static boolean commit(RecipeHolder<ResidentConversionRecipe> holder, Context ctx, Slots slots, Quote shown, OrderStart orders) {
        var now = quote(holder, ctx, slots);
        if (!now.ready() || !now.terms().equals(shown.terms())) return false;
        var payment = ShardWallet.plan(slots.slots(), slots.payment(), slots.inventory(), now.shards());
        if (payment == null || !payment.valid()) return false;
        // Shards are never an input, so paying cannot touch a stack the inputs use.
        Runnable consume = () -> {
            if (!payment.pay()) throw new IllegalStateException("payment changed under a resident conversion");
            for (int i = 0; i < slots.count(); i++) {
                int n = now.take().get(i);
                if (n > 0) slots.slots().getItem(slots.first() + i).shrink(n);
            }
        };
        if (now.ordered()) {
            var consumed = new ArrayList<ItemStack>();
            for (int i = 0; i < slots.count(); i++) {
                int n = now.take().get(i);
                if (n > 0) consumed.add(slots.slots().getItem(slots.first() + i).copyWithCount(n));
            }
            if (now.shards() > 0) consumed.add(new ItemStack(RiftfallItems.RIFT_SHARD.get(), now.shards()));
            if (!orders.start(holder, consumed, consume)) return false;
        } else {
            consume.run();
            var out = slots.slots().getItem(slots.output());
            if (out.isEmpty()) slots.slots().setItem(slots.output(), now.result().copy());
            else out.grow(now.result().getCount());
        }
        slots.slots().setChanged();
        slots.inventory().setChanged();
        return true;
    }

    static boolean fits(ItemStack output, ItemStack result) {
        if (output.isEmpty()) return result.getCount() <= result.getMaxStackSize();
        return ItemStack.isSameItemSameComponents(output, result) && output.getCount() + result.getCount() <= output.getMaxStackSize();
    }

    /** Shards are paid through the wallet only, so a recipe may not take them as an input. */
    public static boolean shardInput(ResidentConversionRecipe recipe) {
        var shard = new ItemStack(RiftfallItems.RIFT_SHARD.get());
        return recipe.inputs().stream().anyMatch(i -> i.ingredient().test(shard))
                || recipe.catalyst().map(c -> c.test(shard)).orElse(false);
    }
}
