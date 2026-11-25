package com.cyberday1.riftfall.mixincompat;

import com.cyberday1.riftfall.skill.RangedCharge;
import com.tacz.guns.entity.shooter.LivingEntityAmmoCheck;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A player's gun needs no ammo: a reload asks for no reserve and takes none, and an inventory-fed gun fires without
 * rounds. The magazine still empties and reloads. Both sides, because the client refuses a reload or a shot without
 * ammo before the server sees it. With the check off TaCZ also returns nothing when a magazine is unloaded, which is
 * what keeps a free magazine from coming back as rounds.
 */
@Mixin(value = LivingEntityAmmoCheck.class, remap = false)
public abstract class MixinTaczAmmoCheck {

    @Shadow
    @Final
    private LivingEntity shooter;

    @Inject(method = "needCheckAmmo", at = @At("HEAD"), cancellable = true)
    private void riftfall$playersNeedNoAmmo(CallbackInfoReturnable<Boolean> cir) {
        if (shooter instanceof Player player && RangedCharge.ammoFree(player)) {
            cir.setReturnValue(false);
        }
    }
}
