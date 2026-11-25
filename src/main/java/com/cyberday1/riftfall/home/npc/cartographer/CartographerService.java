package com.cyberday1.riftfall.home.npc.cartographer;

import com.cyberday1.riftfall.home.npc.ResidentPaths;
import com.cyberday1.riftfall.home.npc.ResidentRank;
import com.cyberday1.riftfall.home.npc.ResidentServiceMenu;
import com.cyberday1.riftfall.home.npc.ShardWallet;
import com.cyberday1.riftfall.rift.RiftAttunement;
import com.cyberday1.riftfall.rift.RiftKeyItem;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.*;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * The Cartographer's page over its three slots: attune a rift key to a theme from this player's atlas (Journeyman), or
 * to a boss the atlas recorded in a theme (Expert). Instant and in place: the key never leaves the page and keeps its
 * tier. Cost (C1): the key's tier in shards plus one crystal of the target theme; a theme with no home crystal (cave) costs
 * twice the tier in shards instead (RULED 2026-11-03). Quotes are pure.
 */
public final class CartographerService {
    public static final int KEY = 0, CRYSTAL = 1, SHARDS = 2, SLOTS = 3;
    /** Selections: a theme by index, or {@link #BOSS} + a boss entry's index. */
    public static final int MAX_THEMES = 18, BOSS = 20, MAX_BOSSES = 30;

    public enum Status {
        READY, NOTHING, RANK_LOCKED, NO_KEY, UNVISITED, OFF_ROSTER, SAME, NO_CRYSTAL, MISSING_SHARDS;
        public String id() { return name().toLowerCase(Locale.ROOT); }
    }

    /** A boss this player killed in a theme: what boss attunement offers. */
    public record BossEntry(ResourceLocation theme, ResourceLocation boss) {}

    /**
     * What the quote needs beyond the slots. {@code crystal} names a theme's crystal (empty: the theme has none, so
     * attunement costs shards only); {@code onRoster} answers whether a boss can stand on a key tier's pad.
     */
    public record Context(ResidentRank rank, Optional<String> path, List<ResourceLocation> themes, List<BossEntry> bosses,
                          Predicate<ResourceLocation> visited, Function<ResourceLocation, Optional<Item>> crystal,
                          BiPredicate<ResourceLocation, Integer> onRoster) {}

    public record Quote(int selection, Status status, int tier, int shards, int balance, int crystals, RiftAttunement after)
            implements ResidentServiceMenu.Quote {
        @Override public boolean ready() { return status == Status.READY; }
        @Override public Object terms() { return Arrays.asList(selection, status, tier, shards, balance, crystals, after); }
    }

    private CartographerService() {}

    public static boolean accepts(int slot, ItemStack stack) {
        return switch (slot) {
            case KEY -> stack.getItem() instanceof RiftKeyItem;
            case CRYSTAL -> !ShardWallet.isShard(stack) && !(stack.getItem() instanceof RiftKeyItem);
            case SHARDS -> ShardWallet.isShard(stack);
            default -> false;
        };
    }

    public static int maxStack(int slot) { return slot == KEY ? 1 : Item.ABSOLUTE_MAX_STACK_SIZE; }

    /** A theme without a crystal pays this many times the tier in shards in its place. */
    public static final int NO_CRYSTAL_FACTOR = 2;

    /**
     * Shards for one attunement: the key's tier, doubled for a theme with no crystal; Pathfinder halves the whole
     * price, rounded up (so a Pathfinder's cave costs the tier); a Bounty Scout's boss step is free.
     */
    public static int shards(int tier, Optional<String> path, boolean boss, boolean themeHasCrystal) {
        if (boss && path.filter(ResidentPaths.BOUNTY_SCOUT::equals).isPresent()) return 0;
        int t = Math.max(1, tier) * (themeHasCrystal ? 1 : NO_CRYSTAL_FACTOR);
        return path.filter(ResidentPaths.PATHFINDER::equals).isPresent() ? (t + 1) / 2 : t;
    }

    /** Theme crystals for one attunement: one of the target theme's, none for a theme without one or a Bounty Scout's boss step. */
    public static int crystals(boolean themeHasCrystal, Optional<String> path, boolean boss) {
        if (boss && path.filter(ResidentPaths.BOUNTY_SCOUT::equals).isPresent()) return 0;
        return themeHasCrystal ? 1 : 0;
    }

    /**
     * What the key carries after attuning to {@code theme}. A Pathfinder adds a second theme (keeping the latest other
     * one); a theme the key already carries narrows it to that theme. Boss attunement pins one theme and the boss.
     */
    public static RiftAttunement after(RiftAttunement current, ResourceLocation theme, Optional<ResourceLocation> boss,
                                       Optional<String> path) {
        if (boss.isPresent()) return new RiftAttunement(List.of(theme), boss);
        boolean pathfinder = path.filter(ResidentPaths.PATHFINDER::equals).isPresent();
        if (pathfinder && !current.themes().isEmpty() && !current.themes().contains(theme))
            return new RiftAttunement(List.of(current.themes().getLast(), theme), Optional.empty());
        return new RiftAttunement(List.of(theme), Optional.empty());
    }

    public static Quote quote(Container slots, Container inventory, int selection, Context ctx) {
        int balance = ShardWallet.balance(slots, SHARDS, inventory);
        ResourceLocation theme;
        Optional<ResourceLocation> boss;
        if (selection >= 0 && selection < Math.min(MAX_THEMES, ctx.themes().size())) {
            theme = ctx.themes().get(selection);
            boss = Optional.empty();
        } else if (selection >= BOSS && selection - BOSS < Math.min(MAX_BOSSES, ctx.bosses().size())) {
            var entry = ctx.bosses().get(selection - BOSS);
            theme = entry.theme();
            boss = Optional.of(entry.boss());
        } else {
            return new Quote(selection, Status.NOTHING, 0, 0, balance, 0, RiftAttunement.NONE);
        }
        var key = slots.getItem(KEY);
        boolean hasKey = key.getItem() instanceof RiftKeyItem && key.getCount() == 1;
        int tier = hasKey ? RiftKeyItem.tier(key) : 0;
        var crystal = ctx.crystal().apply(theme);
        int shards = shards(tier, ctx.path(), boss.isPresent(), crystal.isPresent());
        int crystals = crystals(crystal.isPresent(), ctx.path(), boss.isPresent());
        var current = hasKey ? RiftAttunement.of(key) : RiftAttunement.NONE;
        var after = after(current, theme, boss, ctx.path());
        Status status;
        if (!ctx.rank().atLeast(boss.isPresent() ? ResidentRank.EXPERT : ResidentRank.JOURNEYMAN)) status = Status.RANK_LOCKED;
        else if (!hasKey) status = Status.NO_KEY;
        else if (!ctx.visited().test(theme)) status = Status.UNVISITED;
        else if (boss.isPresent() && !ctx.onRoster().test(boss.get(), tier)) status = Status.OFF_ROSTER;
        else if (after.equals(current)) status = Status.SAME;
        else if (crystals > 0 && (crystal.isEmpty() || !slots.getItem(CRYSTAL).is(crystal.get()) || slots.getItem(CRYSTAL).getCount() < crystals))
            status = Status.NO_CRYSTAL;
        else if (balance < shards) status = Status.MISSING_SHARDS;
        else status = Status.READY;
        return new Quote(selection, status, tier, shards, balance, crystals, after);
    }

    /** One attunement: re-quote, pay, take the crystal, write the key's components in place. */
    public static boolean commit(Quote shown, Container slots, Container inventory, Context ctx) {
        var now = quote(slots, inventory, shown.selection(), ctx);
        if (!now.ready() || !now.terms().equals(shown.terms())) return false;
        var payment = ShardWallet.plan(slots, SHARDS, inventory, now.shards());
        if (payment == null || !payment.pay()) return false;
        if (now.crystals() > 0) slots.getItem(CRYSTAL).shrink(now.crystals());
        now.after().write(slots.getItem(KEY));
        slots.setChanged();
        inventory.setChanged();
        return true;
    }
}
