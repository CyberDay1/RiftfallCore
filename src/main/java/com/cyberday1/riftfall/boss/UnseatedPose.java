package com.cyberday1.riftfall.boss;

import com.cyberday1.riftfall.block.holycore.WorldCoreModel;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import static com.cyberday1.riftfall.boss.UnseatedEncounter.*;

/** Additive poses over the painted model's idle. Entire arm branches move together. */
public final class UnseatedPose {
    private final Vector3f left=new Vector3f(),right=new Vector3f(),point=new Vector3f();
    private final Quaternionf lrot=new Quaternionf(),rrot=new Quaternionf();
    private final float lift,settle;
    public UnseatedPose(WorldCoreModel model,double idleTicks,Phase phase,Action action,double time) {
        float e=envelope(action,time);
        for(var part:model.parts()) {
            if(part.name().equals("ARM L shoulder pivot") || part.name().equals("ARM R shoulder pivot")) {
                var t=new WorldCoreModel.Transform();model.sample(part,idleTicks,t);
                (part.name().contains(" L ")?left:right).set(t.x,t.y,t.z);
            }
        }
        float spread=0,reach=0,swivel=0;
        switch(action) {
            case SWEEP -> { spread=.10f*e;reach=.18f*e;swivel=.24f*e*(1-2*smooth((time-40)/12)); }
            case PULL -> { spread=.15f*e;reach=.32f*e; }
            case PULSE -> { spread=.23f*e;reach=.12f*e; }
            case AWAKEN,TRANSITION -> { spread=.18f*e;reach=.08f*e; }
            case BIND,CONTAINED -> { spread=.10f*e;reach=.08f*e; }
            // F6's two poses, layered on the same arms: the Crown raises both arms high over the mark, Throne Fall
            // throws them wide and the body settles as the seat comes down. No new geometry.
            case CROWN -> { spread=.06f*e;reach=.46f*e; }
            case THRONE_FALL -> { spread=.28f*e;reach=.04f*e; }
            // Combos take the pose of the attack that leads them; the glide rides with arms half open.
            case REACH_RUPTURE -> { spread=.19f*e;reach=.28f*e; }
            case SWEEP_CROWN -> { spread=.08f*e;reach=.40f*e;swivel=.24f*e*(1-2*smooth((time-70)/12)); }
            case RESEAT -> { spread=.14f*e;reach=.10f*e; }
            default -> {}
        }
        // Outward opening plus forward extension keeps the cleaned mantle clear.
        lrot.rotationXYZ(-reach,0,-spread);
        rrot.rotationXYZ(-reach,swivel,spread);
        float ascent=phase==Phase.ASCENT?(action==Action.TRANSITION?smooth(time/70):1):
                phase==Phase.BINDING && action==Action.TRANSITION?1-smooth(time/70):0;
        lift=.35f*ascent+(action==Action.TRANSITION?.10f*e:0);
        settle=(action==Action.BIND || action==Action.CONTAINED)?.12f*e:action==Action.THRONE_FALL?.10f*e:0;
    }
    public void apply(String name,WorldCoreModel.Transform t) {
        boolean l=name.startsWith("ARM L ") || name.startsWith("HAND L ") || name.startsWith("L DIGIT ") || name.startsWith("L THUMB ");
        boolean r=name.startsWith("ARM R ") || name.startsWith("HAND R ") || name.startsWith("R DIGIT ") || name.startsWith("R THUMB ");
        if(l || r) {
            var origin=l?left:right;var rotation=l?lrot:rrot;
            point.set(t.x,t.y,t.z).sub(origin).rotate(rotation).add(origin);
            t.x=point.x;t.y=point.y;t.z=point.z;
            t.rotation.premul(rotation);
        }
        // Uniform settling preserves chest/ring/cloth relationships.
        float scale=1-settle;
        t.x*=scale;t.y=t.y*scale+lift;t.z*=scale;
        t.sx*=scale;t.sy*=scale;t.sz*=scale;
    }
}
