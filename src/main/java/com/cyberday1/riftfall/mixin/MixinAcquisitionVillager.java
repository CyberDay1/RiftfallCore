package com.cyberday1.riftfall.mixin;

import com.cyberday1.riftfall.gear.AcquisitionBan;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.item.trading.MerchantOffers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractVillager.class)
public abstract class MixinAcquisitionVillager {
    @Inject(method = "getOffers", at = @At("RETURN"))
    private void riftfall$noDisabledOffers(CallbackInfoReturnable<MerchantOffers> cir) {
        AcquisitionBan.stripOffers(cir.getReturnValue());
    }
}
