package com.cyberday1.riftfall.mixin;

import com.cyberday1.riftfall.gear.RiftSilence;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The held relics override vanilla's item hooks directly and none of them asks Relics' ability check, so the stack
 * dispatch is their one shared choke point. In a rift a {@code riftfall:rift_silenced} item is not used, does not
 * tick (the Blazing Flask's flight zone lives in its tick) and a use already under way stops. Both sides run it.
 */
@Mixin(ItemStack.class)
public abstract class MixinRiftSilencedStack {

    @Unique
    private ItemStack riftfall$stack() {
        return (ItemStack) (Object) this;
    }

    @Inject(method = "use", at = @At("HEAD"), cancellable = true)
    private void riftfall$riftUse(Level level, Player player, InteractionHand hand,
                                  CallbackInfoReturnable<InteractionResultHolder<ItemStack>> cir) {
        ItemStack stack = riftfall$stack();
        String refusal = RiftSilence.refused(stack, level) ? RiftSilence.SILENCED
                : RiftSilence.intoRift(stack, player) ? RiftSilence.BOUNDARY : null;
        if (refusal == null) return;
        if (refusal == RiftSilence.BOUNDARY) RiftSilence.refusedCrossing();
        RiftSilence.refusedUse();
        RiftSilence.tell(player, refusal);
        cir.setReturnValue(InteractionResultHolder.fail(stack));
    }

    /** Started outside and finished in a rift, or the respawn point moved into one while the Mirror charged. */
    @Inject(method = "finishUsingItem", at = @At("HEAD"), cancellable = true)
    private void riftfall$riftFinish(Level level, LivingEntity holder, CallbackInfoReturnable<ItemStack> cir) {
        ItemStack stack = riftfall$stack();
        boolean here = RiftSilence.refused(stack, level);
        boolean crossing = !here && holder instanceof Player player && RiftSilence.intoRift(stack, player);
        if (!here && !crossing) return;
        if (crossing) RiftSilence.refusedCrossing();
        RiftSilence.refusedUse();
        if (holder instanceof Player player) RiftSilence.tell(player, here ? RiftSilence.SILENCED : RiftSilence.BOUNDARY);
        cir.setReturnValue(stack);
    }

    @Inject(method = "releaseUsing", at = @At("HEAD"), cancellable = true)
    private void riftfall$riftRelease(Level level, LivingEntity holder, int remaining, CallbackInfo ci) {
        if (RiftSilence.refused(riftfall$stack(), level)) {
            RiftSilence.refusedUse();
            ci.cancel();
        }
    }

    /** A use carried into a rift: stop it here rather than let it run to its finish or release. */
    @Inject(method = "onUseTick", at = @At("HEAD"), cancellable = true)
    private void riftfall$riftUsing(Level level, LivingEntity holder, int remaining, CallbackInfo ci) {
        if (!RiftSilence.refused(riftfall$stack(), level)) return;
        ci.cancel();
        if (!level.isClientSide()) {
            RiftSilence.refusedUse();
            holder.stopUsingItem();
            if (holder instanceof Player player) RiftSilence.tell(player, RiftSilence.SILENCED);
        }
    }

    @Inject(method = "inventoryTick", at = @At("HEAD"), cancellable = true)
    private void riftfall$riftTick(Level level, Entity holder, int slot, boolean selected, CallbackInfo ci) {
        if (RiftSilence.refused(riftfall$stack(), level)) {
            RiftSilence.refusedTick();
            ci.cancel();
        }
    }
}
