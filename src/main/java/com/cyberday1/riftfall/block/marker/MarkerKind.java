package com.cyberday1.riftfall.block.marker;

/**
 * The nine rift marker blocks, as a closed set.
 *
 * <p>
 * These are the non-material blocks an authored room places: chests, springs, quest hubs, objective
 * triggers and mob spawners came in with the imported library; the exit portal and the three
 * boss-room fixtures are ours, placed only by rooms we author. Right now they are <em>inert</em> —
 * no block entity, no GUI, no ticking — and exist so that the block id in an authored template
 * resolves to something with the right blockstate. That is not cosmetic: 1.21.1's
 * {@code NbtUtils.readBlockState} answers {@code minecraft:air} for an unknown id and logs nothing
 * at all, so a missing registration turns into an invisible hole in the room with no diagnostic.
 *
 * <p>
 * Behaviour (loot, quest anchoring, spawner activation) comes later. This enum is the handle
 * that work will locate them by — see {@link RiftfallMarkers#identify}.
 */
public enum MarkerKind {

    RIFT_CHEST("rift_chest", "Rift Chest"),
    SPRING_BLOCK("spring_block", "Spring Block"),
    QUEST_HUB("quest_hub", "Quest Hub"),
    OBJECTIVE_BLOCK("objective_block", "Objective Block"),
    RIFT_MOB_SPAWNER("rift_mob_spawner", "Rift Mob Spawner"),

    /** Where a rift's way back out is raised, in place of the geometric guess. */
    EXIT_PORTAL("exit_portal", "Exit Portal"),

    /** Where the boss itself is put, in the middle of the fight's floor. */
    BOSS_ARENA("boss_arena", "Boss Arena"),

    /**
     * Where a run arrives in the boss room, which is deliberately not
     * {@link #BOSS_ARENA}: one point cannot be both the boss's footprint and a doorway to walk in
     * from, and a party that materialises inside the boss has lost the fight's opening.
     */
    BOSS_ENTRY("boss_entry", "Boss Entry"),

    /**
     * The doorway the boss room shuts behind a party, laid as a rectangle rather than a point.
     *
     * <p>
     * The only kind an author places more than one of on purpose, which is why {@link #rectangle} has
     * to exist: a resolver that stops at the first hit would clear one block of the doorway and leave
     * the rest of it standing in the room.
     */
    BOSS_GATE("boss_gate", "Boss Gate", true);

    /** Every kind, in registration order. Cached because {@code values()} copies. */
    public static final MarkerKind[] ALL = values();

    private final String blockId;
    private final String displayName;
    private final boolean rectangle;

    MarkerKind(String blockId, String displayName) {
        this(blockId, displayName, false);
    }

    MarkerKind(String blockId, String displayName, boolean rectangle) {
        this.blockId = blockId;
        this.displayName = displayName;
        this.rectangle = rectangle;
    }

    /**
     * Whether an author is expected to place several of these in one room.
     *
     * <p>
     * Read by the resolver to decide whether it may stop at the first hit. A point kind found twice
     * is an authoring mistake; a rectangle kind found once is probably one too.
     */
    public boolean rectangle() {
        return rectangle;
    }

    /**
     * The registry path, which is also the model/blockstate file name.
     *
     * <p>
     * <strong>Load-bearing.</strong> The import pipeline rewrites {@code wotr:<path>} to
     * {@code riftfall:<path>} without touching the path, so these strings have to match the
     * authored templates character for character.
     */
    public String blockId() {
        return blockId;
    }

    /** en_us name. */
    public String displayName() {
        return displayName;
    }
}
