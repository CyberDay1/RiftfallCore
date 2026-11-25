package com.cyberday1.riftfall.skill;

/**
 * One passive node. It has no ability and no cooldown of its own: a flat passive never fires, and a
 * spell passive's internal cooldown lives on {@link PassiveDef} (PA-3). One lever: a larger bonus.
 *
 * <p>The bonus is added per rank, in the unit of whatever the passive grants, so the node does not
 * have to know what that is. Rank 0 is not bought and grants nothing. {@link PassiveDef} names it.
 * {@code bonusBase} is added once at any bought rank (0 for most nodes), so a row can open higher than
 * its step: 10% at rank 1 and +5% a rank after is base 0.05 and 0.05 a rank.
 */
public record PassiveNode(String id, Shape shape, int maxRank, double bonusPerRank, double bonusBase) {

    /** The page's split: always true, tested per hit, or fired by an event. */
    public enum Shape {
        FLAT, CONDITIONAL, PROC
    }

    public PassiveNode {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("a passive needs an id");
        }
        if (shape == null) {
            throw new IllegalArgumentException(id + ": shape is required");
        }
        if (maxRank < 0) {
            throw new IllegalArgumentException(id + ": maxRank cannot be negative, was " + maxRank);
        }
        SkillNode.nonNegative("bonusPerRank", bonusPerRank);
        SkillNode.nonNegative("bonusBase", bonusBase);
    }

    /** No base: the bonus is the rank times the step. */
    public PassiveNode(String id, Shape shape, int maxRank, double bonusPerRank) {
        this(id, shape, maxRank, bonusPerRank, 0.0);
    }

    public boolean holds(int rank) {
        return rank >= 0 && rank <= maxRank;
    }
}
