package com.cyberday1.riftfall.worldgen.decorator;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Which {@code rift/room/<kind>/} directory a placed template came from.
 *
 * <p>
 * A decorator that wants to treat the boss arena differently from a corridor needs to know which it
 * is standing in, and the neighbourhood contract cannot tell it: seven blockstates look the same
 * everywhere. The kind is read off the template id because that is where the library already keeps
 * it — {@code RiftRoomLibrary} sorts its pools by the same path prefixes — so this introduces no
 * second source of truth that could drift from the directory layout.
 *
 * <p>
 * {@link #UNKNOWN} is not a failure. It is the honest answer for every placement that is not one of
 * the dungeon's rooms: the corridor blender's shifted wall copies, POI props, gallery previews, and
 * anything a datapack places through the theme processor on its own. Decorators that gate on kind
 * should treat it as "not in the set", so a new placement path defaults to leaving the room alone
 * rather than to decorating something nobody looked at.
 */
public enum RoomKind {

    /** The staircase piece that hangs a boss arena off a branch at the right height. */
    APPROACH,

    /** The bulk of the library: irregular, vertical, hostile. */
    CHAOS,

    /** Open cave rooms, the calmest of the three themed sets. */
    STABLE,

    /** Villages, mines and ruins; between chaos and stable in how walkable they are. */
    UNSTABLE,

    /** The arena. One template, and the only room with an authored fight in it. */
    BOSS,

    /**
     * Where a run arrives. Deliberately its own kind so it can be excluded: a player who is debuffed
     * on arrival, before they have seen a single plate, has been punished for nothing.
     */
    PORTAL,

    /** An ore room: an open cavern with no props, placed by the layout's ore quota. */
    ORE,

    /** Not a room of the dungeon, or not identifiable as one. */
    UNKNOWN;

    /** Every kind, in declaration order. Cached because {@code values()} copies. */
    public static final RoomKind[] ALL = values();

    private static final String ROOM_PREFIX = "rift/room/";

    private static final Map<String, RoomKind> BY_NAME = buildNames();

    /**
     * Parses the lowercase directory name, so a theme file writes {@code "boss"} rather than
     * {@code "BOSS"}. {@code "unknown"} parses too, though naming it in a theme file would be an odd
     * thing to want.
     */
    public static final Codec<RoomKind> CODEC = Codec.STRING.comapFlatMap(
            name -> {
                RoomKind kind = BY_NAME.get(name.toLowerCase(Locale.ROOT));
                return kind == null
                        ? DataResult.error(() -> "Unknown room kind '" + name + "'")
                        : DataResult.success(kind);
            },
            RoomKind::serialisedName);

    /** e.g. {@code "unstable"}. The inverse of what {@link #CODEC} parses. */
    public String serialisedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    /**
     * The kind of a room template, by its path.
     *
     * <p>
     * Only the segment straight after {@code rift/room/} is read, so the size directories the
     * library nests underneath it ({@code boss/333/boss_arena}) are irrelevant here, as is anything
     * a pack adds below them. A sibling of {@code room/} — {@code anomaly}, {@code exit},
     * {@code objective}, {@code poi}, {@code terminator} — is {@link #UNKNOWN}, because those are
     * not rooms and were never in scope.
     */
    public static RoomKind fromTemplate(ResourceLocation template) {
        if (template == null) {
            return UNKNOWN;
        }
        String path = template.getPath();
        if (!path.startsWith(ROOM_PREFIX)) {
            return UNKNOWN;
        }
        int start = ROOM_PREFIX.length();
        int end = path.indexOf('/', start);
        if (end < 0) {
            return UNKNOWN;
        }
        return BY_NAME.getOrDefault(path.substring(start, end).toLowerCase(Locale.ROOT), UNKNOWN);
    }

    private static Map<String, RoomKind> buildNames() {
        Map<String, RoomKind> map = new HashMap<>();
        for (RoomKind kind : values()) {
            map.put(kind.serialisedName(), kind);
        }
        return Map.copyOf(map);
    }
}
