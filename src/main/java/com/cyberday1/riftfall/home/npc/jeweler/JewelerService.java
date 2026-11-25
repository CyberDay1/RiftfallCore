package com.cyberday1.riftfall.home.npc.jeweler;

import com.cyberday1.riftfall.home.npc.GearMutation;
import com.cyberday1.riftfall.home.npc.ResidentPaths;
import com.cyberday1.riftfall.home.npc.ResidentRank;
import com.cyberday1.riftfall.home.npc.ResidentServiceMenu;
import com.cyberday1.riftfall.home.npc.ShardWallet;
import com.cyberday1.riftfall.home.npc.depth.HomeResidentDepth;
import com.cyberday1.riftfall.home.npc.smith.SmithService;
import com.cyberday1.riftfall.init.RiftfallDataComponents;
import com.cyberday1.riftfall.init.RiftfallItems;
import com.cyberday1.riftfall.init.RiftfallTags;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * The Jeweler's services over the menu's slots: appraise, set, unset, dissolve, purity up (instant or as a work order)
 * and add a socket. Quotes are pure; {@link #commit} re-quotes, checks room, pays, consumes and edits in one call.
 */
public final class JewelerService {
    public static final int ITEM = 0, GEM = 1, GRIT = 2, CRYSTAL_A = 3, CRYSTAL_B = 4, SHARDS = 5, MATERIAL = 6, SLOTS = 7;
    /** Selections: the actions, then unset by socket. */
    public static final int APPRAISE = 0, SET = 1, DISSOLVE = 2, REFINE = 3, ADD_SOCKET = 4, REFINE_ORDER = 5,
            UNSET = 10, MAX_SOCKETS = 30;
    /** A Sigil of Withdrawal makes one unset free. */
    public static final ResourceLocation WITHDRAWAL = ResourceLocation.fromNamespaceAndPath("apotheosis", "sigil_of_withdrawal");
    /** JW3: the socket cap by rarity, common to mythic; an item with no rarity but a loot category takes 1. */
    static final int[] SOCKET_CAP = {1, 1, 2, 3, 4};
    public static final int CAP_WITHOUT_RARITY = 1;

    public enum Result {
        READY, APPRAISE, NO_ITEM, NO_GEM, NO_EMPTY_SOCKET, GEM_DOES_NOT_FIT, EMPTY_SOCKET, GEM_SLOT_FULL,
        GRIT_SLOT_FULL, MAX_PURITY, RANK, MISSING_GRIT, MISSING_CRYSTALS, MISSING_RARE, MISSING_FACETED,
        MISSING_SHARDS, REFUSED, CHANGED, SET_DONE, UNSET_DONE, DISSOLVE_DONE, REFINE_DONE,
        ORDER_ONLY, NO_ORDER, NO_ORDER_SLOT, NOT_HOME, NOT_SOCKETABLE, AT_CAP, STARTER, MISSING_CHISEL, SOCKET_RANK,
        SOCKET_DONE, ORDER_STARTED;
        public String key() { return JewelerLang.MESSAGE + name().toLowerCase(Locale.ROOT); }
    }

    /** What the home brings: the Jeweler's rank and path, whether the player stands in a home, free order slots. */
    public record Context(ResidentRank rank, Optional<String> path, boolean home, int freeOrders) {
        public static Context of(ResidentRank rank) { return new Context(rank, Optional.empty(), false, 0); }
    }

    /** {@code purity} is the gem's (or the socketed gem's) index, -1 when there is none. */
    public record Quote(int selection, int purity, int shards, int grit, int theme, int rare, int faceted,
                        int yield, ResidentRank rank, Result result, int funds, int chisels, int sockets, int cap,
                        int orderRuns, int orderMinutes,
                        ItemStack item, ItemStack itemSnapshot, ItemStack gem, ItemStack gemSnapshot)
            implements ResidentServiceMenu.Quote {
        @Override public boolean ready() { return result == Result.READY; }
        @Override public Object terms() {
            return Arrays.asList(selection, purity, shards, grit, theme, rare, faceted, yield, rank, result, funds, chisels,
                    sockets, cap, orderRuns, orderMinutes);
        }
    }

    private JewelerService() {}

    public static boolean accepts(int slot, ItemStack stack) {
        return switch (slot) {
            case ITEM -> !stack.is(RiftfallItems.RIFT_SHARD.get()) && !stack.is(RiftfallItems.GEM_GRIT.get())
                    && !stack.is(RiftfallTags.THEME_CRYSTALS) && !material(stack);
            case GEM -> JewelerGems.current().purity(stack) >= 0;
            case GRIT -> stack.is(RiftfallItems.GEM_GRIT.get());
            case CRYSTAL_A, CRYSTAL_B -> stack.is(RiftfallTags.THEME_CRYSTALS);
            case SHARDS -> ShardWallet.isShard(stack);
            case MATERIAL -> material(stack);
            default -> false;
        };
    }

    private static boolean material(ItemStack stack) {
        return stack.is(RiftfallItems.SOCKET_CHISEL.get()) || stack.is(RiftfallItems.FACETED_CRYSTAL.get())
                || is(stack, WITHDRAWAL);
    }

    public static boolean is(ItemStack stack, ResourceLocation id) {
        return !stack.isEmpty() && BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(id);
    }

    public static int maxStack(int slot) { return slot == ITEM || slot == GEM ? 1 : 64; }

    /** JW3: the most sockets "add a socket" fills to, from the item's rarity. */
    public static int socketCap(int rarity, boolean socketable) {
        if (rarity >= 0 && rarity < SOCKET_CAP.length) return SOCKET_CAP[rarity];
        return socketable ? CAP_WITHOUT_RARITY : 0;
    }

    /** Shards for the socket that brings an item to {@code after}: {@code base x after x band(T)}. */
    public static int socketShards(JewelerCosts costs, int after, int tier) {
        return costs.socketShards() * after * HomeResidentDepth.band(Math.max(1, tier));
    }

    /** Setter: set half the shards (rounded up), unset none. */
    static int setShards(int base, Optional<String> path) {
        return path.filter(ResidentPaths.SETTER::equals).isPresent() ? (base + 1) / 2 : base;
    }
    static int unsetShards(int base, Optional<String> path) {
        return path.filter(ResidentPaths.SETTER::equals).isPresent() ? 0 : base;
    }
    /** Cutter: grit 25% less (rounded up), orders one run shorter (and an hour less online). */
    static int grit(int base, Optional<String> path) {
        return path.filter(ResidentPaths.CUTTER::equals).isPresent() ? (int) Math.ceil(base * 0.75) : base;
    }
    static int[] orderClock(JewelerCosts.Step step, Optional<String> path) {
        if (path.filter(ResidentPaths.CUTTER::equals).isEmpty()) return new int[]{step.orderRuns(), step.orderMinutes()};
        int runs = Math.max(0, step.orderRuns() - 1);
        int minutes = step.orderRuns() > 0 ? step.orderMinutes() * runs / step.orderRuns() : step.orderMinutes();
        return new int[]{runs, minutes};
    }

    public static Quote quote(Container inputs, Container inventory, int selection, ResidentRank rank,
                              JewelerCosts costs, JewelerGems gems) {
        return quote(inputs, inventory, selection, Context.of(rank), costs, gems);
    }

    public static Quote quote(Container inputs, Container inventory, int selection, Context ctx,
                              JewelerCosts costs, JewelerGems gems) {
        var rank = ctx.rank();
        var item = inputs.getItem(ITEM);
        var gem = inputs.getItem(GEM);
        int funds = ShardWallet.balance(inputs, SHARDS, inventory);
        int purity = -1, shards = 0, grit = 0, theme = 0, rare = 0, faceted = 0, yield = 0, chisels = 0, sockets = 0, cap = 0;
        int orderRuns = 0, orderMinutes = 0;
        Result result;
        if (selection == APPRAISE) {
            purity = gems.purity(gem);
            sockets = gems.sockets(item);
            cap = socketCap(gems.rarity(item), gems.socketable(item));
            result = Result.APPRAISE;
        } else if (selection == SET) {
            purity = gems.purity(gem);
            if (item.isEmpty()) result = Result.NO_ITEM;
            else if (purity < 0) result = Result.NO_GEM;
            else {
                shards = setShards(costs.set().shards(purity), ctx.path());
                theme = costs.set().theme(purity);
                rare = costs.set().rare(purity);
                if (gems.gems(item).stream().noneMatch(ItemStack::isEmpty)) result = Result.NO_EMPTY_SOCKET;
                else if (!gems.canSet(item, gem)) result = Result.GEM_DOES_NOT_FIT;
                else result = pay(inputs, funds, 0, theme, rare, 0, shards);
            }
        } else if (selection >= UNSET && selection < UNSET + MAX_SOCKETS) {
            var socketed = gems.gems(item);
            int socket = selection - UNSET;
            var held = socket < socketed.size() ? socketed.get(socket) : ItemStack.EMPTY;
            purity = gems.purity(held);
            // A gem that no longer fits its item (a rework moved it) comes out free.
            boolean dormant = !held.isEmpty() && !gems.fits(item, socket);
            if (item.isEmpty()) result = Result.NO_ITEM;
            else if (held.isEmpty() || (purity < 0 && !dormant)) result = Result.EMPTY_SOCKET;
            else {
                if (!dormant) {
                    shards = unsetShards(costs.unset().shards(purity), ctx.path());
                    theme = costs.unset().theme(purity);
                    rare = costs.unset().rare(purity);
                }
                // A withdrawal sigil pays the whole unset; it is kept when the unset is free anyway (Setter).
                if (is(inputs.getItem(MATERIAL), WITHDRAWAL) && shards + theme + rare > 0) { shards = theme = rare = 0; chisels = 1; }
                result = !gem.isEmpty() ? Result.GEM_SLOT_FULL : pay(inputs, funds, 0, theme, rare, 0, shards);
            }
        } else if (selection == DISSOLVE) {
            purity = gems.purity(gem);
            if (purity < 0) result = Result.NO_GEM;
            else {
                yield = costs.dissolve(purity);
                var held = inputs.getItem(GRIT);
                result = !held.isEmpty() && held.getCount() + yield > held.getMaxStackSize() ? Result.GRIT_SLOT_FULL : Result.READY;
            }
        } else if (selection == REFINE || selection == REFINE_ORDER) {
            boolean order = selection == REFINE_ORDER;
            purity = gems.purity(gem);
            var step = purity < 0 ? null : costs.step(purity).orElse(null);
            if (purity < 0) result = Result.NO_GEM;
            else if (step == null || purity >= JewelerCosts.PURITIES - 1) result = Result.MAX_PURITY;
            else {
                grit = grit(step.grit(), ctx.path()); theme = step.theme(); rare = step.rare(); faceted = step.faceted();
                shards = order ? step.orderShards() : step.shards();
                if (order) { var clock = orderClock(step, ctx.path()); orderRuns = clock[0]; orderMinutes = clock[1]; }
                if (!rank.atLeast(step.rank())) result = Result.RANK;
                else if (!order && step.orderOnly()) result = Result.ORDER_ONLY;
                else if (order && !step.orderable()) result = Result.NO_ORDER;
                else {
                    result = pay(inputs, funds, grit, theme, rare, faceted, shards);
                    if (result == Result.READY && order && !ctx.home()) result = Result.NOT_HOME;
                    else if (result == Result.READY && order && ctx.freeOrders() <= 0) result = Result.NO_ORDER_SLOT;
                }
            }
        } else if (selection == ADD_SOCKET) {
            sockets = gems.sockets(item);
            cap = socketCap(gems.rarity(item), gems.socketable(item));
            chisels = 1;
            int tier = Math.max(1, SmithService.tier(item));
            shards = socketShards(costs, sockets + 1, tier);
            if (item.isEmpty()) result = Result.NO_ITEM;
            else if (!rank.atLeast(ResidentRank.JOURNEYMAN)) result = Result.SOCKET_RANK;
            else if (!gems.socketable(item)) result = Result.NOT_SOCKETABLE;
            else if (SmithService.starter(item)) result = Result.STARTER;
            else if (sockets >= cap) result = Result.AT_CAP;
            else if (!inputs.getItem(MATERIAL).is(RiftfallItems.SOCKET_CHISEL.get())) result = Result.MISSING_CHISEL;
            else if (funds < shards) result = Result.MISSING_SHARDS;
            else result = Result.READY;
        } else result = Result.APPRAISE;
        return new Quote(selection, purity, shards, grit, theme, rare, faceted, yield, rank, result, funds, chisels, sockets, cap,
                orderRuns, orderMinutes, item, item.copy(), gem, gem.copy());
    }

    /** Whether the menu slots and wallet cover a cost. */
    private static Result pay(Container inputs, int funds, int grit, int theme, int rare, int faceted, int shards) {
        if (grit > 0 && count(inputs.getItem(GRIT), RiftfallItems.GEM_GRIT.get()) < grit) return Result.MISSING_GRIT;
        if (faceted > 0 && count(inputs.getItem(MATERIAL), RiftfallItems.FACETED_CRYSTAL.get()) < faceted) return Result.MISSING_FACETED;
        var crystals = crystals(inputs, theme, rare);
        if (crystals == null) return rare > 0 && rareCount(inputs) < rare ? Result.MISSING_RARE : Result.MISSING_CRYSTALS;
        return funds < shards ? Result.MISSING_SHARDS : Result.READY;
    }

    private static int count(ItemStack stack, net.minecraft.world.item.Item item) { return stack.is(item) ? stack.getCount() : 0; }
    private static int rareCount(Container inputs) {
        int n = 0;
        for (int slot : new int[]{CRYSTAL_A, CRYSTAL_B}) if (inputs.getItem(slot).is(RiftfallTags.RARE_CRYSTALS)) n += inputs.getItem(slot).getCount();
        return n;
    }

    /** How many to take from crystal slots A and B: rare needs from rare stacks first, then any theme crystal. Null if short. */
    static int[] crystals(Container inputs, int theme, int rare) {
        var a = inputs.getItem(CRYSTAL_A);
        var b = inputs.getItem(CRYSTAL_B);
        int[] left = {a.is(RiftfallTags.THEME_CRYSTALS) ? a.getCount() : 0, b.is(RiftfallTags.THEME_CRYSTALS) ? b.getCount() : 0};
        boolean[] isRare = {a.is(RiftfallTags.RARE_CRYSTALS), b.is(RiftfallTags.RARE_CRYSTALS)};
        int[] take = new int[2];
        int needRare = rare;
        for (int i = 0; i < 2 && needRare > 0; i++) if (isRare[i]) {
            int n = Math.min(needRare, left[i]); take[i] += n; left[i] -= n; needRare -= n;
        }
        if (needRare > 0) return null;
        int needTheme = theme;
        // Plain theme crystals first, so rare ones are kept for the steps that need them.
        for (int pass = 0; pass < 2 && needTheme > 0; pass++) for (int i = 0; i < 2 && needTheme > 0; i++) {
            if (isRare[i] != (pass == 1)) continue;
            int n = Math.min(needTheme, left[i]); take[i] += n; left[i] -= n; needTheme -= n;
        }
        return needTheme > 0 ? null : take;
    }

    /** Starts a purity order holding the gem: records it, then runs {@code consume} in the same tick. */
    @FunctionalInterface public interface OrderStart { boolean start(ItemStack gem, int runs, int minutes, Runnable consume); }

    public static Result commit(Container inputs, Container inventory, Quote shown, JewelerCosts costs, JewelerGems gems) {
        return commit(inputs, inventory, shown, Context.of(shown.rank()), costs, gems, (g, r, m, c) -> false);
    }

    /** Called with the quote the player confirmed. Nothing is taken unless everything below holds. */
    public static Result commit(Container inputs, Container inventory, Quote shown, Context ctx, JewelerCosts costs, JewelerGems gems,
                                OrderStart orders) {
        var item = inputs.getItem(ITEM);
        var gem = inputs.getItem(GEM);
        if (item != shown.item() || !ItemStack.matches(item, shown.itemSnapshot())
                || gem != shown.gem() || !ItemStack.matches(gem, shown.gemSnapshot())) return Result.CHANGED;
        var now = quote(inputs, inventory, shown.selection(), ctx, costs, gems);
        if (!now.terms().equals(shown.terms())) return Result.CHANGED;
        if (!now.ready()) return now.result();
        int selection = shown.selection();

        if (selection == DISSOLVE) {
            var held = inputs.getItem(GRIT);
            gem.shrink(1);
            if (held.isEmpty()) inputs.setItem(GRIT, new ItemStack(RiftfallItems.GEM_GRIT.get(), now.yield()));
            else held.grow(now.yield());
            inputs.setChanged();
            return Result.DISSOLVE_DONE;
        }

        var payment = ShardWallet.plan(inputs, SHARDS, inventory, now.shards());
        var take = crystals(inputs, now.theme(), now.rare());
        if (payment == null || take == null || !payment.valid()) return Result.CHANGED;
        Runnable consume = () -> {
            if (!payment.pay()) throw new IllegalStateException("payment changed under a Jeweler service");
            inputs.getItem(CRYSTAL_A).shrink(take[0]);
            inputs.getItem(CRYSTAL_B).shrink(take[1]);
            if (now.grit() > 0) inputs.getItem(GRIT).shrink(now.grit());
            if (now.faceted() > 0) inputs.getItem(MATERIAL).shrink(now.faceted());
            if (now.chisels() > 0) inputs.getItem(MATERIAL).shrink(now.chisels());
        };

        if (selection == REFINE_ORDER) {
            var held = gem.copyWithCount(1);
            if (!orders.start(held, now.orderRuns(), now.orderMinutes(), () -> { consume.run(); gem.shrink(1); })) return Result.CHANGED;
            inputs.setChanged();
            inventory.setChanged();
            return Result.ORDER_STARTED;
        }

        // Every other action is an in-place edit: work out the edited copy, preview it, then pay and write.
        ItemStack target, edited, returned = ItemStack.EMPTY;
        java.util.Set<DataComponentType<?>> named;
        Result done;
        if (selection == SET) {
            target = item;
            edited = gems.set(item, gem.copyWithCount(1));
            named = gems.socketComponents();
            done = Result.SET_DONE;
        } else if (selection == REFINE) {
            target = gem;
            edited = gems.withPurity(gem, now.purity() + 1);
            named = gems.purityComponents();
            done = Result.REFINE_DONE;
        } else if (selection == ADD_SOCKET) {
            target = item;
            edited = gems.withSockets(item, now.sockets() + 1);
            named = gems.socketCountComponents();
            done = Result.SOCKET_DONE;
        } else {
            target = item;
            returned = gems.gems(item).get(selection - UNSET).copy();
            edited = gems.unset(item, selection - UNSET);
            named = gems.socketComponents();
            done = Result.UNSET_DONE;
        }
        if (edited.isEmpty() || named.isEmpty()) return Result.CHANGED;
        var policy = new GearMutation.Policy(named, false, s -> false);
        final ItemStack source = edited;
        Consumer<ItemStack> edit = stack -> named.forEach(type -> copy(type, source, stack));
        if (GearMutation.preview(target, policy, edit) != GearMutation.Result.EDITED) return Result.REFUSED;

        consume.run();
        GearMutation.apply(target, policy, edit);
        if (selection == SET) gem.shrink(1);
        if (!returned.isEmpty()) inputs.setItem(GEM, returned);
        inputs.setChanged();
        inventory.setChanged();
        return done;
    }

    private static <T> void copy(DataComponentType<T> type, ItemStack from, ItemStack to) {
        var value = from.get(type);
        if (value == null) to.remove(type); else to.set(type, value);
    }

    /** One purity step on a held gem, as a work order's collect applies it; refused edits return the gem unchanged. */
    public static ItemStack purityUp(ItemStack held, JewelerGems gems) {
        int purity = gems.purity(held);
        if (purity < 0 || purity >= JewelerCosts.PURITIES - 1) return held;
        var edited = gems.withPurity(held, purity + 1);
        var named = gems.purityComponents();
        if (edited.isEmpty() || named.isEmpty()) return held;
        var copy = held.copy();
        var policy = new GearMutation.Policy(named, false, s -> false);
        if (GearMutation.apply(copy, policy, stack -> named.forEach(type -> copy(type, edited, stack))) != GearMutation.Result.EDITED) return held;
        return copy;
    }

    /** For the appraisal line: each socket's gem, EMPTY for an empty socket. */
    public static List<ItemStack> sockets(ItemStack item, JewelerGems gems) { return gems.gems(item); }
}
