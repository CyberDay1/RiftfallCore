package com.cyberday1.riftfall.mixin;

import com.cyberday1.riftfall.home.ResidentTileProtection;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Buckets neither empty into nor fill from a resident's tile. Emptying is refused where the fluid would land, so
 * dispensers are covered; filling is refused at the source block, in the one check vanilla's own spawn
 * protection uses.
 */
@Mixin(BucketItem.class)
public abstract class MixinResidentTileBucket {
    @Inject(method = "emptyContents(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/level/Level;"
            + "Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/phys/BlockHitResult;Lnet/minecraft/world/item/ItemStack;)Z",
            at = @At("HEAD"), cancellable = true)
    private void riftfall$keepResidentTileDry(@Nullable Player player, Level level, BlockPos pos, @Nullable BlockHitResult hit,
            @Nullable ItemStack container, CallbackInfoReturnable<Boolean> cir) {
        if (ResidentTileProtection.protectedAt(level, pos)) {
            ResidentTileProtection.tell(player, level);
            cir.setReturnValue(false);
        }
    }

    @WrapOperation(method = "use", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;mayInteract(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/core/BlockPos;)Z"))
    private boolean riftfall$keepResidentTileWater(Level level, Player player, BlockPos pos, Operation<Boolean> original) {
        if (((BucketItem) (Object) this).content == Fluids.EMPTY && ResidentTileProtection.protectedAt(level, pos)) {
            ResidentTileProtection.tell(player, level);
            return false;
        }
        return original.call(level, player, pos);
    }
}
