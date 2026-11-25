package com.cyberday1.riftfall.rift;

import com.cyberday1.riftfall.init.RiftfallDataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Predicate;

/**
 * Telling what a party brought from what the dungeon gave them.
 *
 * <p>
 * That question only has to be answered once, at the moment somebody dies with the loot rule on, and
 * it has no answer at all unless it was prepared for at the door. An inventory in a rift is a pile of
 * items with no history: nothing on a sword says where it came from, and by the time it matters the
 * party has been inside for an hour, spent arrows, eaten food, swapped armour and thrown things away.
 * So the door writes the history down, and this class is both halves of that &mdash; what is written
 * on the way in, and how it is read on the way out.
 *
 * <h2>Two kinds of item, two different answers</h2>
 * A thing that does not stack is <em>marked</em>: {@link RiftfallDataComponents#CARRIED_INTO} is set
 * on it at the door, naming the rift. It is on the item, so it survives being put in a chest, dropped,
 * picked back up, logged out on, and handed to another player &mdash; every route that would launder
 * a rule based on comparing against a list.
 *
 * <p>
 * A thing that stacks is <em>counted</em>. Marking one would stop it merging with an unmarked one, so
 * a player who walked in with half a stack of arrows and picked up more would carry two piles of
 * arrows around for the rest of the run, and every ordinary item would quietly cost an inventory slot.
 * Counting costs nothing and is exactly as strong: sixty-four arrows are sixty-four arrows, and there
 * is no such thing as picking out the better one. {@link RiftRun#carriedIn} is the count, which is
 * the first thing to read that snapshot since it was written.
 *
 * <h2>What this does not cover, and why nothing is spent on it</h2>
 * Crafting inside a rift makes a new item out of carried-in materials, and the new item is unmarked,
 * so it is lost. That is the right answer by accident and an acceptable one on purpose: the materials
 * are gone either way, and a rift is not somewhere anybody sets up a workbench.
 *
 * <p>
 * Putting loot inside a container that is itself marked would defeat the whole thing, and in 1.21.1
 * there is no way to do it. A shulker box has to be placed to be opened and {@link RiftIntegrity}
 * refuses every placement; bundles are not obtainable on this version. Both of those are facts about
 * the game rather than decisions here, so this is written down rather than coded against &mdash; a
 * version or a mod that adds an openable container is what would make it a real hole, and a guard
 * written now against a hole that does not exist is a guard nobody would remember to check.
 *
 * <p>
 * A trinket that stacks is treated as loot however it got there, because the snapshot only walks the
 * inventory and so has no count for anything worn. There are essentially none, and the error leans
 * towards the harsher answer rather than towards a free pass.
 */
final class RiftLoot {

    private RiftLoot() {
    }

    /**
     * What survives a death, and whether anything did not.
     *
     * <p>
     * The flag is separate from the list being shorter because those are different facts: a party can
     * lose everything they found and still have lost nothing they brought, and the only difference a
     * player sees is which sentence they are shown on the death screen.
     */
    record Split(List<ItemStack> kept, boolean lost) {
    }

    /**
     * Writes the rift onto everything a player is carrying and wearing that can hold a mark.
     *
     * <p>
     * Called at the door, on the way in, before the teleport &mdash; so the player is still standing
     * in the level they came from and the rift is named by the run rather than by where they are.
     * Marking again on a second entry is harmless and correct: the mark names a rift, so the last one
     * written is the one that is true.
     */
    static void stamp(ServerPlayer player, ResourceLocation rift) {
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            mark(inventory.getItem(slot), rift);
        }
        RiftDeaths.stampWorn(player, rift);
    }

    /**
     * Marks one stack, and says whether it took the mark.
     *
     * <p>
     * Stackables are refused here rather than at every call site, so that the rule about which items
     * carry a mark is stated once. The return value is for the slots that are behind a handler rather
     * than a live list, where a stack that has been changed has to be written back.
     */
    static boolean mark(ItemStack stack, ResourceLocation rift) {
        if (stack.isEmpty() || stack.getMaxStackSize() > 1) {
            return false;
        }
        stack.set(RiftfallDataComponents.CARRIED_INTO.get(), rift);
        return true;
    }

    /** Whether this stack was carried into this particular rift. */
    static boolean carriedInto(ItemStack stack, ResourceLocation rift) {
        return rift.equals(stack.get(RiftfallDataComponents.CARRIED_INTO.get()));
    }

    /**
     * Divides what a death took into what the party owns and what the dungeon is taking back.
     *
     * <p>
     * {@code carried} is passed in rather than asked for, because the mark is a registered data
     * component and a registry only exists inside a running game. The rule itself is arithmetic over
     * stack counts and is worth testing on its own, so the one line that needs a game is the argument.
     *
     * <p>
     * A partly-owned stack is split rather than rounded either way. Walking in with sixteen arrows and
     * dying on forty means keeping sixteen: rounding up would make a single carried arrow launder a
     * whole stack, and rounding down would confiscate arrows the party owned for the crime of having
     * found more of them.
     */
    static Split split(List<ItemStack> taken, List<ItemStack> kit, Predicate<ItemStack> carried) {
        List<ItemStack> allowance = new ArrayList<>();
        for (ItemStack stack : kit) {
            if (stack.getMaxStackSize() > 1) {
                allowance.add(stack.copy());
            }
        }

        List<ItemStack> kept = new ArrayList<>();
        boolean lost = false;
        for (ItemStack stack : taken) {
            if (stack.getMaxStackSize() == 1) {
                if (carried.test(stack)) {
                    kept.add(stack);
                } else {
                    lost = true;
                }
                continue;
            }
            int allowed = draw(allowance, stack);
            if (allowed == stack.getCount()) {
                kept.add(stack);
                continue;
            }
            lost = true;
            if (allowed > 0) {
                ItemStack part = stack.copy();
                part.setCount(allowed);
                kept.add(part);
            }
        }
        return new Split(kept, lost);
    }

    /**
     * Spends as much of the allowance as this stack can claim, and says how much that was.
     *
     * <p>
     * The allowance is consumed as it goes, which is what makes the rule hold across a split pile: a
     * party that walked in with one stack of arrows and died holding four separate ones gets the first
     * stack back and loses the rest, however the game happened to arrange them.
     *
     * <p>
     * Matched on item and components rather than on item alone, so that an ordinary potion cannot be
     * spent as the allowance for a rare one. Nothing counted here is ever marked, so the mark itself
     * can never be the difference between two stacks that would otherwise match.
     */
    private static int draw(List<ItemStack> allowance, ItemStack stack) {
        int want = stack.getCount();
        int drawn = 0;
        for (Iterator<ItemStack> each = allowance.iterator(); each.hasNext() && drawn < want; ) {
            ItemStack held = each.next();
            if (!ItemStack.isSameItemSameComponents(held, stack)) {
                continue;
            }
            int take = Math.min(held.getCount(), want - drawn);
            drawn += take;
            held.shrink(take);
            if (held.isEmpty()) {
                each.remove();
            }
        }
        return drawn;
    }
}
