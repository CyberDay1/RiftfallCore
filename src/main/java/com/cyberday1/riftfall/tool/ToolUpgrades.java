package com.cyberday1.riftfall.tool;

import com.cyberday1.riftfall.init.RiftfallItems;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.Container;
import com.cyberday1.riftfall.home.npc.GearMutation;
import com.cyberday1.riftfall.home.npc.ResidentServiceMenu;
import com.cyberday1.riftfall.home.npc.ShardWallet;

/** Server-thread installation: one rune + an escalating Rift Shard fee, on the same tool stack. */
public final class ToolUpgrades {
    public static final int BASE_FEE = 8, FEE_PER_INSTALL = 4;
    private ToolUpgrades() {}

    public enum Result {
        READY("Ready to install"), WRONG_TOOL("Insert your Riftfall Looting Pick"),
        GRADE_TOO_LOW("This effect needs a higher-grade rune"), MISSING_RUNE("Insert a runestone"),
        MISSING_SHARDS("Not enough Rift Shards"), CHANGED("Tool changed. Review a new quote."),
        OUT_OF_RANGE("Upgrade exceeds supported numeric range"), INSTALLED("Runestone installed");
        /** English only; players see {@link #key()}. */
        public final String message;
        Result(String message) { this.message = message; }
        public String key() { return ToolLang.MESSAGE + ToolLang.lower(this); }
        public Component text() { return Component.translatable(key()); }
    }

    /** {@code funds} is the shard balance the quote saw; a change moves the menu's confirm id. */
    public record Quote(ItemStack original, ItemStack snapshot, ToolStat stat, RuneGrade grade,
                        int level, int before, int after, int shards, Result result, int funds)
            implements ResidentServiceMenu.Quote {
        @Override public boolean ready() { return result == Result.READY; }
        @Override public Object terms() {
            return java.util.Arrays.asList(stat, grade, level, before, after, shards, result, funds);
        }
    }

    /** Only custom data changes; the pick is the one item this policy lets through. */
    public static final GearMutation.Policy POLICY = GearMutation.Policy.of(DataComponents.CUSTOM_DATA).withLootingPick();

    public static int level(ItemStack tool) {
        return Math.max(1, tool.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getInt(LootingToolItem.LEVEL_KEY));
    }
    public static int ranks(ItemStack tool, ToolStat stat) {
        return Math.max(0, tool.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag()
                .getCompound(LootingToolItem.UPGRADES_KEY).getInt(stat.key));
    }
    public static int count(Inventory inventory, Item item) {
        int total = 0;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            var stack = inventory.getItem(slot);
            if (stack.is(item)) total += stack.getCount();
        }
        return total;
    }
    public static double bonus(ItemStack tool, ToolStat stat) {
        return LootingToolItem.isTool(tool) ? ranks(tool, stat) * stat.perRank : 0;
    }

    /** The one write of a stat's ranks and the pick's level; installs and the op command both use it. */
    public static java.util.function.Consumer<ItemStack> rankEdit(ToolStat stat, int ranks, int level) {
        return stack -> CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            var upgrades = tag.getCompound(LootingToolItem.UPGRADES_KEY);
            upgrades.putInt(stat.key, ranks);
            tag.put(LootingToolItem.UPGRADES_KEY, upgrades);
            tag.putInt(LootingToolItem.LEVEL_KEY, level);
        });
    }

    /** Slots 0/1/2 contain the pick, rune and shards. Inventory shards supplement the payment slot. */
    public static Quote quote(Container inputs, Inventory inventory, ToolStat stat) { return quote(inputs, inventory, stat, false); }

    /** Toolwright (a Master path): 30% off the fee, rounded down, and a rune may install effects one grade above its own. */
    public static final double TOOLWRIGHT_FEE = 0.7;

    public static boolean supports(RuneGrade grade, ToolStat stat, boolean toolwright) {
        return grade != null && grade.ordinal() + (toolwright ? 1 : 0) >= stat.minimum.ordinal();
    }

    /** The fee at pick {@code level} for {@code grade} (null prices as common); Toolwright rounds down, like Fortune and modes. */
    public static long fee(int level, RuneGrade grade, boolean toolwright) {
        long fee = (BASE_FEE + (long) FEE_PER_INSTALL * (level - 1)) * (grade == null ? 1 : grade.ordinal() + 1);
        return toolwright ? Math.max(1, (long) Math.floor(fee * TOOLWRIGHT_FEE)) : fee;
    }

    public static Quote quote(Container inputs, Inventory inventory, ToolStat stat, boolean toolwright) {
        var tool = inputs.getItem(0);
        var grade = RuneGrade.of(inputs.getItem(1));
        int level = level(tool), before = ranks(tool, stat);
        long fee = fee(level, grade, toolwright);
        long after = (long) before + (grade == null ? 0 : grade.strength);
        int funds = shards(inputs, inventory);
        Result result;
        if (!LootingToolItem.isTool(tool) || tool.getCount() != 1) result = Result.WRONG_TOOL;
        else if (grade == null) result = Result.MISSING_RUNE;
        else if (!supports(grade, stat, toolwright)) result = Result.GRADE_TOO_LOW;
        else if (fee > Integer.MAX_VALUE || after > Integer.MAX_VALUE || level == Integer.MAX_VALUE) result = Result.OUT_OF_RANGE;
        else if (funds < fee) result = Result.MISSING_SHARDS;
        else result = Result.READY;
        return new Quote(tool, tool.copy(), stat, grade, level, before,
                (int) Math.min(Integer.MAX_VALUE, after), (int) Math.min(Integer.MAX_VALUE, fee), result, funds);
    }

    public static int shards(Container inputs, Inventory inventory) {
        return ShardWallet.balance(inputs, 2, inventory);
    }

    /** Caller validates the live Blacksmith, menu and one-use confirmation before invoking this. */
    public static Result install(Container inputs, Inventory inventory, Quote shown) { return install(inputs, inventory, shown, false); }

    public static Result install(Container inputs, Inventory inventory, Quote shown, boolean toolwright) {
        var tool = inputs.getItem(0);
        if (tool != shown.original() || !ItemStack.matches(tool, shown.snapshot())) return Result.CHANGED;
        var now = quote(inputs, inventory, shown.stat(), toolwright);
        if (now.grade() != shown.grade()) return Result.CHANGED;
        if (now.result() != Result.READY) return now.result();
        if (now.shards() != shown.shards() || now.after() != shown.after() || now.level() != shown.level()) return Result.CHANGED;
        var payment = ShardWallet.plan(inputs, 2, inventory, now.shards());
        if (payment == null) return Result.MISSING_SHARDS;
        var edit = rankEdit(shown.stat(), now.after(), now.level() + 1);
        if (GearMutation.preview(tool, POLICY, edit) != GearMutation.Result.EDITED) return Result.CHANGED;
        // Everything is checked; pay() rechecks its stacks and the edit was previewed this tick.
        if (!payment.pay()) return Result.CHANGED;
        inputs.getItem(1).shrink(1);
        GearMutation.apply(tool, POLICY, edit);
        inputs.setChanged();
        inventory.setChanged();
        return Result.INSTALLED;
    }
}
