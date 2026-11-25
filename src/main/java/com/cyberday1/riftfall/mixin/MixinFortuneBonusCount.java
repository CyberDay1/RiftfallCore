package com.cyberday1.riftfall.mixin;

import com.cyberday1.riftfall.gear.FortuneGrant;
import net.minecraft.core.Holder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.functions.ApplyBonusCount;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Opens and closes the Fortune cap around a bonus-count roll; {@link MixinFortuneBonusFormula} applies it. */
@Mixin(ApplyBonusCount.class)
public abstract class MixinFortuneBonusCount {
    @Shadow @Final private Holder<Enchantment> enchantment;

    @Inject(method = "run", at = @At("HEAD"))
    private void riftfall$openFortuneCap(ItemStack stack, LootContext context, CallbackInfoReturnable<ItemStack> cir) {
        FortuneGrant.beginBonus(enchantment, context.getParamOrNull(LootContextParams.TOOL));
    }

    @Inject(method = "run", at = @At("RETURN"))
    private void riftfall$closeFortuneCap(ItemStack stack, LootContext context, CallbackInfoReturnable<ItemStack> cir) {
        FortuneGrant.endBonus();
    }
}
