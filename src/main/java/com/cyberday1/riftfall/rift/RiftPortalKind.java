package com.cyberday1.riftfall.rift;

import net.minecraft.util.StringRepresentable;

/**
 * What a lit frame is for, and the only thing that tells the three of them apart on sight.
 *
 * <p>
 * Where a portal leads is not read from here &mdash; {@link RiftPortalBlock} works that out from the
 * level it is standing in and from the run's roster. This is the presentation: an entrance draws
 * inward because it leads further in, and both ways home throw outward because they lead back.
 */
public enum RiftPortalKind implements StringRepresentable {

    /** The frame a player built and lit. Overworld side, drawn inward. */
    ENTRANCE("entrance"),

    /** Raised beside the arrival point when a run begins, so walking in and out are one event. */
    BAIL("bail"),

    /** What a boss drops. Nothing places one yet; the value exists so that code has it to pass. */
    BOSS("boss");

    private final String name;

    RiftPortalKind(String name) {
        this.name = name;
    }

    /** Whether motes are pulled into the pane rather than flung off it. */
    public boolean inward() {
        return this == ENTRANCE;
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
