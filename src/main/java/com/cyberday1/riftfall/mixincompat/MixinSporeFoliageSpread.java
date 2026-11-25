package com.cyberday1.riftfall.mixincompat;

import com.cyberday1.riftfall.mob.SporeContainment;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.Harbinger.Spore.Sentities.FoliageSpread", remap = false)
public interface MixinSporeFoliageSpread {
    // Mounds, the Proto, Hive Tumors, Gastgebers and fungal bonemeal all infest blocks through here.
    @Inject(method = "SpreadInfection", at = @At("HEAD"), cancellable = true)
    default void riftfall$noSpreadOutsideRifts(Level level, double range, BlockPos pos, CallbackInfo ci) {
        if (SporeContainment.contained(level)) {
            SporeContainment.count("spread", null);
            ci.cancel();
        }
    }
}
