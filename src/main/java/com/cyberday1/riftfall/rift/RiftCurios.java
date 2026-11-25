package com.cyberday1.riftfall.rift;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurio;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;
import top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler;

import java.util.List;
import java.util.Map;

/**
 * The half of a player's equipment that is not in their inventory.
 *
 * <p>
 * Curios slots hang off the player as a capability rather than living in {@code Inventory}, so
 * everything {@link RiftDeaths} does by walking container indices misses them entirely, and a ring or
 * an amulet would drop onto the floor of a level that is about to be deleted. Which is the one thing
 * the whole claim exists to stop, and it applied to exactly the items a player is least willing to
 * lose. By design, curios are handled like gear: taken, worn, claimed back.
 *
 * <h2>Why this is a separate class</h2>
 * So that it is a separate class file. Curios is compile-only and optional, and the JVM does not load
 * a class until something reaches for it, so keeping every mention of a {@code top.theillusivec4}
 * type on this side of the wall means a server without the mod never loads this and never sees a
 * {@code NoClassDefFoundError}. {@link RiftDeaths} checks {@code ModList} before it calls in, and
 * that check is the wall.
 *
 * <h2>Curios has its own rules about death and they are honoured, not overridden</h2>
 * A curio can declare that it survives death or that it is destroyed by it, and the slot it sits in
 * can declare the same. Those are the equivalent of {@code keepInventory} and the Curse of Vanishing,
 * and {@link RiftDeaths} already mirrors both of those rather than inventing its own answer, so this
 * mirrors these. Taking a curio whose author said it survives death would be stealing a soulbound
 * charm and filing it as a claim, which is worse than the bug this fixes.
 */
final class RiftCurios {

    private RiftCurios() {
    }

    /**
     * Empties a player's curios slots into {@code taken}, leaving behind whatever is meant to stay.
     *
     * <p>
     * Cosmetic slots are swept as well as functional ones. They hold real items, the level deletion
     * cannot tell the difference, and a player who loses only the hat they were wearing over their
     * helmet has still lost a hat.
     */
    static void strip(Player player, DamageSource source, List<ItemStack> taken) {
        CuriosApi.getCuriosInventory(player).ifPresent(curios -> {
            for (Map.Entry<String, ICurioStacksHandler> slots : curios.getCurios().entrySet()) {
                ICurioStacksHandler handler = slots.getValue();
                strip(player, source, taken, slots.getKey(), handler, handler.getStacks(), false);
                strip(player, source, taken, slots.getKey(), handler, handler.getCosmeticStacks(), true);
            }
        });
    }

    /**
     * Marks everything in a player's curios slots on the way into a rift.
     *
     * <p>
     * Worn trinkets have to be marked or the loot rule has a hole exactly the shape of the items it
     * matters most for: a ring is the thing a party is least willing to lose and the thing a dungeon is
     * most likely to hand out, and an unmarked one that was carried in would be confiscated as loot.
     *
     * <p>
     * Written back through {@code setStackInSlot} rather than left mutated in place. The handler's
     * stacks are live, so the mark takes either way, but its setter is inherited straight from
     * {@code ItemStackHandler} and is a plain set followed by a contents-changed notice &mdash; it
     * infers nothing from what was in the slot before, so handing the same stack back costs nothing
     * and the notice is worth having. {@link RiftAccessories#stamp} does the opposite because the
     * container it is walking has a setter that does infer things.
     */
    static void stamp(Player player, ResourceLocation rift) {
        CuriosApi.getCuriosInventory(player).ifPresent(curios -> {
            for (ICurioStacksHandler handler : curios.getCurios().values()) {
                stamp(handler.getStacks(), rift);
                stamp(handler.getCosmeticStacks(), rift);
            }
        });
    }

    private static void stamp(IDynamicStackHandler stacks, ResourceLocation rift) {
        for (int slot = 0; slot < stacks.getSlots(); slot++) {
            ItemStack stack = stacks.getStackInSlot(slot);
            if (RiftLoot.mark(stack, rift)) {
                stacks.setStackInSlot(slot, stack);
            }
        }
    }

    private static void strip(Player player, DamageSource source, List<ItemStack> taken,
                              String identifier, ICurioStacksHandler slots,
                              IDynamicStackHandler stacks, boolean cosmetic) {
        for (int slot = 0; slot < stacks.getSlots(); slot++) {
            ItemStack stack = stacks.getStackInSlot(slot);
            if (stack.isEmpty()) {
                continue;
            }
            ICurio.DropRule rule = ruleFor(player, source, identifier, slots, stacks, slot, cosmetic);
            if (rule == ICurio.DropRule.ALWAYS_KEEP) {
                continue;
            }
            // Emptied either way. A destroyed curio is destroyed here rather than left to be
            // destroyed by the level going, so that a rift death looks the same as any other one.
            stacks.setStackInSlot(slot, ItemStack.EMPTY);
            if (rule != ICurio.DropRule.DESTROY) {
                taken.add(stack);
            }
        }
    }

    /**
     * What Curios would have done with this stack, asked in the same order Curios asks it.
     *
     * <p>
     * The item speaks first, then the slot it is in, and a curio that nobody has an opinion about is
     * checked for the Curse of Vanishing last. That order is read off {@code CuriosEventHandler}
     * rather than guessed, because a rule resolved differently here than there would show up as a
     * curio that behaves one way when you die in a rift and another way when you die outside one.
     *
     * <p>
     * Of the two overloads of {@code getDropRule}, the one without a looting level is the one to call,
     * and not only because the other is deprecated. The two are not alternatives: the modern one's
     * default implementation delegates to the deprecated one passing looting zero, so calling the
     * modern one reaches a curio that overrides either, while calling the deprecated one directly
     * silently misses any curio written against the modern signature. It is also what Curios calls
     * itself, which is the argument that decides it.
     *
     * <p>
     * Whether the player was recently hit by another player is a property of the drop, and this runs
     * before there is a drop &mdash; it has to, because the level the drop would land in is the thing
     * being escaped. It is read back off {@code getKillCredit} instead, which is public and is the
     * same fact: the field behind it is set with that timer and cleared on the tick the timer runs
     * out. Nothing here reads the answer, and nothing in Curios does either &mdash; it is passed for
     * the sake of a curio that wants to scale what it leaves behind by who killed you, and passing a
     * flat {@code false} would quietly lie to one.
     */
    private static ICurio.DropRule ruleFor(Player player, DamageSource source, String identifier,
                                           ICurioStacksHandler slots, IDynamicStackHandler stacks,
                                           int slot, boolean cosmetic) {
        SlotContext context = new SlotContext(identifier, player, slot, cosmetic,
                slots.getRenders().size() > slot && slots.getRenders().get(slot));
        boolean recentlyHit = player.getKillCredit() instanceof Player;

        ICurio.DropRule rule = CuriosApi.getCurio(stacks.getStackInSlot(slot))
                .map(curio -> curio.getDropRule(context, source, recentlyHit))
                .orElse(ICurio.DropRule.DEFAULT);
        if (rule != ICurio.DropRule.DEFAULT) {
            return rule;
        }
        rule = slots.getDropRule();
        if (rule != ICurio.DropRule.DEFAULT) {
            return rule;
        }
        return EnchantmentHelper.has(stacks.getStackInSlot(slot),
                EnchantmentEffectComponents.PREVENT_EQUIPMENT_DROP)
                ? ICurio.DropRule.DESTROY
                : ICurio.DropRule.DEFAULT;
    }
}
