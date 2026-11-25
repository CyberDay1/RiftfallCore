package com.cyberday1.riftfall.gear;

import dev.shadowsoffire.apotheosis.affix.AffixHelper;
import dev.shadowsoffire.apotheosis.loot.LootController;
import dev.shadowsoffire.apotheosis.loot.LootCategory;
import dev.shadowsoffire.apotheosis.loot.LootRarity;
import dev.shadowsoffire.apotheosis.loot.RarityRegistry;
import dev.shadowsoffire.apotheosis.tiers.GenContext;
import dev.shadowsoffire.apotheosis.socket.SocketHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;

import com.cyberday1.riftfall.init.RiftfallDataComponents;

/**
 * The one place in Riftfall that names an Apotheosis type.
 *
 * <p>
 * Reached only from {@link RiftLootRarity}, and only from behind its {@code ModList} check, on
 * the same terms as {@code RiftCurios} and {@code RiftAccessories}: without the mod installed this
 * class is never loaded, so its imports never have to resolve.
 *
 * Every eligible gear drop receives affixes. Apotheosis' ordinary conversion chance is for
 * general world loot; applying it to our gear rewards left most of them bare.
 *
 * <p>
 * A re-roll is a whole fresh roll rather than an edit, because rarity is not a label on the affixes:
 * it decides how many are rolled and what magnitude each may reach.
 * {@code LootController.createLootItem} clears the affix component before it does anything else, so
 * handing it an already-affixed stack is a supported thing to do rather than a trick, and the second
 * roll leaves nothing of the first.
 */
final class RiftRarity {

    private RiftRarity() {
    }

    /** A gun has a loot category for its sockets, but its grade is its rarity: no Apotheosis roll. */
    static boolean supports(ItemStack stack) {
        return !stack.isEmpty() && !GunGrade.isGun(stack) && !LootCategory.forItem(stack).isNone();
    }

    /**
     * A graded gun's sockets from its rarity's socket rule for its category, as Apotheosis rolls them for other
     * gear (common and uncommon 1, rare 1-2, epic 2-3, mythic 3-4 in Riftfall's rarities). Answers the count.
     */
    static int gunSockets(ItemStack stack, int rarityIndex, net.minecraft.util.RandomSource random) {
        LootCategory category = LootCategory.forItem(stack);
        LootRarity rarity = RarityRegistry.INSTANCE.getValue(ResourceLocation.parse(
                RarityLadder.RARITIES[Math.clamp(rarityIndex, 0, RarityLadder.RARITIES.length - 1)]));
        if (category.isNone() || rarity == null) {
            return 0;
        }
        int sockets = 0;
        for (dev.shadowsoffire.apotheosis.loot.LootRule rule : rarity.getRules(category)) {
            if (rule instanceof dev.shadowsoffire.apotheosis.loot.LootRule.SocketLootRule socket) {
                sockets = socket.min() + random.nextInt(Math.max(1, socket.max() - socket.min() + 1));
            }
        }
        dev.shadowsoffire.apotheosis.socket.SocketHelper.setSockets(stack, sockets);
        return sockets;
    }

    static boolean affixed(ItemStack stack) {
        return !stack.isEmpty() && (AffixHelper.hasAffixes(stack) || AffixHelper.getRarity(stack).isBound());
    }

    /** Where the stack's rarity sits on {@link RarityLadder#RARITIES}, or -1 for none or one off the ladder. */
    static int rarityIndex(ItemStack stack) {
        var rarity = AffixHelper.getRarity(stack);
        if (stack.isEmpty() || !rarity.isBound()) {
            return -1;
        }
        return java.util.Arrays.asList(RarityLadder.RARITIES).indexOf(rarity.getId().toString());
    }

    static ItemStack labelled(ItemStack stack, int index) {
        ItemStack copy = stack.copy();
        if (index < 0 || index >= RarityLadder.RARITIES.length) {
            return copy;
        }
        LootRarity rarity = RarityRegistry.INSTANCE.getValue(ResourceLocation.parse(RarityLadder.RARITIES[index]));
        if (rarity != null) {
            AffixHelper.setRarity(copy, rarity);
        }
        return copy;
    }

    static java.util.Set<ResourceLocation> socketModifierIds(ItemStack stack) {
        java.util.Set<ResourceLocation> ids = new java.util.HashSet<>();
        SocketHelper.getGems(stack).stream().filter(gem -> gem.isValid())
                .forEach(gem -> gem.skipModifierIds(ids::add));
        return ids;
    }

    /**
     * Re-rolls one stack on this rift's row of the ladder, or returns it untouched.
     *
     * <p>
     * Non-gear, drops without a responsible player, and missing datapack rarities are left alone.
     *
     * <p>
     * ⚠ Nobody responsible is the one that is not obvious. {@code GenContext.forLoot} returns
     * {@code null} rather than a default when the loot context names no player at all &mdash; it
     * reads the world tier off one &mdash; and every Apotheosis loot modifier quietly declines that
     * case. Declining it too keeps the two agreeing, and is also the difference between this and a
     * server-thread NPE on a drop no player caused.
     *
     * <p>
     * The roll comes off the loot context's own random source, so a barrel share that is seeded to be
     * the same share every time it is asked for stays that way.
     */
    static ItemStack reroll(ItemStack stack, LootContext context, int riftTier) {
        if (!supports(stack)) {
            return stack;
        }
        GenContext gen = GenContext.forLoot(context);
        if (gen == null) {
            return stack;
        }
        int roll = gen.rand().nextInt(RarityLadder.TOTAL);
        LootRarity rarity = RarityRegistry.INSTANCE.getValue(
                ResourceLocation.parse(RarityLadder.RARITIES[RarityLadder.index(riftTier, roll,
                        com.cyberday1.riftfall.tool.BarrelLootBonuses.rarity())]));
        if (rarity == null) {
            return stack;
        }
        ItemStack rolled = LootController.createLootItem(stack, rarity, gen);
        // The tier the affixes rolled at, for AffixTiers to scale the ×T ones by. On gear the
        // component means "rolled at tier T" the way it means "forged at tier T" on a key.
        rolled.set(RiftfallDataComponents.RIFT_TIER.get(), riftTier);
        return rolled;
    }
    /**
     * Gives a stack one named rarity outright, rolled for a player rather than a loot table. The starter
     * kits' half of {@link #reroll}: same affix pool, same {@code RIFT_TIER} stamp, but the rarity is
     * fixed rather than drawn, so a kit cannot roll above what it was meant to be. An item Apotheosis
     * has no category for comes back untouched, which is what {@code createLootItem} does with it.
     */
    static ItemStack fixed(ItemStack stack, Player player, int riftTier, String rarityId) {
        if (GunGrade.isGun(stack)) {
            return stack;
        }
        LootRarity rarity = RarityRegistry.INSTANCE.getValue(ResourceLocation.parse(rarityId));
        if (rarity == null) {
            return stack;
        }
        ItemStack rolled = LootController.createLootItem(stack, rarity, GenContext.forPlayer(player));
        if (AffixHelper.getRarity(rolled).isBound()) {
            rolled.set(RiftfallDataComponents.RIFT_TIER.get(), riftTier);
        }
        return rolled;
    }
}
