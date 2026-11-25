package com.cyberday1.riftfall.skill;

/**
 * How Riftfall's power replaces Iron's own on a node's cast. Iron's damage is the spell's unit power
 * times {@code spell_power} times the school's power; these factors turn that product into the node's
 * multiplier times its power attribute, so a bound Iron's spell reads Riftfall's stat as Spell Engine does.
 * Pure, so it names no Iron's type.
 */
final class IronsPower {

    /**
     * What to multiply each Iron's power attribute by for the length of one cast. {@code clamped} is
     * true when the target could not be reached: an attribute ceiling, or nothing to scale from.
     */
    record Factors(double spellPower, double school, boolean clamped) {
        static final Factors NONE = new Factors(1.0, 1.0, true);
    }

    private IronsPower() {
    }

    /**
     * The factors that make {@code spellPower × school} equal {@code multiplier × power}: all of it on
     * {@code spell_power} up to its ceiling, the rest on the school's attribute.
     */
    static Factors factors(double multiplier, double power, double spellPower, double spellPowerMax,
                           double school, double schoolMax) {
        double now = spellPower * school;
        if (!(now > 0.0) || !Double.isFinite(now) || !(power >= 0.0) || !Double.isFinite(power)
                || !(multiplier > 0.0) || !Double.isFinite(multiplier)) {
            return Factors.NONE;
        }
        double wanted = multiplier * power / now;
        double room = spellPowerMax / spellPower;
        if (wanted <= room) {
            return new Factors(wanted, 1.0, false);
        }
        double rest = wanted / room;
        double schoolRoom = schoolMax / school;
        if (rest <= schoolRoom) {
            return new Factors(room, rest, false);
        }
        return new Factors(room, schoolRoom, true);
    }
}
