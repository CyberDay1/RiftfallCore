package com.cyberday1.riftfall.mixincompat;

import com.cyberday1.riftfall.client.StellarSophisticated;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.p3pp3rf1y.sophisticatedcore.client.gui.utils.Position;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.*;

/** The settings screen shares the storage panel; its tabs and buttons go through GuiHelper. */
@Pseudo
@Mixin(targets = "net.p3pp3rf1y.sophisticatedcore.client.gui.SettingsScreen", remap = false)
public abstract class MixinStellarSophisticatedSettings {
    @Redirect(method = "renderBg", at = @At(value = "INVOKE", target =
            "Lnet/p3pp3rf1y/sophisticatedcore/client/gui/StorageGuiHelper;renderStorageBackground(Lnet/p3pp3rf1y/sophisticatedcore/client/gui/utils/Position;Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/resources/ResourceLocation;II)V"))
    private void riftfall$background(Position pos, GuiGraphics g, ResourceLocation texture, int w, int slotsHeight) {
        StellarSophisticated.background(g, pos.x(), pos.y(), w, slotsHeight + 114);
    }

    @ModifyConstant(method = {"renderLabels", "renderSettingsTitle"}, constant = @Constant(intValue = 0x404040), require = 3)
    private int riftfall$label(int color) { return StellarSophisticated.label(color); }
}
