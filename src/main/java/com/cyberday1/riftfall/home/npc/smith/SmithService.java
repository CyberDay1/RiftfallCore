package com.cyberday1.riftfall.home.npc.smith;

import com.cyberday1.riftfall.RiftfallCore;
import com.cyberday1.riftfall.gear.DefenceFloor;
import com.cyberday1.riftfall.gear.GearPool;
import com.cyberday1.riftfall.gear.GunGrade;
import com.cyberday1.riftfall.gear.RarityLadder;
import com.cyberday1.riftfall.gear.StarterKits;
import com.cyberday1.riftfall.gear.WeaponFloor;
import com.cyberday1.riftfall.home.npc.GearMutation;
import com.cyberday1.riftfall.home.npc.ResidentRank;
import com.cyberday1.riftfall.home.npc.ResidentServiceMenu;
import com.cyberday1.riftfall.home.npc.ShardWallet;
import com.cyberday1.riftfall.home.npc.jeweler.JewelerGems;
import com.cyberday1.riftfall.init.RiftfallDataComponents;
import com.cyberday1.riftfall.init.RiftfallItems;
import com.cyberday1.riftfall.init.RiftfallTags;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * The Blacksmith's re-temper and salvage over the menu's slots. Re-temper raises {@code riftfall:rift_tier} by one and
 * restamps the floors; gems, affixes and rarity stay. A rift gun's tier is its {@code riftfall:gun_grade}, so re-temper
 * steps that instead, and salvage reads the grade's rarity. Salvage turns rift gear into its rarity material and tempering
 * stock, the one source of rarity materials since the salvaging table went (ruling 14). Starter-marked items are refused
 * (B2), socketed items at salvage too (B3: unset at the Jeweler first), boss uniques always (they are the Keeper's).
 */
public final class SmithService {
    public static final int GEAR = 0, STOCK = 1, SHARDS = 2, OUT_MATERIAL = 3, OUT_STOCK = 4, SLOTS = 5;
    public static final int RETEMPER = 0, SALVAGE = 1;
    /** Tempering orders are gone (orders never hold run gear); kept so orders already saved still collect. */
    public static final ResourceLocation RETEMPER_EDIT = RiftfallCore.id("blacksmith/retemper");

    public enum Result {
        READY, NO_ITEM, NOT_SINGLE, BANNED, LOOTING_PICK, NO_TIER, STARTER, UNIQUE, SOCKETED, RANK, BAND, CAP,
        MISSING_STOCK, MISSING_SHARDS, OUTPUT_FULL, REFUSED, CHANGED, RETEMPERED, SALVAGED;
        public String key() { return SmithLang.MESSAGE + name().toLowerCase(Locale.ROOT); }
    }

    /** What the service needs that is not in the slots. */
    public record Context(ResidentRank rank, Optional<String> path, boolean prospector, int playerCap, JewelerGems gems) {}

    public record Quote(int selection, Result result, SmithRules.Kind kind, int tier, int steps, int band, int playerCap,
                        int shards, int stock, ItemStack material, int stockOut, int funds,
                        ItemStack item, ItemStack snapshot) implements ResidentServiceMenu.Quote {
        @Override public boolean ready() { return result == Result.READY; }
        @Override public Object terms() {
            return Arrays.asList(selection, result, kind, tier, steps, band, playerCap, shards, stock,
                    BuiltInRegistries.ITEM.getKey(material.getItem()), material.getCount(), stockOut, funds);
        }
    }

    /** Re-temper edits only these; everything else on the item is asserted unchanged by {@link GearMutation}. */
    public static final GearMutation.Policy RETEMPER_POLICY = GearMutation.Policy.of(RiftfallDataComponents.RIFT_TIER.get(),
            RiftfallDataComponents.RETEMPERED.get(), DataComponents.ATTRIBUTE_MODIFIERS, RiftfallDataComponents.GUN_GRADE.get());

    private SmithService() {}

    public static boolean accepts(int slot, ItemStack stack) {
        return switch (slot) {
            case GEAR -> !ShardWallet.isShard(stack) && !stack.is(RiftfallItems.TEMPERING_STOCK.get());
            case STOCK -> stack.is(RiftfallItems.TEMPERING_STOCK.get());
            case SHARDS -> ShardWallet.isShard(stack);
            default -> false;
        };
    }

    public static int maxStack(int slot) { return slot == GEAR ? 1 : 64; }

    public static boolean starter(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getBoolean(StarterKits.MARKER);
    }

    /** Weapon or armour by the floors' own buckets, a graded gun a weapon; anything else with a rift tier is a trinket. */
    public static SmithRules.Kind kind(ItemStack stack) {
        if (GunGrade.read(stack) != null || GearPool.weaponSlot(stack.getItem()) != null) return SmithRules.Kind.WEAPON;
        if (GearPool.armourSlot(stack.getItem()) != null) return SmithRules.Kind.ARMOUR;
        return SmithRules.Kind.TRINKET;
    }

    /** {@code riftfall:rift_tier}, or a graded gun's tier; 0 for neither (an ungraded gun included). */
    public static int tier(ItemStack stack) {
        var grade = GunGrade.read(stack);
        return grade != null ? grade.tier() : stack.getOrDefault(RiftfallDataComponents.RIFT_TIER.get(), 0);
    }
    public static int steps(ItemStack stack) { return stack.getOrDefault(RiftfallDataComponents.RETEMPERED.get(), 0); }

    /** The refusals every Blacksmith service shares. */
    static Result refusal(ItemStack item) {
        if (item.isEmpty()) return Result.NO_ITEM;
        var check = GearMutation.check(item, RETEMPER_POLICY);
        if (check == GearMutation.Result.NOT_SINGLE) return Result.NOT_SINGLE;
        if (check == GearMutation.Result.BANNED) return Result.BANNED;
        if (check == GearMutation.Result.LOOTING_PICK) return Result.LOOTING_PICK;
        if (tier(item) <= 0) return Result.NO_TIER;
        if (starter(item)) return Result.STARTER;
        if (item.is(RiftfallTags.UNIQUE_WEAPONS)) return Result.UNIQUE;
        return null;
    }

    public static Quote quote(Container slots, Container inventory, int selection, Context ctx) {
        var item = slots.getItem(GEAR);
        int funds = ShardWallet.balance(slots, SHARDS, inventory);
        int tier = tier(item), steps = steps(item), band = SmithRules.band(ctx.rank());
        var kind = item.isEmpty() ? null : kind(item);
        int shards = 0, stock = 0, stockOut = 0;
        ItemStack material = ItemStack.EMPTY;
        Result result = refusal(item);
        if (selection == SALVAGE) {
            if (result == null && ctx.gems().gems(item).stream().anyMatch(g -> !g.isEmpty())) result = Result.SOCKETED;
            if (result == null) {
                int rarity = rarity(item, ctx.gems());
                material = materialFor(rarity, SmithRules.material(tier, rarity));
                stockOut = SmithRules.salvageStock(tier, rarity, ctx.prospector());
                result = fits(slots.getItem(OUT_MATERIAL), material) && fits(slots.getItem(OUT_STOCK),
                        new ItemStack(RiftfallItems.TEMPERING_STOCK.get(), stockOut)) ? Result.READY : Result.OUTPUT_FULL;
            }
        } else if (selection == RETEMPER) {
            if (result == null) {
                shards = SmithRules.shards(tier + 1, kind, ctx.path());
                stock = SmithRules.stock(tier + 1, kind, ctx.path());
                var step = SmithRules.step(tier, steps, ctx.playerCap(), ctx.rank());
                if (!SmithRules.rankAllows(kind, ctx.rank())) result = Result.RANK;
                else if (step == SmithRules.Step.BAND) result = Result.BAND;
                else if (step == SmithRules.Step.CAP) result = Result.CAP;
                else if (count(slots.getItem(STOCK)) < stock) result = Result.MISSING_STOCK;
                else if (funds < shards) result = Result.MISSING_SHARDS;
                else if (GearMutation.preview(item, RETEMPER_POLICY, SmithService::stepUp) != GearMutation.Result.EDITED)
                    result = Result.REFUSED;
                else result = Result.READY;
            }
        } else result = Result.NO_ITEM;
        return new Quote(selection, result, kind, tier, steps, band, ctx.playerCap(), shards, stock, material, stockOut, funds,
                item, item.copy());
    }

    /** A graded gun's rarity is its grade's (Apotheosis gives guns none); anything else, the gem source's. */
    public static int rarity(ItemStack item, JewelerGems gems) {
        var grade = GunGrade.read(item);
        return grade != null ? grade.rarity() : gems.rarity(item);
    }

    private static int count(ItemStack stack) { return stack.is(RiftfallItems.TEMPERING_STOCK.get()) ? stack.getCount() : 0; }

    static boolean fits(ItemStack output, ItemStack add) {
        if (add.isEmpty() || output.isEmpty()) return add.getCount() <= Math.max(1, add.getMaxStackSize());
        return ItemStack.isSameItemSameComponents(output, add) && output.getCount() + add.getCount() <= output.getMaxStackSize();
    }

    /** The rarity's material from {@code #riftfall:rarity_material/<rarity>}; EMPTY without one. */
    public static ItemStack materialFor(int rarity, int count) {
        if (rarity < 0 || rarity >= RarityLadder.RARITIES.length || count <= 0) return ItemStack.EMPTY;
        String name = ResourceLocation.parse(RarityLadder.RARITIES[rarity]).getPath();
        var tag = TagKey.create(Registries.ITEM, RiftfallCore.id("rarity_material/" + name));
        Item item = BuiltInRegistries.ITEM.getTag(tag).flatMap(set -> set.stream().findFirst()).map(h -> h.value()).orElse(null);
        return item == null ? ItemStack.EMPTY : new ItemStack(item, count);
    }

    /** One re-temper step on {@code stack} in place: tier up, steps up, floors (or a gun's grade) restamped. */
    public static void stepUp(ItemStack stack) {
        int next = tier(stack) + 1;
        stack.set(RiftfallDataComponents.RETEMPERED.get(), steps(stack) + 1);
        if (GunGrade.read(stack) != null) {
            GunGrade.retier(stack, next);
            return;
        }
        stack.set(RiftfallDataComponents.RIFT_TIER.get(), next);
        WeaponFloor.stamp(DefenceFloor.stamp(stack, next), next);
    }

    /** The edit for tempering orders saved before they went: one step; a refused edit hands the item back unchanged. */
    public static ItemStack orderEdit(ItemStack held) {
        var copy = held.copy();
        if (GearMutation.apply(copy, RETEMPER_POLICY, SmithService::stepUp) != GearMutation.Result.EDITED) return held;
        return copy;
    }

    /** Called with the quote the player confirmed. Nothing is taken unless everything holds. */
    public static Result commit(Container slots, Container inventory, Quote shown, Context ctx) {
        var item = slots.getItem(GEAR);
        if (item != shown.item() || !ItemStack.matches(item, shown.snapshot())) return Result.CHANGED;
        var now = quote(slots, inventory, shown.selection(), ctx);
        if (!now.terms().equals(shown.terms())) return Result.CHANGED;
        if (!now.ready()) return now.result();
        if (now.selection() == SALVAGE) {
            item.shrink(1);
            put(slots, OUT_MATERIAL, now.material());
            put(slots, OUT_STOCK, new ItemStack(RiftfallItems.TEMPERING_STOCK.get(), now.stockOut()));
            slots.setChanged();
            return Result.SALVAGED;
        }
        var payment = ShardWallet.plan(slots, SHARDS, inventory, now.shards());
        if (payment == null || !payment.valid()) return Result.CHANGED;
        if (!payment.pay()) throw new IllegalStateException("payment changed under a re-temper");
        slots.getItem(STOCK).shrink(now.stock());
        if (GearMutation.apply(item, RETEMPER_POLICY, SmithService::stepUp) != GearMutation.Result.EDITED)
            throw new IllegalStateException("a previewed re-temper failed to apply");
        slots.setChanged();
        inventory.setChanged();
        return Result.RETEMPERED;
    }

    private static void put(Container slots, int slot, ItemStack add) {
        if (add.isEmpty()) return;
        var out = slots.getItem(slot);
        if (out.isEmpty()) slots.setItem(slot, add.copy()); else out.grow(add.getCount());
    }

    /** Components re-temper must leave alone, for the tests' "everything else is equal" check. */
    public static Set<ResourceLocation> edited() {
        return Set.of(RiftfallCore.id("rift_tier"), RiftfallCore.id("retempered"), RiftfallCore.id("gun_grade"),
                ResourceLocation.withDefaultNamespace("attribute_modifiers"));
    }
}
