package com.cyberday1.riftfall.mixincompat;

import com.cyberday1.riftfall.rift.PartyCombat;
import net.bettercombat.logic.TargetHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = TargetHelper.class, remap = false)
public abstract class MixinBetterCombatParties {
    // Run before other team integrations can classify different FTB/scoreboard teams as hostile.
    @Inject(method = "getRelation", at = @At("HEAD"), cancellable = true)
    private static void riftfall$party(Player attacker, Entity target,
                                      CallbackInfoReturnable<TargetHelper.Relation> cir) {
        if (PartyCombat.allies(attacker, target)) cir.setReturnValue(TargetHelper.Relation.FRIENDLY);
    }
}
