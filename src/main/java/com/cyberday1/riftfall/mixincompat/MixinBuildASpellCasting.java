package com.cyberday1.riftfall.mixincompat;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Native wand/ring casts must not turn saved or command-imported spells into extra class skills. */
@Pseudo
@Mixin(targets = "buildaspell.spell.execution.SpellExecutor", remap = false)
public abstract class MixinBuildASpellCasting {
    @Inject(method = {
            "executeSpell(Lnet/minecraft/world/entity/player/Player;Lbuildaspell/spell/Spell;)Z",
            "executeSpell(Lnet/minecraft/world/entity/player/Player;Lbuildaspell/spell/Spell;Z)Z"
    }, at = @At("HEAD"), cancellable = true)
    private static void riftfall$classSkillsOnly(CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(false);
    }
}
