package com.cyberday1.riftfall.compat.jei;

import com.cyberday1.riftfall.RiftfallCore;
import com.cyberday1.riftfall.compat.viewer.ResidentIngredient;
import com.cyberday1.riftfall.compat.viewer.ViewerLang;
import com.cyberday1.riftfall.lore.ResidentStory;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.IIngredientRenderer;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.rendering.IIngredientRenderContext;
import mezz.jei.api.ingredients.subtypes.UidContext;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** The resident ingredient type, its helper and its renderer (the face from the skin, else the role's icon item). */
public final class ResidentIngredients {
    public static final IIngredientType<ResidentIngredient> TYPE = new IIngredientType<>() {
        @Override public Class<? extends ResidentIngredient> getIngredientClass() { return ResidentIngredient.class; }
        @Override public String getUid() { return RiftfallCore.MODID + ":resident"; }
    };

    private ResidentIngredients() {}

    static final class Helper implements IIngredientHelper<ResidentIngredient> {
        @Override public IIngredientType<ResidentIngredient> getIngredientType() { return TYPE; }
        @Override public String getDisplayName(ResidentIngredient r) { return Component.translatable(r.nameKey()).getString(); }
        @SuppressWarnings("removal")
        @Override public String getUniqueId(ResidentIngredient r, UidContext context) { return r.uid(); }
        @Override public Object getUid(ResidentIngredient r, UidContext context) { return r.uid(); }
        @Override public ResourceLocation getResourceLocation(ResidentIngredient r) { return r.id(); }
        @Override public ResidentIngredient copyIngredient(ResidentIngredient r) { return r; }
        @Override public String getErrorInfo(ResidentIngredient r) { return r == null ? "null resident" : r.uid(); }
    }

    static final class Renderer implements IIngredientRenderer<ResidentIngredient> {
        private final Map<ResourceLocation, Boolean> skins = new ConcurrentHashMap<>();

        @Override public void render(GuiGraphics graphics, ResidentIngredient r, int x, int y, IIngredientRenderContext context) {
            var skin = r.skin();
            if (skins.computeIfAbsent(skin, s -> Minecraft.getInstance().getResourceManager().getResource(s).isPresent()))
                PlayerFaceRenderer.draw(graphics, skin, x, y, 16);
            else graphics.renderFakeItem(new ItemStack(r.icon()), x, y);
        }

        @SuppressWarnings("removal")
        @Override public void render(GuiGraphics graphics, ResidentIngredient r) { render(graphics, r, 0, 0, null); }

        @Override public void getTooltip(ITooltipBuilder tooltip, ResidentIngredient r, Item.TooltipContext context, Player player, TooltipFlag flag) {
            tooltip.addAll(lines(r));
        }

        @SuppressWarnings("removal")
        @Override public List<Component> getTooltip(ResidentIngredient r, TooltipFlag flag) { return lines(r); }

        private static List<Component> lines(ResidentIngredient r) {
            var lines = new ArrayList<Component>();
            lines.add(Component.translatable(r.nameKey()));
            lines.add(Component.translatable(ResidentStory.personKey(r.role())).withStyle(ChatFormatting.GRAY));
            lines.add(Component.translatable(ViewerLang.RESIDENT_TOOLTIP).withStyle(ChatFormatting.DARK_GRAY));
            return lines;
        }
    }
}
