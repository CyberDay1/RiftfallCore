package com.cyberday1.riftfall.client;

import com.cyberday1.riftfall.RiftfallCore;
import com.cyberday1.riftfall.network.SkillScreenPayload;
import com.cyberday1.riftfall.skill.SkillCommand;
import com.cyberday1.riftfall.skill.SkillNames;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.MutableComponent;

import java.util.function.Function;

/**
 * Every display name the book draws: a skill, a class, an ascendancy, a school. A skill is named by
 * {@link SkillNames} (its authored spell name); the others have one lang key each, keyed by the id the server
 * sends, and a key with no translation reads as the id made readable.
 */
final class CodexNames {

    static final String CLASS = "class";
    static final String ASCENDANCY = "ascendancy";
    static final String SCHOOL = "school";

    private CodexNames() {
    }

    static MutableComponent skill(SkillScreenPayload.Entry entry) {
        return SkillNames.of(entry.id(), entry.facts().engine(), entry.facts().ability());
    }

    static String skillClass(String id) {
        return display(CLASS, id);
    }

    static String ascendancy(String id) {
        return display(ASCENDANCY, id);
    }

    /** A school id such as {@code spell_power:physical_melee}; keyed by its path. */
    static String school(String id) {
        return display(SCHOOL, id);
    }

    /** A class skill's school heading: gun and bow skills run on the melee school, and read as ranged. */
    static String school(String skillClass, String id) {
        return school(headingSchool(skillClass, id));
    }

    static String headingSchool(String skillClass, String id) {
        boolean ranged = skillClass.equals("gunner") || skillClass.equals("ranger");
        return ranged && id.equals("spell_power:physical_melee") ? "spell_power:physical_ranged" : id;
    }

    static String display(String kind, String id) {
        return display(key -> I18n.exists(key) ? I18n.get(key) : null, kind, id);
    }

    /** The one rule, with the lookup passed in so it is plain JUnit. */
    static String display(Function<String, String> lookup, String kind, String id) {
        if (id == null || id.isEmpty()) {
            return "";
        }
        String translated = lookup.apply(key(kind, id));
        return translated != null ? translated : readable(id);
    }

    static String key(String kind, String id) {
        return kind + "." + RiftfallCore.MODID + "." + path(id);
    }

    /** {@code riftfall:monk_palm_strike} or {@code monk_palm_strike} to "Monk Palm Strike"; shared with chat. */
    static String readable(String id) {
        return SkillCommand.readable(id);
    }

    private static String path(String id) {
        int colon = id.indexOf(':');
        return colon < 0 ? id : id.substring(colon + 1);
    }
}
