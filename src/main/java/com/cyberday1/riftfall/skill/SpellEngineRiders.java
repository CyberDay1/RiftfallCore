package com.cyberday1.riftfall.skill;

/**
 * {@link SkillCasts.Riders} translated into the Spell Engine modifier fields that carry them, as
 * plain numbers so the mapping is testable without the mod.
 *
 * <p>There is no field for {@code costMultiplier}: the engine's cast path stays free, and Control's
 * lever is paid on Riftfall's own mana charge instead ({@code SkillDef#costAt}).
 *
 * <p>{@code powerMultiplierAdd} is the node's power multiplier as the engine reads it: an additive
 * term, so 1.0 is 0 ({@code SpellImpacts#performImpact} sums 1 and every modifier's term).
 */
record SpellEngineRiders(float effectDurationAdd, float spawnDurationAdd, int activeSecondsAdd,
                         double ownerCoefficientScale, float powerMultiplierAdd) {

    static SpellEngineRiders of(SkillCasts.Riders riders) {
        return of(riders, SkillNode.NATIVE_POWER);
    }

    static SpellEngineRiders of(SkillCasts.Riders riders, double nodePower) {
        return new SpellEngineRiders(
                (float) (riders.dotSeconds() + riders.buffSeconds()),
                // SPAWN actions and CLOUD deliveries share one field: a DoT cloud and a spawned summon.
                // A buff's seconds stay off it: they lengthen the effect, not the cloud that carries it.
                (float) (riders.dotSeconds() + riders.summonSeconds()),
                (int) Math.round(riders.summonSeconds()),
                riders.ownerScaling(),
                (float) (nodePower - SkillNode.NATIVE_POWER));
    }

    /** True when nothing would change, so the engine is handed no modifier at all. */
    boolean neutral() {
        return effectDurationAdd == 0.0F && spawnDurationAdd == 0.0F && activeSecondsAdd == 0
                && ownerCoefficientScale == 0.0 && powerMultiplierAdd == 0.0F;
    }
}
