package com.cyberday1.riftfall.mixincompat;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** OpenPAC normally expires inactive parties after 720 hours; home teams are permanent. */
@Mixin(targets = "xaero.pac.common.server.parties.party.expiration.PartyExpirationHandler", remap = false)
public abstract class MixinPermanentHomeTeams {
    @Inject(method = "checkIfActive(Lxaero/pac/common/server/parties/party/ServerParty;)Z", at = @At("HEAD"), cancellable = true)
    private void riftfall$keepTeam(CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(true);
    }
}
