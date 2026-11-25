package com.cyberday1.riftfall.mixincompat;

import com.cyberday1.riftfall.skill.RiftIronsGate;
import com.llamalad7.mixinextras.sugar.Local;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * irons_apothic's spell-trigger affixes cast for a player without {@code attemptInitiateCast}, and
 * run the spell's pre-cast and per-tick work before {@code castSpell}. An ungranted spell is refused
 * before any of it.
 */
@Mixin(targets = "dev.muon.irons_apothic.util.SpellCastUtil", remap = false)
public abstract class MixinIronsAffixCast {

    // The two arguments it reads, taken by type: irons_apothic 2.2.8 changed the last one (Optional<Integer> to
    // SpellCastOptions), and a full-signature handler no longer applied, which failed SpellCastUtil's class load.
    @Inject(method = "castForPlayer", at = @At("HEAD"), cancellable = true)
    private static void riftfall$gateAffix(CallbackInfoReturnable<Boolean> cir,
                                           @Local(argsOnly = true) ServerPlayer player,
                                           @Local(argsOnly = true) AbstractSpell spell) {
        if (RiftIronsGate.refusesAffix(player, spell.getSpellId())) {
            cir.setReturnValue(false);
        }
    }
}
