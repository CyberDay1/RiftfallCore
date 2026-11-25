package com.cyberday1.riftfall.mixincompat;

import com.cyberday1.riftfall.gear.WeaponDonors;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Applied after Scythes 5.2's mixin: its passive bypasses ItemStack's hit dispatch. */
@Mixin(value = LivingEntity.class, priority = 900)
public abstract class MixinScytheBloodPassive {
    @Dynamic("Added by Scythes LivingEntityMixin")
    @Inject(method = "scythes$getBloodScytheAttacker", at = @At("HEAD"), cancellable = true, remap = false)
    private void riftfall$noUnbudgetedBloodPassive(DamageSource source, CallbackInfoReturnable<ServerPlayer> cir) {
        if (source.getEntity() instanceof ServerPlayer player && WeaponDonors.applies(player.getMainHandItem())) {
            cir.setReturnValue(null);
        }
    }
}
