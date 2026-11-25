package com.cyberday1.riftfall.client;

import java.util.List;

/**
 * What the client can read about an ability from the engine's synced registry: school, the largest
 * damage and heal coefficient, range and target shape, and the status effects it applies.
 * {@link #NONE} when the engine is absent, the id is unknown, or the ability is not Spell Engine's.
 */
record AbilityFacts(boolean resolved, String school, double damage, double heal, double range, String target,
                    boolean includeCaster, List<Effect> effects) {

    static final AbilityFacts NONE = new AbilityFacts(false, "", 0.0, 0.0, 0.0, "", false, List.of());

    /** A status effect by id, its seconds, and its amplifier (0 is level I). */
    record Effect(String id, double seconds, int amplifier) {
    }

    AbilityFacts {
        school = school == null ? "" : school;
        target = target == null ? "" : target;
        effects = effects == null ? List.of() : List.copyOf(effects);
    }

    /** The same ability as a node with this power multiplier casts it: both coefficients scaled. */
    AbilityFacts scaled(double powerMultiplier) {
        return powerMultiplier == 1.0 ? this : new AbilityFacts(resolved, school, damage * powerMultiplier,
                heal * powerMultiplier, range, target, includeCaster, effects);
    }

    /** The line under "Area", as a SKILLS line key and its argument; null when there is nothing to say. */
    String[] area() {
        String blocks = CharacterSheet.amount(range);
        return switch (target) {
            case "AREA" -> new String[] {"area_around", blocks};
            case "AIM" -> new String[] {"area_aimed", blocks};
            case "BEAM" -> new String[] {"area_beam", blocks};
            case "CASTER" -> new String[] {"area_self", ""};
            default -> null;
        };
    }
}
