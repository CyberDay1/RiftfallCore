package com.cyberday1.riftfall.skill;

import com.cyberday1.riftfall.skill.SkillNode.Buff;
import com.cyberday1.riftfall.skill.SkillNode.Control;
import com.cyberday1.riftfall.skill.SkillNode.Damage;
import com.cyberday1.riftfall.skill.SkillNode.DoT;
import com.cyberday1.riftfall.skill.SkillNode.Heal;
import com.cyberday1.riftfall.skill.SkillNode.Shield;
import com.cyberday1.riftfall.skill.SkillNode.Summon;

/**
 * What a point buys, as arithmetic, one method per lever. A lever the node's archetype does not
 * carry reads as neutral (1.0 or 0), never as an error, so a caller can ask every question.
 *
 * <p>Multipliers are linear; cooldown and cost are geometric, because a geometric reduction cannot
 * cross zero and no rank may make a node free.
 */
public final class SkillRanks {

    /** A cooldown never rounds below this, so a large rank cannot make a node instant. */
    public static final int MIN_COOLDOWN_TICKS = 1;

    private SkillRanks() {
    }

    /** Damage and DoT. The factor on the node's power attribute; 1.0 at rank 0. */
    public static double damageMultiplier(SkillNode node, int rank) {
        require(node, rank);
        return switch (node.lever()) {
            case Damage d -> linear(d.damagePerRank(), rank);
            case DoT d -> linear(d.damagePerRank(), rank);
            default -> 1.0;
        };
    }

    /** Heal, Shield and Buff: how much larger the heal, the shield or the buff is. */
    public static double magnitudeMultiplier(SkillNode node, int rank) {
        require(node, rank);
        return switch (node.lever()) {
            case Heal h -> linear(h.magnitudePerRank(), rank);
            case Shield s -> linear(s.magnitudePerRank(), rank);
            case Buff b -> linear(b.magnitudePerRank(), rank);
            default -> 1.0;
        };
    }

    /** DoT only: seconds added to the damage-over-time's own duration. */
    public static double dotSeconds(SkillNode node, int rank) {
        require(node, rank);
        return node.lever() instanceof DoT d ? d.durationSecondsPerRank() * rank : 0.0;
    }

    /** Buff only: seconds added to the buff's status effect. */
    public static double buffSeconds(SkillNode node, int rank) {
        require(node, rank);
        return node.lever() instanceof Buff b ? b.durationSecondsPerRank() * rank : 0.0;
    }

    /** Summon only: seconds added to the summon's lifespan. */
    public static double summonSeconds(SkillNode node, int rank) {
        require(node, rank);
        return node.lever() instanceof Summon s ? s.durationSecondsPerRank() * rank : 0.0;
    }

    /** Summon only: the fraction added to the ability's own owner-scaling coefficients. */
    public static double ownerScaling(SkillNode node, int rank) {
        require(node, rank);
        return node.lever() instanceof Summon s ? s.ownerScalingPerRank() * rank : 0.0;
    }

    /** Control only: the factor on the cast's mana cost, read by {@link SkillDef#costAt}. Geometric. */
    public static double costMultiplier(SkillNode node, int rank) {
        require(node, rank);
        return node.lever() instanceof Control c ? Math.pow(1.0 - c.costPerRank(), rank) : 1.0;
    }

    /** The cooldown a rank leaves, in ticks. Geometric, and floored at {@link #MIN_COOLDOWN_TICKS}. */
    public static int cooldownTicks(SkillNode node, int rank) {
        return cooldownTicks(node, rank, 0.0);
    }

    /** As {@link #cooldownTicks(SkillNode, int)}, then divided by {@code 1 + haste / 100} (ability haste). */
    public static int cooldownTicks(SkillNode node, int rank, double haste) {
        require(node, rank);
        double left = node.baseCooldownTicks() * Math.pow(1.0 - node.cooldownPerRank(), rank) / hasteDivisor(haste);
        return Math.max(MIN_COOLDOWN_TICKS, (int) Math.round(left));
    }

    /** {@code 1 + haste / 100}; negative or NaN haste counts as none. */
    public static double hasteDivisor(double haste) {
        return haste > 0.0 ? 1.0 + haste / 100.0 : 1.0;
    }

    /** A passive's one lever: base + step × rank, in its own unit. 0 at rank 0, which is not bought. */
    public static double passiveBonus(PassiveNode node, int rank) {
        if (!node.holds(rank)) {
            throw new IllegalArgumentException(node.id() + ": rank " + rank + " is outside 0.." + node.maxRank());
        }
        return rank == 0 ? 0.0 : node.bonusBase() + node.bonusPerRank() * rank;
    }

    private static double linear(double rate, int rank) {
        return 1.0 + rate * rank;
    }

    private static void require(SkillNode node, int rank) {
        if (!node.holds(rank)) {
            throw new IllegalArgumentException(node.id() + ": rank " + rank + " is outside 0.." + node.maxRank());
        }
    }
}
