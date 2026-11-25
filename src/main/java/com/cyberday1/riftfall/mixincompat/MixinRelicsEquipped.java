package com.cyberday1.riftfall.mixincompat;

import com.cyberday1.riftfall.gear.CurioSilence;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * Relics' event handlers and its own mixins ask these two methods whether a relic is worn before acting, so a
 * silenced relic answers "not worn" and every one of them stands down.
 */
@Pseudo
@Mixin(targets = "it.hurts.sskirillss.relics.utils.EntityUtils", remap = false)
public abstract class MixinRelicsEquipped {

    @Inject(method = "findEquippedCurio(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/item/Item;)Lnet/minecraft/world/item/ItemStack;",
            at = @At("HEAD"), cancellable = true)
    private static void riftfall$hideOne(Entity entity, Item item, CallbackInfoReturnable<ItemStack> cir) {
        if (CurioSilence.silenced(item)) {
            CurioSilence.hidLookup();
            cir.setReturnValue(ItemStack.EMPTY);
        }
    }

    @Inject(method = "findEquippedCurios(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/item/Item;)Ljava/util/List;",
            at = @At("HEAD"), cancellable = true)
    private static void riftfall$hideAll(Entity entity, Item item, CallbackInfoReturnable<List<ItemStack>> cir) {
        if (CurioSilence.silenced(item)) {
            CurioSilence.hidLookup();
            cir.setReturnValue(List.of());
        }
    }
}
