package com.cyberday1.riftfall.mixincompat;

import com.cyberday1.riftfall.compat.TaczGuns;
import com.cyberday1.riftfall.skill.RangedCharge;
import com.tacz.guns.api.item.gun.AbstractGunItem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fitting or removing an extended magazine makes TaCZ hand the whole magazine back as ammo items, without asking
 * whether ammo is checked. A magazine is filled for free now, so that would mint rounds: it is emptied instead and
 * nothing is returned. Creative keeps TaCZ's own refill, and a fake player (which still loads real rounds) its own
 * hand-back.
 */
@Mixin(value = AbstractGunItem.class, remap = false)
public abstract class MixinTaczDropAllAmmo {

    @Inject(method = "dropAllAmmo", at = @At("HEAD"), cancellable = true)
    private void riftfall$aFreeMagazineReturnsNothing(Player player, ItemStack gunItem, CallbackInfo ci) {
        if (!player.isCreative() && RangedCharge.ammoFree(player)) {
            TaczGuns.emptyMagazine(gunItem);
            ci.cancel();
        }
    }
}
