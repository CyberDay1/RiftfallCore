package com.cyberday1.riftfall.block.processor;

/**
 * The fifteen material slots a room author can paint with.
 *
 * <p>
 * Slots 1-8 carry a semantic name inherited from the structure library these placeholders come
 * from; that naming is the builder-facing contract and is surfaced verbatim in the lang file.
 * Slots 9-15 are generic accents with no promised meaning — a theme is free to map them to
 * anything.
 */
public enum ProcessorSlot {

    SLOT_1(1, "Wall"),
    SLOT_2(2, "Path"),
    SLOT_3(3, "Floor"),
    SLOT_4(4, "Alt Wall"),
    SLOT_5(5, "Alt Floor"),
    SLOT_6(6, "Planks"),
    SLOT_7(7, "Bricks"),
    SLOT_8(8, "Alt Path"),
    SLOT_9(9, null),
    SLOT_10(10, null),
    SLOT_11(11, null),
    SLOT_12(12, null),
    SLOT_13(13, null),
    SLOT_14(14, null),
    SLOT_15(15, null);

    /** All slots, in numeric order. Cached — {@code values()} clones on every call. */
    public static final ProcessorSlot[] ALL = values();

    private final int number;
    private final String semanticName;

    ProcessorSlot(int number, String semanticName) {
        this.number = number;
        this.semanticName = semanticName;
    }

    /** 1-based slot number as it appears in the block id. */
    public int number() {
        return number;
    }

    /** e.g. {@code "Wall"} for slot 1, or {@code null} for the generic accent slots 9-15. */
    public String semanticName() {
        return semanticName;
    }

    /** Registry-name stem for this slot's family, e.g. {@code "processor_block_6"}. */
    public String familyId() {
        return "processor_block_" + number;
    }

    /** Registry name for one variant of this slot, e.g. {@code "processor_block_6_stairs"}. */
    public String blockId(ProcessorVariant variant) {
        return familyId() + variant.suffix();
    }

    /** @param number 1-based slot number */
    public static ProcessorSlot byNumber(int number) {
        if (number < 1 || number > ALL.length) {
            throw new IllegalArgumentException("No processor slot " + number + " (valid: 1-" + ALL.length + ")");
        }
        return ALL[number - 1];
    }
}
