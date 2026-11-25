package com.cyberday1.riftfall.skill;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * A passive as the economy sees it: bought on the same page and with the same points as a skill,
 * never equipped. A flat passive is written onto {@code attribute} by {@link PassiveModifiers}; a
 * conditional or proc passive grants a Spell Engine PASSIVE {@code spell}, whose triggers do the work.
 *
 * <p>{@code internalCooldownTicks} is Riftfall's own proc timer on a spell passive; {@link #SPELL_COOLDOWN}
 * takes the spell's authored cooldown, which the engine itself never applies to a player.
 *
 * <p>{@code targetHealthBelow} is a spell passive's hit window, on the node so it can be retuned without
 * touching the spell: a proc is delivered only on a target at or under that fraction of its health.
 * {@link #ANY_HEALTH} is no window.
 *
 * <p>{@code reaction} and {@code surge} are a spell passive's reaction terms (Reactions): an event Riftfall fires,
 * a hit or cast counter, and the buff its {@code riftfall:surge} impact grants. Both null on most nodes.
 */
public record PassiveDef(PassiveNode node, String skillClass, @Nullable String ascendancy,
                         @Nullable ResourceLocation attribute, @Nullable ResourceLocation spell,
                         int internalCooldownTicks, double targetHealthBelow,
                         Reactions.@Nullable Reaction reaction, Reactions.@Nullable Surge surge)
        implements SkillBook.Purchase {

    /** No cooldown of the node's own: the spell's authored {@code cost.cooldown.duration} applies. */
    public static final int SPELL_COOLDOWN = -1;
    /** No hit window: every target qualifies. */
    public static final double ANY_HEALTH = 1.0;

    /** No reaction terms. */
    public PassiveDef(PassiveNode node, String skillClass, @Nullable String ascendancy, @Nullable ResourceLocation attribute,
                      @Nullable ResourceLocation spell, int internalCooldownTicks, double targetHealthBelow) {
        this(node, skillClass, ascendancy, attribute, spell, internalCooldownTicks, targetHealthBelow, null, null);
    }

    /** A spell passive with no hit window. */
    public PassiveDef(PassiveNode node, String skillClass, @Nullable String ascendancy, @Nullable ResourceLocation attribute,
                      @Nullable ResourceLocation spell, int internalCooldownTicks) {
        this(node, skillClass, ascendancy, attribute, spell, internalCooldownTicks, ANY_HEALTH);
    }

    /** A flat passive on an attribute. */
    public PassiveDef(PassiveNode node, String skillClass, @Nullable String ascendancy, ResourceLocation attribute) {
        this(node, skillClass, ascendancy, attribute, null, SPELL_COOLDOWN, ANY_HEALTH, null, null);
    }

    public PassiveDef {
        if (node == null || (attribute == null) == (spell == null)) {
            throw new IllegalArgumentException("a passive needs a node and exactly one of an attribute or a spell");
        }
        if (!ResourceLocation.isValidPath(node.id())) {
            throw new IllegalArgumentException(node.id() + ": a passive's id is its modifier path, so it must be a valid path");
        }
        if (attribute != null && node.shape() != PassiveNode.Shape.FLAT) {
            throw new IllegalArgumentException(node.id() + ": only a flat passive can be an attribute, was " + node.shape());
        }
        if (spell != null && node.shape() == PassiveNode.Shape.FLAT) {
            throw new IllegalArgumentException(node.id() + ": a spell passive fires, so it is conditional or proc, not flat");
        }
        if (attribute != null && internalCooldownTicks != SPELL_COOLDOWN) {
            throw new IllegalArgumentException(node.id() + ": an attribute never fires, so it has no internal cooldown");
        }
        if (!(targetHealthBelow >= 0.0 && targetHealthBelow <= ANY_HEALTH)) {
            throw new IllegalArgumentException(node.id() + ": a hit window is a health fraction from 0 to 1, was "
                    + targetHealthBelow);
        }
        if (attribute != null && targetHealthBelow != ANY_HEALTH) {
            throw new IllegalArgumentException(node.id() + ": an attribute never hits, so it has no hit window");
        }
        if (attribute != null && (reaction != null || surge != null)) {
            throw new IllegalArgumentException(node.id() + ": an attribute never fires, so it has no reaction or surge");
        }
        if (internalCooldownTicks < SPELL_COOLDOWN) {
            throw new IllegalArgumentException(node.id() + ": internal cooldown must be 0 or more ticks, was "
                    + internalCooldownTicks);
        }
        if (node.maxRank() != SkillBook.Kind.PASSIVE.maxRank()) {
            throw new IllegalArgumentException(node.id() + ": a PASSIVE has " + SkillBook.Kind.PASSIVE.maxRank()
                    + " ranks, the node says " + node.maxRank());
        }
        if (skillClass == null || skillClass.isBlank()) {
            throw new IllegalArgumentException(node.id() + ": a passive needs a class, or " + UNIVERSAL);
        }
        if (ascendancy != null && (ascendancy.isBlank() || UNIVERSAL.equals(skillClass))) {
            throw new IllegalArgumentException(node.id() + ": a universal passive has no ascendancy, and a name cannot be blank");
        }
    }

    @Override
    public String id() {
        return node.id();
    }

    @Override
    public SkillBook.Kind kind() {
        return SkillBook.Kind.PASSIVE;
    }

    /** What this passive puts on its attribute at {@code rank}, or, for a spell, the spell's power multiplier. */
    public double amountAt(int rank) {
        return SkillRanks.passiveBonus(node, rank);
    }

    public boolean grantsSpell() {
        return spell != null;
    }

    /** Whether a target at {@code healthFraction} of its health is inside the hit window (inclusive). */
    public boolean inWindow(double healthFraction) {
        return targetHealthBelow == ANY_HEALTH || healthFraction <= targetHealthBelow;
    }

    /** The proc timer in ticks: the node's own, else {@code spellCooldownSeconds} (the spell's authored one). */
    public int cooldownTicks(double spellCooldownSeconds) {
        if (internalCooldownTicks != SPELL_COOLDOWN) {
            return internalCooldownTicks;
        }
        return spellCooldownSeconds > 0.0 ? (int) Math.round(spellCooldownSeconds * 20.0) : 0;
    }
}
