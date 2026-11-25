package com.cyberday1.riftfall.client;

import com.cyberday1.riftfall.RiftfallCore;
import com.cyberday1.riftfall.boss.UnseatedTelegraph;
import com.cyberday1.riftfall.boss.UnseatedTetherEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/** An ASCENT tether: a slowly turning full-bright pillar with a crown of four fins, dimmer as it takes damage. */
final class UnseatedTetherRenderer extends EntityRenderer<UnseatedTetherEntity> {
    private static final ResourceLocation TEXTURE=RiftfallCore.id("textures/block/world_core/white.png");
    /** x0,y0,z0,x1,y1,z1 from the tether's feet: the shaft, then four fins near the top. */
    private static final float[][] BOXES={
            {-.3f,0,-.3f,.3f,5f,.3f},
            {-.75f,3.6f,-.08f,-.3f,4.6f,.08f},{.3f,3.6f,-.08f,.75f,4.6f,.08f},
            {-.08f,3.6f,-.75f,.08f,4.6f,-.3f},{-.08f,3.6f,.3f,.08f,4.6f,.75f},
    };
    UnseatedTetherRenderer(EntityRendererProvider.Context context){super(context);}
    @Override public ResourceLocation getTextureLocation(UnseatedTetherEntity entity){return TEXTURE;}
    @Override public void render(UnseatedTetherEntity entity,float yaw,float partial,PoseStack poses,MultiBufferSource buffers,int light){
        float health=Math.clamp(entity.getHealth()/entity.getMaxHealth(),0,1);
        int c=UnseatedTelegraph.VIOLET;
        float shade=.45f+.55f*health;
        poses.pushPose();poses.mulPose(Axis.YP.rotationDegrees((entity.tickCount+partial)*1.5f));
        var out=buffers.getBuffer(RenderType.entitySolid(TEXTURE));var pose=poses.last();
        for(float[] b:BOXES){
            float x0=b[0],y0=b[1],z0=b[2],x1=b[3],y1=b[4],z1=b[5];
            float[][] faces={
                    {x0,y0,z1,x1,y0,z1,x1,y1,z1,x0,y1,z1,0,0,1},{x1,y0,z0,x0,y0,z0,x0,y1,z0,x1,y1,z0,0,0,-1},
                    {x1,y0,z1,x1,y0,z0,x1,y1,z0,x1,y1,z1,1,0,0},{x0,y0,z0,x0,y0,z1,x0,y1,z1,x0,y1,z0,-1,0,0},
                    {x0,y1,z1,x1,y1,z1,x1,y1,z0,x0,y1,z0,0,1,0},{x0,y0,z0,x1,y0,z0,x1,y0,z1,x0,y0,z1,0,-1,0}};
            for(float[] f:faces)for(int v=0;v<4;v++)
                out.addVertex(pose,f[v*3],f[v*3+1],f[v*3+2])
                        .setColor((int)(((c>>16)&255)*shade),(int)(((c>>8)&255)*shade),(int)((c&255)*shade),255).setUv(.5f,.5f)
                        .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(pose,f[12],f[13],f[14]);
        }
        poses.popPose();super.render(entity,yaw,partial,poses,buffers,light);
    }
}
