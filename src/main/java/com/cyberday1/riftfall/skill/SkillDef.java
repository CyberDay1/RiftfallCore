package com.cyberday1.riftfall.skill;

import org.jetbrains.annotations.Nullable;

/**
 * A castable node as the economy sees it: what it casts, what kind of purchase it is, the class and
 * ascendancy it belongs to, and the catalogue's mana cost (not charged: {@link #costAt}). Wraps
 * {@link SkillNode} rather than extending it, so the cast path stays ignorant of points and slots.
 *
 * <p>No tier and no prerequisite (ruled 2026-09-22): availability is the class, plus the chosen
 * ascendancy for an ascendancy row. A passive is a {@link PassiveDef}, never this, so it cannot be equipped.
 */
public record SkillDef(SkillNode cast, SkillBook.Kind kind, String skillClass, @Nullable String ascendancy,
                       double manaCost, Reflects.@Nullable Spec reflect, Stored.@Nullable Spec stored)
        implements SkillBook.Purchase {

    public SkillDef(SkillNode cast, SkillBook.Kind kind, String skillClass, @Nullable String ascendancy,
                    double manaCost) {
        this(cast, kind, skillClass, ascendancy, manaCost, null, null);
    }

    /** The same node with its reflect and stored terms, read beside the node codec. */
    public SkillDef withTerms(Reflects.@Nullable Spec reflect, Stored.@Nullable Spec stored) {
        return new SkillDef(cast, kind, skillClass, ascendancy, manaCost, reflect, stored);
    }

    public SkillDef {
        if (cast == null || kind == null) {
            throw new IllegalArgumentException("a skill needs a node and a kind");
        }
        if (kind == SkillBook.Kind.PASSIVE) {
            throw new IllegalArgumentException(cast.id() + ": a castable cannot be a passive");
        }
        if (skillClass == null || skillClass.isBlank()) {
            throw new IllegalArgumentException(cast.id() + ": a skill needs a class");
        }
        if (ascendancy != null && ascendancy.isBlank()) {
            throw new IllegalArgumentException(cast.id() + ": an ascendancy name cannot be blank; omit it for a base skill");
        }
        if (cast.maxRank() != kind.maxRank()) {
            throw new IllegalArgumentException(cast.id() + ": a " + kind + " has " + kind.maxRank()
                    + " ranks, the node says " + cast.maxRank());
        }
        if (kind == SkillBook.Kind.ULTIMATE && ascendancy == null) {
            throw new IllegalArgumentException(cast.id() + ": every ultimate belongs to an ascendancy");
        }
        if (!(manaCost >= 0.0) || Double.isInfinite(manaCost)) {
            throw new IllegalArgumentException(cast.id() + ": mana cost must be finite and non-negative, was " + manaCost);
        }
    }

    public String id() {
        return cast.id();
    }

    public boolean isAscendancy() {
        return ascendancy != null;
    }

    /**
     * What one cast at this rank takes from the bar: the lever kind's price (M-1, free for an ultimate), which
     * Control's lever is the only thing to move. {@link #manaCost} is the catalogue's number and is not charged.
     */
    public double costAt(int rank) {
        return ManaRules.price(kind, cast.lever()) * SkillRanks.costMultiplier(cast, rank);
    }
}
