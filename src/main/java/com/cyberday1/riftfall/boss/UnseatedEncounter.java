package com.cyberday1.riftfall.boss;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;
import java.util.random.RandomGenerator;

/** Shared combat/animation clock. Times are server ticks, not render frames. */
public final class UnseatedEncounter {
    private UnseatedEncounter() {}
    // Heights (h) are measured from the court floor, never the god's feet, so altitude answers nothing.
    public static final double SWEEP_INNER=0, SWEEP_OUTER=12, SWEEP_HALF_ANGLE=65, SWEEP_HEIGHT=12;
    public static final double PULL_INNER=5, PULL_OUTER=28, PULSE_START=0, PULSE_END=28, PULSE_HALF_WIDTH=.9;
    public static final double PULL_SINK=-.06, PULL_SINK_ABOVE=1.5;
    public static final double COURT_RADIUS=30, COURT_FLOOR=-1, COURT_CEILING=26;
    public static final int PRACTICE_LEAD=40;
    // F6. Crown of Weight: a circle follows each marked player, locks for the two-second warning, then strikes a column.
    public static final double CROWN_RADIUS=3.5, CROWN_EXTRA_CHANCE=.2, CROWN_STACK=.5, CROWN_MARK_ABOVE=2;
    public static final int CROWN_FOLLOW=30, CROWN_LOCK=40;
    // Throne Fall: a disc around the god, the cost of camping the pull's calm ring and its feet.
    public static final double THRONE_RADIUS=8, THRONE_HEIGHT=16;
    // F7. Seats 8 off centre towards the four anchors; the glide's lane is the god's body, 8 wide and 15 tall.
    public static final double SEAT_OFFSET=8, LANE_HALF_WIDTH=4, LANE_HEIGHT=15, TETHER_RADIUS=24;
    public static final int RESEAT_EVERY=3, SEATS=4, MAX_TETHERS=4;
    /** The far edge of the combat floor; reach is measured past it so the perimeter cannot be camped. */
    public static final double FLOOR_EDGE=26, REACH_MARGIN=2;
    /** Ticks the god rests between attacks. The binding's is the longest, so one player alone has room to bind. */
    public static int idleGap(Phase phase){return phase==Phase.BINDING?40:phase==Phase.ASCENT?20:30;}
    /** Practice once: an attempt skips the trials only when every entrant has finished them before. */
    public static boolean skipsPractice(java.util.Collection<Boolean> practiced){return !practiced.isEmpty() && !practiced.contains(false);}
    public static boolean isAttack(Action action){return action.windup>0;}
    /** The attacks after the trials: each is harmless to a player the first time they see it (F8). */
    public static boolean firstSight(Action action){return isAttack(action) && action.ordinal()>Action.CONTAINED.ordinal();}
    public static Action lessonAction(int lesson){return switch(lesson){case 1 -> Action.SWEEP;case 2 -> Action.PULSE;case 3 -> Action.PULL;default -> Action.IDLE;};}
    public enum Phase { CLAIM, FRACTURE, ASCENT, BINDING, CONTAINED }
    public enum Action {
        IDLE(0,0,0), AWAKEN(80,0,0), SWEEP(80,40,41), PULL(110,40,80),
        PULSE(110,50,80), TRANSITION(70,0,0), BIND(100,0,0), CONTAINED(0,0,0),
        // Recovery after a strike is the 25 ticks every attack keeps, then the normal idle gap: nothing extra (2026-11-16).
        CROWN(CROWN_FOLLOW+CROWN_LOCK+26,CROWN_FOLLOW+CROWN_LOCK,CROWN_FOLLOW+CROWN_LOCK+1), THRONE_FALL(80,40,41),
        // The lane shows for 40 ticks, then the god glides through it over the next 40.
        RESEAT(105,40,80),
        // ASCENT combos: two attacks on one clock, with answers that fit together.
        REACH_RUPTURE(110,40,80), SWEEP_CROWN(CROWN_FOLLOW+CROWN_LOCK+26,CROWN_FOLLOW+CROWN_LOCK,CROWN_FOLLOW+CROWN_LOCK+1);
        public final int duration, windup, activeEnd;
        Action(int duration,int windup,int activeEnd) {
            this.duration=duration;this.windup=windup;this.activeEnd=activeEnd;
        }
        public boolean active(int tick) { return tick>=windup && tick<activeEnd; }
    }
    /** One attack inside an action, started {@code offset} ticks after the action. */
    public record Part(Action action,int offset){}
    /** Pull with pulse: stand in the calm ring and jump. A sweep during a Crown lock: its wind-up is the lock. */
    public static List<Part> parts(Action action){
        return switch(action){
            case REACH_RUPTURE -> List.of(new Part(Action.PULL,0),new Part(Action.PULSE,0));
            case SWEEP_CROWN -> List.of(new Part(Action.CROWN,0),new Part(Action.SWEEP,CROWN_FOLLOW));
            default -> List.of(new Part(action,0));
        };
    }
    /** Whether two consecutive draws share an attack: never the same twice in a row, combos included. */
    public static boolean overlaps(Action a,Action b){
        for(var x:parts(a))for(var y:parts(b))if(x.action()==y.action())return true;
        return false;
    }
    public static float floor(Phase phase,float maximum) {
        return switch(phase) { case CLAIM -> maximum*.70f; case FRACTURE -> maximum*.35f; default -> 1; };
    }
    /** Each phase's bag (ruled 2026-11-16). ASCENT adds one combo per refill; the binding has no pull to drag a binder out. */
    public static List<Action> bag(Phase phase) {
        return switch(phase) {
            case CLAIM -> List.of(Action.SWEEP,Action.PULSE,Action.PULL);
            case FRACTURE -> List.of(Action.SWEEP,Action.PULSE,Action.PULL,Action.CROWN);
            case ASCENT -> List.of(Action.SWEEP,Action.PULSE,Action.PULL,Action.CROWN,Action.THRONE_FALL);
            case BINDING -> List.of(Action.PULSE,Action.SWEEP,Action.CROWN);
            default -> List.of();
        };
    }
    /**
     * The attack a phase opens with before its first bag, or null. The binding's is the binder-aimed sweep, so a solo
     * binder is contested (ruled 2026-11-16): the Crown would land at 110 ticks, after the binder's 100.
     */
    public static Action opener(Phase phase){return phase==Phase.BINDING?Action.SWEEP:null;}
    public static List<Action> combos(Phase phase){return phase==Phase.ASCENT?List.of(Action.REACH_RUPTURE,Action.SWEEP_CROWN):List.of();}
    /** From the Fracture on the god moves: after every third attack it glides to another seat. */
    public static boolean reseats(Phase phase){return phase==Phase.FRACTURE || phase==Phase.ASCENT;}
    /** A column from the floor: the wedge reaches the god's feet and any height a player can hold under the ceiling. */
    public static boolean sweepHits(double x,double z,float yaw,double h) {
        double r=Math.hypot(x,z),a=Math.toRadians(yaw);
        if(h<0 || h>=SWEEP_HEIGHT || r<SWEEP_INNER || r>SWEEP_OUTER)return false;
        // The god's own centre is inside the wedge at every heading.
        return r<1e-6 || (-Math.sin(a)*x+Math.cos(a)*z)/r>=Math.cos(Math.toRadians(SWEEP_HALF_ANGLE));
    }
    /** The court: where a player fights from, and where a hit on the god may come from. Offsets are from the court centre. */
    public static boolean inCourt(double x,double z,double h) {
        return Math.hypot(x,z)<=COURT_RADIUS && h>=COURT_FLOOR && h<=COURT_CEILING;
    }
    /** The pull's vertical term: under gravity's 0.08, so a hovering player drifts down into the pulse's reach. */
    public static double pullSink(double h){return h>PULL_SINK_ABOVE?PULL_SINK:0;}
    /** Pulse end and pull outer radius for a god this far from the court centre: past the far floor edge from any seat. */
    public static double reach(double offset){return FLOOR_EDGE+offset+REACH_MARGIN;}
    // Cross the whole court, including its recovery lane, so the perimeter cannot be camped indefinitely.
    public static double pulseRadius(double tick) { return pulseRadius(tick,PULSE_END); }
    public static double pulseRadius(double tick,double end) { return PULSE_START+(end-PULSE_START)*Math.clamp((tick-Action.PULSE.windup)/(Action.PULSE.activeEnd-Action.PULSE.windup),0,1); }
    /** A ground wave: feet within [-0.8, 1) of the floor. Jumping it stays the answer. */
    public static boolean pulseHits(double radius,double footHeight,double tick) { return pulseHits(radius,footHeight,tick,PULSE_END); }
    public static boolean pulseHits(double radius,double footHeight,double tick,double end) {
        return footHeight>=-.8 && footHeight<1 && Math.abs(radius-pulseRadius(tick,end))<=PULSE_HALF_WIDTH;
    }
    /** A full column over a locked circle: offsets from the circle's centre, h from the floor. */
    public static boolean crownHits(double dx,double dz,double h){return Math.hypot(dx,dz)<=CROWN_RADIUS && h>=COURT_FLOOR && h<=COURT_CEILING;}
    /** Sharing a circle costs more: x(1 + 0.5(k - 1)) for k players inside. */
    public static double crownShare(int inside){return 1+CROWN_STACK*(Math.max(1,inside)-1);}
    /** How many players the Crown marks: one, and a 20% chance of one more for each player beyond the first. */
    public static int crownMarks(int players,RandomGenerator random){
        int marks=players>0?1:0;
        for(int p=2;p<=players;p++)if(random.nextDouble()<CROWN_EXTRA_CHANCE)marks++;
        return marks;
    }
    /**
     * Who the Crown marks, in order: binders first when there are any, then whoever's feet are highest if anyone is
     * more than 2 above the floor, otherwise anyone at random (the TargetRotation rule).
     */
    public static <T> List<T> crownTargets(List<T> players,ToDoubleFunction<T> height,Predicate<T> binder,int marks,RandomGenerator random){
        var pool=new ArrayList<>(players);var out=new ArrayList<T>();
        while(out.size()<marks && !pool.isEmpty()){
            var first=pool.stream().filter(binder).toList();
            var candidates=first.isEmpty()?pool:first;
            T highest=candidates.get(0);
            for(var p:candidates)if(height.applyAsDouble(p)>height.applyAsDouble(highest))highest=p;
            T pick=height.applyAsDouble(highest)>CROWN_MARK_ABOVE?highest:candidates.get(random.nextInt(candidates.size()));
            out.add(pick);pool.remove(pick);
        }
        return out;
    }
    /** A filled disc around the god, a column from the floor to 16 up. Offsets are from the god. */
    public static boolean throneHits(double x,double z,double h){return Math.hypot(x,z)<=THRONE_RADIUS && h>=0 && h<THRONE_HEIGHT;}
    /** Seat {@code n} (0 to 3) as an offset from the court centre, towards anchor n; -1 is the centre itself. */
    public static double[] seat(int n){
        if(n<0)return new double[]{0,0};
        double a=n*Math.PI/2;return new double[]{Math.sin(a)*SEAT_OFFSET,Math.cos(a)*SEAT_OFFSET};
    }
    /** A seat other than the current one: three to choose from at a seat, four from the centre. */
    public static int nextSeat(int current,RandomGenerator random){
        int pick=random.nextInt(current<0?SEATS:SEATS-1);
        return current>=0 && pick>=current?pick+1:pick;
    }
    /** How far along the lane the god is at this tick of a glide: 0 through the wind-up, 1 from its last glide tick. */
    public static double glide(double tick){
        var a=Action.RESEAT;return Math.clamp((tick-a.windup+1)/(a.activeEnd-a.windup),0,1);
    }
    /** Whether a point is within the god's half-width of the segment it glides along: the lane, ends included. */
    public static boolean laneHits(double px,double pz,double ax,double az,double bx,double bz,double h){
        return h>=0 && h<LANE_HEIGHT && laneDistance(px,pz,ax,az,bx,bz)<=LANE_HALF_WIDTH;
    }
    public static double laneDistance(double px,double pz,double ax,double az,double bx,double bz){
        double dx=bx-ax,dz=bz-az,length=dx*dx+dz*dz;
        double t=length<1e-9?0:Math.clamp(((px-ax)*dx+(pz-az)*dz)/length,0,1);
        return Math.hypot(px-(ax+dx*t),pz-(az+dz*t));
    }
    /** ASCENT tethers: two solo, three for two or three players, four for four or more. */
    public static int tethers(int headcount){return Math.min(MAX_TETHERS,2+Math.max(1,headcount)/2);}
    /** The anchor bridgeheads tethers rise at, as anchor indexes: opposite pairs first so a party has to split. */
    public static int[] tetherAnchors(int count){return java.util.Arrays.copyOf(new int[]{0,2,1,3},Math.clamp(count,0,MAX_TETHERS));}
    public static float smooth(double x) { float t=(float)Math.clamp(x,0,1);return t*t*(3-2*t); }
    public static float envelope(Action action,double tick) {
        if(action==Action.CONTAINED)return 1;
        if(action==Action.IDLE)return 0;
        if(action==Action.BIND)return smooth(tick/action.duration);
        return smooth(tick/Math.max(1,action.windup==0?30:action.windup))
                *(1-smooth((tick-Math.max(action.activeEnd,action.duration-25))/25));
    }
}
