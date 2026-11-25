package com.cyberday1.riftfall.mixin;

import com.cyberday1.riftfall.client.ClientSkillTravel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LocalPlayer.class)
public abstract class MixinSkillTravel {
    @ModifyVariable(method = "move", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private Vec3 riftfall$travel(Vec3 movement, MoverType type, Vec3 original) {
        return ClientSkillTravel.movement((LocalPlayer) (Object) this, type, movement);
    }

    @Inject(method = "updateAutoJump", at = @At("HEAD"), cancellable = true)
    private void riftfall$noAutoJumpDuringTravel(float x, float z, CallbackInfo ci) {
        if (ClientSkillTravel.active()) ci.cancel();
    }
}
