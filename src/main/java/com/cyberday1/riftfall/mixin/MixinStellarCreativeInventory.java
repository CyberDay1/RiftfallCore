package com.cyberday1.riftfall.mixin;

import com.cyberday1.riftfall.client.StellarInventory;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.EffectRenderingInventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.CreativeModeTab;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;

/** Changes art only: NeoForge still owns tab paging, search, slots and creative actions. */
@Mixin(CreativeModeInventoryScreen.class)
public abstract class MixinStellarCreativeInventory extends EffectRenderingInventoryScreen<CreativeModeInventoryScreen.ItemPickerMenu> {
    @Shadow private static CreativeModeTab selectedTab;
    @Shadow private EditBox searchBox;
    protected MixinStellarCreativeInventory(CreativeModeInventoryScreen.ItemPickerMenu menu, Inventory inv, Component title) { super(menu, inv, title); }

    @Redirect(method = "renderBg", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/gui/GuiGraphics;blit(Lnet/minecraft/resources/ResourceLocation;IIIIII)V"))
    private void riftfall$background(GuiGraphics g, ResourceLocation texture, int x, int y, int u, int v, int w, int h) {
        StellarInventory.creativeBackground(g, x, y, w, h, menu,
                selectedTab.getType() == CreativeModeTab.Type.INVENTORY, selectedTab.canScroll());
        if (searchBox.isVisible()) StellarInventory.control(g, searchBox.getX() - 2, searchBox.getY() - 2,
                searchBox.getWidth() + 4, searchBox.getHeight() + 4, false, searchBox.isFocused());
    }

    @Redirect(method = {"renderBg", "renderTabButton"}, at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/gui/GuiGraphics;blitSprite(Lnet/minecraft/resources/ResourceLocation;IIII)V"))
    private void riftfall$tab(GuiGraphics g, ResourceLocation texture, int x, int y, int w, int h) {
        String path = texture.getPath();
        boolean selected = path.contains("tab_") && !path.contains("unselected");
        StellarInventory.control(g, x, y, w, h, selected, selected || h == 15);
    }

    @ModifyArg(method = "renderLabels", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIIZ)I"), index = 4)
    private int riftfall$label(int color) { return StellarInventory.labelColor(); }

    @Redirect(method = "init", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/gui/components/Button$Builder;build()Lnet/minecraft/client/gui/components/Button;"))
    private Button riftfall$pages(Button.Builder builder) {
        Button nativeButton = builder.build();
        return new Button(nativeButton.getX(), nativeButton.getY(), nativeButton.getWidth(), nativeButton.getHeight(),
                nativeButton.getMessage(), b -> nativeButton.onPress(), supplier -> supplier.get()) {
            @Override public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partial) {
                StellarInventory.control(g, getX(), getY(), width, height, isHoveredOrFocused(), isFocused());
                StellarInventory.arrow(g, getX() + 8, getY() + height / 2, getMessage().getString().equals("<"));
            }
        };
    }
}
