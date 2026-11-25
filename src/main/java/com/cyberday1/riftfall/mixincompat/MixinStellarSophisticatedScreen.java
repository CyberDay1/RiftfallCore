package com.cyberday1.riftfall.mixincompat;

import com.cyberday1.riftfall.client.StellarSophisticated;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.p3pp3rf1y.sophisticatedcore.client.gui.utils.Position;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.*;

/** Backpack and storage screens: panel, upgrade column, labels. Slots and controls stay native. */
@Pseudo
@Mixin(targets = "net.p3pp3rf1y.sophisticatedcore.client.gui.StorageScreenBase", remap = false)
public abstract class MixinStellarSophisticatedScreen {
    @Redirect(method = "drawInventoryBg", at = @At(value = "INVOKE", target =
            "Lnet/p3pp3rf1y/sophisticatedcore/client/gui/StorageGuiHelper;renderStorageBackground(Lnet/p3pp3rf1y/sophisticatedcore/client/gui/utils/Position;Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/resources/ResourceLocation;II)V"))
    private void riftfall$background(Position pos, GuiGraphics g, ResourceLocation texture, int w, int slotsHeight) {
        StellarSophisticated.background(g, pos.x(), pos.y(), w, slotsHeight + 114);
    }

    @Redirect(method = "drawUpgradeBackground", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/gui/GuiGraphics;blit(Lnet/minecraft/resources/ResourceLocation;IIFFIIII)V"))
    private void riftfall$upgrades(GuiGraphics g, ResourceLocation tex, int x, int y, float u, float v,
                                   int w, int h, int tw, int th) {
        if (!StellarSophisticated.upgradePiece(g, tex, x, y, (int) v, w, h)) g.blit(tex, x, y, u, v, w, h, tw, th);
    }

    @ModifyConstant(method = {"renderLabels", "renderStorageTitle"}, constant = @Constant(intValue = 0x404040), require = 3)
    private int riftfall$label(int color) { return StellarSophisticated.label(color); }
}
