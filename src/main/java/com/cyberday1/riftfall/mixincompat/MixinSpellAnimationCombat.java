package com.cyberday1.riftfall.mixincompat;

import com.cyberday1.riftfall.compat.BetterCombatCasting;
import net.minecraft.client.Minecraft;
import net.spell_engine.client.ClientNetwork;
import net.spell_engine.internals.casting.SpellCast;
import net.spell_engine.network.Packets;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ClientNetwork.class, remap = false)
public abstract class MixinSpellAnimationCombat {
    @Inject(method = "handleSpellAnimation", at = @At("HEAD"))
    private static void riftfall$release(Packets.SpellAnimation packet, CallbackInfo ci) {
        if (((SpellAnimationAccess) (Object) packet).riftfall$animationType() == SpellCast.Animation.RELEASE) {
            Minecraft.getInstance().execute(() ->
                    BetterCombatCasting.release(packet.playerId(), packet.name(), packet.speed()));
        }
    }
}
