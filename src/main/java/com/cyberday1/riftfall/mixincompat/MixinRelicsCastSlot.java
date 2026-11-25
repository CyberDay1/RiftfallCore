package com.cyberday1.riftfall.mixincompat;

import com.cyberday1.riftfall.gear.CurioSilence;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Every Relics active ability is cast from a curio slot, and both the server's cast packet and the client's ability
 * bar read the relic back through this method. An empty answer means no cast and no bar entry.
 */
@Pseudo
@Mixin(targets = "it.hurts.sskirillss.relics.system.casts.slots.CurioSlotReference", remap = false)
public abstract class MixinRelicsCastSlot {

    @Inject(method = "gatherStack(Lnet/minecraft/world/entity/player/Player;)Lnet/minecraft/world/item/ItemStack;",
            at = @At("RETURN"), cancellable = true)
    private void riftfall$refuseCast(Player player, CallbackInfoReturnable<ItemStack> cir) {
        if (CurioSilence.silenced(cir.getReturnValue())) {
            CurioSilence.hidLookup();
            cir.setReturnValue(ItemStack.EMPTY);
        }
    }
}
