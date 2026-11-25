package com.cyberday1.riftfall.block.holycore;

/** Corresponding painted surfaces move together; only newly restored triangles change opacity. */
public final class WorldCoreBlend {
    public final WorldCoreModel lower, upper;
    private final WorldCoreModel.Transform other = new WorldCoreModel.Transform();

    public WorldCoreBlend(WorldCoreModel lower, WorldCoreModel upper) {
        if (lower.parts().size() != upper.parts().size()) throw new IllegalArgumentException("Core part count changed");
        for (int i=0;i<lower.parts().size();i++) {
            var a=lower.parts().get(i); var b=upper.parts().get(i);
            if (!a.name().equals(b.name()) || a.vertexCount()!=b.vertexCount()
                    || !java.util.Arrays.equals(a.materials(),b.materials())
                    || !java.util.Arrays.equals(a.uvs(),b.uvs()))
                throw new IllegalArgumentException("Core stages need matching topology: " + a.name());
        }
        this.lower=lower; this.upper=upper;
    }

    public void sample(int part, double ticks, float blend, WorldCoreModel.Transform out) {
        lower.sample(lower.parts().get(part),ticks,out);
        if (blend<=0) return;
        upper.sample(upper.parts().get(part),ticks,other);
        out.x=mix(out.x,other.x,blend);out.y=mix(out.y,other.y,blend);out.z=mix(out.z,other.z,blend);
        out.rotation.slerp(other.rotation,blend);
        out.sx=mix(out.sx,other.sx,blend);out.sy=mix(out.sy,other.sy,blend);out.sz=mix(out.sz,other.sz,blend);
    }

    public static final class Vertex {
        public float x,y,z,nx,ny,nz,alpha;
        public int red,green,blue;
    }

    public void vertex(int part, int v, float blend, Vertex out) {
        // Most frames are settled stages. Avoid interpolation and normalization work then.
        if(blend<=0 || blend>=1) {
            var source=(blend<=0?lower:upper).parts().get(part);int k=v*6;
            float[] data=source.vertices();int color=source.colors()[v];
            out.x=data[k];out.y=data[k+1];out.z=data[k+2];
            out.nx=data[k+3];out.ny=data[k+4];out.nz=data[k+5];
            out.red=(color>>16)&255;out.green=(color>>8)&255;out.blue=color&255;
            out.alpha=source.visibility()[v];return;
        }
        var a=lower.parts().get(part);var b=upper.parts().get(part);int k=v*6;
        float[] x=a.vertices(),y=b.vertices();
        out.x=mix(x[k],y[k],blend);out.y=mix(x[k+1],y[k+1],blend);out.z=mix(x[k+2],y[k+2],blend);
        out.nx=mix(x[k+3],y[k+3],blend);out.ny=mix(x[k+4],y[k+4],blend);out.nz=mix(x[k+5],y[k+5],blend);
        float length=(float)Math.sqrt(out.nx*out.nx+out.ny*out.ny+out.nz*out.nz);
        if(length>1e-6f){out.nx/=length;out.ny/=length;out.nz/=length;}
        int ca=a.colors()[v],cb=b.colors()[v];
        out.red=Math.round(mix((ca>>16)&255,(cb>>16)&255,blend));
        out.green=Math.round(mix((ca>>8)&255,(cb>>8)&255,blend));
        out.blue=Math.round(mix(ca&255,cb&255,blend));
        out.alpha=visibility(part,v,blend);
    }

    public float visibility(int part,int v,float blend) {
        if(blend<=0)return lower.parts().get(part).visibility()[v];
        if(blend>=1)return upper.parts().get(part).visibility()[v];
        return mix(lower.parts().get(part).visibility()[v],upper.parts().get(part).visibility()[v],blend);
    }
    public static float mix(float a,float b,float t){return a+(b-a)*t;}
}
