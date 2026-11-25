package com.cyberday1.riftfall.gear;

import com.cyberday1.riftfall.rift.RiftLevels;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.neoforged.fml.ModList;

/**
 * Makes the tier of a rift, and nothing else, decide how good the gear inside it is.
 *
 * <p>
 * Two halves of one answer, both keyed to the tier and both applied here so that neither can run
 * without the other: {@link RiftRarity} rolls the affixes and {@link DefenceFloor} writes the
 * deterministic stats underneath them. ⚠ The class is still named for the first half alone, which is
 * older than the second.
 *
 * <h2>Why every loot table and not the barrel</h2>
 * Gear reaches a player from more than one place &mdash; a barrel share, a mob's drops, anything a
 * room happens to contain &mdash; and every one of them is a loot table. Answering the question once
 * for all of them is the only way there is one answer to "how good is loot in here" rather than one
 * per source that can drift apart.
 *
 * <h2>⚠ Why this is not a global loot modifier</h2>
 * It was, and the reason it is not is worth keeping. Apotheosis converts a drop into an affix item
 * and picks its rarity; our final pass must run after that so its conversion and injections cannot
 * overwrite the rift rarity or leave newly injected gear bare. Riftfall's data <em>does</em> load after Apotheosis' &mdash; that is
 * what the {@code ordering = "AFTER"} in {@code neoforge.mods.toml} buys, and it is what the rarity
 * and affix overrides rely on &mdash; but that is datapack layering, and modifiers do not run in it.
 * {@code LootModifierManager.prepare} builds the layered order into a plain {@link java.util.HashMap}
 * and {@code apply} iterates that, so the running order is hash order over the modifier ids: stable
 * between runs, arbitrary between packs, and different again the moment a pack adds or drops one.
 *
 * <p>
 * So the hook is the return of {@code LootTable#getRandomItems}, which is the single line every loot
 * table passes through and the line immediately after the last modifier has run. Last by construction
 * rather than last by luck.
 */
public final class RiftLootRarity {

    /**
     * Whether {@link RiftRarity} may be called into. Answered once, on first use, so the check is not
     * paid per drop and so that the class naming Apotheosis is never loaded when it is absent.
     */
    private static final class Apotheosis {
        static final boolean LOADED = ModList.get().isLoaded("apotheosis");
    }

    private RiftLootRarity() {
    }

    /** Any level on the rift generator, the shipped {@code riftfall:rift} included (fixed 2026-10-06). */
    static boolean appliesIn(ServerLevel level) {
        return RiftLevels.isRiftWorld(level);
    }

    /** Utility wearables without an affix category are not part of the combat gear economy. */
    static boolean supportsAffixes(ItemStack stack) {
        return !Apotheosis.LOADED || RiftRarity.supports(stack);
    }

    /** Whether the stack carries Apotheosis affix or rarity data. */
    public static boolean affixed(ItemStack stack) {
        return Apotheosis.LOADED && RiftRarity.affixed(stack);
    }

    /** The stack's rarity as an index into {@link RarityLadder#RARITIES}, or -1 when it has none. */
    public static int rarityIndex(ItemStack stack) {
        if (GunGrade.isGun(stack)) {
            GunGrade grade = GunGrade.read(stack);
            return grade == null ? -1 : Math.clamp(grade.rarity(), 0, RarityLadder.RARITIES.length - 1);
        }
        return Apotheosis.LOADED ? RiftRarity.rarityIndex(stack) : -1;
    }

    /** A copy carrying rarity {@code index} and no affixes, for the recipe viewer; a plain copy without Apotheosis. */
    public static ItemStack labelled(ItemStack stack, int index) {
        return Apotheosis.LOADED ? RiftRarity.labelled(stack, index) : stack.copy();
    }

    /** Exact modifier IDs from this stack's installed gems, not a blanket namespace exemption. */
    static java.util.Set<net.minecraft.resources.ResourceLocation> socketModifierIds(ItemStack stack) {
        return Apotheosis.LOADED ? RiftRarity.socketModifierIds(stack) : java.util.Set.of();
    }

    /**
     * Gives everything in {@code loot} this rift's tier, in place, if it was drawn inside one.
     *
     * <p>
     * Two things, and the order between them is the point. The rarity re-roll rebuilds the stack's
     * affixes and may hand back a different stack; {@link DefenceFloor} and {@link WeaponFloor}
     * write a component onto whatever that leaves. Doing the floors second means they are written
     * onto the stack that survives rather than onto one that is about to be replaced.
     *
     * <p>
     * ⚠ Only the re-roll is behind {@code Apotheosis.LOADED}. The floor is Riftfall's own numbers on
     * Riftfall's own armour and owes nothing to that mod being installed &mdash; gating it here as
     * well would mean a pack that drops Apotheosis silently gets modded armour back at its own mod's
     * stats, which is the one outcome the whole gear system exists to prevent.
     */
    public static void apply(ObjectArrayList<ItemStack> loot, LootContext context) {
        ServerLevel level = context.getLevel();
        boolean rift = appliesIn(level);
        if (!rift && !com.cyberday1.riftfall.tool.BarrelLootBonuses.active()) {
            return;
        }
        applyAt(loot, context, rift ? RiftLevels.tier(level) : 1);
    }

    /** {@link #apply} at a given tier, without the rift gate: the probe pins the tier with it. */
    public static void applyAt(ObjectArrayList<ItemStack> loot, LootContext context, int tier) {
        for (int slot = 0; slot < loot.size(); slot++) {
            ItemStack stack = loot.get(slot);
            // Some custom weapons expose no usable attack attribute until our base stats exist.
            // Apotheosis classifies those by their effective attributes, so stamp BEFORE classifying.
            stack = WeaponFloor.stamp(DefenceFloor.stamp(stack, tier), tier);
            if (Apotheosis.LOADED) {
                stack = RiftRarity.reroll(stack, context, tier);
            }
            // Both floors are offered every stack and each hands back anything that is not its own,
            // so the pair is exhaustive without either having to know what the other claims.
            loot.set(slot, DonorProgression.earned(WeaponFloor.stamp(DefenceFloor.stamp(stack, tier), tier), tier));
            // A TaCZ gun is no Apotheosis gear and no floor row; it takes its own grade.
            if (GunGrade.isGun(stack)) {
                GunGrade.stamp(stack, tier, RarityLadder.index(tier, context.getRandom().nextInt(RarityLadder.TOTAL),
                        com.cyberday1.riftfall.tool.BarrelLootBonuses.rarity()));
                socketGun(stack, context.getRandom());
            }
        }
    }
    /**
     * A graded rift gun's sockets, rolled from its grade's rarity. Starter guns go
     * through {@link #starter} instead and get none. Answers the count; 0 without Apotheosis or a grade.
     */
    public static int socketGun(ItemStack stack, net.minecraft.util.RandomSource random) {
        GunGrade grade = GunGrade.read(stack);
        return Apotheosis.LOADED && grade != null ? RiftRarity.gunSockets(stack, grade.rarity(), random) : 0;
    }

    /** The tier a starter kit is stamped at: the first rift's, and no better. */
    public static final int STARTER_TIER = 1;

    /** The rarity a starter kit rolls at: the ladder's lowest rung. */
    public static final String STARTER_RARITY = RarityLadder.RARITIES[0];

    /**
     * Gives one starter-kit stack what a tier-1 rift would have given it, through the same two steps as
     * {@link #apply}: a rarity first (fixed at {@link #STARTER_RARITY} rather than drawn, and only with
     * Apotheosis present), then both floors. Anything that is not the rifts' gear comes back from the
     * floors untouched, exactly as it would from a barrel.
     */
    public static ItemStack starter(ItemStack stack, ServerPlayer player) {
        if (stack.isEmpty()) {
            return stack;
        }
        return GunGrade.stamp(fixedAt(stack, player, STARTER_TIER, STARTER_RARITY), STARTER_TIER, 0);
    }

    /** One named rarity (with Apotheosis) then both floors, at {@code tier}: the starter's path, open to the probe. */
    public static ItemStack fixedAt(ItemStack stack, ServerPlayer player, int tier, String rarity) {
        if (stack.isEmpty()) {
            return stack;
        }
        stack = WeaponFloor.stamp(DefenceFloor.stamp(stack, tier), tier);
        if (Apotheosis.LOADED) {
            stack = RiftRarity.fixed(stack, player, tier, rarity);
        }
        return DonorProgression.earned(WeaponFloor.stamp(DefenceFloor.stamp(stack, tier), tier), tier);
    }
}
