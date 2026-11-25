package com.cyberday1.riftfall.mixincompat;

import com.cyberday1.riftfall.home.npc.ResidentLang;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A home resident's CNPC name reads "Penk, Blacksmith": its display name is the role's lang key. */
@Pseudo
@Mixin(targets = "noppes.npcs.entity.EntityNPCInterface", remap = false)
public abstract class MixinCnpcResidentName {
    @Inject(method = "getName", at = @At("HEAD"), cancellable = true)
    private void riftfall$nameplate(CallbackInfoReturnable<Component> cir) {
        var role = ResidentLang.roleOfName(((noppes.npcs.entity.EntityNPCInterface) (Object) this).display.getName());
        if (role != null) cir.setReturnValue(ResidentLang.nameplate(role));
    }
}
