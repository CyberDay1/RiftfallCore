package com.cyberday1.riftfall.block.marker;

import net.minecraft.util.StringRepresentable;

/**
 * The five prop footprints, as a blockstate value.
 *
 * <p>
 * These are the pool sizes the library was imported with &mdash; {@code rift/poi/<category>/{3,5,7,9,11}}
 * &mdash; and not a range: an even footprint has no centre cell to hang off, so the set is the odd
 * numbers and an {@code IntegerProperty} would declare six values nothing can ever be drawn from.
 */
public enum PoiAnchorSize implements StringRepresentable {

    SIZE_3(3),
    SIZE_5(5),
    SIZE_7(7),
    SIZE_9(9),
    SIZE_11(11);

    /** Every size, smallest first. Cached because {@code values()} copies. */
    public static final PoiAnchorSize[] ALL = values();

    /**
     * What a builder reaches for first. The 841 authored anchors are mostly 3s and 5s, and a 5 is
     * the one that reads as a prop rather than a trinket at a glance.
     */
    public static final PoiAnchorSize DEFAULT = SIZE_5;

    private final int blocks;
    private final String serialized;

    PoiAnchorSize(int blocks) {
        this.blocks = blocks;
        this.serialized = Integer.toString(blocks);
    }

    /** Edge length in blocks, which is also the pool's last path segment. */
    public int blocks() {
        return blocks;
    }

    /** The next size up, wrapping past 11 back to 3. */
    public PoiAnchorSize next() {
        return ALL[(ordinal() + 1) % ALL.length];
    }

    @Override
    public String getSerializedName() {
        return serialized;
    }
}
