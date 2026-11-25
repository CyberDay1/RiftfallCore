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

/** The World Shard: key-style voxel parts posed by ShatteredShardMotion. */
@EventBusSubscriber(modid = RiftfallCore.MODID, value = Dist.CLIENT)
public final class ShatteredShardRenderer extends BlockEntityWithoutLevelRenderer {
    private static final ModelResourceLocation[] MODELS = ShatteredShardMotion.PARTS.stream()
            .map(name -> ModelResourceLocation.standalone(RiftfallCore.id("item/shattered_world_shard/" + name)))
            .toArray(ModelResourceLocation[]::new);

    private ShatteredShardRenderer(BlockEntityRenderDispatcher dispatcher, EntityModelSet models) {
        super(dispatcher, models);
    }

    @SubscribeEvent public static void models(ModelEvent.RegisterAdditional event) {
        for (var model : MODELS) event.register(model);
    }

    @SubscribeEvent public static void extensions(RegisterClientExtensionsEvent event) {
        event.registerItem(new IClientItemExtensions() {
            private ShatteredShardRenderer renderer;
            @Override public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (renderer == null) {
                    var mc = Minecraft.getInstance();
                    renderer = new ShatteredShardRenderer(mc.getBlockEntityRenderDispatcher(), mc.getEntityModels());
                }
                return renderer;
            }
        }, RiftfallItems.SHATTERED_WORLD_SHARD.get());
    }

    @Override public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack pose,
            MultiBufferSource buffers, int light, int overlay) {
        var mc = Minecraft.getInstance();
        double ticks = mc.level == null ? Util.getMillis() / 50.0
                : (double)mc.level.getGameTime() + mc.getTimer().getGameTimeDeltaPartialTick(true);
        var models = mc.getModelManager(); // Resolve again after resource reloads.
        var renderer = mc.getBlockRenderer().getModelRenderer();
        // ItemRenderer has already applied the item's display transform and -0.5 centering.
        pose.pushPose();
        pose.translate(.5, 0, .5);
        for (int i = 0; i < MODELS.length; i++) {
            var part = ShatteredShardMotion.pose(ShatteredShardMotion.PARTS.get(i), ticks);
            var model = models.getModel(MODELS[i]);
            pose.pushPose();
            pose.translate(part.x() / 16, part.y() / 16, part.z() / 16);
            pose.mulPose(Axis.ZP.rotationDegrees((float) part.rz()));
            pose.translate(-.5, 0, -.5);
            for (var pass : model.getRenderPasses(stack, true)) {
                for (var type : pass.getRenderTypes(stack, true)) {
                    var vertices = ItemRenderer.getFoilBufferDirect(buffers, type, true, stack.hasFoil());
                    renderer.renderModel(pose.last(), vertices, null, pass, 1, 1, 1,
                            light, overlay, ModelData.EMPTY, RenderType.cutout());
                }
            }
            pose.popPose();
        }
        pose.popPose();
    }
}
