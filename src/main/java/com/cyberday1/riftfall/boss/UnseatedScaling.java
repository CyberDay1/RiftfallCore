package com.cyberday1.riftfall.boss;

import com.cyberday1.riftfall.progression.RiftTier;
import com.cyberday1.riftfall.progression.RiftXp;
import com.cyberday1.riftfall.rift.RiftKeyItem;
import com.cyberday1.riftfall.boss.UnseatedEncounter.Action;
import com.cyberday1.riftfall.boss.UnseatedEncounter.Phase;

/** The finale on the rift-boss model: tier and headcount locked at entry, effective health as a damage-taken multiplier. */
public final class UnseatedScaling {
    private UnseatedScaling(){}
    /** Vanilla caps MAX_HEALTH at 1024; the rest of the intended size rides {@link #damageTaken}. */
    public static final double MAX_HEALTH=1024, FINALE_HEALTH=1.25, ATTACK_DAMAGE=10;
    public static final double SWEEP_SHARE=1, PULSE_SHARE=.8, BINDING_SHARE=.75;
    // F6 and F7: the Crown's strike before sharing, Throne Fall, and the glide's lane.
    public static final double CROWN_SHARE=1, THRONE_SHARE=1.2, LANE_SHARE=.6;
    /** ASCENT tethers: each 6% of the god's effective health, and while any stands the god takes half. */
    public static final double TETHER_HEALTH=.06, TETHERED=.5;
    /** The highest entrant's tier, like a rift key forged by the party's highest-level member (D2). */
    public static int tier(int... levels){
        int tier=RiftKeyItem.DEFAULT_TIER;
        for(int level:levels)tier=Math.max(tier,RiftTier.of(Math.max(RiftXp.FIRST_LEVEL,level)));
        return tier;
    }
    /** Players who crossed; an empty attempt still fights as one. */
    public static int headcount(int crossed){return Math.max(1,crossed);}
    /** The fraction of a hit the god takes: the rift-boss curve over tier and party, then the finale's size. */
    public static float damageTaken(int tier,int headcount){
        return (float)(BossScaling.damageTakenMultiplier(tier,headcount)/FINALE_HEALTH);
    }
    /** {@link #damageTaken(int,int)} with this many tethers standing: half while any stands. */
    public static float damageTaken(int tier,int headcount,int tethersStanding){
        return damageTaken(tier,headcount)*(tethersStanding>0?(float)TETHERED:1);
    }
    /** Raw damage of one hit, from the scaled ATTACK_DAMAGE. Attacks during the binding are interrupts, not the damage check. */
    public static float damage(Action action,Phase phase,double attackDamage){
        double share=switch(action){
            case SWEEP -> SWEEP_SHARE;case PULSE -> PULSE_SHARE;case CROWN -> CROWN_SHARE;case THRONE_FALL -> THRONE_SHARE;
            case RESEAT -> LANE_SHARE;default -> 0;
        };
        return (float)(attackDamage*share*(phase==Phase.BINDING?BINDING_SHARE:1));
    }
}
