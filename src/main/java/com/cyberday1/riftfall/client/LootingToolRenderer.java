package com.cyberday1.riftfall.client;

import com.cyberday1.riftfall.tool.LootingToolItem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;

/** Bridges builtin/entity cosmetics to their source mod's renderer, without changing the real item. */
final class LootingToolRenderer extends BlockEntityWithoutLevelRenderer {
    LootingToolRenderer(BlockEntityRenderDispatcher dispatcher, EntityModelSet models) {
        super(dispatcher, models);
    }

    @Override public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack pose,
            MultiBufferSource buffers, int light, int overlay) {
        var id = LootingToolItem.appearance(stack).item();
        if (!BuiltInRegistries.ITEM.containsKey(id)) return;
        var source = BuiltInRegistries.ITEM.get(id);
        if (source instanceof LootingToolItem) return; // Never recursively render ourselves.
        var renderer = IClientItemExtensions.of(source).getCustomRenderer();
        if (renderer == null || renderer == this) return;
        // ItemRenderer already applied the selected source model's transforms. Delegating
        // directly avoids applying those transforms (and the centering offset) twice.
        renderer.renderByItem(visualStack(stack, source), context, pose, buffers, light, overlay);
    }

    static ItemStack visualStack(ItemStack tool, Item source) {
        var visual = new ItemStack(source);
        visual.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, tool.hasFoil());
        // A disposable render-only stack: no progression, identity or upgrades reach a
        // third-party renderer. Source use/tick/attack logic is never invoked.
        return visual;
    }
}
