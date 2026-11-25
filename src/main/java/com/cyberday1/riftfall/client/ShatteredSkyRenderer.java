package com.cyberday1.riftfall.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** NASA star map, independently moving Carina layers, and event-based spatial tears. */
public final class ShatteredSkyRenderer {
    private static final ResourceLocation NEBULA = SkyTextures.NEBULA, STARS = SkyTextures.STARS;
    private static final SkyTearAnimation.Event[] events = new SkyTearAnimation.Event[5];
    private static final long[] cycles = new long[5];
    private static final VertexBuffer[] clouds = new VertexBuffer[3];
    private static ShaderInstance shader;
    private static VertexBuffer sphere, fallback, tears;
    private static final Object IRIS_API;
    private static final java.lang.reflect.Method IRIS_ACTIVE;
    static {
        Object api = null; java.lang.reflect.Method active = null;
        try {
            Class<?> type = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            api = type.getMethod("getInstance").invoke(null);
            active = type.getMethod("isShaderPackInUse");
        } catch (ReflectiveOperationException ignored) { }
        IRIS_API = api; IRIS_ACTIVE = active;
    }
    private ShatteredSkyRenderer() {}
    public static void setShader(ShaderInstance value) { shader = value; }
    static boolean shaderPackActive() {
        if (IRIS_ACTIVE == null) return false;
        try { return (boolean) IRIS_ACTIVE.invoke(IRIS_API); }
        catch (ReflectiveOperationException ignored) { return true; }
    }
    public static void draw(Matrix4f view, Matrix4f projection, double seconds) {
        draw(view, projection, seconds, false);
    }
    /** Iris has finished its composite pipeline at AFTER_LEVEL; the authored shader is safe here. */
    static void drawAfterShaderPack(Matrix4f view, Matrix4f projection, double seconds) {
        draw(view, projection, seconds, true);
    }
    private static void draw(Matrix4f view, Matrix4f projection, double seconds, boolean afterShaderPack) {
        if (!SkyTextures.ready()) return; // still decoding: a bind now would decode on this thread
        for (int i=0;i<5;i++) {
            long cycle=SkyTearAnimation.cycle(seconds,i);
            if(events[i]==null || cycles[i]!=cycle) { events[i]=SkyTearAnimation.event(seconds,i); cycles[i]=cycle; }
        }
        RenderSystem.setShaderTexture(0, STARS);
        RenderSystem.setShaderColor(1,1,1,1);
        if (shader != null && (afterShaderPack || !shaderPackActive())) {
            if (sphere == null) sphere = makeSphere(false);
            RenderSystem.setShader(() -> shader);
            shader.safeGetUniform("SkyTime").set((float)seconds);
            RenderSystem.setShaderTexture(1, NEBULA);
            for(int i=0;i<5;i++) {
                SkyTearAnimation.Event e=events[i];
                shader.safeGetUniform("RiftDirection"+i).set(e.x(),e.y(),e.z(),e.tilt());
                shader.safeGetUniform("RiftShape"+i).set(e.length(),e.width(),e.seed(),e.hue());
                shader.safeGetUniform("RiftLife"+i).set((float)SkyTearAnimation.phase(seconds,i),SkyTearAnimation.openness(seconds,i),0F,0F);
            }
            sphere.bind(); sphere.drawWithShader(view, projection, shader); VertexBuffer.unbind();
        } else {
            if (fallback == null) fallback = makeSphere(true);
            RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
            fallback.bind(); fallback.drawWithShader(view, projection, RenderSystem.getShader()); VertexBuffer.unbind();
            RenderSystem.setShaderTexture(0, NEBULA);
            RenderSystem.blendFunc(com.mojang.blaze3d.platform.GlStateManager.SourceFactor.SRC_ALPHA,
                    com.mojang.blaze3d.platform.GlStateManager.DestFactor.ONE);
            for(int layer=0;layer<2;layer++) {
                Matrix4f drift = new Matrix4f(view).rotateY((float)(seconds*(layer==0?.004:-.0027)))
                        .rotateZ(layer==0?0F:1.2F);
                RenderSystem.setShaderColor(layer==0?.38F:.40F,layer==0?.56F:.18F,layer==0?.88F:.55F,layer==0?.62F:.20F);
                for(int axis=0;axis<3;axis++) {
                    if(clouds[axis]==null) clouds[axis]=makeCloudSphere(axis);
                    clouds[axis].bind(); clouds[axis].drawWithShader(drift,projection,RenderSystem.getShader()); VertexBuffer.unbind();
                }
            }
            RenderSystem.setShaderColor(1,1,1,1);
            RenderSystem.defaultBlendFunc();
            drawFallbackTears(view, projection, seconds);
            fallbackGlow(view, projection, seconds);
        }
    }
    private static VertexBuffer makeSphere(boolean textured) {
        BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS,
                textured ? DefaultVertexFormat.POSITION_TEX_COLOR : DefaultVertexFormat.POSITION);
        int columns=96, rows=48;
        for(int y=0;y<rows;y++) for(int x=0;x<columns;x++) {
            sphereVertex(b,(float)x/columns,(float)y/rows,textured);
            sphereVertex(b,(float)(x+1)/columns,(float)y/rows,textured);
            sphereVertex(b,(float)(x+1)/columns,(float)(y+1)/rows,textured);
            sphereVertex(b,(float)x/columns,(float)(y+1)/rows,textured);
        }
        VertexBuffer result = new VertexBuffer(VertexBuffer.Usage.STATIC);
        result.bind(); result.upload(b.buildOrThrow()); VertexBuffer.unbind(); return result;
    }
    private static void sphereVertex(BufferBuilder b, float u, float v, boolean textured) {
        double longitude=(u-.5)*Math.PI*2, latitude=v*Math.PI;
        b.addVertex((float)(Math.cos(longitude)*Math.sin(latitude)*90), (float)(Math.cos(latitude)*90),
                (float)(Math.sin(longitude)*Math.sin(latitude)*90));
        if(textured) b.setUv(u,v).setColor(255,255,255,255);
    }
    private static VertexBuffer makeCloudSphere(int axis) {
        BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        for(int row=0;row<48;row++) for(int col=0;col<96;col++) {
            cloudVertex(b,axis,col,row); cloudVertex(b,axis,col+1,row);
            cloudVertex(b,axis,col+1,row+1); cloudVertex(b,axis,col,row+1);
        }
        VertexBuffer result=new VertexBuffer(VertexBuffer.Usage.STATIC);
        result.bind(); result.upload(b.buildOrThrow()); VertexBuffer.unbind(); return result;
    }
    private static void cloudVertex(BufferBuilder b,int axis,int column,int row) {
        double lat=row*Math.PI/48,lon=(column/96.0-.5)*Math.PI*2;
        float x=(float)(Math.cos(lon)*Math.sin(lat)),y=(float)Math.cos(lat),z=(float)(Math.sin(lon)*Math.sin(lat));
        double wx=Math.pow(x,6),wy=Math.pow(y,6),wz=Math.pow(z,6);
        float weight=(float)((axis==0?wx:axis==1?wy:wz)/(wx+wy+wz));
        float u=axis==0?y:axis==1?z:x,v=axis==0?z:axis==1?x:y;
        b.addVertex(x*90,y*90,z*90).setUv(.5F+u*.44F,.5F+v*.44F).setColor(1F,1F,1F,weight);
    }
    private static void drawFallbackTears(Matrix4f view, Matrix4f projection, double seconds) {
        // Standard textured geometry keeps the lifecycle working when Iris replaces core shaders.
        BufferBuilder b=Tesselator.getInstance().begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_TEX_COLOR);
        for(int i=0;i<5;i++) {
            float open=SkyTearAnimation.openness(seconds,i);
            if(open<=.0001F) continue;
            SkyTearAnimation.Event event=events[i];
            Vector3f normal=new Vector3f(event.x(),event.y(),event.z());
            Vector3f right=new Vector3f(normal).cross(0,1,0).normalize(), up=new Vector3f(right).cross(normal);
            for(int j=0;j<48;j++) {
                float y0=-1+j/24F, y1=-1+(j+1)/24F;
                tearVertex(b,normal,right,up,y0,-1,open,i,seconds);
                tearVertex(b,normal,right,up,y0,1,open,i,seconds);
                tearVertex(b,normal,right,up,y1,1,open,i,seconds);
                tearVertex(b,normal,right,up,y1,-1,open,i,seconds);
            }
            forkGeometry(b,normal,right,up,event,open,seconds,true,i);
        }
        MeshData mesh=b.build();
        if(mesh!=null) {
            // Dynamic upload is bounded to five small strips; no textures are allocated per frame.
            if(tears==null) tears=new VertexBuffer(VertexBuffer.Usage.DYNAMIC);
            tears.bind(); tears.upload(mesh);
            RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
            tears.drawWithShader(view,projection,RenderSystem.getShader()); VertexBuffer.unbind();
        }
    }
    private static void tearVertex(BufferBuilder b,Vector3f normal,Vector3f right,Vector3f up,
                                   float y,int side,float open,int index,double seconds) {
        Vector3f p=tearPoint(normal,right,up,y,side,open,index,0);
        float u=.5F+side*.18F+.08F*(float)Math.sin(seconds*.03+events[index].seed());
        float v=.5F+y*.36F+.08F*(float)Math.cos(seconds*.02+events[index].seed());
        b.addVertex(p.x,p.y,p.z).setUv(u,v).setColor(.55F,1F,1F,open);
    }
    private static Vector3f tearPoint(Vector3f n,Vector3f right,Vector3f up,float y,int side,float open,int index,float extra) {
        return localPoint(n,right,up,tearLocalX(y,side,open,index,extra),y*events[index].length(),events[index].tilt());
    }
    private static float tearLocalX(float y,int side,float open,int index,float extra) {
        SkyTearAnimation.Event event=events[index];
        return SkyTearAnimation.center(event,y)+side*(SkyTearAnimation.halfWidth(event,y,open)+extra);
    }
    private static Vector3f localPoint(Vector3f n,Vector3f right,Vector3f up,float x,float y,float tilt) {
        float px=x*(float)Math.cos(tilt)-y*(float)Math.sin(tilt);
        float py=x*(float)Math.sin(tilt)+y*(float)Math.cos(tilt);
        return new Vector3f(n).fma(px,right).fma(py,up).normalize().mul(89);
    }
    private static void fallbackGlow(Matrix4f view,Matrix4f projection,double seconds) {
        BufferBuilder b=Tesselator.getInstance().begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_COLOR);
        for(int i=0;i<5;i++) {
            float open=SkyTearAnimation.openness(seconds,i);
            if(open<=.0001F) continue;
            SkyTearAnimation.Event event=events[i];
            Vector3f n=new Vector3f(event.x(),event.y(),event.z());
            Vector3f r=new Vector3f(n).cross(0,1,0).normalize(), u=new Vector3f(r).cross(n);
            for(int side:new int[]{-1,1}) for(int j=0;j<48;j++) {
                float y0=-1+j/24F,y1=-1+(j+1)/24F;
                glowVertex(b,tearPoint(n,r,u,y0,side,open,i,0),open,i);
                glowVertex(b,tearPoint(n,r,u,y1,side,open,i,0),open,i);
                glowVertex(b,tearPoint(n,r,u,y1,side,open,i,.025F),0,i);
                glowVertex(b,tearPoint(n,r,u,y0,side,open,i,.025F),0,i);
                // Geometry approximation of the shader's narrow RGB fringe for shader packs.
                for(int channel=0;channel<3;channel++) {
                    float offset=(1-channel)*.0018F*open;
                    fringeVertex(b,tearPoint(n,r,u,y0,side,open,i,offset-.0007F),open,channel);
                    fringeVertex(b,tearPoint(n,r,u,y1,side,open,i,offset-.0007F),open,channel);
                    fringeVertex(b,tearPoint(n,r,u,y1,side,open,i,offset+.0007F),open,channel);
                    fringeVertex(b,tearPoint(n,r,u,y0,side,open,i,offset+.0007F),open,channel);
                }
            }
            forkGeometry(b,n,r,u,event,open,seconds,false,i);
            for(int branchId=0;branchId<SkyTearAnimation.branchCount(event);branchId++) {
                var branch=SkyTearAnimation.branch(event,branchId);
                float ax=tearLocalX(branch.root(),branch.side(),open,i,0), ay=branch.root()*event.length();
                float px=ax,py=ay;
                for(int segment=1;segment<=3;segment++) {
                    float f=segment/3F;
                    float nx=ax+branch.side()*branch.reach()*f;
                    float ny=ay+branch.rise()*f+(segment==1?branch.kink():segment==2?-branch.kink()*.5F:0);
                    float dx=nx-px,dy=ny-py,length=(float)Math.sqrt(dx*dx+dy*dy);
                    float ox=-dy/length*.001F,oy=dx/length*.001F;
                    float endAlpha=segment==3?0:open;
                    glowVertex(b,localPoint(n,r,u,px-ox,py-oy,event.tilt()),open,i);
                    glowVertex(b,localPoint(n,r,u,nx-ox,ny-oy,event.tilt()),endAlpha,i);
                    glowVertex(b,localPoint(n,r,u,nx+ox,ny+oy,event.tilt()),endAlpha,i);
                    glowVertex(b,localPoint(n,r,u,px+ox,py+oy,event.tilt()),open,i);
                    px=nx; py=ny;
                }
            }
        }
        MeshData mesh=b.build();
        if(mesh!=null) {
            if(tears==null) tears=new VertexBuffer(VertexBuffer.Usage.DYNAMIC);
            tears.bind(); tears.upload(mesh);
            RenderSystem.setShader(GameRenderer::getPositionColorShader);
            tears.drawWithShader(view,projection,RenderSystem.getShader()); VertexBuffer.unbind();
        }
    }
    private static void forkGeometry(BufferBuilder b,Vector3f n,Vector3f right,Vector3f up,
                                     SkyTearAnimation.Event event,float open,double seconds,boolean fill,int index) {
        float ax=0,ay=0;
        SkyTearAnimation.Fork previous=null;
        for(int f=0;f<SkyTearAnimation.forkCount(event);f++) {
            var fork=SkyTearAnimation.fork(event,f);
            if(f==0) { ax=SkyTearAnimation.center(event,fork.root()); ay=fork.root()*event.length(); }
            else {
                float length=(float)Math.hypot(previous.dx(),previous.dy());
                float bend=SkyTearAnimation.forkBend(previous,.55F);
                ax+=previous.dx()*.55F-previous.dy()/length*bend;
                ay+=previous.dy()*.55F+previous.dx()/length*bend;
            }
            for(int segment=0;segment<24;segment++) {
                float a=segment/24F,c=(segment+1)/24F;
                if(fill) {
                    forkVertex(b,n,right,up,event,fork,ax,ay,a,-1,0,open,seconds,true,index);
                    forkVertex(b,n,right,up,event,fork,ax,ay,a,1,0,open,seconds,true,index);
                    forkVertex(b,n,right,up,event,fork,ax,ay,c,1,0,open,seconds,true,index);
                    forkVertex(b,n,right,up,event,fork,ax,ay,c,-1,0,open,seconds,true,index);
                } else for(int side:new int[]{-1,1}) {
                    forkVertex(b,n,right,up,event,fork,ax,ay,a,side,0,open,seconds,false,index);
                    forkVertex(b,n,right,up,event,fork,ax,ay,c,side,0,open,seconds,false,index);
                    forkVertex(b,n,right,up,event,fork,ax,ay,c,side,.012F,open,seconds,false,index);
                    forkVertex(b,n,right,up,event,fork,ax,ay,a,side,.012F,open,seconds,false,index);
                }
            }
            previous=fork;
        }
    }
    private static void forkVertex(BufferBuilder b,Vector3f n,Vector3f right,Vector3f up,
                                   SkyTearAnimation.Event event,SkyTearAnimation.Fork fork,float ax,float ay,
                                   float along,int side,float extra,float open,double seconds,boolean fill,int index) {
        float length=(float)Math.hypot(fork.dx(),fork.dy());
        float taper=(float)Math.pow(1-along,.85);
        float lateral=SkyTearAnimation.forkBend(fork,along)
                +side*((.0015F+event.width()*open)*fork.width()+extra)*taper;
        float x=ax+fork.dx()*along-fork.dy()/length*lateral;
        float y=ay+fork.dy()*along+fork.dx()/length*lateral;
        Vector3f p=localPoint(n,right,up,x,y,event.tilt());
        if(fill) b.addVertex(p.x,p.y,p.z).setUv(.5F+x*.6F+.05F*(float)Math.sin(seconds*.03+event.seed()),
                .5F+y*.6F).setColor(.55F,1F,1F,open);
        else glowVertex(b,p,extra>0?0:open*Math.min(1,along*8),index);
    }
    private static void fringeVertex(BufferBuilder b,Vector3f p,float alpha,int channel) {
        b.addVertex(p.x,p.y,p.z).setColor(channel==0?1F:0F,channel==1?1F:0F,channel==2?1F:0F,alpha*.65F);
    }
    private static void glowVertex(BufferBuilder b,Vector3f p,float alpha,int index) {
        b.addVertex(p.x,p.y,p.z).setColor(events[index].hue()>.82F?1F:.35F,events[index].hue()>.82F?.6F:.8F,events[index].hue()>.82F?.2F:1F,alpha);
    }
    public static void releaseBuffers() {
        if(sphere!=null) { sphere.close(); sphere=null; }
        if(fallback!=null) { fallback.close(); fallback=null; }
        for(int i=0;i<clouds.length;i++) if(clouds[i]!=null) { clouds[i].close(); clouds[i]=null; }
        if(tears!=null) { tears.close(); tears=null; }
    }
}
