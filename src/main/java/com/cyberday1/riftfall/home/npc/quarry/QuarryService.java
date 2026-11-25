package com.cyberday1.riftfall.home.npc.quarry;

import com.cyberday1.riftfall.block.marker.NpcRole;
import com.cyberday1.riftfall.home.npc.ResidentPaths;
import com.cyberday1.riftfall.home.npc.ResidentRank;
import com.cyberday1.riftfall.home.npc.ResidentServiceMenu;
import com.cyberday1.riftfall.home.npc.ShardWallet;
import com.cyberday1.riftfall.home.npc.conversion.ResidentConversionRecipe;
import com.cyberday1.riftfall.home.npc.conversion.ResidentConversions;
import com.cyberday1.riftfall.init.RiftfallTags;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.util.*;

/**
 * The Quarry Specialist's page over the menu's slots: its conversion recipes, the survey (Apprentice, free, nothing
 * consumed) and lossy crystal swaps (Journeyman, into a crystal this player has surveyed). Quotes are pure.
 */
public final class QuarryService {
    public static final int INPUTS = 6, SHARDS = 6, OUTPUT = 7, SLOTS = 8;
    /** Selections: recipes by index in the visible list, then the survey, then swaps by target index. */
    public static final int RECIPES = 30, SURVEY = 30, SWAP = 31, MAX_TARGETS = 18;
    /** Swaps take 3 theme crystals for one (Crystalwright 2); a rare crystal costs double unless the home is a Prospector. */
    public static final int SWAP_COST = 3, SWAP_COST_CRYSTALWRIGHT = 2, RARE_FACTOR = 2, SWAP_SHARDS = 1;

    public enum Kind { RECIPE, SURVEY, SWAP, NONE }

    /** Statuses beyond the conversion ones; all are lower-case keys of {@link QuarryLang#status}. */
    public static final String READY = "ready", SURVEY_KNOWN = "survey_known", SURVEY_NOT_CRYSTAL = "survey_not_crystal",
            SURVEY_EMPTY = "survey_empty", NOTHING = "nothing";

    /** Every status the page can show, in a fixed order for the data slot. */
    public static final List<String> STATUSES;
    static {
        var all = new ArrayList<String>();
        for (var s : ResidentConversions.Status.values()) all.add(s.name().toLowerCase(Locale.ROOT));
        all.addAll(List.of(SURVEY_KNOWN, SURVEY_NOT_CRYSTAL, SURVEY_EMPTY, NOTHING));
        STATUSES = List.copyOf(all);
    }

    /** The page's quote. {@code take} is per input slot; {@code target} is the swap's crystal. */
    public record Quote(int selection, Kind kind, String status, List<Integer> take, int shards, int balance, ItemStack result,
                        boolean ordered, int runs, int minutes, ResidentConversions.Quote recipe)
            implements ResidentServiceMenu.Quote {
        @Override public boolean ready() { return READY.equals(status); }
        @Override public Object terms() {
            return Arrays.asList(selection, kind, status, take, shards, balance,
                    BuiltInRegistries.ITEM.getKey(result.getItem()), result.getCount(), ordered, runs, minutes);
        }
    }

    /** What the quote needs that is not in the slots; {@code charted} are crystals of themes in the player's atlas. */
    public record Context(ResidentConversions.Context conversion, List<RecipeHolder<ResidentConversionRecipe>> recipes,
                          List<Item> targets, Set<ResourceLocation> charted) {
        public Context(ResidentConversions.Context conversion, List<RecipeHolder<ResidentConversionRecipe>> recipes, List<Item> targets) {
            this(conversion, recipes, targets, Set.of());
        }
    }

    /** A swap into a crystal opens once it is surveyed or its theme is in the Cartographer's atlas (RULED 2026-11-03). */
    public static boolean swapOpen(ResourceLocation crystal, Context ctx) {
        return ctx.conversion().discoveries().contains(crystal) || ctx.charted().contains(crystal);
    }

    private QuarryService() {}

    public static boolean accepts(int slot, ItemStack stack) {
        if (slot == SHARDS) return ShardWallet.isShard(stack);
        return slot >= 0 && slot < INPUTS && !ShardWallet.isShard(stack);
    }

    /** Swap targets: every theme crystal, in id order so both sides agree. */
    public static List<Item> targets(Collection<Item> crystals) {
        return crystals.stream().sorted(Comparator.comparing(i -> BuiltInRegistries.ITEM.getKey(i).toString()))
                .limit(MAX_TARGETS).toList();
    }

    /** Theme crystals one swap into {@code target} takes, for this path. */
    public static int swapCost(boolean rare, Optional<String> path) {
        int base = path.filter(ResidentPaths.CRYSTALWRIGHT::equals).isPresent() ? SWAP_COST_CRYSTALWRIGHT : SWAP_COST;
        boolean full = rare && path.filter(ResidentPaths.PROSPECTOR::equals).isEmpty();
        return full ? base * RARE_FACTOR : base;
    }

    public static Quote quote(Container slots, Container inventory, int selection, Context ctx) {
        int balance = ShardWallet.balance(slots, SHARDS, inventory);
        var view = new ResidentConversions.Slots(slots, 0, INPUTS, SHARDS, OUTPUT, inventory);
        if (selection >= 0 && selection < RECIPES) {
            if (selection >= ctx.recipes().size()) return none(selection, balance);
            var q = ResidentConversions.quote(ctx.recipes().get(selection), ctx.conversion(), view);
            return new Quote(selection, Kind.RECIPE, q.status().name().toLowerCase(Locale.ROOT), q.take(), q.shards(), q.balance(),
                    q.result(), q.ordered(), q.runs(), q.minutes(), q);
        }
        if (selection == SURVEY) {
            var item = slots.getItem(0);
            String status;
            if (item.isEmpty()) status = SURVEY_EMPTY;
            else if (!item.is(RiftfallTags.THEME_CRYSTALS)) status = SURVEY_NOT_CRYSTAL;
            else if (ctx.conversion().discoveries().contains(BuiltInRegistries.ITEM.getKey(item.getItem()))) status = SURVEY_KNOWN;
            else status = READY;
            return new Quote(selection, Kind.SURVEY, status, List.of(), 0, balance, item.copyWithCount(1), false, 0, 0, null);
        }
        int t = selection - SWAP;
        if (t < 0 || t >= ctx.targets().size()) return none(selection, balance);
        var target = ctx.targets().get(t);
        var result = new ItemStack(target);
        boolean rare = result.is(RiftfallTags.RARE_CRYSTALS);
        int cost = swapCost(rare, ctx.conversion().path());
        int[] take = allocate(slots, target, cost);
        String status;
        if (!ctx.conversion().safe()) status = name(ResidentConversions.Status.UNSAFE_SET);
        else if (!ctx.conversion().rank().atLeast(ResidentRank.JOURNEYMAN)) status = name(ResidentConversions.Status.RANK_LOCKED);
        else if (!swapOpen(BuiltInRegistries.ITEM.getKey(target), ctx)) status = name(ResidentConversions.Status.UNDISCOVERED);
        else if (take == null) status = name(ResidentConversions.Status.NO_MATCH);
        else if (balance < SWAP_SHARDS) status = name(ResidentConversions.Status.MISSING_SHARDS);
        else if (!fits(slots.getItem(OUTPUT), result)) status = name(ResidentConversions.Status.NO_SPACE);
        else status = READY;
        var list = new ArrayList<Integer>();
        if (take != null) for (int n : take) list.add(n);
        return new Quote(selection, Kind.SWAP, status, List.copyOf(list), SWAP_SHARDS, balance, result, false, 0, 0, null);
    }

    private static Quote none(int selection, int balance) {
        return new Quote(selection, Kind.NONE, NOTHING, List.of(), 0, balance, ItemStack.EMPTY, false, 0, 0, null);
    }

    private static String name(ResidentConversions.Status status) { return status.name().toLowerCase(Locale.ROOT); }

    /** Theme crystals other than the target, from the input slots in order; null if short. */
    static int[] allocate(Container slots, Item target, int need) {
        int[] take = new int[INPUTS];
        for (int i = 0; i < INPUTS && need > 0; i++) {
            var stack = slots.getItem(i);
            if (stack.isEmpty() || !stack.is(RiftfallTags.THEME_CRYSTALS) || stack.is(target)) continue;
            int n = Math.min(need, stack.getCount());
            take[i] = n; need -= n;
        }
        return need > 0 ? null : take;
    }

    static boolean fits(ItemStack output, ItemStack result) {
        if (output.isEmpty()) return true;
        return ItemStack.isSameItemSameComponents(output, result) && output.getCount() + result.getCount() <= output.getMaxStackSize();
    }

    /** A survey records the crystal for this player; nothing is consumed. */
    public static boolean survey(Quote shown, Container slots, Container inventory, Context ctx,
                                 java.util.function.Predicate<ResourceLocation> discover) {
        var now = quote(slots, inventory, shown.selection(), ctx);
        if (!now.ready() || now.kind() != Kind.SURVEY || !now.terms().equals(shown.terms())) return false;
        return discover.test(BuiltInRegistries.ITEM.getKey(now.result().getItem()));
    }

    /** One swap: re-quote, pay, take the crystals, fill the output. */
    public static boolean swap(Quote shown, Container slots, Container inventory, Context ctx) {
        var now = quote(slots, inventory, shown.selection(), ctx);
        if (!now.ready() || now.kind() != Kind.SWAP || !now.terms().equals(shown.terms())) return false;
        var payment = ShardWallet.plan(slots, SHARDS, inventory, now.shards());
        if (payment == null || !payment.pay()) return false;
        for (int i = 0; i < INPUTS; i++) if (now.take().get(i) > 0) slots.getItem(i).shrink(now.take().get(i));
        var out = slots.getItem(OUTPUT);
        if (out.isEmpty()) slots.setItem(OUTPUT, now.result().copy()); else out.grow(now.result().getCount());
        slots.setChanged();
        inventory.setChanged();
        return true;
    }

    /** For the role check in tests and the probe. */
    public static NpcRole role() { return NpcRole.QUARRY_SPECIALIST; }
}
