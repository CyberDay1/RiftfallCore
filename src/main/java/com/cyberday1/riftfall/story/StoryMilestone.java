package com.cyberday1.riftfall.story;

import com.cyberday1.riftfall.RiftfallCore;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * One fact the campaign can prove from play. Each has a hidden advancement, {@code riftfall:story/<id>}, that an
 * FTB Quests advancement task watches; the advancement is only ever granted by {@link StoryBridge}.
 */
public enum StoryMilestone {
    /** A1.01: the Core's greeting, heard by touching the Core of the player's own home. */
    INTRODUCED("introduced", Owner.PLAYER),
    KEY_READY("key_ready", Owner.PLAYER),
    RIFT_ENTERED("rift_entered", Owner.PLAYER),
    DEFENDER_DEFEATED("defender_defeated", Owner.PLAYER),
    FIRST_CLEAR("first_clear", Owner.PLAYER),
    OFFERED("offered", Owner.PLAYER),
    RESIDENT_MET("resident_met", Owner.PLAYER),
    // Read from RiftStats, never stored: the skill book is already the record.
    CLASS_CHOSEN("class_chosen", Owner.BUILD),
    SKILL_EQUIPPED("skill_equipped", Owner.BUILD),
    ASCENDED("ascended", Owner.BUILD),
    HOME_OFFERING("home_offering", Owner.HOME);

    /** Who holds the fact: the player, their current home, or their character build. */
    public enum Owner { PLAYER, HOME, BUILD }

    private final String id;
    private final Owner owner;

    StoryMilestone(String id, Owner owner) {
        this.id = id;
        this.owner = owner;
    }

    public String id() { return id; }

    public Owner owner() { return owner; }

    public ResourceLocation advancement() { return RiftfallCore.id("story/" + id); }

    public static @Nullable StoryMilestone byId(String id) {
        for (StoryMilestone milestone : values()) if (milestone.id.equals(id)) return milestone;
        return null;
    }
}
