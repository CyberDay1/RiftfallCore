package com.cyberday1.riftfall.mixincompat;

import com.cyberday1.riftfall.client.StellarInventory;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;

/** CustomNPCs' inventory, factions and quest tabs keep their item icons and native navigation. */
@Pseudo
@Mixin(targets = "noppes.npcs.client.gui.player.tabs.AbstractTab", remap = false)
public abstract class MixinStellarNpcTabs extends AbstractButton {
    protected MixinStellarNpcTabs(int x, int y, int w, int h, Component title) { super(x, y, w, h, title); }

    @Redirect(method = "renderWidget", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/gui/GuiGraphics;blit(Lnet/minecraft/resources/ResourceLocation;IIIIII)V"))
    private void riftfall$tab(GuiGraphics g, ResourceLocation texture, int x, int y, int u, int v, int w, int h) {
        // CustomNPCs disables the currently selected tab.
        StellarInventory.control(g, x, y, w, h, isHoveredOrFocused(), !active);
    }
}
