package com.cyberday1.riftfall.client;

import com.cyberday1.riftfall.RiftfallCore;
import com.cyberday1.riftfall.block.holycore.WorldCoreModel;
import com.cyberday1.riftfall.block.holycore.WorldCoreBlend;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;

/** Resource-pack-aware native geometry; retains the painted surfaces and separate tilted orbits. */
// Read on first use and kept while the player is home
public final class WorldCoreMesh implements ResourceManagerReloadListener {
    public static final WorldCoreMesh INSTANCE = new WorldCoreMesh();
    static final long IDLE_MILLIS = 30_000;
    private final LazyMesh<WorldCoreBlend[]> stages = new LazyMesh<>("World Core", WorldCoreMesh::read,
            net.minecraft.Util.backgroundExecutor(), net.minecraft.Util::getMillis, IDLE_MILLIS);
    private final PaintedMeshRenderer renderer = new PaintedMeshRenderer();
    private static ModelResourceLocation pedestal() {
        return ModelResourceLocation.standalone(RiftfallCore.id("block/world_core/pedestal"
                + (ModList.get().isLoaded("chipped") ? "_chipped" : "")));
    }
    public static void registerModels(java.util.function.Consumer<ModelResourceLocation> register) {
        register.accept(pedestal());
    }

    private WorldCoreMesh() {}
    /** True once the meshes are in memory; the first call starts reading them. */
    public boolean ready() { return stages.get() != null; }
    /** Blocking read, for the probes. */
    public boolean loadNow() { return stages.loadNow(); }
    void prefetch() { stages.prefetch(); }
    void tick() { stages.tick(); }

    @Override public void onResourceManagerReload(ResourceManager manager) { stages.invalidate(); }

    private static WorldCoreBlend[] read() throws java.io.IOException {
        var manager = Minecraft.getInstance().getResourceManager();
        var next = new WorldCoreModel[5];
        for (int i = 0; i < 5; i++) {
            var resource = manager.getResourceOrThrow(RiftfallCore.id("world_core/stage_" + (i+1) + ".wcm.gz"));
            try (var in = resource.open()) { next[i] = WorldCoreModel.read(in); }
        }
        var blends = new WorldCoreBlend[5];
        for (int i=0;i<5;i++) blends[i]=new WorldCoreBlend(next[i],next[Math.min(4,i+1)]);
        return blends;
    }

    public void render(double phase, double ticks, PoseStack poses, MultiBufferSource buffers, int light, int overlay) {
        var loaded = stages.get();
        if (loaded == null) return;
        phase = Math.clamp(phase, 0, 4);
        int stage = (int) phase;
        float blend = (float)(phase-stage);
        var mc = Minecraft.getInstance();
        // The socket stays solid throughout restoration; it is never cross-faded.
        poses.pushPose();
        poses.translate(-.5, 0, -.5);
        mc.getBlockRenderer().getModelRenderer().renderModel(poses.last(), buffers.getBuffer(Sheets.cutoutBlockSheet()), null,
                mc.getModelManager().getModel(pedestal()), 1, 1, 1, light, overlay, ModelData.EMPTY, RenderType.cutout());
        poses.popPose();
        float energy = WorldCoreBlend.mix(energy(stage,ticks),energy(Math.min(4,stage+1),ticks),blend);
        renderer.render(loaded[stage],blend,ticks,poses,buffers,light,overlay,energy,true);
    }

    private static float energy(int stage,double ticks) {
        return stage<2?1:(float)(.52+.48*Math.min(1,WorldCoreModel.heartbeat(stage,ticks)));
    }
}
