package com.cyberday1.riftfall.mixincompat;

import com.cyberday1.riftfall.skill.RiftSpellGate;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.spell_engine.internals.delivery.melee.Melee;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The server half of a melee skill: the client names a spell and the engine swings it, with no
 * container check and no attempt event. Both run on the server thread, the first place the packet
 * meets a player whose book can be read, so an ungranted spell is dropped here. A granted hit no
 * paid cast covers pays Riftfall's price itself, or is dropped too.
 */
@Mixin(value = Melee.class, remap = false)
public abstract class MixinMelee {

    @WrapMethod(method = "performAttackAgainstTargets")
    private static void riftfall$gateHit(ServerPlayer player, Melee.AttackContext context, int[] targetIds,
                                         Operation<Void> original) {
        ResourceLocation ability = context == null ? null : context.spellId();
        if (RiftSpellGate.refusesMelee(player, ability) || RiftSpellGate.refusesUnpaidHit(player, ability)) {
            return;
        }
        RiftSpellGate.rankedMelee(player, ability, () -> original.call(player, context, targetIds));
    }

    @Inject(method = "broadcastAttackFx", at = @At("HEAD"), cancellable = true)
    private static void riftfall$gateSwing(ServerPlayer player, Melee.AttackContext context, CallbackInfo ci) {
        if (RiftSpellGate.refusesMelee(player, context == null ? null : context.spellId())) {
            ci.cancel();
        }
    }
}
