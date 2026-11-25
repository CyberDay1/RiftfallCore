package com.cyberday1.riftfall.mixincompat;

import com.cyberday1.riftfall.client.StellarInventory;
import io.wispforest.owo.ui.core.OwoUIDrawContext;
import io.wispforest.owo.ui.core.PositionedRectangle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.resources.ResourceLocation;

/** Match Accessories' theme IDs only; other owo screens keep their own surfaces. */
@Pseudo
@Mixin(targets = "io.wispforest.owo.ui.util.NinePatchTexture", remap = false)
public abstract class MixinStellarAccessoryPatches {
    @Inject(method = "draw(Lnet/minecraft/resources/ResourceLocation;Lio/wispforest/owo/ui/core/OwoUIDrawContext;IIII)V", at = @At("HEAD"), cancellable = true)
    private static void riftfall$patch(ResourceLocation texture, OwoUIDrawContext g, int x, int y, int w, int h, CallbackInfo ci) {
        if (StellarInventory.accessoryPatch(g, texture, x, y, w, h)) ci.cancel();
    }
    @Inject(method = "draw(Lnet/minecraft/resources/ResourceLocation;Lio/wispforest/owo/ui/core/OwoUIDrawContext;Lio/wispforest/owo/ui/core/PositionedRectangle;)V", at = @At("HEAD"), cancellable = true)
    private static void riftfall$rectangle(ResourceLocation texture, OwoUIDrawContext g, PositionedRectangle area, CallbackInfo ci) {
        if (StellarInventory.accessoryPatch(g, texture, area.x(), area.y(), area.width(), area.height())) ci.cancel();
    }
}
