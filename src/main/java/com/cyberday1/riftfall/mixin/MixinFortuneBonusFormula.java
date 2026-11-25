package com.cyberday1.riftfall.mixin;

import com.cyberday1.riftfall.gear.FortuneGrant;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * The level a Fortune formula rolls with never exceeds the tool's granted Fortune. Inside the formula, so it runs
 * after every caller-side bonus (Lucky Scarf, curios, accessories, Puffish), whatever their mixin order.
 */
@Mixin(targets = {
        "net.minecraft.world.level.storage.loot.functions.ApplyBonusCount$OreDrops",
        "net.minecraft.world.level.storage.loot.functions.ApplyBonusCount$BinomialWithBonusCount",
        "net.minecraft.world.level.storage.loot.functions.ApplyBonusCount$UniformBonusCount"})
public abstract class MixinFortuneBonusFormula {
    @ModifyVariable(method = "calculateNewCount", at = @At("HEAD"), argsOnly = true, ordinal = 1)
    private int riftfall$capFortune(int level) { return FortuneGrant.capBonus(level); }
}
