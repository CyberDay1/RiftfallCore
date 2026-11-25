package com.cyberday1.riftfall.story;

import com.cyberday1.riftfall.block.marker.NpcRole;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The opening's story text (S2, STORY-006): the Core's greeting, its lines at the first defender's fall, the first
 * safe return and the first offering, and Penk's first words on his landing. Draft English under {@code lore.riftfall.},
 * like the other story text, until the voice pass. Beats 1 and 2 are lore-draft-v3 section 6 without contractions;
 * the greeting is sanctuary_1's sentences.
 */
public final class StoryLines {
    private static final String PREFIX = "lore.riftfall.story.";
    public static final List<String> GREETING = List.of(PREFIX + "greeting.0", PREFIX + "greeting.1", PREFIX + "greeting.2");
    public static final String FIRST_ANCHOR = PREFIX + "first_anchor";
    public static final String RETURN = PREFIX + "return";
    public static final String WARM = PREFIX + "warm.0", FOUR_MORE = PREFIX + "warm.1";

    private static final Map<String, String> ENGLISH = english(new LinkedHashMap<>());
    private static final Map<NpcRole, String> MEETINGS = Map.of(NpcRole.BLACKSMITH, meetKey(NpcRole.BLACKSMITH));

    private StoryLines() {}

    public static String meetKey(NpcRole role) { return PREFIX + "meet." + role.id(); }

    /** The resident's first line on their landing, if they have one (Penk only, for now). */
    public static Optional<String> meeting(NpcRole role) { return Optional.ofNullable(MEETINGS.get(role)); }

    /** The English for every key, for the lang provider. */
    public static Map<String, String> english() { return ENGLISH; }

    private static Map<String, String> english(Map<String, String> en) {
        en.put(GREETING.get(0), "You are awake. Good. Do not strain to remember. We remember enough for both of us.");
        en.put(GREETING.get(1), "This ground is home. We were whole once. This is what held when everything broke.");
        en.put(GREETING.get(2), "The pieces of home are scattered through the far places. Make a key, give it to the breaker, "
                + "and step through. Bring back what you find. The book will show you how.");
        en.put(FIRST_ANCHOR, "Something out there was holding a piece of us far away. You loosened it. You were always good "
                + "at that, even if you do not remember. Bring it home to the Core.");
        en.put(RETURN, "You came back. That is what matters. Give the Core what you carried.");
        en.put(WARM, "Feel that? The Core is warmer already. That warmth is us coming back.");
        en.put(FOUR_MORE, "Four more, and we can bring the next piece of ground home.");
        en.put(meetKey(NpcRole.BLACKSMITH), "Penk. I keep the forge. Last I knew I was waiting on Brask for a bell-pin, "
                + "and then I was here. Bring me your kit. That much I can do.");
        return Collections.unmodifiableMap(en);
    }
}
