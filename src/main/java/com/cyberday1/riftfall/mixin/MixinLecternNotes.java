package com.cyberday1.riftfall.mixin;

import com.cyberday1.riftfall.init.RiftfallItems;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A story note counts as a lectern book, so its lectern menu stays open (vanilla checks the item type). */
@Mixin(LecternBlockEntity.class)
public abstract class MixinLecternNotes {
    @Shadow private ItemStack book;

    @Inject(method = "hasBook", at = @At("HEAD"), cancellable = true)
    private void riftfall$noteIsABook(CallbackInfoReturnable<Boolean> cir) {
        if (book.is(RiftfallItems.LOST_NOTE.get())) cir.setReturnValue(true);
    }
}
