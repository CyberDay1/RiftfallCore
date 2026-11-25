package com.cyberday1.riftfall.gear;

import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * Armour a rift handed you is armour, and nothing else.
 *
 * <h2>The rule</h2>
 * A piece of modded armour in an armour slot does not get its per-tick turn. Its defence, its
 * enchantments and the affixes rolled onto it all still work, because none of those runs from the
 * tick; what stops is the mod's own idea of what wearing it should do — the potion effect it grants,
 * the flight, the step assist, the aura.
 *
 * <h2>Why all of them and not a list</h2>
 * Fifty-three armour classes in the pack tick. Ten name {@code MobEffectInstance} or {@code addEffect}
 * in their own constant pool, and it would be convenient if those ten were the answer. They are not:
 * born_in_chaos is MCreator-generated, so {@code DarkMetalArmorItem$Chestplate#inventoryTick} calls
 * {@code DarkMetalArmorSobytiieTaktovShliemaProcedure} and names no effect API at all. The ten are a
 * floor, and there is no static test that separates a tick which grants an effect from one that does
 * bookkeeping. So the rule is uniform, and uniform is also the rule a player can learn.
 *
 * <h2>What decides it is {@link GearPool#drawable}, which is the same question again</h2>
 * If a rift can drop it, it is silenced; if its own mod keeps it, it keeps its behaviour too. So
 * MekaSuit, JustDireThings, Voidscape and AdvancedAE's quantum set are untouched, because they are
 * already named in {@code riftfall:gear_denied} and are their mods' own progression. One tag edit
 * still moves a piece in all three directions at once: loot, recipe, and now tick.
 */
public final class WornGearBan {

    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Armour is the four slots between the backpack and the offhand.
     *
     * <p>
     * NeoForge patched {@code Inventory#tick} to pass the global index rather than the
     * per-compartment one, and the compartments are items(36), armor(4), offhand(1) in that order.
     */
    private static final int FIRST_ARMOUR = Inventory.INVENTORY_SIZE;
    private static final int AFTER_ARMOUR = Inventory.SLOT_OFFHAND;

    /** Silenced at least once, so the log names each piece once rather than twenty times a second. */
    private static final Set<Item> seen = Collections.synchronizedSet(
            Collections.newSetFromMap(new IdentityHashMap<>()));

    private WornGearBan() {
    }

    /** Whether this stack, in this slot, should be denied its tick. */
    public static boolean silenced(ItemStack stack, int slot) {
        if (slot < FIRST_ARMOUR || slot >= AFTER_ARMOUR || stack.isEmpty()) {
            return false;
        }
        Item item = stack.getItem();
        if (!GearPool.drawable(item)) {
            return false;
        }
        if (seen.add(item)) {
            LOGGER.debug("Worn gear ban: {} will not tick while worn.", BuiltInRegistries.ITEM.getKey(item));
        }
        return true;
    }
}
