package com.cyberday1.riftfall.skill;

import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * The live skill table, as the verbs read it. Installed by {@link SkillTableLoader} on every reload
 * and empty before the first one. Server side only: nothing sends the table to a client yet.
 */
public final class SkillDefs {

    private static volatile SkillTable.Loaded loaded = new SkillTable.Loaded(SkillTable.EMPTY, java.util.List.of(), 0);

    private SkillDefs() {
    }

    /** The skill with this id, or null. */
    public static @Nullable SkillDef get(String id) {
        return loaded.table().get(id);
    }

    public static Set<String> ids() {
        return loaded.table().ids();
    }

    public static SkillTable table() {
        return loaded.table();
    }

    /** The last reload's result, refusals and file count included, for the probe. */
    public static SkillTable.Loaded lastLoad() {
        return loaded;
    }

    public static void install(SkillTable.Loaded next) {
        loaded = next;
    }
}
