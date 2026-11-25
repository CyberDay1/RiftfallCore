package com.cyberday1.riftfall.skill;

/**
 * The mana rules, ruled 2026-10-11 ("go with your suggestion"): one price per lever kind (M-1), a free
 * ultimate (M-3), a refill on entering a rift and on nothing else (M-4), and faster regen out of combat
 * (M-5). Every number is a default until alpha balance.
 */
public final class ManaRules {

    // M-1: the price of one cast by lever kind. The node's catalogue mana_cost is not charged.
    public static final double DAMAGE = 22.0;
    public static final double DOT = 24.0;
    public static final double CONTROL = 20.0;
    public static final double HEAL = 26.0;
    public static final double SHIELD = 26.0;
    public static final double BUFF = 22.0;
    public static final double SUMMON = 26.0;
    public static final double MOVEMENT = 14.0;

    // M-5: in combat for this long after dealing or taking an entity's damage, or casting a skill.
    public static final int COMBAT_WINDOW_TICKS = 100;
    /** Times the {@code riftfall:mana_regen} attribute, in combat. */
    public static final double IN_COMBAT_REGEN = 1.0;
    /** Times the {@code riftfall:mana_regen} attribute, out of combat. */
    public static final double OUT_OF_COMBAT_REGEN = 2.5;

    /** M-4: the moments a bar could be refilled at. Only entry does. */
    public enum Moment {
        RIFT_ENTRY,
        /** Back into a run already entered: a relog, or a paused run resumed. */
        REJOIN,
        /** Closes the die-to-refill exploit. */
        RESPAWN
    }

    private ManaRules() {
    }

    /** What one cast costs before Control's rank discount. Rank never raises it. */
    public static double price(SkillBook.Kind kind, SkillNode.Lever lever) {
        if (kind == SkillBook.Kind.ULTIMATE) {
            return 0.0;
        }
        return switch (lever) {
            case SkillNode.Damage d -> DAMAGE;
            case SkillNode.DoT d -> DOT;
            case SkillNode.Control c -> CONTROL;
            case SkillNode.Heal h -> HEAL;
            case SkillNode.Shield s -> SHIELD;
            case SkillNode.Buff b -> BUFF;
            case SkillNode.Summon s -> SUMMON;
            case SkillNode.Movement m -> MOVEMENT;
        };
    }

    public static double regenMultiplier(boolean inCombat) {
        return inCombat ? IN_COMBAT_REGEN : OUT_OF_COMBAT_REGEN;
    }

    /** {@code lastMark} below 0 means never marked. */
    public static boolean inCombat(long lastMark, long now) {
        return lastMark >= 0 && now - lastMark < COMBAT_WINDOW_TICKS;
    }

    public static boolean refills(Moment moment) {
        return moment == Moment.RIFT_ENTRY;
    }
}
