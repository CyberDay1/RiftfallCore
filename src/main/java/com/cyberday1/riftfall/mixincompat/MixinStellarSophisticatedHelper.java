package com.cyberday1.riftfall.mixincompat;

import com.cyberday1.riftfall.client.StellarSophisticated;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.p3pp3rf1y.sophisticatedcore.client.gui.utils.TextureBlitData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Sophisticated's shared draw helpers: buttons, slot grids, tabs and insets. Icons keep their sprites. */
@Pseudo
@Mixin(targets = "net.p3pp3rf1y.sophisticatedcore.client.gui.utils.GuiHelper", remap = false)
public abstract class MixinStellarSophisticatedHelper {
    @Redirect(method = "blit(Lnet/minecraft/client/gui/GuiGraphics;IILnet/p3pp3rf1y/sophisticatedcore/client/gui/utils/TextureBlitData;)V",
            at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/gui/GuiGraphics;blit(Lnet/minecraft/resources/ResourceLocation;IIFFIIII)V"))
    private static void riftfall$piece(GuiGraphics g, ResourceLocation tex, int x, int y, float u, float v,
                                       int w, int h, int tw, int th) {
        if (!StellarSophisticated.controlPiece(g, tex, x, y, (int) u, (int) v, w, h)
                && !StellarSophisticated.icon(g, tex, x, y, u, v, w, h, tw, th))
            g.blit(tex, x, y, u, v, w, h, tw, th);
    }

    @Inject(method = "blit(Lnet/minecraft/client/gui/GuiGraphics;IILnet/p3pp3rf1y/sophisticatedcore/client/gui/utils/TextureBlitData;II)V",
            at = @At("HEAD"), cancellable = true)
    private static void riftfall$stretched(GuiGraphics g, int x, int y, TextureBlitData data, int w, int h, CallbackInfo ci) {
        if (StellarSophisticated.controlPiece(g, data.getTextureName(), x + data.getXOffset(), y + data.getYOffset(),
                data.getU(), data.getV(), w, h)) ci.cancel();
    }

    @Inject(method = "renderControlBackground(Lnet/minecraft/client/gui/GuiGraphics;IIIIIIII)V", at = @At("HEAD"), cancellable = true)
    private static void riftfall$control(GuiGraphics g, int x, int y, int w, int h, int u, int v, int tw, int th, CallbackInfo ci) {
        if (StellarSophisticated.controlBackground(g, x, y, w, h, u, v)) ci.cancel();
    }

    @Inject(method = "renderTiledControlBackground(Lnet/minecraft/client/gui/GuiGraphics;IIIIIIIIIIII)V", at = @At("HEAD"), cancellable = true)
    private static void riftfall$tiled(GuiGraphics g, int x, int y, int w, int h, int u, int v, int tw, int th,
                                       int left, int right, int top, int bottom, CallbackInfo ci) {
        if (StellarSophisticated.controlBackground(g, x, y, w, h, u, v)) ci.cancel();
    }

    @Inject(method = "renderTabBackground", at = @At("HEAD"), cancellable = true)
    private static void riftfall$tab(GuiGraphics g, int x, int y, int w, int h, CallbackInfo ci) {
        StellarSophisticated.tab(g, x, y, w, h);
        ci.cancel();
    }
}
