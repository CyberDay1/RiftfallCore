package com.cyberday1.riftfall.client;

import com.cyberday1.riftfall.RiftfallCore;
import com.cyberday1.riftfall.block.holycore.WorldCoreBlend;
import com.cyberday1.riftfall.block.holycore.WorldCoreModel;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.renderer.*;
import net.minecraft.resources.ResourceLocation;

/** Opaque stone is submitted once; restoration fades and living seams use separate passes. */
final class PaintedMeshRenderer {
    private static final ResourceLocation WHITE=RiftfallCore.id("textures/block/world_core/white.png");
    private final ResourceLocation texture;
    private final RenderType lightLayer;
    PaintedMeshRenderer() { this(WHITE); }
    PaintedMeshRenderer(ResourceLocation texture) {
        this.texture=texture;
        this.lightLayer=LightLayer.forTexture(texture);
    }
    private final WorldCoreModel.Transform transform=new WorldCoreModel.Transform();
    private final WorldCoreBlend.Vertex vertex=new WorldCoreBlend.Vertex();

    void render(WorldCoreBlend mesh,float blend,double ticks,PoseStack poses,MultiBufferSource buffers,
                int light,int overlay,float energy,boolean skipFoundation) {
        render(mesh,blend,ticks,poses,buffers,light,overlay,energy,skipFoundation,null);
    }
    void render(WorldCoreBlend mesh,float blend,double ticks,PoseStack poses,MultiBufferSource buffers,
                int light,int overlay,float energy,boolean skipFoundation,
                java.util.function.BiConsumer<String,WorldCoreModel.Transform> modifier) {
        for(int pass=0;pass<3;pass++) {
            var out=buffers.getBuffer(pass==0?RenderType.entityCutoutNoCull(texture)
                    :pass==1?RenderType.entityTranslucent(texture):lightLayer);
            for(int p=0;p<mesh.lower.parts().size();p++) {
                var part=mesh.lower.parts().get(p);
                if(part.vertexCount()==0 || skipFoundation && part.name().equals("foundation"))continue;
                mesh.sample(p,ticks,blend,transform);
                if(modifier!=null)modifier.accept(part.name(),transform);
                poses.pushPose();poses.translate(transform.x,transform.y,transform.z);
                poses.mulPose(transform.rotation);poses.scale(transform.sx,transform.sy,transform.sz);
                var pose=poses.last();
                for(int tri=0;tri<part.vertexCount();tri+=3) {
                    float opacity=mesh.visibility(p,tri,blend);
                    if(opacity<=0)continue;
                    int material=part.materials()[tri];
                    int layer=material!=0?2:opacity>=1?0:1;
                    if(layer!=pass)continue;
                    float brightness=material==1?energy:1;
                    for(int corner=0;corner<4;corner++) {
                        int index=tri+Math.min(corner,2);
                        mesh.vertex(p,index,blend,vertex);
                        float u=part.uvs()==null?.5f:part.uvs()[index*2];
                        float v=part.uvs()==null?.5f:part.uvs()[index*2+1];
                        out.addVertex(pose,vertex.x,vertex.y,vertex.z)
                                .setColor(Math.round(vertex.red*brightness),Math.round(vertex.green*brightness),
                                        Math.round(vertex.blue*brightness),Math.round(vertex.alpha*255))
                                .setUv(u,v).setOverlay(overlay).setLight(pass==2?LightTexture.FULL_BRIGHT:light)
                                .setNormal(pose,vertex.nx,vertex.ny,vertex.nz);
                    }
                }
                poses.popPose();
            }
        }
    }

    private static final class LightLayer extends RenderType {
        static RenderType forTexture(ResourceLocation texture) { return create("riftfall_painted_light",DefaultVertexFormat.NEW_ENTITY,
                VertexFormat.Mode.QUADS,262144,false,true,CompositeState.builder()
                        .setShaderState(RENDERTYPE_EYES_SHADER).setTextureState(new TextureStateShard(texture,false,false))
                        .setTransparencyState(TRANSLUCENT_TRANSPARENCY).setCullState(NO_CULL).createCompositeState(false)); }
        private LightLayer(){super("riftfall_painted_light",DefaultVertexFormat.NEW_ENTITY,VertexFormat.Mode.QUADS,
                256,false,true,()->{},()->{});}
    }
}
