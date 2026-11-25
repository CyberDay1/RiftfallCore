package com.cyberday1.riftfall.mixincompat;

import com.cyberday1.riftfall.gear.InfinityHam;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Marinade is off, so a ham poured before the ruling loses its stored effects on its next server tick (inventory,
 * or a backpack's feeding upgrade, which calls this tick too). Eating never applies them either way.
 */
@Pseudo
@Mixin(targets = "it.hurts.sskirillss.relics.items.relics.InfiniteHamItem", remap = false)
public abstract class MixinRelicsHamMarinade {

    @Inject(method = "inventoryTick(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/Entity;IZ)V",
            at = @At("HEAD"))
    private void riftfall$stripMarinade(ItemStack stack, Level level, Entity holder, int slot, boolean selected,
                                        CallbackInfo ci) {
        if (!level.isClientSide()) InfinityHam.strip(stack);
    }
}
