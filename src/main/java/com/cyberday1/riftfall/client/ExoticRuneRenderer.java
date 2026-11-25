package com.cyberday1.riftfall.client;

import com.cyberday1.riftfall.RiftfallCore;
import com.cyberday1.riftfall.init.RiftfallItems;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.client.model.data.ModelData;

/** Exotic-only animation; uses the same baked-part path as the world fragment. */
@EventBusSubscriber(modid = RiftfallCore.MODID, value = Dist.CLIENT)
public final class ExoticRuneRenderer extends BlockEntityWithoutLevelRenderer {
    private static final ModelResourceLocation[] MODELS = ExoticRuneMotion.PARTS.stream()
            .map(name -> ModelResourceLocation.standalone(RiftfallCore.id("item/exotic_runestone/" + name)))
            .toArray(ModelResourceLocation[]::new);

    private ExoticRuneRenderer(BlockEntityRenderDispatcher dispatcher, EntityModelSet models) {
        super(dispatcher, models);
    }

    @SubscribeEvent public static void models(ModelEvent.RegisterAdditional event) {
        for (var model : MODELS) event.register(model);
    }

    @SubscribeEvent public static void extensions(RegisterClientExtensionsEvent event) {
        event.registerItem(new IClientItemExtensions() {
            private ExoticRuneRenderer renderer;
            @Override public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (renderer == null) {
                    var mc = Minecraft.getInstance();
                    renderer = new ExoticRuneRenderer(mc.getBlockEntityRenderDispatcher(), mc.getEntityModels());
                }
                return renderer;
            }
        }, RiftfallItems.EXOTIC_RUNESTONE.get());
    }

    @Override public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack pose,
            MultiBufferSource buffers, int light, int overlay) {
        var mc = Minecraft.getInstance();
        double ticks = mc.level == null ? Util.getMillis() / 50.0
                : mc.level.getGameTime() + mc.getTimer().getGameTimeDeltaPartialTick(true);
        var models = mc.getModelManager();
        var renderer = mc.getBlockRenderer().getModelRenderer();
        for (int i = 0; i < MODELS.length; i++) {
            var motion = ExoticRuneMotion.pose(ExoticRuneMotion.PARTS.get(i), ticks);
            pose.pushPose();
            // ItemRenderer already applied the display transform. Rotate/scale
            // the baked model around its own center exactly once.
            pose.translate(.5 + motion.x() / 16, .5 + motion.y() / 16, .5 + motion.z() / 16);
            pose.mulPose(Axis.ZP.rotationDegrees((float) motion.rz()));
            pose.scale((float) motion.scale(), (float) motion.scale(), (float) motion.scale());
            pose.translate(-.5, -.5, -.5);
            var model = models.getModel(MODELS[i]);
            for (var pass : model.getRenderPasses(stack, true)) {
                for (var type : pass.getRenderTypes(stack, true)) {
                    var vertices = ItemRenderer.getFoilBufferDirect(buffers, type, true, stack.hasFoil());
                    renderer.renderModel(pose.last(), vertices, null, pass, 1, 1, 1,
                            light, overlay, ModelData.EMPTY, RenderType.cutout());
                }
            }
            pose.popPose();
        }
    }
}
