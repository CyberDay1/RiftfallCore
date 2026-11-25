package com.cyberday1.riftfall.mixin;

import com.cyberday1.riftfall.gear.AbilityDropBan;
import com.cyberday1.riftfall.gear.AcquisitionBan;
import com.cyberday1.riftfall.gear.BarrelAbilityStrip;
import com.cyberday1.riftfall.gear.BarrelTools;
import com.cyberday1.riftfall.gear.RiftLootRarity;
import com.cyberday1.riftfall.gear.RiftOnlyLoot;
import com.cyberday1.riftfall.gear.RiftMobDrops;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootTable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The last word on a drop, after every loot modifier has had theirs.
 *
 * <p>
 * This private overload is where the four public ones converge and where NeoForge calls
 * {@code CommonHooks.modifyLoot}, so injecting on its return is the one place in the game that is
 * reliably after Apotheosis has decided a drop is an affix item. {@link RiftLootRarity} says why that
 * has to be a fixed point rather than a modifier ordered behind Apotheosis'.
 *
 * <p>
 * Nothing is cancelled and nothing is replaced: the list is the one the caller will read, and the
 * hook edits it in place. {@link AbilityDropBan} strips first, so nothing it removes is rerolled.
 */
@Mixin(LootTable.class)
public class MixinLootTable {

    @com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation(
            method = "getRandomItems(Lnet/minecraft/world/level/storage/loot/LootContext;)Lit/unimi/dsi/fastutil/objects/ObjectArrayList;",
            at = @At(value = "INVOKE", target = "Lnet/neoforged/neoforge/common/CommonHooks;modifyLoot(Lnet/minecraft/resources/ResourceLocation;Lit/unimi/dsi/fastutil/objects/ObjectArrayList;Lnet/minecraft/world/level/storage/loot/LootContext;)Lit/unimi/dsi/fastutil/objects/ObjectArrayList;"))
    private ObjectArrayList<ItemStack> riftfall$authoredRewardBudget(net.minecraft.resources.ResourceLocation table,
            ObjectArrayList<ItemStack> rolled, LootContext context,
            com.llamalad7.mixinextras.injector.wrapoperation.Operation<ObjectArrayList<ItemStack>> original) {
        return com.cyberday1.riftfall.gear.RiftRewardLoot.modify(table, rolled,
                input -> original.call(table, input, context));
    }

    @com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod(
            method = "getRandomItems(Lnet/minecraft/world/level/storage/loot/LootContext;)Lit/unimi/dsi/fastutil/objects/ObjectArrayList;")
    private ObjectArrayList<ItemStack> riftfall$weightedBarrelAccessories(LootContext context,
            com.llamalad7.mixinextras.injector.wrapoperation.Operation<ObjectArrayList<ItemStack>> original) {
        return com.cyberday1.riftfall.gear.BarrelAccessoryLoot.roll(
                ((LootTable) (Object) this).getLootTableId(), () -> original.call(context));
    }

    @Inject(
            method = "getRandomItems(Lnet/minecraft/world/level/storage/loot/LootContext;)"
                    + "Lit/unimi/dsi/fastutil/objects/ObjectArrayList;",
            at = @At("RETURN"))
    private void riftfall$lastWordOnDrops(
            LootContext context, CallbackInfoReturnable<ObjectArrayList<ItemStack>> callback) {
        RiftOnlyLoot.strip(callback.getReturnValue(), context);
        com.cyberday1.riftfall.gear.NonRiftLoot.strip(callback.getReturnValue(), context);
        com.cyberday1.riftfall.gear.DonorProgression.stripLoot(callback.getReturnValue(),
                com.cyberday1.riftfall.rift.RiftLevels.isRiftWorld(context.getLevel())
                        || com.cyberday1.riftfall.tool.BarrelLootBonuses.active());
        AbilityDropBan.strip(callback.getReturnValue(), context);
        RiftLootRarity.apply(callback.getReturnValue(), context);
        // Last, so it judges the stack actually handed out, whatever the reroll did to it.
        BarrelAbilityStrip.apply(callback.getReturnValue(), ((LootTable) (Object) this).getLootTableId(), context);
        BarrelTools.strip(callback.getReturnValue(), ((LootTable) (Object) this).getLootTableId());
        RiftMobDrops.strip(callback.getReturnValue(), context);
        AcquisitionBan.stripLoot(callback.getReturnValue());
    }
}
