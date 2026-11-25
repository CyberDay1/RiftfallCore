package com.cyberday1.riftfall.mixin;

import com.cyberday1.riftfall.gear.AcquisitionBan;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Also protects other Merchant implementations and offers retained before a tag reload. */
@Mixin(MerchantOffer.class)
public abstract class MixinAcquisitionOffer {
    @Inject(method = "satisfiedBy", at = @At("HEAD"), cancellable = true)
    private void riftfall$noDisabledPayment(ItemStack first, ItemStack second, CallbackInfoReturnable<Boolean> cir) {
        if (AcquisitionBan.blockedTrade(((MerchantOffer) (Object) this).getResult())) cir.setReturnValue(false);
    }

    @Inject(method = "assemble", at = @At("HEAD"), cancellable = true)
    private void riftfall$noDisabledSale(CallbackInfoReturnable<ItemStack> cir) {
        if (AcquisitionBan.blockedTrade(((MerchantOffer) (Object) this).getResult())) cir.setReturnValue(ItemStack.EMPTY);
    }
}
