package com.cyberday1.riftfall.home.npc.smith;

import com.cyberday1.riftfall.RiftfallCore;
import com.cyberday1.riftfall.gear.FortuneGrant;
import com.cyberday1.riftfall.gear.RarityLadder;
import com.cyberday1.riftfall.init.RiftfallDataComponents;
import com.cyberday1.riftfall.home.npc.GearMutation;
import com.cyberday1.riftfall.home.npc.ResidentRank;
import com.cyberday1.riftfall.home.npc.ResidentServiceMenu;
import com.cyberday1.riftfall.home.npc.ShardWallet;
import com.cyberday1.riftfall.init.RiftfallItems;
import com.cyberday1.riftfall.init.RiftfallTags;
import com.cyberday1.riftfall.tool.LootingToolItem;
import com.cyberday1.riftfall.tool.ToolMode;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import java.util.Arrays;
import java.util.Locale;

/**
 * The Blacksmith's tool upgrades: Fortune one granted level at a time to VIII on anything Fortune supports (and the
 * looting pick), and shovel, axe and hoe modes on the looting pick. The Blacksmith is the only source of Fortune
 * ({@link FortuneGrant}): only the granted level counts, and a raise rewrites stored Fortune to it, so unearned
 * Fortune on the item is dropped, never built on. Starter items are refused (B2) except the looting pick.
 */
public final class ToolWorkService {
    public static final int TOOL = 0, MATERIAL = 1, CRYSTAL = 2, SHARDS = 3, SLOTS = 4;
    public static final int FORTUNE = 0, MODE = 1;
    public static final TagKey<Item> RARITY_MATERIALS = TagKey.create(Registries.ITEM, RiftfallCore.id("rarity_materials"));

    public enum Result {
        READY, NO_ITEM, NOT_SINGLE, BANNED, NOT_SUPPORTED, STARTER, SILK_TOUCH, AT_CAP, RANK, NOT_PICK, ALREADY,
        MISSING_MATERIAL, MISSING_CRYSTAL, MISSING_FACETED, MISSING_SHARDS, REFUSED, CHANGED, RAISED, LEARNT;
        public String key() { return ToolWorkLang.MESSAGE + name().toLowerCase(Locale.ROOT); }
    }

    /** {@code fortune}: the server's Fortune holder; null refuses every Fortune quote. */
    public record Context(ResidentRank rank, boolean toolwright, Holder<Enchantment> fortune) {}

    public record Quote(int selection, Result result, int level, int target, int shards, int rarity, int material,
                        int crystals, int faceted, int funds, ItemStack item, ItemStack snapshot)
            implements ResidentServiceMenu.Quote {
        @Override public boolean ready() { return result == Result.READY; }
        @Override public Object terms() {
            return Arrays.asList(selection, result, level, target, shards, rarity, material, crystals, faceted, funds);
        }
    }

    public static final GearMutation.Policy FORTUNE_POLICY = GearMutation.Policy.of(DataComponents.ENCHANTMENTS,
            RiftfallDataComponents.FORTUNE_GRANT.get()).withLootingPick();
    public static final GearMutation.Policy MODE_POLICY = GearMutation.Policy.of(DataComponents.CUSTOM_DATA).withLootingPick();

    private ToolWorkService() {}

    public static ToolMode mode(int selection) {
        int i = selection - MODE;
        return i >= 0 && i < ToolMode.values().length ? ToolMode.values()[i] : null;
    }

    public static boolean selectable(int selection) { return selection == FORTUNE || mode(selection) != null; }

    public static boolean accepts(int slot, ItemStack stack) {
        return switch (slot) {
            case TOOL -> !ShardWallet.isShard(stack) && !stack.is(RARITY_MATERIALS) && !crystal(stack);
            case MATERIAL -> stack.is(RARITY_MATERIALS);
            case CRYSTAL -> crystal(stack);
            case SHARDS -> ShardWallet.isShard(stack);
            default -> false;
        };
    }

    public static int maxStack(int slot) { return slot == TOOL ? 1 : 64; }

    private static boolean crystal(ItemStack stack) { return stack.is(RiftfallTags.RARE_CRYSTALS) || stack.is(RiftfallItems.FACETED_CRYSTAL.get()); }

    public static TagKey<Item> rarityTag(int rarity) {
        String name = ResourceLocation.parse(RarityLadder.RARITIES[rarity]).getPath();
        return TagKey.create(Registries.ITEM, RiftfallCore.id("rarity_material/" + name));
    }

    /** Vanilla Fortune's own items, plus the looting pick. */
    public static boolean fortuneFits(ItemStack stack) {
        return stack.is(ItemTags.MINING_LOOT_ENCHANTABLE) || LootingToolItem.isTool(stack);
    }

    /** The Fortune that counts: stored, never above the Blacksmith's grant. Unearned Fortune is 0. */
    public static int grantedFortune(ItemStack stack, Holder<Enchantment> fortune) {
        return fortune == null ? 0 : FortuneGrant.effective(stack);
    }

    /** Silk Touch, or anything else Fortune's exclusive set names, already stored on the item. */
    static boolean exclusive(ItemStack stack, Holder<Enchantment> fortune) {
        for (var entry : stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).entrySet()) {
            var other = entry.getKey();
            if (other.equals(fortune)) continue;
            if (other.is(Enchantments.SILK_TOUCH) || !Enchantment.areCompatible(fortune, other)) return true;
        }
        return false;
    }

    private static int count(ItemStack stack, TagKey<Item> tag) { return stack.is(tag) ? stack.getCount() : 0; }

    public static Quote quote(Container slots, Container inventory, int selection, Context ctx) {
        var item = slots.getItem(TOOL);
        int funds = ShardWallet.balance(slots, SHARDS, inventory);
        int level = 0, target = 0, shards = 0, rarity = -1, material = 0, crystals = 0, faceted = 0;
        Result result;
        var mode = mode(selection);
        if (item.isEmpty()) result = Result.NO_ITEM;
        else if (item.getCount() != 1) result = Result.NOT_SINGLE;
        else if (selection == FORTUNE) {
            level = grantedFortune(item, ctx.fortune());
            target = level + 1;
            if (target <= ToolWorkRules.FORTUNE_MAX) {
                shards = ToolWorkRules.fortuneShards(target, ctx.toolwright());
                rarity = ToolWorkRules.fortuneRarity(target);
                material = ToolWorkRules.fortuneMaterial(target);
                crystals = ToolWorkRules.fortuneCrystals(target);
                faceted = ToolWorkRules.fortuneFaceted(target);
            }
            var check = GearMutation.check(item, FORTUNE_POLICY);
            if (ctx.fortune() == null) result = Result.REFUSED;
            else if (check == GearMutation.Result.BANNED) result = Result.BANNED;
            else if (!fortuneFits(item)) result = Result.NOT_SUPPORTED;
            else if (SmithService.starter(item) && !LootingToolItem.isTool(item)) result = Result.STARTER;
            else if (exclusive(item, ctx.fortune())) result = Result.SILK_TOUCH;
            else if (target > ToolWorkRules.FORTUNE_MAX) result = Result.AT_CAP;
            else if (!ctx.rank().atLeast(ToolWorkRules.fortuneRank(target))) result = Result.RANK;
            else result = paid(slots, funds, shards, rarity, material, crystals, faceted,
                    () -> GearMutation.preview(item, FORTUNE_POLICY, s -> raise(s, ctx.fortune())));
        } else if (mode != null) {
            shards = ToolWorkRules.modeShards(ctx.toolwright());
            rarity = ToolWorkRules.MODE_RARITY;
            material = ToolWorkRules.MODE_MATERIAL;
            if (!LootingToolItem.isTool(item)) result = Result.NOT_PICK;
            else if (ToolMode.has(item, mode)) result = Result.ALREADY;
            else result = paid(slots, funds, shards, rarity, material, 0, 0,
                    () -> GearMutation.preview(item, MODE_POLICY, s -> learn(s, mode)));
        } else result = Result.NO_ITEM;
        return new Quote(selection, result, level, target, shards, rarity, material, crystals, faceted, funds, item, item.copy());
    }

    private static Result paid(Container slots, int funds, int shards, int rarity, int material, int crystals, int faceted,
                               java.util.function.Supplier<GearMutation.Result> preview) {
        if (count(slots.getItem(MATERIAL), rarityTag(rarity)) < material) return Result.MISSING_MATERIAL;
        if (count(slots.getItem(CRYSTAL), RiftfallTags.RARE_CRYSTALS) < crystals) return Result.MISSING_CRYSTAL;
        var held = slots.getItem(CRYSTAL);
        if (faceted > 0 && (held.is(RiftfallItems.FACETED_CRYSTAL.get()) ? held.getCount() : 0) < faceted) return Result.MISSING_FACETED;
        if (funds < shards) return Result.MISSING_SHARDS;
        return preview.get() == GearMutation.Result.EDITED ? Result.READY : Result.REFUSED;
    }

    /** One granted level above the current one, stored and granted together; other enchantments and the tooltip flag stay. */
    public static void raise(ItemStack stack, Holder<Enchantment> fortune) {
        FortuneGrant.set(stack, fortune, FortuneGrant.effective(stack) + 1);
    }

    /** Sets stored and granted Fortune; 0 removes both. The op command's path. */
    public static void setFortune(ItemStack stack, Holder<Enchantment> fortune, int level) {
        FortuneGrant.set(stack, fortune, level);
    }

    public static void learn(ItemStack stack, ToolMode mode) { setMode(stack, mode, true); }

    public static void setMode(ItemStack stack, ToolMode mode, boolean on) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            var upgrades = tag.getCompound(LootingToolItem.UPGRADES_KEY);
            if (on) upgrades.putBoolean(mode.key, true); else upgrades.remove(mode.key);
            tag.put(LootingToolItem.UPGRADES_KEY, upgrades);
        });
    }

    /** Called with the quote the player confirmed. Nothing is taken unless everything holds. */
    public static Result commit(Container slots, Container inventory, Quote shown, Context ctx) {
        var item = slots.getItem(TOOL);
        if (item != shown.item() || !ItemStack.matches(item, shown.snapshot())) return Result.CHANGED;
        var now = quote(slots, inventory, shown.selection(), ctx);
        if (!now.terms().equals(shown.terms())) return Result.CHANGED;
        if (!now.ready()) return now.result();
        var payment = ShardWallet.plan(slots, SHARDS, inventory, now.shards());
        if (payment == null || !payment.valid()) return Result.CHANGED;
        if (!payment.pay()) return Result.CHANGED;
        slots.getItem(MATERIAL).shrink(now.material());
        slots.getItem(CRYSTAL).shrink(now.crystals() + now.faceted());
        var mode = mode(now.selection());
        var edited = mode == null ? GearMutation.apply(item, FORTUNE_POLICY, s -> raise(s, ctx.fortune()))
                : GearMutation.apply(item, MODE_POLICY, s -> learn(s, mode));
        if (edited != GearMutation.Result.EDITED) throw new IllegalStateException("a previewed tool upgrade failed to apply");
        slots.setChanged();
        inventory.setChanged();
        return mode == null ? Result.RAISED : Result.LEARNT;
    }
}
