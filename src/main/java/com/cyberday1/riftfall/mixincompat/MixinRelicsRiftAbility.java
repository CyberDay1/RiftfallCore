package com.cyberday1.riftfall.mixincompat;

import com.cyberday1.riftfall.gear.InfinityHam;
import com.cyberday1.riftfall.gear.RiftSilence;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Relics' own ability checks. A rift-silenced relic's abilities answer no in a rift. The Infinity Ham asks per
 * ability on every route (eating, the Marinade pour, the Meat Bat hit), so it gets the ham ruling instead, and
 * Marinade is disabled outright, which also takes it off Relics' ability and experience screens.
 */
@Pseudo
@Mixin(targets = "it.hurts.sskirillss.relics.items.relics.base.IRelicItem", remap = false)
public interface MixinRelicsRiftAbility {

    @Inject(method = "canPlayerUseAbility(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/ItemStack;Ljava/lang/String;)Z",
            at = @At("HEAD"), cancellable = true)
    private void riftfall$riftAbility(Player player, ItemStack stack, String ability, CallbackInfoReturnable<Boolean> cir) {
        if (RiftSilence.refused(stack, player)) {
            RiftSilence.refusedAbility();
            cir.setReturnValue(false);
            return;
        }
        if (!InfinityHam.is(stack)) return;
        Boolean answer = InfinityHam.answer(ability, player != null && RiftSilence.inRift(player.level()));
        if (answer == null) return;
        if (InfinityHam.disabled(ability)) InfinityHam.refusedMarinade();
        else RiftSilence.refusedAbility();
        cir.setReturnValue(answer);
    }

    @Inject(method = "isAbilityEnabled(Lnet/minecraft/world/item/ItemStack;Ljava/lang/String;)Z",
            at = @At("HEAD"), cancellable = true)
    private void riftfall$hamAbilityOff(ItemStack stack, String ability, CallbackInfoReturnable<Boolean> cir) {
        if (InfinityHam.disabled(ability) && InfinityHam.is(stack)) cir.setReturnValue(false);
    }
}
