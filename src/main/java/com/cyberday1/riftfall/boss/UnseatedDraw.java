package com.cyberday1.riftfall.boss;

import com.cyberday1.riftfall.boss.UnseatedEncounter.Action;
import com.cyberday1.riftfall.boss.UnseatedEncounter.Phase;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * The order of the god's attacks (F6): each phase draws from its bag without replacement, and no attack follows
 * itself, across a refill or a phase change included; only a phase's opener (the binding's sweep) is exempt, from the
 * last phase or from before a reset. Seeded per attempt, so the same attempt replays the same fight.
 * The same random also places the Crown's marks and picks the next seat. Practice keeps its fixed order.
 */
final class UnseatedDraw {
    private final Random random;
    private final Deque<Action> bag=new ArrayDeque<>();
    private Phase phase;
    private Action last;
    private boolean reopen;

    UnseatedDraw(long seed){this(seed,Action.IDLE);}
    UnseatedDraw(long seed,Action last){random=new Random(seed);this.last=last;}
    /** Carries on a saved draw: what was left in the bag of that phase, so a reload neither skips nor repeats a draw. */
    UnseatedDraw(long seed,Action last,Phase phase,List<Action> left){this(seed,last);if(phase!=null){this.phase=phase;bag.addAll(left);}}
    /** The attempt's seed: its arena's UUID, folded to a long. */
    static long seed(UUID attempt){return attempt==null?0:attempt.getMostSignificantBits()^attempt.getLeastSignificantBits();}

    Random random(){return random;}
    Action last(){return last;}
    /** The phase the bag was filled for, null before the first draw. */
    Phase phase(){return phase;}
    /** What is still in the bag, in draw order. */
    List<Action> left(){return List.copyOf(bag);}
    /** The phase starts again from nothing (its progress reset): its next draw is the opener again (ruled 2026-11-16). */
    void reopen(){reopen=true;}

    Action next(Phase now){
        var first=UnseatedEncounter.opener(now);
        // A phase's opener goes in ahead of its first bag, even after the same attack in the last phase. Drawn at
        // once, it is never saved: a save before it holds the old phase, so a reload still opens with it.
        if(now!=phase){phase=now;bag.clear();if(first!=null)bag.add(first);}
        // A reopen leads with it too, after anything. The bag's own goes first if it is still in, so none follows it.
        else if(reopen && first!=null){bag.remove(first);bag.addFirst(first);}
        reopen=false;
        if(bag.isEmpty())bag.addAll(refill(now));
        Action next=bag.poll();
        if(next==null)return Action.IDLE;
        last=next;return next;
    }
    /** One bag, in an order picked uniformly from every order that never puts an attack next to itself. */
    private List<Action> refill(Phase now){
        var entries=new ArrayList<>(UnseatedEncounter.bag(now));
        var combos=UnseatedEncounter.combos(now);
        // Once per bag cycle in the Ascent: one combo of two attacks.
        if(!combos.isEmpty())entries.add(combos.get(random.nextInt(combos.size())));
        var valid=new ArrayList<List<Action>>();
        permute(entries,0,valid);
        if(valid.isEmpty())return entries;
        return valid.get(random.nextInt(valid.size()));
    }
    private void permute(List<Action> entries,int from,List<List<Action>> valid){
        if(from==entries.size()){
            Action previous=last;
            for(var a:entries){if(UnseatedEncounter.overlaps(previous,a))return;previous=a;}
            valid.add(List.copyOf(entries));return;
        }
        for(int i=from;i<entries.size();i++){
            java.util.Collections.swap(entries,from,i);permute(entries,from+1,valid);java.util.Collections.swap(entries,from,i);
        }
    }
}
