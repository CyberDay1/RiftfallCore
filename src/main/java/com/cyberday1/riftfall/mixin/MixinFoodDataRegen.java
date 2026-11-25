package com.cyberday1.riftfall.mixin;

import com.cyberday1.riftfall.gear.RelicSignatureCosts;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Last Stand's cost: for its wearer, food regeneration reads as the gamerule off, so neither regen branch heals or
 * burns food. Starvation and every other heal (potions, skills, Last Rites, peaceful) still run.
 */
@Mixin(FoodData.class)
public abstract class MixinFoodDataRegen {
    @ModifyExpressionValue(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/GameRules;getBoolean(Lnet/minecraft/world/level/GameRules$Key;)Z"))
    private boolean riftfall$lastStandStopsRegen(boolean natural, @Local(argsOnly = true) Player player) {
        return natural && !RelicSignatureCosts.blocksRegen(player);
    }
}
