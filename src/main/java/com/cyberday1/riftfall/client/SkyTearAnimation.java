package com.cyberday1.riftfall.client;

/** Time-based lifecycle: quiet, opening, holding, closing, quiet. No frame-dependent state. */
public final class SkyTearAnimation {
    public static final double SLOT_SECONDS = 12;
    public static final double CHANNEL_SECONDS = SLOT_SECONDS * 5;
    private SkyTearAnimation() {}
    public record Schedule(long cycle,double start,double opening,double hold,double closing) {
        public double closingStart() { return start+opening+hold; }
        public double end() { return closingStart()+closing; }
    }
    /** Reserve separated start slots, then vary every event's timing within them.
     * A channel is quiet for at least 30 seconds before it can be used again.
     * This prevents five independent periodic oscillators from drifting into sync.
     */
    public static Schedule scheduleForCycle(long cycle,int index) {
        long key=cycle*31+index*7919L;
        double slot=cycle*CHANNEL_SECONDS+index*SLOT_SECONDS;
        double start=slot+random(key,40)*3, end=slot+27+random(key,44)*3;
        double opening=4+random(key,41)*3, closing=4+random(key,43)*4;
        return new Schedule(cycle,start,opening,end-start-opening-closing,closing);
    }
    public static Schedule schedule(double seconds,int index) {
        long cycle=(long)Math.floor((seconds-index*SLOT_SECONDS)/CHANNEL_SECONDS);
        Schedule candidate=scheduleForCycle(cycle,index);
        return seconds<candidate.start()?scheduleForCycle(cycle-1,index):candidate;
    }
    public static long cycle(double seconds, int index) { return schedule(seconds,index).cycle(); }
    public static double phase(double seconds, int index) {
        Schedule event=schedule(seconds,index);
        return Math.clamp((seconds-event.start())/(event.end()-event.start()),0,1);
    }
    /** All parameters change only in the invisible interval at a cycle boundary. */
    public record Event(float x, float y, float z, float tilt, float length, float width, float seed, float hue) {}
    public record Branch(float root, int side, float reach, float rise, float kink) {}
    /** A secondary opening; fork 1 grows from fork 0 rather than the trunk. */
    public record Fork(float root, float dx, float dy, float bend, float width) {}
    public static int forkCount(Event event) {
        float choice=branchRandom(event.seed(),0,20);
        return choice<.62F?0:choice<.87F?1:2;
    }
    public static Fork fork(Event event,int index) {
        float side=branchRandom(event.seed(),index,21)<.5F?-1:1;
        return new Fork(-.4F+.8F*branchRandom(event.seed(),index,22),
                side*(.16F+.20F*branchRandom(event.seed(),index,23)),
                -.23F+.46F*branchRandom(event.seed(),index,24),
                -.065F+.13F*branchRandom(event.seed(),index,25),
                .4F+.35F*branchRandom(event.seed(),index,26));
    }
    public static float forkBend(Fork fork,float along) {
        return (float)Math.sin(Math.PI*along)*fork.bend();
    }
    /** Seeded trunk profile: variable curvature, asymmetry, taper and fracture spacing. */
    public static float center(Event event,float y) {
        float taper=(float)Math.pow(Math.max(0,1-y*y),.55+.7*branchRandom(event.seed(),0,30));
        float cell=(y+1)*(5+11*branchRandom(event.seed(),0,31));
        int k=(int)Math.floor(cell);
        float a=branchRandom(event.seed(),k+128,32)-.5F,b=branchRandom(event.seed(),k+129,32)-.5F;
        float jagged=a+(b-a)*(cell-k);
        float bend=(branchRandom(event.seed(),0,33)-.5F)*.34F;
        float wave=(float)Math.sin(y*(2+4*branchRandom(event.seed(),0,34))+event.seed());
        return (jagged*.07F+wave*bend+y*y*(branchRandom(event.seed(),0,35)-.5F)*.14F)*taper;
    }
    public static float halfWidth(Event event,float y,float open) {
        float taper=(float)Math.pow(Math.max(0,1-y*y),.55+.7*branchRandom(event.seed(),0,30));
        float lobes=.72F+.22F*(float)Math.sin(y*(3+5*branchRandom(event.seed(),0,36))+event.seed());
        return (.0015F+event.width()*open)*taper*lobes;
    }
    public static int branchCount(Event event) { return 3+(int)(branchRandom(event.seed(),0,0)*4); }
    public static Branch branch(Event event,int index) {
        return new Branch(-.68F+1.36F*branchRandom(event.seed(),index,1),
                branchRandom(event.seed(),index,2)<.5F?-1:1,
                .045F+.11F*branchRandom(event.seed(),index,3),
                -.11F+.22F*branchRandom(event.seed(),index,4),
                -.035F+.07F*branchRandom(event.seed(),index,5));
    }
    // Integer hash matches GLSL exactly; branch geometry is stable across both render paths.
    private static float branchRandom(float seed,int branch,int channel) {
        int value=(int)Math.floor(seed*256)+branch*374761393+channel*668265263;
        value=(value^(value>>>16))*0x7feb352d;
        value=(value^(value>>>15))*0x846ca68b;
        return ((value^(value>>>16))>>>8)/16777216F;
    }
    public static Event event(double seconds, int index) {
        long key=cycle(seconds,index)*31+index*7919L;
        double angle=random(key,0)*Math.PI*2;
        float y=(float)(.12+random(key,1)*.78);
        double radius=Math.sqrt(1-y*y);
        return new Event((float)(Math.cos(angle)*radius),y,(float)(Math.sin(angle)*radius),
                (float)(random(key,2)*Math.PI*2), (float)(.23+random(key,3)*.28),
                (float)(.045+random(key,4)*.085), (float)(random(key,5)*100), (float)random(key,6));
    }
    private static double random(long key, int channel) {
        long value=key+0x9E3779B97F4A7C15L*(channel+1);
        value=(value^(value>>>30))*0xBF58476D1CE4E5B9L;
        value=(value^(value>>>27))*0x94D049BB133111EBL;
        return ((value^(value>>>31))>>>11)*0x1.0p-53;
    }
    public static float openness(double seconds, int index) {
        Schedule event=schedule(seconds,index);
        return (float)(smooth(event.start(),event.start()+event.opening(),seconds)
                *(1-smooth(event.closingStart(),event.end(),seconds)));
    }
    private static double smooth(double a, double b, double x) {
        double t = Math.clamp((x-a)/(b-a),0,1);
        return t*t*(3-2*t);
    }
}
