package com.cyberday1.riftfall.mixincompat;

import com.cyberday1.riftfall.client.StellarInventory;
import io.wispforest.owo.ui.core.OwoUIDrawContext;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;

/** The H-key layout embeds its inventory/player backgrounds inside surface lambdas. */
@Pseudo
@Mixin(targets = "io.wispforest.accessories.client.gui.AccessoriesExperimentalScreen", remap = false)
public abstract class MixinStellarAccessorySurfaces {
    @Redirect(method = "*", at = @At(value = "INVOKE", target =
            "Lio/wispforest/owo/ui/core/OwoUIDrawContext;blitSprite(Lnet/minecraft/resources/ResourceLocation;IIII)V"))
    private static void riftfall$back(OwoUIDrawContext g, ResourceLocation texture, int x, int y, int w, int h) {
        if (texture.getNamespace().equals("accessories") && texture.getPath().startsWith("widget/back"))
            StellarInventory.arrow(g, x + 2, y + h / 2, true);
        else g.blitSprite(texture, x, y, w, h);
    }

    @Redirect(method = "*", at = @At(value = "INVOKE", target =
            "Lio/wispforest/owo/ui/core/OwoUIDrawContext;blit(Lnet/minecraft/resources/ResourceLocation;IIIIFFIIII)V"))
    private static void riftfall$surface(OwoUIDrawContext g, ResourceLocation texture, int x, int y, int w, int h,
                                   float u, float v, int regionW, int regionH, int textureW, int textureH) {
        if (!StellarInventory.accessoryTexture(g, texture, x, y, w, h))
            g.blit(texture, x, y, w, h, u, v, regionW, regionH, textureW, textureH);
    }
}
