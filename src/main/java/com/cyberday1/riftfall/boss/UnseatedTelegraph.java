package com.cyberday1.riftfall.boss;

import static com.cyberday1.riftfall.boss.UnseatedEncounter.*;

/** Shared, CPU-only ground geometry; radii/heading are the exact server combat parameters. */
public final class UnseatedTelegraph {
    public static final int AMBER=0xFFBE55, DANGER=0xFF6655, VIOLET=0xC492FF, CYAN=0x72ECFF, WHITE=0xFFF4D9;
    @FunctionalInterface public interface Quads {
        void quad(double x0,double z0,double x1,double z1,double x2,double z2,double x3,double z3,int rgb,int alpha);
    }
    /** A vertical stroke standing on the floor at (x,z): the edge of an attack that hits a column, for a player in the air. */
    @FunctionalInterface public interface Strokes {
        void stroke(double x,double z,double height,double half,int rgb,int alpha);
    }
    private UnseatedTelegraph(){}
    /** The god-centred shapes of an action, drawn around the god where it sits now, at the reach of its seat. */
    public static void emit(Action action,double tick,float yaw,Quads out){emit(action,tick,yaw,reach(0),out);}
    public static void emit(Action action,double tick,float yaw,double reach,Quads out){
        if(!isAttack(action) || tick>=action.activeEnd)return;
        // A combo draws each of its attacks on its own clock; the Crown's circles and the lane are drawn on the court.
        for(var part:parts(action))shape(part.action(),tick-part.offset(),yaw,reach,out);
    }
    private static void shape(Action action,double tick,float yaw,double reach,Quads out){
        if(tick>=action.activeEnd)return;
        boolean warning=tick<action.windup;
        int fill=warning?32+(int)(Math.clamp(tick/action.windup,0,1)*38):105;
        if(action==Action.SWEEP){
            double from=yaw-SWEEP_HALF_ANGLE,to=yaw+SWEEP_HALF_ANGLE;
            int color=warning?AMBER:DANGER;
            arc(out,SWEEP_INNER,SWEEP_OUTER,from,to,color,fill);
            arc(out,SWEEP_OUTER-.13,SWEEP_OUTER,from,to,WHITE,220);
            if(SWEEP_INNER>0)arc(out,SWEEP_INNER,SWEEP_INNER+.13,from,to,WHITE,220);
            // Edge strokes sit wholly inside the wedge, so the marking never claims ground the hit test does not.
            edge(out,SWEEP_INNER,SWEEP_OUTER,from,.12,true,WHITE,220);
            edge(out,SWEEP_INNER,SWEEP_OUTER,to,.12,false,WHITE,220);
            // Hatching communicates danger without relying on red/green colour discrimination.
            for(double a=from+10;a<to;a+=15)ray(out,SWEEP_INNER+.2,SWEEP_OUTER-.2,a,.035,color,100);
        }else if(action==Action.PULL){
            arc(out,PULL_INNER,reach,0,360,VIOLET,warning?30:50);
            arc(out,PULL_INNER,PULL_INNER+.16,0,360,CYAN,225);
            arc(out,reach-.16,reach,0,360,VIOLET,225);
            for(int a=0;a<360;a+=30)for(int r=10;r<=reach-4;r+=7)
                arrow(out,r-(warning?0:(tick-action.windup)%16/8),a,true,VIOLET);
        }else if(action==Action.PULSE){
            if(warning){
                // Dim extent = travel path. Only the thick launch ring becomes the moving hit band.
                arc(out,Math.max(0,PULSE_START-PULSE_HALF_WIDTH),reach+PULSE_HALF_WIDTH,0,360,CYAN,16);
                for(int a=0;a<360;a+=12)arc(out,reach-.08,reach+.08,a,a+6,CYAN,120);
                for(int a=0;a<360;a+=30)arrow(out,7,a,false,CYAN);
            }
            double radius=warning?PULSE_START:pulseRadius(tick,reach);
            // The ring starts at the god's centre: until it has grown past its own half-width it is a disc.
            arc(out,Math.max(0,radius-PULSE_HALF_WIDTH),radius+PULSE_HALF_WIDTH,0,360,CYAN,warning?fill:150);
            arc(out,radius+PULSE_HALF_WIDTH-.12,radius+PULSE_HALF_WIDTH,0,360,WHITE,240);
            if(radius>PULSE_HALF_WIDTH)arc(out,radius-PULSE_HALF_WIDTH,radius-PULSE_HALF_WIDTH+.12,0,360,WHITE,200);
        }else if(action==Action.THRONE_FALL){
            // A filled disc with radial spokes: get out of it.
            int color=warning?AMBER:DANGER;
            arc(out,0,THRONE_RADIUS,0,360,color,fill);
            arc(out,THRONE_RADIUS-.14,THRONE_RADIUS,0,360,WHITE,230);
            for(int a=0;a<360;a+=20)ray(out,1,THRONE_RADIUS-.3,a,.07,WHITE,190);
        }
    }
    /**
     * One Crown of Weight circle around its own centre: a faint fill with a dashed double ring on its rim. It is dim
     * while it follows its player and fills as the lock runs out.
     */
    public static void crown(double tick,Quads out){
        var action=Action.CROWN;
        if(tick>=action.activeEnd)return;
        boolean locked=tick>=CROWN_FOLLOW,warning=tick<action.windup;
        int color=warning?AMBER:DANGER;
        int fill=!locked?22:warning?36+(int)(Math.clamp((tick-CROWN_FOLLOW)/CROWN_LOCK,0,1)*44):110;
        arc(out,0,CROWN_RADIUS,0,360,color,fill);
        for(int a=0;a<360;a+=20){
            arc(out,CROWN_RADIUS-.14,CROWN_RADIUS,a,a+12,WHITE,locked?235:150);
            arc(out,CROWN_RADIUS-.5,CROWN_RADIUS-.36,a+6,a+18,WHITE,locked?200:120);
        }
    }
    /**
     * The glide's lane, as offsets from the court centre: the god's body swept from {@code a} to {@code b}, 8 wide with
     * rounded ends, chevrons along the travel and a dashed edge. During the glide only the stretch still ahead remains.
     */
    public static void lane(double tick,double ax,double az,double bx,double bz,Quads out){
        var action=Action.RESEAT;
        if(tick>=action.activeEnd)return;
        boolean warning=tick<action.windup;
        double f=glide(tick-1);
        double sx=ax+(bx-ax)*f,sz=az+(bz-az)*f,dx=bx-sx,dz=bz-sz,length=Math.hypot(dx,dz);
        int color=warning?AMBER:DANGER,fill=warning?32+(int)(Math.clamp(tick/action.windup,0,1)*38):95;
        double heading=Math.toDegrees(Math.atan2(-(bx-ax),bz-az));
        // Rounded ends: the body is a circle of the lane's half-width, so the ground it covers is a capsule.
        disc(out,sx,sz,LANE_HALF_WIDTH,heading+90,heading+270,color,fill);
        disc(out,bx,bz,LANE_HALF_WIDTH,heading-90,heading+90,color,fill);
        if(length<1e-6)return;
        double ux=dx/length,uz=dz/length,px=-uz*LANE_HALF_WIDTH,pz=ux*LANE_HALF_WIDTH;
        out.quad(sx+px,sz+pz,bx+px,bz+pz,bx-px,bz-pz,sx-px,sz-pz,color,fill);
        // Dashed edges, inside the lane, and chevrons pointing the way the god will travel.
        for(double t=0;t<length;t+=1.2){
            double e=Math.min(length,t+.7),k=(LANE_HALF_WIDTH-.12)/LANE_HALF_WIDTH;
            for(int side:new int[]{1,-1})line(out,sx+ux*t+px*k*side,sz+uz*t+pz*k*side,sx+ux*e+px*k*side,sz+uz*e+pz*k*side,.1,WHITE,220);
        }
        for(double t=1.5;t<length-.5;t+=2.5){
            double cx=sx+ux*t,cz=sz+uz*t,bxk=cx-ux*.9,bzk=cz-uz*.9;
            line(out,bxk+px*.35,bzk+pz*.35,cx,cz,.09,WHITE,200);line(out,bxk-px*.35,bzk-pz*.35,cx,cz,.09,WHITE,200);
        }
    }
    /** Vertical edges for attacks that hit a column, around the god: the sweep's on its wedge rim, Throne Fall's on its disc. */
    public static void emitVertical(Action action,double tick,float yaw,Strokes out){
        if(!isAttack(action) || tick>=action.activeEnd)return;
        for(var part:parts(action))vertical(part.action(),tick-part.offset(),yaw,out);
    }
    private static void vertical(Action action,double tick,float yaw,Strokes out){
        if(tick>=action.activeEnd)return;
        int color=tick<action.windup?AMBER:DANGER;
        if(action==Action.SWEEP){
            for(double a=yaw-SWEEP_HALF_ANGLE;a<=yaw+SWEEP_HALF_ANGLE+1e-6;a+=SWEEP_HALF_ANGLE/3){
                double r=Math.toRadians(a);
                out.stroke(-Math.sin(r)*SWEEP_OUTER,Math.cos(r)*SWEEP_OUTER,SWEEP_HEIGHT,.08,color,170);
            }
            // The two straight sides, at mid-length.
            for(double a:new double[]{yaw-SWEEP_HALF_ANGLE,yaw+SWEEP_HALF_ANGLE}){
                double r=Math.toRadians(a),mid=(SWEEP_INNER+SWEEP_OUTER)/2;
                out.stroke(-Math.sin(r)*mid,Math.cos(r)*mid,SWEEP_HEIGHT,.08,color,170);
            }
        }else if(action==Action.THRONE_FALL){
            for(int a=0;a<360;a+=30)out.stroke(-Math.sin(Math.toRadians(a))*THRONE_RADIUS,Math.cos(Math.toRadians(a))*THRONE_RADIUS,THRONE_HEIGHT,.08,color,170);
        }
    }
    /** The Crown's column edges, around its circle's centre: floor to ceiling, so a flyer sees it follow them up. */
    public static void crownVertical(double tick,Strokes out){
        var action=Action.CROWN;
        if(tick>=action.activeEnd)return;
        int color=tick<action.windup?AMBER:DANGER;
        for(int a=0;a<360;a+=45)out.stroke(-Math.sin(Math.toRadians(a))*CROWN_RADIUS,Math.cos(Math.toRadians(a))*CROWN_RADIUS,
                COURT_CEILING,.07,color,tick>=CROWN_FOLLOW?170:90);
    }
    /** The lane's edges as offsets from the court centre, the height of the god's body. */
    public static void laneVertical(double tick,double ax,double az,double bx,double bz,Strokes out){
        var action=Action.RESEAT;
        if(tick>=action.activeEnd)return;
        int color=tick<action.windup?AMBER:DANGER;
        double dx=bx-ax,dz=bz-az,length=Math.hypot(dx,dz);
        if(length<1e-6)return;
        double px=-dz/length*LANE_HALF_WIDTH,pz=dx/length*LANE_HALF_WIDTH;
        for(double t=0;t<=1+1e-6;t+=.25)for(int side:new int[]{1,-1})
            out.stroke(ax+dx*t+px*side,az+dz*t+pz*side,LANE_HEIGHT,.08,color,160);
    }
    public static void binding(Quads out){
        for(int a=0;a<360;a+=15)arc(out,8.8,9,a,a+10,CYAN,180);
    }
    private static void disc(Quads out,double cx,double cz,double radius,double from,double to,int rgb,int alpha){
        int steps=(int)Math.ceil((to-from)/6);
        for(int i=0;i<steps;i++){
            double a=Math.toRadians(from+(to-from)*i/steps),b=Math.toRadians(from+(to-from)*(i+1)/steps);
            out.quad(cx,cz,cx-Math.sin(a)*radius,cz+Math.cos(a)*radius,cx-Math.sin(b)*radius,cz+Math.cos(b)*radius,cx,cz,rgb,alpha);
        }
    }
    private static void arrow(Quads out,double radius,double angle,boolean inward,int rgb){
        double a=Math.toRadians(angle),dx=-Math.sin(a),dz=Math.cos(a),sx=Math.cos(a),sz=Math.sin(a);
        double tip=radius+(inward?-1:1),back=radius+(inward?.5:-.5);
        // Two strokes form a chevron; arrow tips always point along the actual force/travel direction.
        line(out,dx*back+sx*.6,dz*back+sz*.6,dx*tip,dz*tip,.09,rgb,190);
        line(out,dx*back-sx*.6,dz*back-sz*.6,dx*tip,dz*tip,.09,rgb,190);
    }
    private static void ray(Quads out,double inner,double outer,double angle,double half,int rgb,int alpha){
        double a=Math.toRadians(angle);line(out,-Math.sin(a)*inner,Math.cos(a)*inner,-Math.sin(a)*outer,Math.cos(a)*outer,half,rgb,alpha);
    }
    /** A ray along a wedge side, shifted inward by its own half-width. {@code left} is the side at the lower angle. */
    private static void edge(Quads out,double inner,double outer,double angle,double half,boolean left,int rgb,int alpha){
        double a=Math.toRadians(angle),dx=-Math.sin(a),dz=Math.cos(a),sign=left?1:-1;
        // Perpendicular towards increasing angle is (-dz, dx).
        double px=-dz*sign*half,pz=dx*sign*half;
        line(out,dx*inner+px,dz*inner+pz,dx*outer+px,dz*outer+pz,half,rgb,alpha);
    }
    private static void line(Quads out,double x0,double z0,double x1,double z1,double half,int rgb,int alpha){
        double length=Math.hypot(x1-x0,z1-z0),dx=-(z1-z0)/length*half,dz=(x1-x0)/length*half;
        out.quad(x0+dx,z0+dz,x1+dx,z1+dz,x1-dx,z1-dz,x0-dx,z0-dz,rgb,alpha);
    }
    private static void arc(Quads out,double inner,double outer,double from,double to,int rgb,int alpha){
        int steps=(int)Math.ceil((to-from)/3);
        for(int i=0;i<steps;i++){
            double a=Math.toRadians(from+(to-from)*i/steps),b=Math.toRadians(from+(to-from)*(i+1)/steps);
            out.quad(-Math.sin(a)*inner,Math.cos(a)*inner,-Math.sin(a)*outer,Math.cos(a)*outer,
                    -Math.sin(b)*outer,Math.cos(b)*outer,-Math.sin(b)*inner,Math.cos(b)*inner,rgb,alpha);
        }
    }
}
