package com.cyberday1.riftfall.mixincompat;

import com.cyberday1.riftfall.compat.LegendaryHelmetTeleport;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.concurrent.CompletableFuture;

@Pseudo
@Mixin(targets = {
        "net.miauczel.legendary_monsters.Network.LMServerPayLoadHandler",
        "net.miauczel.legendary_monsters.Message.AnnihilatorHelmetAbilityMessage"
}, remap = false)
public abstract class MixinLegendaryHelmetTeleport {
    @Redirect(method = "handleAnnihilatorHelmetAbility", at = @At(value = "INVOKE",
            target = "Lnet/neoforged/neoforge/network/handling/IPayloadContext;enqueueWork(Ljava/lang/Runnable;)Ljava/util/concurrent/CompletableFuture;"))
    private static CompletableFuture<Void> riftfall$validateTeleport(IPayloadContext context, Runnable action) {
        return LegendaryHelmetTeleport.enqueue(context, action);
    }
}
