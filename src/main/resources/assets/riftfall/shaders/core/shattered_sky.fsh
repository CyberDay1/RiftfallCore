#version 150
uniform sampler2D Sampler0;
uniform float SkyTime;
uniform sampler2D Sampler1;
uniform vec4 RiftDirection0;
uniform vec4 RiftShape0;
uniform vec4 RiftLife0;
uniform vec4 RiftDirection1;
uniform vec4 RiftShape1;
uniform vec4 RiftLife1;
uniform vec4 RiftDirection2;
uniform vec4 RiftShape2;
uniform vec4 RiftLife2;
uniform vec4 RiftDirection3;
uniform vec4 RiftShape3;
uniform vec4 RiftLife3;
uniform vec4 RiftDirection4;
uniform vec4 RiftShape4;
uniform vec4 RiftLife4;
in vec3 skyDirection;
out vec4 fragColor;
const float PI = 3.14159265359;
float hash(vec3 p) { return fract(sin(dot(p, vec3(127.1,311.7,74.7))) * 43758.5453); }
float noise(vec3 p) {
    vec3 i=floor(p), f=fract(p); f=f*f*(3.0-2.0*f);
    return mix(mix(mix(hash(i),hash(i+vec3(1,0,0)),f.x),mix(hash(i+vec3(0,1,0)),hash(i+vec3(1,1,0)),f.x),f.y),
               mix(mix(hash(i+vec3(0,0,1)),hash(i+vec3(1,0,1)),f.x),mix(hash(i+vec3(0,1,1)),hash(i+vec3(1,1,1)),f.x),f.y),f.z);
}
vec2 sphereUV(vec3 d) { return vec2(atan(d.z,d.x)/(2.0*PI)+0.5, acos(clamp(d.y,-1.0,1.0))/PI); }
// Triplanar cloud mapping has no longitude seam or singular pole.
vec3 nebula(vec3 d) {
    vec3 weights=pow(abs(d),vec3(6.0)); weights/=weights.x+weights.y+weights.z;
    return texture(Sampler1,.5+d.yz*.44).rgb*weights.x
         + texture(Sampler1,.5+d.zx*.44).rgb*weights.y
         + texture(Sampler1,.5+d.xy*.44).rgb*weights.z;
}
vec3 rotateClouds(vec3 d,float a) {
    return vec3(cos(a)*d.x-sin(a)*d.z,d.y,sin(a)*d.x+cos(a)*d.z);
}
vec4 directionFor(int id) {
    if(id==0) return RiftDirection0; if(id==1) return RiftDirection1;
    if(id==2) return RiftDirection2; if(id==3) return RiftDirection3; return RiftDirection4;
}
vec4 shapeFor(int id) {
    if(id==0) return RiftShape0; if(id==1) return RiftShape1;
    if(id==2) return RiftShape2; if(id==3) return RiftShape3; return RiftShape4;
}
vec4 lifeFor(int id) {
    if(id==0) return RiftLife0; if(id==1) return RiftLife1;
    if(id==2) return RiftLife2; if(id==3) return RiftLife3; return RiftLife4;
}
vec3 skyBackground(vec3 d,float t) {
    // Continuous angular drift, plus evolving direction-space flow: visible within seconds,
    // independent of the camera and continuous across the equirectangular seam.
    float angle=t*.004;
    vec3 clouds=vec3(cos(angle)*d.x-sin(angle)*d.z,d.y,sin(angle)*d.x+cos(angle)*d.z);
    vec3 flow=vec3(t*.035,sin(t*.031)*.35,cos(t*.027)*.35);
    vec3 curl=vec3(noise(clouds*5.0+flow),noise(clouds*5.0+flow+7.0),noise(clouds*5.0+flow+19.0))-.5;
    vec3 fine=vec3(noise(clouds*12.0-flow*.6),noise(clouds*12.0-flow*.6+11.0),noise(clouds*12.0-flow*.6+23.0))-.5;
    vec3 warped=normalize(clouds+curl*.065+fine*.012);
    vec3 farCloud=nebula(warped);
    vec3 nearDirection=rotateClouds(vec3(d.z,d.x,d.y),-t*.0027+.8);
    vec3 nearCloud=nebula(normalize(nearDirection+curl*.035));
    float veil=noise(clouds*4.0+vec3(t*.018,0,t*.008));
    // NASA stars remain in their own undistorted celestial coordinate frame.
    vec3 stars=texture(Sampler0,sphereUV(d)).rgb;
    vec3 c=stars*.8 + farCloud*vec3(.38,.56,.88)*(.45+.35*veil)
          + nearCloud*vec3(.40,.18,.55)*(.12+.15*(1.0-veil));
    return c;
}
float branchRandom(float seed,int branch,int channel) {
    uint v=uint(floor(seed*256.0))+uint(branch)*374761393u+uint(channel)*668265263u;
    v=(v^(v>>16u))*0x7feb352du;
    v=(v^(v>>15u))*0x846ca68bu;
    return float((v^(v>>16u))>>8u)/16777216.0;
}
vec2 tearProfile(float y,vec4 shape,float open) {
    float taper=pow(max(0.0,1.0-y*y),.55+.7*branchRandom(shape.z,0,30));
    float cell=(y+1.0)*(5.0+11.0*branchRandom(shape.z,0,31));
    int k=int(floor(cell));
    float a=branchRandom(shape.z,k+128,32)-.5,b=branchRandom(shape.z,k+129,32)-.5;
    float jagged=mix(a,b,fract(cell));
    float bend=(branchRandom(shape.z,0,33)-.5)*.34;
    float wave=sin(y*(2.0+4.0*branchRandom(shape.z,0,34))+shape.z);
    float center=(jagged*.07+wave*bend+y*y*(branchRandom(shape.z,0,35)-.5)*.14)*taper;
    float lobes=.72+.22*sin(y*(3.0+5.0*branchRandom(shape.z,0,36))+shape.z);
    return vec2(center,(.0015+shape.y*open)*taper*lobes);
}
int forkCount(float seed) {
    float choice=branchRandom(seed,0,20);
    return choice<.62?0:choice<.87?1:2;
}
vec4 forkShape(float seed,int index) {
    float side=branchRandom(seed,index,21)<.5?-1.0:1.0;
    return vec4(side*(.16+.20*branchRandom(seed,index,23)),
                -.23+.46*branchRandom(seed,index,24),
                -.065+.13*branchRandom(seed,index,25),.4+.35*branchRandom(seed,index,26));
}
vec2 forkPoint(vec2 start,vec4 fork,float along) {
    vec2 normal=normalize(vec2(-fork.y,fork.x));
    return start+fork.xy*along+normal*sin(PI*along)*fork.z;
}
float riftDistance(vec2 p,vec4 shape,float open) {
    float y=p.y/shape.x;
    vec2 profile=tearProfile(y,shape,open);
    float edge=max(abs(p.x-profile.x)-profile.y,(abs(y)-1.0)*shape.x);
    int count=forkCount(shape.z);
    vec2 start=vec2(0); vec4 previous=vec4(0);
    for(int i=0;i<2;i++) {
        if(i>=count) break;
        vec4 fork=forkShape(shape.z,i);
        if(i==0) {
            float root=-.4+.8*branchRandom(shape.z,0,22);
            start=vec2(tearProfile(root,shape,open).x,root*shape.x);
        } else start=forkPoint(start,previous,.55);
        float len=length(fork.xy); vec2 axis=fork.xy/len,normal=vec2(-axis.y,axis.x);
        vec2 relative=p-start;
        float along=dot(relative,axis)/len;
        float u=clamp(along,0.0,1.0);
        float lateral=dot(relative,normal)-sin(PI*u)*fork.z;
        float width=(.0015+shape.y*open)*fork.w*pow(1.0-u,.85);
        float distance=max(abs(lateral)-width,max(-along,along-1.0)*len);
        edge=min(edge,distance);
        previous=fork;
    }
    return edge;
}
float segmentDistance(vec2 p,vec2 a,vec2 b) {
    vec2 delta=b-a;
    return length(p-a-delta*clamp(dot(p-a,delta)/max(dot(delta,delta),.0000001),0.0,1.0));
}
vec3 tear(vec3 d, float id, float t, vec3 underneath) {
    vec4 orientation=directionFor(int(id)), shape=shapeFor(int(id)), life=lifeFor(int(id));
    vec3 n=orientation.xyz;
    float facing=dot(d,n); if(facing<.72) return underneath;
    vec3 right=normalize(cross(n,vec3(0,1,0))), up=cross(right,n);
    vec2 p=vec2(dot(d,right),dot(d,up))/facing;
    float tilt=orientation.w; p=mat2(cos(tilt),-sin(tilt),sin(tilt),cos(tilt))*p;
    float phase=life.x;
    float open=life.y; if(open<.0001) return underneath;
    float halfLength=shape.x;
    float y=p.y/halfLength;
    vec2 profile=tearProfile(y,shape,open);
    float center=profile.x,width=profile.y;
    float edge=riftDistance(p,shape,open);
    float endFade=1.0; // Distance field bounds every trunk and fork, including their tips.
    float inside=(1.0-smoothstep(-.001,.001,edge))*endFade*open;
    vec3 other=rotateClouds(normalize(vec3(d.z,-d.y,d.x)),t*.014+shape.z);
    vec3 beyond=nebula(other)*vec3(.55,1.25,1.7)*1.5;
    beyond+=vec3(.012,.035,.065);
    vec3 c=mix(underneath,beyond,inside);
    // Refract actual sky/interior color channels separately, only in a narrow edge band.
    float edgeBand=(1.0-smoothstep(.002,.014,abs(edge)))*endFade*open;
    float dispersion=(.0018+.00035*sin(t*1.3+shape.z))*open;
    if(edgeBand>.001) {
        vec2 gradient=vec2(riftDistance(p+vec2(.0005,0),shape,open)-riftDistance(p-vec2(.0005,0),shape,open),
                           riftDistance(p+vec2(0,.0005),shape,open)-riftDistance(p-vec2(0,.0005),shape,open));
        gradient/=max(length(gradient),.000001);
        vec2 outward=mat2(cos(tilt),sin(tilt),-sin(tilt),cos(tilt))*gradient;
        vec3 edgeDirection=right*outward.x+up*outward.y;
        vec3 dr=normalize(d+edgeDirection*dispersion), db=normalize(d-edgeDirection*dispersion);
        vec3 splitSky=vec3(skyBackground(dr,t).r,underneath.g,skyBackground(db,t).b);
        vec3 splitInterior=vec3(nebula(normalize(other+edgeDirection*dispersion)).r*.825,
                beyond.g,nebula(normalize(other-edgeDirection*dispersion)).b*2.55)+vec3(.012,0,.065);
        c=mix(c,mix(splitSky,splitInterior,inside),edgeBand*.8);
    }
    vec3 fringe=vec3(exp(-abs(edge-dispersion)*1300.0),exp(-abs(edge)*1600.0)*.25,
                    exp(-abs(edge+dispersion)*1300.0));
    c+=fringe*endFade*open*.65;

    vec3 tint=shape.w>.82 ? vec3(1.0,.58,.19) : mix(vec3(.20,.72,1.0),vec3(.65,.25,1.0),shape.w);
    float pulse=.90+.10*sin(t*1.7+id*4.0+y*12.0);
    float rim=exp(-abs(edge)*1250.0)*1.7+exp(-abs(edge)*180.0)*.48+exp(-abs(edge)*35.0)*.11;
    c+=tint*rim*endFade*open*pulse;
    c+=vec3(.65,.85,1.0)*exp(-abs(edge)*1900.0)*endFade*open*.65;
    // Small branch cracks and drifting embers fade with the event, never a permanent overlay.
    int count=3+int(branchRandom(shape.z,0,0)*4.0);
    for(int branchId=0;branchId<6;branchId++) {
        if(branchId>=count) break;
        float root=-.68+1.36*branchRandom(shape.z,branchId,1);
        float side=branchRandom(shape.z,branchId,2)<.5?-1.0:1.0;
        float reach=.045+.11*branchRandom(shape.z,branchId,3);
        float rise=-.11+.22*branchRandom(shape.z,branchId,4);
        float kink=-.035+.07*branchRandom(shape.z,branchId,5);
        vec2 rootProfile=tearProfile(root,shape,open);
        vec2 anchor=vec2(rootProfile.x+side*rootProfile.y,root*halfLength);
        vec2 previous=anchor;
        for(int segment=1;segment<=3;segment++) {
            float f=float(segment)/3.0;
            vec2 next=anchor+vec2(side*reach*f,rise*f+(segment==1?kink:segment==2?-kink*.5:0.0));
            float distance=segmentDistance(p,previous,next);
            float fade=(1.0-smoothstep(.65,1.0,length(p-anchor)/max(length(next-anchor),.001)));
            // Retain joins; taper only the final segment into its tip.
            fade=segment==3?fade:1.0;
            float red=segmentDistance(p-vec2(dispersion*.6,0),previous,next);
            float blue=segmentDistance(p+vec2(dispersion*.6,0),previous,next);
            c+=(tint*(exp(-distance*1100.0)*.5+exp(-distance*150.0)*.10)
                +vec3(exp(-red*1500.0),0,exp(-blue*1500.0))*.22)*fade*open;
            previous=next;
        }
    }
    for(int j=0;j<7;j++) {
        float seed=shape.z+float(j)*7.1;
        float life=fract(phase*3.0+float(j)*.143);
        vec2 ember=vec2(center+sin(seed)*(.06+life*.06), sin(seed*1.7)*halfLength+life*.045);
        float spark=exp(-length(p-ember)*1500.0)*sin(life*PI)*open;
        c+=vec3(1.0,.55,.17)*spark;
    }
    return c;
}
void main() {
    vec3 d=normalize(skyDirection);
    float t=SkyTime;
    vec3 c=skyBackground(d,t);
    for(int i=0;i<5;i++) c=tear(d,float(i),t,c);
    // Gentle highlight rolloff retains color in luminous rims without requiring external bloom.
    c=c/(vec3(1.0)+max(c-vec3(.75),vec3(0.0))*.6);
    fragColor=vec4(c,1.0);
}
