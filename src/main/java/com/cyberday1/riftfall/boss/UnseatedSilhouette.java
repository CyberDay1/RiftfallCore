package com.cyberday1.riftfall.boss;

/** The body the combat boss is drawn with when its painted mesh failed to load: plain boxes, so the fight always shows one. */
public final class UnseatedSilhouette {
    private UnseatedSilhouette(){}
    public static final int COLOR=0x2A2140;
    /** x0,y0,z0,x1,y1,z1 in blocks from the entity's feet, front towards +z: robe, torso, shoulders, head, halo. */
    public static final float[][] BOXES={
            {-2.2f,0,-1.4f,2.2f,6.5f,1.4f},
            {-1.7f,6.5f,-1.1f,1.7f,10.5f,1.1f},
            {-3.6f,9.3f,-.9f,3.6f,10.7f,.9f},
            {-1f,10.7f,-1f,1f,13f,1f},
            {-2.4f,11.2f,-1.5f,2.4f,14.4f,-1.2f},
    };
}
