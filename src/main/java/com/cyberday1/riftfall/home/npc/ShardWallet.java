package com.cyberday1.riftfall.home.npc;

import com.cyberday1.riftfall.init.RiftfallItems;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Rift Shards for a resident service: the menu's payment slot first, then the player's inventory and offhand.
 * Creative players pay too. Never reads chests, backpacks or storage networks.
 */
public final class ShardWallet {
    private ShardWallet() {}

    /** One source stack and the amount the plan takes from it. */
    private record Take(Container container, int slot, ItemStack stack, int amount) {}

    /** A checked payment. {@link #pay} takes all of it or none of it. */
    public static final class Payment {
        private final List<Take> takes;
        private final int total;
        private boolean spent;
        private Payment(List<Take> takes, int total) { this.takes = takes; this.total = total; }
        public int total() { return total; }

        /** Every source must still hold its planned stack and amount; otherwise nothing is taken. */
        public boolean valid() {
            if (spent) return false;
            for (var take : takes) {
                var now = take.container().getItem(take.slot());
                if (now != take.stack() || !isShard(now) || now.getCount() < take.amount()) return false;
            }
            return true;
        }

        /** Same tick as the service's own validation; there is no callback between check and shrink. The caller marks containers changed. */
        public boolean pay() {
            if (!valid()) return false;
            for (var take : takes) take.stack().shrink(take.amount());
            spent = true;
            return true;
        }
    }

    public static boolean isShard(ItemStack stack) { return stack.is(RiftfallItems.RIFT_SHARD.get()); }

    /** Shards in the payment slot (null for none) plus the player's inventory. */
    public static int balance(Container inputs, int paymentSlot, Container inventory) {
        long total = 0;
        if (inputs != null && paymentSlot >= 0 && isShard(inputs.getItem(paymentSlot))) total += inputs.getItem(paymentSlot).getCount();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            var stack = inventory.getItem(i);
            if (isShard(stack)) total += stack.getCount();
        }
        return (int) Math.min(Integer.MAX_VALUE, total);
    }

    /** A payment for exactly {@code amount}, or null if the shards are not there. Zero is a valid, empty payment. */
    public static Payment plan(Container inputs, int paymentSlot, Container inventory, int amount) {
        if (amount < 0) return null;
        var takes = new ArrayList<Take>();
        int left = amount;
        if (left > 0 && inputs != null && paymentSlot >= 0) left = take(takes, inputs, paymentSlot, left);
        for (int i = 0; left > 0 && i < inventory.getContainerSize(); i++) left = take(takes, inventory, i, left);
        return left > 0 ? null : new Payment(List.copyOf(takes), amount);
    }

    private static int take(List<Take> takes, Container container, int slot, int left) {
        var stack = container.getItem(slot);
        if (!isShard(stack) || stack.isEmpty()) return left;
        int amount = Math.min(left, stack.getCount());
        takes.add(new Take(container, slot, stack, amount));
        return left - amount;
    }
}
