package com.cyberday1.riftfall.gear;

import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import org.jetbrains.annotations.Nullable;

import com.cyberday1.riftfall.RiftfallCore;

/**
 * The defence a rift's armour wears, in place of the numbers its own mod gave it.
 *
 * <h2>Writing the stats is the same act as blanking the old ones</h2>
 * There is no separate strip step and there must not be one.
 * {@code IItemStackExtension#getAttributeModifiers} reads
 * {@code DataComponents.ATTRIBUTE_MODIFIERS} and only falls back to the item's own
 * {@code getDefaultAttributeModifiers} when that component is <em>empty</em>, so a non-empty
 * component replaces the donor mod's stats outright. The corollary is the trap: an empty list is not
 * "no stats", it is "ask the mod", and a piece whose floor came out empty would silently keep
 * everything this is meant to take away. Every row below therefore writes at least armour.
 *
 * <h2>Why the affixes survive it</h2>
 * Apotheosis does not put affix stats in this component. {@code AttributeEvents} builds its own
 * {@code StackAttributeModifiersEvent} out of {@code ItemAttributeModifierEvent.build()}, which is
 * the event NeoForge fires <em>after</em> the component has been read &mdash; so the component is the
 * base the affix layer is added to, not a rival to it. Blanking a piece therefore costs it its mod's
 * numbers and none of its rift ones.
 *
 * <p>
 * ⚠ The same event is what {@link com.cyberday1.riftfall.init.RiftfallTags#GEAR_DENIED} exists for.
 * A mod that subscribes to it and re-applies its own attributes wins against anything written here,
 * which is not stoppable by data, so those items are kept out of the pool rather than fought.
 *
 * <h2>Tier, not rarity</h2>
 * The floor is the deterministic half of a rift item and rarity is the rolled half. Keying it to the
 * tier of the rift it dropped in means two pieces out of the same dungeon carry the same defence
 * however differently they rolled, which is what makes a tier legible to a player: the rarity says
 * how lucky you got, the floor says where you were.
 */
public final class DefenceFloor {

    private static final ResourceLocation ARMOUR_ID = RiftfallCore.id("defence_floor/armor");
    private static final ResourceLocation TOUGHNESS_ID = RiftfallCore.id("defence_floor/armor_toughness");

    /** Every per-slot armour id starts with this; toughness ids do not. */
    public static final String ARMOUR_PREFIX = ARMOUR_ID + "/";

    private DefenceFloor() {
    }

    /**
     * One slot's row of the floor: a constant plus a per-tier term, for each of the two stats.
     *
     * <p>
     * Armour and toughness have to keep pace with a tier-50 mob or the floor stops being a floor, so
     * both carry a per-tier term. Mitigation is deliberately not here: it is rolled and earned, and
     * {@code affixes/flat/resistance.json} is the only thing that grants it.
     */
    record Row(double armour, double armourPerTier,
               double toughness, double toughnessPerTier) {

        double armourAt(int tier) {
            return armour + armourPerTier * tier;
        }

        double toughnessAt(int tier) {
            return toughness + toughnessPerTier * tier;
        }
    }

    /**
     * The table, and the only place these numbers are written down in code.
     *
     * <p>
     * A full tier-1 set is 20.5 armour, a shade over diamond, so the worst thing a rift hands out is
     * still a reason to have walked in. Head and feet share a row because the design gives them the
     * same numbers, and saying so once is the difference between a shared row and two rows that can
     * drift apart.
     */
    static @Nullable Row row(GearPool.Slot slot) {
        return switch (slot) {
            case HEAD, FEET -> new Row(3.0, 0.10, 1.0, 0.02);
            case CHEST -> new Row(8.0, 0.16, 2.0, 0.06);
            case LEGS -> new Row(6.0, 0.14, 1.0, 0.04);
            default -> null;
        };
    }

    /**
     * Gives one stack this rift's defence, or hands it back untouched.
     *
     * <p>
     * Untouched is the ordinary case and covers everything that is not armour the rifts own: the
     * food and blocks in the same draw, vanilla armour another mod's loot modifier injected, and the
     * pieces either gear tag keeps out of the pool. None of those is a rift item, and stamping one
     * would be this taking over an item the rifts never claimed.
     */
    public static ItemStack stamp(ItemStack stack, int riftTier) {
        if (stack.isEmpty()) {
            return stack;
        }
        GearPool.Slot slot = GearPool.armourSlot(stack.getItem());
        Row row = slot == null ? null : row(slot);
        if (row == null) {
            return stack;
        }
        ItemAttributeModifiers.Builder floor = ItemAttributeModifiers.builder();
        EquipmentSlotGroup group = group(slot);
        floor.add(Attributes.ARMOR, add(perSlot(ARMOUR_ID, group), row.armourAt(riftTier)), group);
        floor.add(Attributes.ARMOR_TOUGHNESS,
                add(perSlot(TOUGHNESS_ID, group), row.toughnessAt(riftTier)), group);
        stack.set(DataComponents.ATTRIBUTE_MODIFIERS, floor.build());
        return stack;
    }

    /**
     * The id made unique to one worn slot, the {@code TrinketCategory#keyPerSlot} convention.
     * One shared id meant a full set counted one piece; same slot, same id, so unequip is exact.
     */
    static ResourceLocation perSlot(ResourceLocation id, EquipmentSlotGroup group) {
        return id.withSuffix("/" + group.getSerializedName());
    }

    private static AttributeModifier add(ResourceLocation id, double amount) {
        return new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_VALUE);
    }

    /**
     * The slot group the stats apply in.
     *
     * <p>
     * The worn slot rather than {@link EquipmentSlotGroup#ARMOR}, so a helmet held in a hand is worth
     * nothing. Riftfall's own numbers should not be a reason to carry armour you are not wearing.
     */
    static EquipmentSlotGroup group(GearPool.Slot slot) {
        return switch (slot) {
            case HEAD -> EquipmentSlotGroup.HEAD;
            case CHEST -> EquipmentSlotGroup.CHEST;
            case LEGS -> EquipmentSlotGroup.LEGS;
            default -> EquipmentSlotGroup.FEET;
        };
    }
}
