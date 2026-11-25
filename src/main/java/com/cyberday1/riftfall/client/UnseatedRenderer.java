package com.cyberday1.riftfall.client;

import com.cyberday1.riftfall.RiftfallCore;
import com.cyberday1.riftfall.boss.*;
import com.cyberday1.riftfall.block.holycore.WorldCoreModel;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import net.minecraft.client.renderer.culling.Frustum;

final class UnseatedRenderer extends EntityRenderer<UnseatedEntity> {
    private static final ResourceLocation TEXTURE=RiftfallCore.id("textures/entity/unseated/sovereign.png");
    private final PaintedMeshRenderer painter=new PaintedMeshRenderer(TEXTURE);
    UnseatedRenderer(EntityRendererProvider.Context context){super(context);}
    @Override public ResourceLocation getTextureLocation(UnseatedEntity entity){return TEXTURE;}
    @Override public boolean shouldRender(UnseatedEntity entity,Frustum frustum,double x,double y,double z) {
        return entity.shouldRender(x,y,z) && frustum.isVisible(new AABB(entity.getX()-8,entity.getY()-.5,entity.getZ()-8,
                entity.getX()+8,entity.getY()+17,entity.getZ()+8));
    }
    private static final java.util.concurrent.atomic.AtomicBoolean WARNED=new java.util.concurrent.atomic.AtomicBoolean();
    /** The mesh failed to load (a heavier export than the loader accepts, or a broken file): boxes, and one log line. */
    private static void silhouette(float yaw,PoseStack poses,MultiBufferSource buffers,int light) {
        if(WARNED.compareAndSet(false,true))
            com.mojang.logging.LogUtils.getLogger().error("The Unseated's painted mesh is not loaded; drawing a plain silhouette so the fight still shows a body");
        poses.pushPose();poses.mulPose(Axis.YP.rotationDegrees(-yaw));
        var out=buffers.getBuffer(net.minecraft.client.renderer.RenderType.entitySolid(RiftfallCore.id("textures/block/world_core/white.png")));
        var pose=poses.last();int c=UnseatedSilhouette.COLOR;
        for(float[] b:UnseatedSilhouette.BOXES) {
            float x0=b[0],y0=b[1],z0=b[2],x1=b[3],y1=b[4],z1=b[5];
            float[][] faces={
                    {x0,y0,z1,x1,y0,z1,x1,y1,z1,x0,y1,z1,0,0,1},{x1,y0,z0,x0,y0,z0,x0,y1,z0,x1,y1,z0,0,0,-1},
                    {x1,y0,z1,x1,y0,z0,x1,y1,z0,x1,y1,z1,1,0,0},{x0,y0,z0,x0,y0,z1,x0,y1,z1,x0,y1,z0,-1,0,0},
                    {x0,y1,z1,x1,y1,z1,x1,y1,z0,x0,y1,z0,0,1,0},{x0,y0,z0,x1,y0,z0,x1,y0,z1,x0,y0,z1,0,-1,0}};
            for(float[] f:faces)for(int v=0;v<4;v++)
                out.addVertex(pose,f[v*3],f[v*3+1],f[v*3+2]).setColor((c>>16)&255,(c>>8)&255,c&255,255).setUv(.5f,.5f)
                        .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose,f[12],f[13],f[14]);
        }
        poses.popPose();
    }
    @Override public void render(UnseatedEntity entity,float yaw,float partial,PoseStack poses,MultiBufferSource buffers,int light) {
        var mesh=UnseatedPreviewRenderer.mesh();
        if(mesh==null){
            // Still reading (prefetched when the entity arrives): nothing for those few frames.
            if(UnseatedPreviewRenderer.MESH.failed())silhouette(yaw,poses,buffers,light);
            super.render(entity,yaw,partial,poses,buffers,light);return;
        }
        double ticks=(entity.level().getGameTime()%240)+partial;
        var motion=new UnseatedPose(mesh.lower,ticks,entity.phase(),entity.action(),Math.max(0,entity.attackTicks(partial)));
        float fade=UnseatedEncounter.smooth(entity.actionTicks(partial)/8);
        var previous=new UnseatedPose(mesh.lower,ticks,entity.previousPhase(),entity.previousAction(),entity.previousActionTicks());
        var old=new WorldCoreModel.Transform();
        poses.pushPose();poses.mulPose(Axis.YP.rotationDegrees(-yaw));
        float energy=(float)(.30+.70*Math.min(1,WorldCoreModel.heartbeat(4,ticks)));
        painter.render(mesh,0,ticks,poses,buffers,light,OverlayTexture.NO_OVERLAY,energy,false,(name,t)->{
            if(fade>=1){motion.apply(name,t);return;}
            old.x=t.x;old.y=t.y;old.z=t.z;old.sx=t.sx;old.sy=t.sy;old.sz=t.sz;old.rotation.set(t.rotation);
            previous.apply(name,old);motion.apply(name,t);
            t.x=old.x+(t.x-old.x)*fade;t.y=old.y+(t.y-old.y)*fade;t.z=old.z+(t.z-old.z)*fade;
            t.sx=old.sx+(t.sx-old.sx)*fade;t.sy=old.sy+(t.sy-old.sy)*fade;t.sz=old.sz+(t.sz-old.sz)*fade;
            old.rotation.slerp(t.rotation,fade);t.rotation.set(old.rotation);
        });
        poses.popPose();super.render(entity,yaw,partial,poses,buffers,light);
    }
}
