package com.cyberday1.riftfall.rift;

import net.minecraft.util.StringRepresentable;

/**
 * Which third of a tear a block is.
 *
 * <p>
 * A tear is one piece of artwork three blocks tall, so unlike the entrance pane &mdash; which tiles
 * to fill whatever frame a player built &mdash; each block has to know which slice of it to draw.
 * Three rather than one tall block because a model may only reach a block out on each side, which
 * caps a single-block tear at exactly three; going through the blockstate instead costs two extra
 * models and is not capped at anything.
 */
public enum TearSegment implements StringRepresentable {

    BOTTOM("bottom"),
    MIDDLE("middle"),
    TOP("top");

    /** How many blocks tall a tear is, which is the same thing as how many values are here. */
    public static final int HEIGHT = values().length;

    private final String name;

    TearSegment(String name) {
        this.name = name;
    }

    /** The slice `blocks` up from the bottom of a tear. */
    public static TearSegment atHeight(int blocks) {
        return values()[Math.clamp(blocks, 0, HEIGHT - 1)];
    }

    /** How far up the tear this slice is: {@link #atHeight} read backwards, so they cannot disagree. */
    public int height() {
        return ordinal();
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
