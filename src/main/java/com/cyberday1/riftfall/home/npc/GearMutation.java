package com.cyberday1.riftfall.home.npc;

import com.cyberday1.riftfall.init.RiftfallTags;
import com.cyberday1.riftfall.tool.LootingToolItem;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.component.TypedDataComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * The in-place item edit every resident service goes through: one stack in, the same stack out.
 * The edit runs on a copy; only if every unnamed component is unchanged, nothing named was removed and no
 * custom-data key was dropped is the result written back onto the original object.
 */
public final class GearMutation {
    public enum Result { EDITED, NOT_SINGLE, BANNED, LOOTING_PICK, REFUSED, UNCHANGED, OUT_OF_SCOPE, STRIPPED }

    /** What a service may touch and which items it refuses. */
    public record Policy(Set<DataComponentType<?>> components, boolean allowLootingPick, Predicate<ItemStack> refuse) {
        public Policy {
            components = Set.copyOf(components);
            if (components.isEmpty()) throw new IllegalArgumentException("A mutation must name what it edits");
        }
        public static Policy of(DataComponentType<?>... components) { return new Policy(Set.of(components), false, s -> false); }
        public Policy withLootingPick() { return new Policy(components, true, refuse); }
        public Policy refusing(Predicate<ItemStack> more) { return new Policy(components, allowLootingPick, refuse.or(more)); }
    }

    private GearMutation() {}

    public static Result check(ItemStack stack, Policy policy) {
        if (stack.isEmpty() || stack.getCount() != 1) return Result.NOT_SINGLE;
        if (stack.is(RiftfallTags.ACQUISITION_BANNED)) return Result.BANNED;
        if (LootingToolItem.isTool(stack) && !policy.allowLootingPick()) return Result.LOOTING_PICK;
        if (policy.refuse().test(stack)) return Result.REFUSED;
        return Result.EDITED;
    }

    /** Dry run: what {@link #apply} would do, without touching the stack. */
    public static Result preview(ItemStack stack, Policy policy, Consumer<ItemStack> edit) {
        var allowed = check(stack, policy);
        if (allowed != Result.EDITED) return allowed;
        var copy = stack.copy();
        edit.accept(copy);
        return verify(stack, copy, policy);
    }

    /** All or nothing: on anything but EDITED the original is untouched. */
    public static Result apply(ItemStack stack, Policy policy, Consumer<ItemStack> edit) {
        var allowed = check(stack, policy);
        if (allowed != Result.EDITED) return allowed;
        var copy = stack.copy();
        edit.accept(copy);
        var verdict = verify(stack, copy, policy);
        if (verdict != Result.EDITED) return verdict;
        for (var type : policy.components()) transfer(type, copy, stack);
        return Result.EDITED;
    }

    static Result verify(ItemStack before, ItemStack after, Policy policy) {
        if (after.getItem() != before.getItem() || after.getCount() != before.getCount()) return Result.OUT_OF_SCOPE;
        var types = new HashSet<DataComponentType<?>>();
        for (TypedDataComponent<?> c : before.getComponents()) types.add(c.type());
        for (TypedDataComponent<?> c : after.getComponents()) types.add(c.type());
        boolean changed = false;
        for (var type : types) {
            boolean same = Objects.equals(before.get(type), after.get(type));
            if (same) continue;
            if (!policy.components().contains(type)) return Result.OUT_OF_SCOPE;
            if (before.has(type) && !after.has(type)) return Result.STRIPPED;
            changed = true;
        }
        if (stripsCustomData(before, after)) return Result.STRIPPED;
        return changed ? Result.EDITED : Result.UNCHANGED;
    }

    private static boolean stripsCustomData(ItemStack before, ItemStack after) {
        var old = before.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        var now = after.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        for (var key : old.getAllKeys()) if (!now.contains(key)) return true;
        return false;
    }

    private static <T> void transfer(DataComponentType<T> type, ItemStack from, ItemStack to) {
        var value = from.get(type);
        if (value == null) to.remove(type); else to.set(type, value);
    }
}
