package com.cyberday1.riftfall.mixin;

import com.cyberday1.riftfall.client.StellarContainers;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Leaf draw calls, so themed screens need no per-screen redirects; inert unless a themed screen is open. */
@Mixin(GuiGraphics.class)
public abstract class MixinStellarContainerArt {
    @Inject(method = "blit(Lnet/minecraft/resources/ResourceLocation;IIIFFIIII)V", at = @At("HEAD"), cancellable = true)
    private void riftfall$blit(ResourceLocation tex, int x, int y, int z, float u, float v, int w, int h, int tw, int th, CallbackInfo ci) {
        if (StellarContainers.blit((GuiGraphics) (Object) this, tex, x, y, u, v, w, h, tw, th)) ci.cancel();
    }

    @Inject(method = "blitSprite(Lnet/minecraft/resources/ResourceLocation;IIIII)V", at = @At("HEAD"), cancellable = true)
    private void riftfall$sprite(ResourceLocation sprite, int x, int y, int z, int w, int h, CallbackInfo ci) {
        if (StellarContainers.sprite((GuiGraphics) (Object) this, sprite, x, y, w, h)) ci.cancel();
    }

    @Inject(method = "blitSprite(Lnet/minecraft/resources/ResourceLocation;IIIIIIIII)V", at = @At("HEAD"), cancellable = true)
    private void riftfall$partial(ResourceLocation sprite, int tw, int th, int u, int v, int x, int y, int z, int w, int h, CallbackInfo ci) {
        if (StellarContainers.partialSprite((GuiGraphics) (Object) this, sprite, tw, th, u, v, x, y, w, h)) ci.cancel();
    }

    @ModifyVariable(method = "drawString(Lnet/minecraft/client/gui/Font;Ljava/lang/String;FFIZ)I", at = @At("HEAD"), argsOnly = true)
    private int riftfall$string(int color) { return StellarContainers.text(color); }

    @ModifyVariable(method = "drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/util/FormattedCharSequence;FFIZ)I", at = @At("HEAD"), argsOnly = true)
    private int riftfall$sequence(int color) { return StellarContainers.text(color); }
}
