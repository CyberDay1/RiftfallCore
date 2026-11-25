package com.cyberday1.riftfall.mixin;

import com.cyberday1.riftfall.gear.RiftMobDrops;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Covers hard-coded drops after super.die() and other mob-owned item spawns. */
@Mixin(Entity.class)
public abstract class MixinMobItemSpawns {
    @Inject(method = "spawnAtLocation(Lnet/minecraft/world/item/ItemStack;F)Lnet/minecraft/world/entity/item/ItemEntity;",
            at = @At("HEAD"), cancellable = true)
    private void riftfall$noNormalMobItems(ItemStack stack, float offset, CallbackInfoReturnable<ItemEntity> cir) {
        if (RiftMobDrops.suppressSpawn((Entity) (Object) this)) cir.setReturnValue(null);
    }
}
