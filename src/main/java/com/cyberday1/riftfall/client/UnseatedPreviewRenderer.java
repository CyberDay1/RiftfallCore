package com.cyberday1.riftfall.client;

import com.cyberday1.riftfall.RiftfallCore;
import com.cyberday1.riftfall.boss.UnseatedPreviewEntity;
import com.cyberday1.riftfall.block.holycore.WorldCoreBlend;
import com.cyberday1.riftfall.block.holycore.WorldCoreModel;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;

/** Baked painted surfaces through Minecraft's entity pipeline, including shader packs. */
final class UnseatedPreviewRenderer extends EntityRenderer<UnseatedPreviewEntity> {
    private static final ResourceLocation TEXTURE = RiftfallCore.id("textures/entity/unseated/sovereign.png");
    // Read on first use, kept while an Unseated is loaded
    static final LazyMesh<WorldCoreBlend> MESH = new LazyMesh<>("Unseated", UnseatedPreviewRenderer::read,
            net.minecraft.Util.backgroundExecutor(), net.minecraft.Util::getMillis, WorldCoreMesh.IDLE_MILLIS);
    /** The mesh, or null while it loads or when it failed ({@link LazyMesh#failed()}). */
    static WorldCoreBlend mesh() { return MESH.get(); }
    static final ResourceManagerReloadListener RELOAD = resources -> MESH.invalidate();

    private static WorldCoreBlend read() throws java.io.IOException {
        var resources = net.minecraft.client.Minecraft.getInstance().getResourceManager();
        try (var in = resources.getResourceOrThrow(RiftfallCore.id("entity/unseated/sovereign.wcm.gz")).open()) {
            var model = WorldCoreModel.read(in);
            return new WorldCoreBlend(model, model);
        }
    }
    private final PaintedMeshRenderer painter = new PaintedMeshRenderer(TEXTURE);

    UnseatedPreviewRenderer(EntityRendererProvider.Context context) { super(context); }

    @Override public ResourceLocation getTextureLocation(UnseatedPreviewEntity entity) { return TEXTURE; }

    @Override public void render(UnseatedPreviewEntity entity, float yaw, float partialTick,
                                 PoseStack poses, MultiBufferSource buffers, int light) {
        var loaded = MESH.get();
        if (loaded == null) return;
        poses.pushPose();
        // Authored front is +Z; vanilla yaw increases towards -X.
        poses.mulPose(Axis.YP.rotationDegrees(-yaw));
        double ticks = entity.animationTicks(partialTick);
        float heartbeat = (float)(.30 + .70 * Math.min(1, WorldCoreModel.heartbeat(4, ticks)));
        painter.render(loaded, 0, ticks, poses, buffers,
                light, OverlayTexture.NO_OVERLAY, heartbeat, false);
        poses.popPose();
        super.render(entity, yaw, partialTick, poses, buffers, light);
    }
}
