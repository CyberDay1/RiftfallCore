package com.cyberday1.riftfall.client;

import com.cyberday1.riftfall.RiftfallCore;
import com.cyberday1.riftfall.block.holycore.HolyCoreBlockEntity;
import com.cyberday1.riftfall.block.holycore.HolyCoreMotion;
import com.cyberday1.riftfall.block.holycore.HolyCoreAnimation;
import com.cyberday1.riftfall.block.holycore.HolyCoreStructure;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.client.model.data.ModelData;
import java.util.function.Consumer;

/** Draws the authored shrine once, centered on the middle of the physical pedestal. */
public final class HolyCoreRenderer implements BlockEntityRenderer<HolyCoreBlockEntity> {
    private static final ModelResourceLocation[][] MODELS = new ModelResourceLocation[5][HolyCoreMotion.PARTS.size()];
    private final java.util.Map<HolyCoreBlockEntity, HolyCoreAnimation> animations = new java.util.WeakHashMap<>();
    static {
        for (int s = 0; s < 5; s++) for (int i = 0; i < HolyCoreMotion.PARTS.size(); i++) {
            MODELS[s][i] = ModelResourceLocation.standalone(RiftfallCore.id("block/holy_core/stage_" + (s + 1)
                    + "/" + HolyCoreMotion.PARTS.get(i)));
        }
    }
    private final BlockEntityRendererProvider.Context context;
    public HolyCoreRenderer(BlockEntityRendererProvider.Context context) { this.context = context; }
    public static void registerModels(Consumer<ModelResourceLocation> register) {
        for (var stage : MODELS) for (var model : stage) register.accept(model);
    }
    @Override
    public void render(HolyCoreBlockEntity core, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (core.getLevel() == null) return;
        // Cast before adding: long + float otherwise loses sub-tick precision in older worlds.
        double now = (double) core.getLevel().getGameTime() + partialTick;
        var frame = animations.computeIfAbsent(core, ignored -> new HolyCoreAnimation())
                .sample(now, core.visualPhase(now), core.phase(), core.formed(), core.partMask());
        if (WorldCoreMesh.INSTANCE.ready()) {
            pose.pushPose();
            pose.translate(.5, 0, .5);
            WorldCoreMesh.INSTANCE.render(frame.phase(), frame.ticks(), pose, buffers, light, overlay);
            pose.popPose();
            return;
        }
        var renderer = context.getBlockRenderDispatcher().getModelRenderer();
        var models = Minecraft.getInstance().getModelManager();
        pose.pushPose();
        pose.translate(.5, 0, .5);
        float scale = (float) HolyCoreStructure.MODEL_SCALE;
        pose.scale(scale, scale, scale);
        for (int i = 0; i < HolyCoreMotion.PARTS.size(); i++) {
            String name = HolyCoreMotion.PARTS.get(i);
            double visibility = name.startsWith("rib_") ? frame.ribs()[name.charAt(4) - '0'] : 1;
            if (visibility <= 0) continue;
            var part = HolyCoreMotion.pose(name, frame.phase(), frame.ticks(), frame.haloOrbit());
            pose.pushPose();
            pose.translate(part.x() / 16, part.y() / 16, part.z() / 16);
            pose.mulPose(Axis.ZP.rotationDegrees((float) part.rz()));
            pose.mulPose(Axis.YP.rotationDegrees((float) part.ry()));
            pose.mulPose(Axis.XP.rotationDegrees((float) part.rx()));
            pose.scale((float) part.sx(), (float) part.sy(), (float) part.sz());
            pose.translate(-.5, 0, -.5);
            for (int pass = 0; pass < 2; pass++) {
                double opacity = visibility * (pass == 0 ? 1 - frame.blend() : frame.blend());
                if (opacity <= 0) continue;
                int stage = pass == 0 ? frame.lowerStage() : frame.upperStage();
                var into = opacity >= 1 ? buffers.getBuffer(Sheets.cutoutBlockSheet())
                        : new CoreFadeVertices(buffers.getBuffer(Sheets.translucentCullBlockSheet()), opacity);
                renderer.renderModel(pose.last(), into, null, models.getModel(MODELS[stage][i]),
                        1, 1, 1, light, overlay, ModelData.EMPTY, RenderType.cutout());
            }
            pose.popPose();
        }
        pose.popPose();
    }
    @Override public int getViewDistance() { return RiftfallClientDistances.renderBlocks(); }
    @Override public AABB getRenderBoundingBox(HolyCoreBlockEntity core) {
        return new AABB(core.getBlockPos()).inflate(2, 0, 2).expandTowards(0, 6, 0);
    }
}
