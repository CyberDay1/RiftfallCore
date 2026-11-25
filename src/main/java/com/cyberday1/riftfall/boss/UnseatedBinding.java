package com.cyberday1.riftfall.boss;

/** Shared binding progress in binder-ticks. Standing up pauses a binder; only a hit on one takes progress away. */
public final class UnseatedBinding {
    /** The dashed ring a binder crouches inside. */
    public static final double RADIUS=9;
    static final double SOLO=100, BREAK=.25;
    static final int DECAY_EVERY=4;
    private double required=SOLO,progress;
    private int quiet;
    private boolean complete;
    /** B(n) = 100 x H(n): 100 / 160 / 210 / 250 binder-ticks for one to four players. */
    public static double required(int headcount){return SOLO*BossScaling.partyHealth(headcount);}
    /** Whether a crouched player at this horizontal distance from the god stands inside the ring. */
    public static boolean inRing(double distance){return distance<=RADIUS;}
    /** Back to nothing, sized for the party. A reload never resumes a half bind. */
    void reset(int headcount){required=required(headcount);progress=0;quiet=0;complete=false;}
    /** One server tick with this many players binding. True on the tick the binding completes, once. */
    boolean tick(int binders){
        if(complete)return false;
        if(binders>0){progress+=binders;quiet=0;}
        else if(++quiet%DECAY_EVERY==0)progress=Math.max(0,progress-1);
        if(progress<required)return false;
        progress=required;complete=true;return true;
    }
    /** An Unseated hit on a binder breaks a quarter of the whole, never all of it. */
    void strike(){if(!complete)progress=Math.max(0,progress-BREAK*required);}
    double progress(){return progress;}
    double required(){return required;}
    boolean complete(){return complete;}
    float fraction(){return (float)Math.clamp(progress/required,0,1);}
}
