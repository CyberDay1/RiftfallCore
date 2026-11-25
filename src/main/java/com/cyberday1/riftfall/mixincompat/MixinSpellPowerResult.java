package com.cyberday1.riftfall.mixincompat;

import net.spell_power.api.SpellPower;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Spell Power never crits: every roll, forced one or not, returns the uncritted value its own
 * DISABLED branch would. The hit's one crit is then Apothic's, inside the damage event.
 */
@Mixin(value = SpellPower.Result.class, remap = false)
public abstract class MixinSpellPowerResult {

    @Inject(method = "value", at = @At("HEAD"), cancellable = true)
    private void riftfall$noSpellPowerCrit(@Coerce Object mode, SpellPower.Vulnerability vulnerability,
                                           CallbackInfoReturnable<SpellPower.Result.Value> cir) {
        SpellPower.Result self = (SpellPower.Result) (Object) this;
        cir.setReturnValue(new SpellPower.Result.Value(
                self.baseValue() * (double) (1.0F + vulnerability.powerBaseMultiplier()), false));
    }
}
