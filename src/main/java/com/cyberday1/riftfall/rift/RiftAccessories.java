package com.cyberday1.riftfall.rift;

import io.wispforest.accessories.api.AccessoriesCapability;
import io.wispforest.accessories.api.AccessoriesContainer;
import io.wispforest.accessories.impl.AccessoriesEventHandler;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.Collection;
import java.util.List;

/**
 * The other half of a player's equipment that is not in their inventory.
 *
 * <p>
 * Accessories is a separate mod from Curios and not a front end for it: it keeps its own capability,
 * its own containers and its own death rules, and the one class in it that mentions Curios is a mixin
 * about not decoding the same tag twice. So being told about Curios says nothing about Accessories,
 * and the ring that {@link RiftCurios} rescues and the ring that this one rescues are in different
 * places. In this pack it is the one that matters more of the two: Spell Engine requires it, and the
 * RPG series' necklaces and rings sit in its slots.
 *
 * <h2>Its own sweep, called early, rather than a copy of its own sweep</h2>
 * {@code AccessoriesEventHandler.onDeath} is what empties the slots and decides what should have
 * dropped, and it is called here directly instead of being reimplemented against the API. That is a
 * different choice from {@link RiftCurios}, which mirrors Curios' rule order by hand, and it is the
 * better one wherever it is available: the rules it applies are not a short list. An accessory speaks
 * first, then a nest accessory speaks for each item nested inside it, then {@code OnDropCallback} gets
 * to substitute a rule, then the slot type's own rule, then the Curse of Vanishing. Written out again
 * here, all of that would be right on the day it was written and would drift silently afterwards,
 * which is the failure this mod already has too much respect for.
 *
 * <p>
 * It also means Accessories' own {@code keepAccessoryInventory} gamerule is honoured without this
 * class knowing the rule exists, which is the same argument {@link RiftDeaths} makes for reading
 * {@code keepInventory} back rather than inventing a policy.
 *
 * <h2>Early enough, for the same reason as everything else here</h2>
 * Accessories drops at {@code LivingDropsEvent}, which fires from inside {@code dropAllDeathLoot} and
 * so is strictly later than the {@code LivingDeathEvent} this is reached from. By the time its own
 * listener runs the containers are already empty, it finds nothing to drop, and the items are on file
 * in {@link RiftClaims} instead of on the floor of a level that is about to be deleted. The one visible
 * consequence is that {@code OnDeathCallback} is invoked a second time with an empty list, which no
 * listener can mistake for a second death.
 *
 * <h2>Two ways this can be wrong, and both of them are loud</h2>
 * {@code onDeath} lives in {@code impl} rather than {@code api}, so it carries no compatibility
 * promise. If it is renamed the build fails; if the mod is updated underneath a built jar the death
 * throws where it is called. Neither is quiet, which is the property that decides it: the alternative
 * is a hand-written copy of the rules that would keep working and quietly stop matching.
 *
 * <p>
 * A veto is the other one, and it is upstream's behaviour rather than ours. If an
 * {@code OnDeathCallback} listener refuses the drop, {@code onDeath} returns null having already
 * emptied the containers, and the stacks are gone. That is what happens outside a rift too, so a rift
 * death is not a special case, and nothing is invented here to paper over it.
 */
final class RiftAccessories {

    private RiftAccessories() {
    }

    /**
     * Empties a player's accessory slots into {@code taken}, leaving behind whatever is meant to stay.
     *
     * <p>
     * Everything the mod decided should have dropped is claimed, and everything it decided should be
     * kept or destroyed has already been kept or destroyed by the time this returns.
     */
    static void strip(Player player, DamageSource source, List<ItemStack> taken) {
        Collection<ItemStack> dropped = AccessoriesEventHandler.onDeath(player, source);
        if (dropped != null) {
            taken.addAll(dropped);
        }
    }

    /**
     * Marks everything in a player's accessory slots on the way into a rift, for the reason
     * {@link RiftCurios#stamp} gives: an unmarked ring that was carried in is confiscated as loot.
     *
     * <p>
     * This one is a hand-written walk rather than a call into the mod, which is the opposite of the
     * choice {@link #strip} makes and for the same reason. Emptying the slots is a pile of rules about
     * what should happen and upstream owns them; reading every slot is not a rule at all, and there is
     * no method here to borrow that does it &mdash; {@code getAllEquipped} returns references built for
     * a different question. A walk over the containers is the whole of it either way.
     *
     * <p>
     * Marked in place rather than written back through {@code setItem}, which is the opposite of what
     * {@link RiftCurios#stamp} does and for a reason that is in the setter rather than in the rule.
     * This one cancels the container's per-slot subscription to the old stack and takes out a new one
     * on whatever it is handed, so putting the same stack back is a subscription torn down and rebuilt
     * to describe a slot whose contents never changed. The containers hand out their live stacks, so
     * the mark takes without it, and {@code markChanged} says the one thing that is actually true.
     */
    static void stamp(Player player, ResourceLocation rift) {
        AccessoriesCapability capability = AccessoriesCapability.get(player);
        if (capability == null) {
            return;
        }
        for (AccessoriesContainer container : capability.getContainers().values()) {
            boolean marked = stamp(container.getAccessories(), rift);
            marked |= stamp(container.getCosmeticAccessories(), rift);
            if (marked) {
                container.markChanged();
            }
        }
    }

    private static boolean stamp(Container slots, ResourceLocation rift) {
        boolean marked = false;
        for (int slot = 0; slot < slots.getContainerSize(); slot++) {
            marked |= RiftLoot.mark(slots.getItem(slot), rift);
        }
        return marked;
    }
}
