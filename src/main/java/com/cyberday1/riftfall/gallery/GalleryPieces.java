package com.cyberday1.riftfall.gallery;

import com.cyberday1.riftfall.RiftfallCore;
import net.minecraft.resources.ResourceLocation;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Which of the loaded structure templates a gallery is allowed to show, and in what order.
 *
 * <p>
 * Deliberately the template manager and not the pools, which is the opposite of what
 * {@code RiftRoomLibrary} does and for the opposite reason. The pools are curated: datagen drops
 * blank starts, doorless boxes and duplicates, because the layout must never draw one. A gallery is
 * where an author goes to find out <em>why</em> a piece was dropped, so it has to be able to show a
 * piece the pools refuse.
 *
 * <h2>One exclusion</h2>
 * The {@code riftfall} namespace, because a gallery of every template a modpack has loaded would be
 * thousands of pieces from mods that have nothing to do with this one.
 */
public final class GalleryPieces {

    /** Everything a rift is built out of lives under here; {@code guides/} and {@code templates/} do not. */
    public static final String PIECE_PREFIX = "rift/";

    private GalleryPieces() {
    }

    /** True for a template id a gallery may stamp. */
    public static boolean isPiece(ResourceLocation id) {
        return RiftfallCore.MODID.equals(id.getNamespace())
                && id.getPath().startsWith(PIECE_PREFIX);
    }

    /**
     * The pieces a filter selects, sorted by id.
     *
     * <p>
     * Sorted rather than left in the manager's order, and the reason is the same one
     * {@code RiftRoomLibrary} sorts for: template ids come out of a hash-ordered map and that order
     * differs between JVM runs. Here it decides which cell of the grid a piece lands in, so an
     * unsorted walk would rearrange the whole gallery on every restart and make "the room three
     * along the front row" a useless way to refer to anything.
     *
     * <p>
     * The filter is a plain path prefix, so {@code rift/room} selects every room,
     * {@code rift/room/chaos} one theme's worth, and an empty filter the entire library. A filter
     * that does not already name the {@code rift/} directory is taken to be relative to it, because
     * every piece is under it and making an author type it every time buys nothing.
     */
    public static List<ResourceLocation> select(Stream<ResourceLocation> loaded, String filter) {
        String trimmed = trimLeadingSlash(filter);
        // Two ways a filter is already absolute: it starts with `rift/`, or it is a prefix of it
        // (`rift`, and the empty filter that means everything). Anything else is relative.
        String prefix = trimmed.startsWith(PIECE_PREFIX) || PIECE_PREFIX.startsWith(trimmed)
                ? trimmed
                : PIECE_PREFIX + trimmed;
        return loaded.filter(GalleryPieces::isPiece)
                .filter(id -> id.getPath().startsWith(prefix))
                .sorted(Comparator.comparing(ResourceLocation::getPath))
                .toList();
    }

    private static String trimLeadingSlash(String filter) {
        return filter.startsWith("/") ? filter.substring(1) : filter;
    }
}
