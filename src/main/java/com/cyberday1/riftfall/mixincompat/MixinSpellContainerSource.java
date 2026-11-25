package com.cyberday1.riftfall.mixincompat;

import com.cyberday1.riftfall.skill.RiftSpellGate;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.player.Player;
import net.spell_engine.internals.container.SpellContainerSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A player's spell offer passes {@code RiftSpellGate} before the engine stores it: the one call
 * every container source feeds. {@code update} runs every player tick but rebuilds only on a dirty
 * source, so its head first dirties one when the player's grants changed.
 */
@Mixin(value = SpellContainerSource.class, remap = false)
public abstract class MixinSpellContainerSource {

    @Inject(method = "update", at = @At("HEAD"))
    private static void riftfall$refreshOnGrantChange(Player player, CallbackInfo ci) {
        RiftSpellGate.refresh(player);
    }

    @WrapOperation(method = "update", at = @At(value = "INVOKE",
            target = "Lnet/spell_engine/internals/container/SpellContainerSource$Owner;setSpellContainers(Lnet/spell_engine/internals/container/SpellContainerSource$Result;)V"))
    private static void riftfall$gateOffer(SpellContainerSource.Owner owner, SpellContainerSource.Result result,
                                           Operation<Void> original) {
        original.call(owner, RiftSpellGate.offer(owner, result));
    }
}
