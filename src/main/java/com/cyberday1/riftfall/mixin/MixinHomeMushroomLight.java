package com.cyberday1.riftfall.mixin;

import com.cyberday1.riftfall.home.HomeSkyLight;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.MushroomBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * In a home a small mushroom ignores sky light, as it did before homes had any: the tiles grow 181 of them on
 * open grass, and at sky light 15 each would break at the next neighbour update.
 */
@Mixin(MushroomBlock.class)
public abstract class MixinHomeMushroomLight {
    @WrapOperation(method = "canSurvive", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/level/LevelReader;getRawBrightness(Lnet/minecraft/core/BlockPos;I)I"))
    private int riftfall$homeIgnoresSkyLight(LevelReader level, BlockPos pos, int darkening, Operation<Integer> original) {
        return HomeSkyLight.inHome(level) ? level.getBrightness(LightLayer.BLOCK, pos) : original.call(level, pos, darkening);
    }
}
