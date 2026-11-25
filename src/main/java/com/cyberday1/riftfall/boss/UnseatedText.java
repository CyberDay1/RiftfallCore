package com.cyberday1.riftfall.boss;

import com.cyberday1.riftfall.boss.UnseatedEncounter.Action;
import com.cyberday1.riftfall.boss.UnseatedEncounter.Phase;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Every line a player reads in the Unseated fight, as lang keys. Operator command output stays literal. */
public final class UnseatedText {
    private static final Map<String, String> ENGLISH = new LinkedHashMap<>();

    public static final String NAME = "entity.riftfall.unseated";
    // The god's own lines (story review S9): its respawn and wilderness words, changed.
    public static final String GOD_DEFEAT = god("defeat", "I have got you. Still.");
    public static final String GOD_ASCENT = god("ascent", "There is so much room up there.");
    public static final String GOD_BINDING = god("binding", "A small place again. After everything I set down for you.");
    public static final String GOD_BEGIN = god("begin", "So this is what you have decided.");
    public static final String GOD_FRACTURE = god("fracture", "I kept you safe. Every time you fell, I brought you back.");
    public static final String GOD_BROKEN = god("broken", "They took the sky from me once. Please. Not again.");
    public static final String GOD_REMEMBER = god("remember", "Will you remember me?");
    public static final String GOD_CONTAINED = god("contained", "This is home now.");

    public static final String TRIALS_BEGIN = message("trials_begin", "The anchors remember. Three harmless trials: watch the floor and practice the movements before the binding begins.");
    public static final String ASCENT_BROKEN = message("ascent_broken", "The ascent is broken. Approach the Core, crouch, and bind it with an empty hand.");
    public static final String FRACTURE = message("fracture", "The Unseated tears at the Foothold.");
    public static final String ASCENT = message("ascent", "The Unseated begins its ascent.");
    public static final String ARENA_ONLY = message("arena_only", "The Unseated can only be fought at the Last Anchorage.");
    public static final String HOLD_CROUCH = message("hold_crouch", "Hold your crouch nearby to finish the binding.");
    public static final String CONTAINED = message("contained", "The Unseated is contained. The Foothold holds.");
    public static final String PRACTICE_PULL = message("practice.pull", "That was the pull. The inner circle is calm; outside it, the arrows show the force.");
    public static final String PRACTICE_CAUGHT = message("practice.caught", "The practice zone caught you. %s");
    public static final String PRACTICE_AVOIDED = message("practice.avoided", "You avoided that practice attack.");
    public static final String PRACTICE_ZONE = message("practice.zone", "Practice: the zone caught you.");
    public static final String PRACTICE_COMPLETE = message("practice.complete", "Practice complete. The next attacks deal damage. The same floor markings still warn you.");
    public static final String TELEGRAPH = message("telegraph", "%1$s: %2$s");
    public static final String TELEGRAPH_PRACTICE = message("telegraph.practice", "Practice %1$s/3. %2$s: %3$s");
    public static final String GUIDE_INTRO = message("guide.intro", "The Anchors Remember: approach and interact with the Unseated for three harmless practice attacks. Live combat follows.");
    public static final String GUIDE_BINDING = message("guide.binding", "Binding: when the Core is exposed, crouch and touch the Unseated with an empty hand, then stay crouched inside the marked ring. Everyone binding adds to one shared bar. The god keeps attacking: stand up to dodge and crouch again to go on. A hit on someone binding breaks a quarter of the bar.");
    public static final String ARENA_PREPARING = message("arena.preparing", "Preparing the Last Anchorage. You will cross when the court is ready.");
    public static final String ARENA_ARRIVAL = message("arena.arrival", "The Last Anchorage. Approach and touch the Unseated to begin three harmless practice attacks, then the fight. The lit gate behind you returns home.");
    public static final String ARENA_DEFEAT_RULE = message("arena.defeat_rule", "A knockout never costs gear. The party shares two returns to the fight; after those, a knockout sends you home. The lit gate takes you home at any time.");
    public static final String ARENA_FAILED = message("arena.failed", "The arena could not be prepared. You have not lost your gear; please retry.");
    public static final String ARENA_CONTAINED = message("arena.contained", "The god is contained. The anchorage releases you in 20 seconds; the lit gate also leads home.");
    public static final String ARENA_REJECTED = message("arena.rejected", "The binding rejects you. Returning you and your equipment to safety.");

    public static final String BAR_TRIALS = bar("trials", "The Anchors Remember: harmless trials");
    public static final String BAR_PRACTICE = bar("practice", "Practice %1$s/3: %2$s (no damage)");

    public static final String HUD_CONTAINED = hud("contained", "CONTAINED");
    public static final String HUD_CONTAINED_HINT = hud("contained.hint", "The lit gate leads home. The anchorage will release you shortly.");
    public static final String HUD_BINDING = hud("binding", "BINDING THE CORE");
    public static final String HUD_EXPOSED = hud("exposed", "THE CORE IS EXPOSED");
    public static final String HUD_STAGGER_HINT = hud("stagger.hint", "The god is staggering. Prepare an empty hand.");
    public static final String HUD_BIND_HINT = hud("bind.hint", "Crouch + interact empty-handed once, then crouch inside the marked ring to bind. Standing pauses you. A hit on a binder breaks a quarter.");
    public static final String HUD_DORMANT = hud("dormant", "THE ANCHORS REMEMBER");
    public static final String HUD_DORMANT_HINT = hud("dormant.hint", "Approach and interact empty-handed to begin three harmless practice attacks.");
    public static final String HUD_LEARN = hud("learn", "LEARN THE BINDING");
    public static final String HUD_LEARN_HINT = hud("learn.hint", "Watch the floor. First: dodge a wedge, jump a ring, then resist a pull. Practice deals no damage.");
    public static final String HUD_ATTACK_WARNING = hud("attack.warning", "%1$s: %2$ss");
    public static final String HUD_ATTACK_NOW = hud("attack.now", "%s: NOW");
    public static final String HUD_ATTACK_PRACTICE = hud("attack.practice", "PRACTICE %1$s/3 · %2$s");
    public static final String HUD_NO_DAMAGE = hud("no_damage", "%s (No damage.)");
    public static final String HUD_PREPARE = hud("prepare", "PREPARE");
    public static final String HUD_PREPARE_HINT = hud("prepare.hint", "Watch for the next ground marking. Live attacks deal damage.");
    public static final String HUD_RECOVERY = hud("recovery", "PRACTICE: RECOVERY");
    public static final String HUD_RECOVERY_HINT = hud("recovery.hint", "The next demonstration follows shortly. Watch the floor markings.");
    public static final String HUD_OPENING = hud("opening", "OPENING");
    public static final String HUD_OPENING_HINT = hud("opening.hint", "Attack now. The next ground marking warns of the next strike.");

    // Fight track F4 onward. Kept apart from the lines above so parallel text passes merge cleanly.
    public static final String HUD_BINDING_PARTY = hud("binding.party", "BINDING THE CORE: %s binding");
    public static final String TRIALS_SKIPPED = message("trials_skipped", "The anchors remember you. No trials this time: the attacks deal damage from the start.");
    public static final String PRACTICE_ONLY_COMPLETE = message("practice.only_complete", "Practice complete. Touch the Unseated to run the trials again, or leave by the lit gate.");
    public static final String GUIDE_PRACTICE = message("guide.practice", "Once you have finished the trials, later attempts skip them.");
    public static final String ARENA_PRACTICE_ARRIVAL = message("arena.practice_arrival", "The Last Anchorage, for practice only. Approach and touch the Unseated to run the three harmless trials. The trials deal no damage. The lit gate behind you returns home.");
    public static final String ARENA_REENTRY = message("arena.reentry", "The binding throws you back to the gate. You return to the fight in %1$s seconds. Returns left for the party: %2$s.");
    public static final String ARENA_REENTRY_WAIT = message("arena.reentry_wait", "Returning to the fight in %ss.");
    public static final String ARENA_REENTRY_READY = message("arena.reentry_ready", "The anchorage lets you back in. The god has not healed.");
    public static final String ARENA_CEILING = message("arena.ceiling", "The anchorage does not let you rise that far. It sets you back on the court.");

    // Fight track F6 and F7: the draw, Crown of Weight, Throne Fall, the reseat glide, tethers and combos.
    public static final String TETHER_NAME = "entity.riftfall.unseated_tether";
    public static final String FIRST_SIGHT = message("first_sight", "First sight: this one deals you no damage.");
    public static final String CROWN_MARKED = message("crown_marked", "The Crown of Weight marks you. Leave the circle when it locks, away from allies.");
    public static final String TETHERS_RISE = message("tethers_rise", "%s tethers rise at the bridges. While any stands, the Unseated takes half damage.");
    public static final String TETHER_FELL = message("tether_fell", "A tether falls. Still standing: %s.");
    public static final String TETHERS_GONE = message("tethers_gone", "The last tether falls: the Unseated takes full damage again.");
    public static final String GUIDE_LATER = message("guide.later", "From the Fracture on, the god draws its attacks in no fixed order, adds new ones and moves between seats. Each new attack deals you no damage the first time you see it.");
    public static final String BAR_ASCENT_TETHERS = bar("ascent.tethers", "The Unseated: Ascent. Tethers standing: %s");

    static {
        put(attackKey(Action.SWEEP), "Sovereign's Sweep");
        put(attackKey(Action.PULL), "The Reach");
        put(attackKey(Action.PULSE), "Rupture");
        put(attackKey(Action.SWEEP) + ".response", "Leave the marked wedge, or get behind the boss.");
        put(attackKey(Action.PULSE) + ".response", "Jump as the bright ring reaches your feet.");
        put(attackKey(Action.PULL) + ".response", "Stand in the inner ring, or run outward against the arrows.");
        put(attackKey(Action.CROWN), "Crown of Weight");
        put(attackKey(Action.THRONE_FALL), "Throne Fall");
        put(attackKey(Action.RESEAT), "The Throne Moves");
        put(attackKey(Action.REACH_RUPTURE), "The Reach and Rupture");
        put(attackKey(Action.SWEEP_CROWN), "Sweep under the Crown");
        put(attackKey(Action.CROWN) + ".response", "When the circle locks, leave it, away from your allies.");
        put(attackKey(Action.THRONE_FALL) + ".response", "Get out of the disc around the god before it falls.");
        put(attackKey(Action.RESEAT) + ".response", "Step out of the marked lane before the god glides through it.");
        put(attackKey(Action.REACH_RUPTURE) + ".response", "Stand in the calm inner ring and jump the bright ring.");
        put(attackKey(Action.SWEEP_CROWN) + ".response", "Leave the locked circle without stepping into the wedge.");
        put(barKey(Phase.CLAIM), "The Unseated: The Claim");
        put(barKey(Phase.FRACTURE), "The Unseated: Fracture");
        put(barKey(Phase.ASCENT), "The Unseated: Ascent");
        put(barKey(Phase.BINDING), "The Unseated: Bind the Core");
        put(barKey(Phase.CONTAINED), "The Unseated: Contained");
    }

    private UnseatedText() {}

    /** The English for every key, for the lang provider. */
    public static Map<String, String> english() { return Collections.unmodifiableMap(ENGLISH); }

    public static MutableComponent attack(Action action) {
        return Component.translatable(UnseatedEncounter.isAttack(action) ? attackKey(action) : NAME);
    }
    public static MutableComponent response(Action action) {
        return UnseatedEncounter.isAttack(action) ? Component.translatable(attackKey(action) + ".response") : Component.empty();
    }
    public static MutableComponent bar(Phase phase) { return Component.translatable(barKey(phase)); }

    static String attackKey(Action action) { return "attack.riftfall.unseated." + action.name().toLowerCase(Locale.ROOT); }
    static String barKey(Phase phase) { return "bossbar.riftfall.unseated." + phase.name().toLowerCase(Locale.ROOT); }

    private static String god(String path, String english) { return put("lore.riftfall.unseated." + path, english); }
    private static String message(String path, String english) { return put("message.riftfall.unseated." + path, english); }
    private static String bar(String path, String english) { return put("bossbar.riftfall.unseated." + path, english); }
    private static String hud(String path, String english) { return put("hud.riftfall.unseated." + path, english); }
    private static String put(String key, String english) {
        if (ENGLISH.put(key, english) != null) throw new IllegalStateException("Duplicate Unseated text " + key);
        return key;
    }
}
