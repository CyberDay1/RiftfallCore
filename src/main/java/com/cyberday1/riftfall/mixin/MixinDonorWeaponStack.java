package com.cyberday1.riftfall.mixin;

import com.cyberday1.riftfall.gear.WeaponDonors;
import com.cyberday1.riftfall.gear.AcquisitionBan;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.stats.Stats;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Gate the stack dispatch, before a donor's item override can spawn a spell or apply its own damage. */
@Mixin(ItemStack.class)
public abstract class MixinDonorWeaponStack {
    @Unique private ItemStack riftfall$self() { return (ItemStack) (Object) this; }

    @Inject(method = "use", at = @At("HEAD"), cancellable = true)
    private void riftfall$use(Level level, Player player, InteractionHand hand,
                             CallbackInfoReturnable<InteractionResultHolder<ItemStack>> cir) {
        ItemStack stack = riftfall$self();
        if (AcquisitionBan.blocked(stack)) {
            cir.setReturnValue(InteractionResultHolder.fail(stack));
            return;
        }
        if (WeaponDonors.applies(stack)) cir.setReturnValue(stack.getItem() instanceof BowItem
                ? Items.BOW.use(level, player, hand) : InteractionResultHolder.pass(stack));
    }

    @Inject(method = "useOn", at = @At("HEAD"), cancellable = true)
    private void riftfall$useOn(UseOnContext context, CallbackInfoReturnable<InteractionResult> cir) {
        if (AcquisitionBan.blocked(riftfall$self())) {
            cir.setReturnValue(InteractionResult.FAIL);
            return;
        }
        if (WeaponDonors.applies(riftfall$self())) cir.setReturnValue(InteractionResult.PASS);
    }

    @Inject(method = "releaseUsing", at = @At("HEAD"), cancellable = true)
    private void riftfall$release(Level level, LivingEntity holder, int remaining, CallbackInfo ci) {
        ItemStack stack = riftfall$self();
        if (!WeaponDonors.applies(stack)) return;
        if (stack.getItem() instanceof BowItem) Items.BOW.releaseUsing(stack, level, holder, remaining);
        ci.cancel();
    }

    @Inject(method = "onUseTick", at = @At("HEAD"), cancellable = true)
    private void riftfall$using(Level level, LivingEntity holder, int remaining, CallbackInfo ci) {
        if (WeaponDonors.applies(riftfall$self())) ci.cancel();
    }

    @Inject(method = "inventoryTick", at = @At("HEAD"), cancellable = true)
    private void riftfall$tick(Level level, Entity holder, int slot, boolean selected, CallbackInfo ci) {
        if (WeaponDonors.applies(riftfall$self())) ci.cancel();
    }

    @Inject(method = "hurtEnemy", at = @At("HEAD"), cancellable = true)
    private void riftfall$hit(LivingEntity target, Player player, CallbackInfoReturnable<Boolean> ci) {
        ItemStack stack = riftfall$self();
        if (!WeaponDonors.applies(stack)) return;
        // Keep vanilla wear/stat handling, without the donor's poison, bonus hit or health drain.
        player.awardStat(Stats.ITEM_USED.get(stack.getItem()));
        ci.setReturnValue(true);
    }

    @Inject(method = "postHurtEnemy", at = @At("HEAD"), cancellable = true)
    private void riftfall$wear(LivingEntity target, Player player, CallbackInfo ci) {
        ItemStack stack = riftfall$self();
        if (!WeaponDonors.applies(stack)) return;
        Items.IRON_SWORD.postHurtEnemy(stack, target, player);
        ci.cancel();
    }
}
