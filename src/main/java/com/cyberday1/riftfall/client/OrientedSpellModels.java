package com.cyberday1.riftfall.client;

/** Which model effects turn to their spawn yaw: Riftfall's swing-shaped ones. No Spell Engine types here. */
public final class OrientedSpellModels {
    private static final String[] PREFIXES = {"riftfall:spell_effect/arc_", "riftfall:spell_effect/sweep_",
            "riftfall:spell_effect/pierce_", "riftfall:spell_effect/rake_"};

    private OrientedSpellModels() {
    }

    public static boolean oriented(String modelId) {
        if (modelId == null) return false;
        for (String prefix : PREFIXES) if (modelId.startsWith(prefix)) return true;
        return false;
    }

    /** Degrees about +Y that carry model -Z onto the facing of {@code yaw}, the entity convention. */
    public static float turn(float yaw) {
        return 180.0F - yaw;
    }
}
