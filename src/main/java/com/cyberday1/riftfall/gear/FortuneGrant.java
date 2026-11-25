package com.cyberday1.riftfall.gear;

import com.cyberday1.riftfall.init.RiftfallDataComponents;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AnvilUpdateEvent;
import net.neoforged.neoforge.event.enchanting.EnchantedBlockLootEvent;
import net.neoforged.neoforge.event.enchanting.GetEnchantmentLevelEvent;

/**
 * Fortune is Blacksmith-only (ruled 2026-11-02). The Blacksmith and {@code /riftfall pick fortune} write the level
 * into {@link RiftfallDataComponents#FORTUNE_GRANT} beside the enchantment; the floor reports Fortune as
 * min(answer, stored, grant), so Fortune from tables, books, loot, trades, affixes, gems or other mods does nothing.
 * Same shape and priority as {@link EnchantmentBan}: LOWEST, after Apotheosis adds its levels at HIGH.
 * The anvil refuses any Fortune on the right; the inventory sweep strips unearned Fortune so tooltips stay honest.
 */
public final class FortuneGrant {
    private FortuneGrant() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, FortuneGrant::floor);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, FortuneGrant::lootFloor);
        NeoForge.EVENT_BUS.addListener(FortuneGrant::anvil);
    }

    /** What Fortune may do: the stored level, never above the grant; 0 without one. */
    public static int allowed(int stored, int granted) { return Math.max(0, Math.min(stored, granted)); }

    public static int granted(ItemStack stack) { return stack.getOrDefault(RiftfallDataComponents.FORTUNE_GRANT.get(), 0); }

    public static int stored(ItemEnchantments enchantments) {
        for (var entry : enchantments.entrySet()) if (entry.getKey().is(Enchantments.FORTUNE)) return entry.getIntValue();
        return 0;
    }

    public static int stored(ItemStack stack) { return stored(stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY)); }

    /** Cap for the loot roll in progress; null when it is not a Fortune roll. */
    private static final ThreadLocal<Integer> BONUS_CAP = new ThreadLocal<>();

    /**
     * Fortune bonus-count rolls (`ApplyBonusCount`): Artifacts, Curios, Accessories and Puffish Attributes add levels
     * there, past the event, so the formula itself is capped at the tool's effective level.
     */
    public static void beginBonus(Holder<Enchantment> enchantment, ItemStack tool) {
        BONUS_CAP.set(enchantment.is(Enchantments.FORTUNE) ? (tool == null ? 0 : effective(tool)) : null);
    }

    public static void endBonus() { BONUS_CAP.remove(); }

    public static int capBonus(int level) { return cap(level, BONUS_CAP.get()); }

    public static int cap(int level, Integer cap) { return cap == null ? level : Math.min(level, Math.max(0, cap)); }

    /** The level that applies in play. */
    public static int effective(ItemStack stack) { return allowed(stored(stack), granted(stack)); }

    /** Writes stored Fortune and the grant together; 0 clears Fortune and zeroes the grant (GearMutation forbids removing it). */
    public static void set(ItemStack stack, Holder<Enchantment> fortune, int level) {
        var enchantments = new ItemEnchantments.Mutable(stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY));
        enchantments.set(fortune, level);
        stack.set(DataComponents.ENCHANTMENTS, enchantments.toImmutable());
        stack.set(RiftfallDataComponents.FORTUNE_GRANT.get(), Math.max(0, level));
    }

    private static void floor(GetEnchantmentLevelEvent event) {
        var answer = event.getEnchantments();
        for (var holder : answer.keySet()) {
            if (!holder.is(Enchantments.FORTUNE)) continue;
            answer.set(holder, Math.min(answer.getLevel(holder), effective(event.getStack())));
            return;
        }
    }

    /**
     * The loot-context level, fired by {@code ApplyBonusCount} and {@code table_bonus}: Curios 9.6+ adds curio Fortune
     * here instead of by mixin, so the grant caps it as well.
     */
    private static void lootFloor(EnchantedBlockLootEvent event) {
        if (event.getEnchantment().is(Enchantments.FORTUNE))
            event.setEnchantmentLevel(cap(event.getEnchantmentLevel(), effective(event.getTool())));
    }

    /** No anvil merge carries Fortune: from a book it was never granted, from a tool it would be clamped. */
    private static void anvil(AnvilUpdateEvent event) {
        if (stored(EnchantmentHelper.getEnchantmentsForCrafting(event.getRight())) > 0) event.setCanceled(true);
    }

    /**
     * Strips Fortune nobody granted, in place; a book left with no enchantment becomes a plain book. Returns the
     * stack to keep (the same one unless it became a book), or null when nothing changed.
     */
    public static ItemStack clean(ItemStack stack) {
        if (stack.isEmpty()) return null;
        boolean changed = false;
        var enchantments = stack.get(DataComponents.ENCHANTMENTS);
        if (enchantments != null && stored(enchantments) > granted(stack)) {
            var keep = new ItemEnchantments.Mutable(enchantments);
            keep.removeIf(h -> h.is(Enchantments.FORTUNE));
            int granted = granted(stack);
            if (granted > 0) for (var entry : enchantments.entrySet())
                if (entry.getKey().is(Enchantments.FORTUNE)) keep.set(entry.getKey(), granted);
            stack.set(DataComponents.ENCHANTMENTS, keep.toImmutable());
            changed = true;
        }
        var book = stack.get(DataComponents.STORED_ENCHANTMENTS);
        if (book != null && stored(book) > 0) {
            var keep = new ItemEnchantments.Mutable(book);
            keep.removeIf(h -> h.is(Enchantments.FORTUNE));
            if (keep.keySet().isEmpty() && stack.is(Items.ENCHANTED_BOOK)) return new ItemStack(Items.BOOK, stack.getCount());
            stack.set(DataComponents.STORED_ENCHANTMENTS, keep.toImmutable());
            changed = true;
        }
        return changed ? stack : null;
    }
}
