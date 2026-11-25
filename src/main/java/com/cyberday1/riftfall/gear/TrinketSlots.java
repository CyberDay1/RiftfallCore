package com.cyberday1.riftfall.gear;

import java.util.List;
import java.util.function.Predicate;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import com.cyberday1.riftfall.init.RiftfallTags;

/**
 * Which Curios slots the Tr column covers, and what counts as a trinket or a necklace.
 *
 * <p>
 * Split out of {@link TrinketCategory} so it names no other mod's types: that class can only be
 * loaded behind {@link RiftTrinkets}'s three-mod check, and this rule is the half worth testing.
 *
 * <h2>⛔ Three categories, and they must not overlap</h2>
 * {@code LootCategory.forItem} walks the registry in priority order and returns the <em>first</em>
 * validator that passes, so an item has exactly one loot category. Two categories that could both
 * claim an amulet would silently hand it to whichever sorted first &mdash; and which that is is not
 * something a datapack can see. So the split is by construction: {@link #NECKLACE_SLOTS} is the one
 * slot, {@link #TRINKET_SLOTS} is the other six, and {@link #isTrinket} refuses anything the
 * necklace rule would take. {@link #isRelic} takes only silenced relics in {@link #RELIC_SLOTS} that
 * fit none of the seven. Order then cannot matter, because only one of them can ever answer yes.
 */
public final class TrinketSlots {

    /**
     * The Curios slot types that carry stats rather than utility. Every other slot the pack
     * registers is deliberately out: {@code spellbook} is already the F column
     * ({@code irons_apothic:spellbook}); {@code head}/{@code hands}/{@code feet}/{@code body}/
     * {@code back} are Curios' mirrors of the worn slots H/C/L/B already price, and {@code back} is
     * where the backpacks live; {@code charm} is totems and magnets rather than worn stats; and
     * {@code curio}, {@code crafting_on_a_stick}, {@code psd}, {@code deep_learner},
     * {@code adv_pattern_encoder} and {@code refinedstorage_curios_integration} are the storage and
     * terminal slots {@link RiftfallTags#NOT_GEAR} already names item by item.
     */
    static final List<String> SLOTS =
            List.of("ring", "rings", "necklace", "bracelet", "belt", "waist", "talisman");

    /**
     * The size-1 half of {@link #SLOTS}, and the reason it is split off at all.
     *
     * <p>
     * An affix priced for one piece has to land somewhere a player can only wear one of.
     * {@code ring}, {@code rings} and {@code charm} are size 2 in the pack, {@code belt} has four
     * eligible items in it, and {@code back} is backpacks; {@code necklace} is the only slot that is
     * both size 1 and well populated (15 items, none of them in {@code not_gear}).
     */
    static final List<String> NECKLACE_SLOTS = List.of("necklace");

    /**
     * The relic places (ruled 2026-11-20): the worn-mirror slots and the spell slot, for silenced relics only.
     * Feet, hands and charm are 2 wide; back and spell_trinket 1. Belt, ring and necklace relics stay trinkets.
     */
    public static final List<String> RELIC_SLOTS = List.of("feet", "hands", "back", "charm", "spell_trinket");

    static final List<TagKey<Item>> RELIC_TAGS = tags(RELIC_SLOTS);

    /** {@link #SLOTS} less {@link #NECKLACE_SLOTS}. Disjoint by construction, not by convention. */
    static final List<String> TRINKET_SLOTS = SLOTS.stream()
            .filter(slot -> !NECKLACE_SLOTS.contains(slot))
            .toList();

    /** {@code curios:<slot>} is how Curios itself decides what fits where; same source of truth. */
    static final List<TagKey<Item>> SLOT_TAGS = tags(SLOTS);

    static final List<TagKey<Item>> NECKLACE_TAGS = tags(NECKLACE_SLOTS);

    static final List<TagKey<Item>> TRINKET_TAGS = tags(TRINKET_SLOTS);

    /**
     * What {@code Affix#makeUniqueId} puts in every id it mints for the {@code riftfall:trinket}
     * group: affix, this, salt. Nothing in it names the slot, so six worn trinkets share one id.
     */
    static final String TRINKET_MINT = mint("trinket");

    /** The same mint for {@code riftfall:relic}: feet, hands and charm are 2 wide, so it collides the same way. */
    static final String RELIC_MINT = mint("relic");

    /** The groups re-keyed per slot. The necklace is one place and keeps its ids. */
    static final List<String> PER_SLOT_MINTS = List.of(TRINKET_MINT, RELIC_MINT);

    private TrinketSlots() {
    }

    /**
     * The affix id, made unique to one Curios slot and index. Same slot, same id, so Curios'
     * unequip still removes exactly what its equip added. Anything else passes through unchanged.
     */
    static ResourceLocation perSlot(ResourceLocation id, String slot, int index) {
        for (String mint : PER_SLOT_MINTS) {
            if (id.getPath().contains(mint)) {
                return ResourceLocation.fromNamespaceAndPath(id.getNamespace(),
                        id.getPath() + "/" + slot + "_" + index);
            }
        }
        return id;
    }

    private static String mint(String group) {
        return "_modifier_" + ResourceLocation.fromNamespaceAndPath("riftfall", group).toShortLanguageKey() + "_";
    }

    /**
     * A trinket is anything that fits one of the six non-necklace jewellery slots and that Riftfall
     * has not already disclaimed. Both tags are reused rather than re-decided: {@code not_gear}
     * means "was never gear" and {@code gear_denied} means "is gear, but its own mod runs the
     * ladder", and a trinket is neither.
     */
    static boolean isTrinket(ItemStack stack) {
        return isTrinket(stack::is);
    }

    /** Split from the stack so a test can hand it a truth table without a bound tag registry. */
    static boolean isTrinket(Predicate<TagKey<Item>> tagged) {
        // An item in both necklace and ring is a necklace, so its affixes apply only at the neck.
        // Accepted: slot-exclusivity is the point, and it is the half a player can be told.
        return !slotted(tagged, NECKLACE_TAGS) && slotted(tagged, TRINKET_TAGS) && owned(tagged);
    }

    static boolean isNecklace(ItemStack stack) {
        return isNecklace(stack::is);
    }

    static boolean isNecklace(Predicate<TagKey<Item>> tagged) {
        return slotted(tagged, NECKLACE_TAGS) && owned(tagged);
    }

    static boolean isRelic(ItemStack stack) {
        return isRelic(stack::is);
    }

    /** A silenced relic in a relic slot and in none of the seven: the third category, disjoint from both. */
    static boolean isRelic(Predicate<TagKey<Item>> tagged) {
        return tagged.test(RiftfallTags.CURIO_SILENCED) && slotted(tagged, RELIC_TAGS)
                && !slotted(tagged, SLOT_TAGS) && owned(tagged);
    }

    private static boolean slotted(Predicate<TagKey<Item>> tagged, List<TagKey<Item>> slots) {
        for (TagKey<Item> tag : slots) {
            if (tagged.test(tag)) {
                return true;
            }
        }
        return false;
    }

    private static boolean owned(Predicate<TagKey<Item>> tagged) {
        return !tagged.test(RiftfallTags.NOT_GEAR) && !tagged.test(RiftfallTags.GEAR_DENIED);
    }

    private static List<TagKey<Item>> tags(List<String> slots) {
        return slots.stream()
                .map(slot -> TagKey.create(Registries.ITEM,
                        ResourceLocation.fromNamespaceAndPath("curios", slot)))
                .toList();
    }
}
