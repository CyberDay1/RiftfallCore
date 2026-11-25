package com.cyberday1.riftfall.home.npc.smith;

import com.cyberday1.riftfall.gear.RarityLadder;
import com.cyberday1.riftfall.home.npc.ResidentServiceMenu;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The Fitter's bulk scrap: every grid slot is quoted and committed as a single salvage on its own scratch Salvage
 * page, so the rules stay {@link SmithService}'s. Adds the batch totals, the Epic confirmation and the stale checks.
 */
public final class BulkScrapService {
    public static final int GRID = 27;
    /** Apotheosis epic in {@link RarityLadder#RARITIES}; this or rarer takes a second press. */
    public static final int CONFIRM_RARITY = 3;
    /** How long a first press stays armed: 3 seconds. */
    public static final int CONFIRM_TICKS = 60;

    /** One grid item: its scratch Salvage page and the single quote over it. */
    public record Line(int slot, Container page, SmithService.Quote quote, int rarity, boolean socketed) {}

    /** {@code materials} is indexed by rarity; {@code result} is READY, NO_ITEM (empty) or the first refusal. */
    public record Quote(SmithService.Result result, List<Line> lines, int[] materials, int stock, boolean needsConfirm)
            implements ResidentServiceMenu.Quote {
        @Override public boolean ready() { return result == SmithService.Result.READY; }
        public int items() { return lines.size(); }
        @Override public Object terms() {
            var perLine = new ArrayList<Object>();
            for (var line : lines) {
                var q = line.quote();
                perLine.add(Arrays.asList(line.slot(), BuiltInRegistries.ITEM.getKey(q.item().getItem()), q.tier(), line.rarity(),
                        BuiltInRegistries.ITEM.getKey(q.material().getItem()), q.material().getCount(), q.stockOut(), line.socketed()));
            }
            return Arrays.asList(result, perLine, Arrays.stream(materials).boxed().toList(), stock, needsConfirm);
        }
    }

    public record Outcome(SmithService.Result result, int scrapped, List<ItemStack> outputs) {}

    private BulkScrapService() {}

    /** The single salvage quote for {@code stack} alone on a fresh Salvage page holding that very stack. */
    static Line single(int slot, ItemStack stack, Container inventory, SmithService.Context ctx) {
        var page = new SimpleContainer(SmithService.SLOTS);
        page.setItem(SmithService.GEAR, stack);
        var quote = SmithService.quote(page, inventory, SmithService.SALVAGE, ctx);
        boolean socketed = !stack.isEmpty() && ctx.gems().gems(stack).stream().anyMatch(g -> !g.isEmpty());
        return new Line(slot, page, quote, stack.isEmpty() ? -1 : SmithService.rarity(stack, ctx.gems()), socketed);
    }

    /** Why single salvage would refuse one of {@code stack}; null when it would salvage. */
    public static SmithService.Result refusal(ItemStack stack, Container inventory, SmithService.Context ctx) {
        if (stack.isEmpty()) return SmithService.Result.NO_ITEM;
        var result = single(0, stack.copyWithCount(1), inventory, ctx).quote().result();
        return result == SmithService.Result.READY ? null : result;
    }

    public static boolean needsConfirm(int rarity, boolean socketed) { return socketed || rarity >= CONFIRM_RARITY; }

    public static Quote quote(Container grid, Container inventory, SmithService.Context ctx) {
        var lines = new ArrayList<Line>();
        int[] materials = new int[RarityLadder.RARITIES.length];
        int stock = 0;
        boolean confirm = false;
        SmithService.Result refused = null;
        for (int slot = 0; slot < grid.getContainerSize(); slot++) {
            var stack = grid.getItem(slot);
            if (stack.isEmpty()) continue;
            var line = single(slot, stack, inventory, ctx);
            if (!line.quote().ready()) { if (refused == null) refused = line.quote().result(); continue; }
            lines.add(line);
            if (line.rarity() >= 0 && line.rarity() < materials.length) materials[line.rarity()] += line.quote().material().getCount();
            stock += line.quote().stockOut();
            confirm |= needsConfirm(line.rarity(), line.socketed());
        }
        var result = refused != null ? refused : lines.isEmpty() ? SmithService.Result.NO_ITEM : SmithService.Result.READY;
        return new Quote(result, List.copyOf(lines), materials, stock, confirm);
    }

    /**
     * Scraps the grid if it still matches {@code shown}: same terms, same stack objects. Each item goes through
     * {@link SmithService#commit}; its slot is emptied and its outputs collected, merged into full stacks.
     */
    public static Outcome commit(Container grid, Container inventory, Quote shown, SmithService.Context ctx) {
        var now = quote(grid, inventory, ctx);
        if (!now.terms().equals(shown.terms())) return new Outcome(SmithService.Result.CHANGED, 0, List.of());
        for (int i = 0; i < shown.lines().size(); i++)
            if (grid.getItem(shown.lines().get(i).slot()) != shown.lines().get(i).quote().item()
                    || !ItemStack.matches(now.lines().get(i).quote().item(), shown.lines().get(i).quote().snapshot()))
                return new Outcome(SmithService.Result.CHANGED, 0, List.of());
        if (!now.ready()) return new Outcome(now.result(), 0, List.of());
        var outputs = new ArrayList<ItemStack>();
        int scrapped = 0;
        for (var line : now.lines()) {
            var result = SmithService.commit(line.page(), inventory, line.quote(), ctx);
            if (result != SmithService.Result.SALVAGED) break; // Nothing taken for this one; the rest stay in the grid.
            grid.setItem(line.slot(), ItemStack.EMPTY);
            add(outputs, line.page().getItem(SmithService.OUT_MATERIAL));
            add(outputs, line.page().getItem(SmithService.OUT_STOCK));
            scrapped++;
        }
        grid.setChanged();
        return new Outcome(scrapped == now.items() ? SmithService.Result.SALVAGED : SmithService.Result.CHANGED, scrapped, outputs);
    }

    /** Merges {@code add} into {@code outputs}, topping up matching stacks before starting new ones. */
    static void add(List<ItemStack> outputs, ItemStack add) {
        var left = add.copy();
        for (var out : outputs) {
            if (left.isEmpty()) return;
            if (!ItemStack.isSameItemSameComponents(out, left)) continue;
            int moved = Math.min(left.getCount(), out.getMaxStackSize() - out.getCount());
            out.grow(moved);
            left.shrink(moved);
        }
        while (!left.isEmpty()) outputs.add(left.split(left.getMaxStackSize()));
    }

    /** A first press holds the quote it saw for {@link #CONFIRM_TICKS}; only a second press on the same quote passes. */
    public static final class Arm {
        private Object terms;
        private long until = Long.MIN_VALUE;

        public boolean armed(Object now, long tick) { return terms != null && terms.equals(now) && tick <= until; }
        public void arm(Object now, long tick) { terms = now; until = tick + CONFIRM_TICKS; }
        public void clear() { terms = null; until = Long.MIN_VALUE; }
    }
}
