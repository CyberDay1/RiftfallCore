package com.cyberday1.riftfall.mixincompat;

import com.cyberday1.riftfall.compat.PuffishQuiet;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collection;

/** With no trees there is nothing to update, so the call ends before Puffish's missing-config toast. */
@Pseudo
@Mixin(targets = "net.puffish.skillsmod.SkillsMod", remap = false)
public abstract class MixinPuffishNoTreesToast {
    @Shadow
    private boolean isConfigValid() {
        throw new AssertionError();
    }

    @Shadow
    private Collection<?> getAllCategories() {
        throw new AssertionError();
    }

    @Inject(method = "updateAllCategories", at = @At("HEAD"), cancellable = true)
    private void riftfall$noToastWithoutTrees(ServerPlayer player, CallbackInfo ci) {
        if (PuffishQuiet.skip(isConfigValid(), isConfigValid() && getAllCategories().isEmpty())) ci.cancel();
    }
}
