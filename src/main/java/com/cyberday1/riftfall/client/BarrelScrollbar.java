package com.cyberday1.riftfall.client;

import com.cyberday1.riftfall.block.barrel.RiftBarrelMenu;

/**
 * Where the loot barrel's scrollbar is, and which row a pointer in it means.
 *
 * <p>
 * Lifted out of {@link RiftBarrelScreen} because none of it is drawing. Every method here is
 * arithmetic over {@code leftPos}/{@code topPos} and the constants {@link RiftBarrelMenu} already
 * fixed, and no Minecraft type appears in the file &mdash; which is the whole reason it is a
 * separate class. A {@code Screen} cannot be constructed without a client, so while this lived
 * inside one, the hit box, the clamps and the drag mapping were things only a human at a window
 * could check. They are plain JUnit here.
 *
 * <p>
 * Nothing in this class decides anything. {@link RiftBarrelMenu#scrollTo} is still what clamps and
 * still what refuses; {@link #rowFor} clamps to the same bounds so that the row it hands back is
 * readable on its own terms, which is a statement of the same rule rather than a second one.
 */
final class BarrelScrollbar {

    /** The bar sits in the gutter right of the grid, inside the panel's border. */
    static final int X = 174;
    static final int WIDTH = 12;
    static final int TOP = RiftBarrelMenu.TOP - 1;
    static final int HEIGHT = RiftBarrelMenu.VISIBLE_ROWS * RiftBarrelMenu.SLOT + 2;

    /** Tall enough to read as a grip, short enough that the travel is visible. */
    static final int GRIP_HEIGHT = HEIGHT * RiftBarrelMenu.VISIBLE_ROWS / RiftBarrelMenu.ROWS;

    /** How far the grip's top edge moves between the first row and the last. */
    static final int TRAVEL = HEIGHT - GRIP_HEIGHT;

    private BarrelScrollbar() {
    }

    /** Where {@code AbstractContainerScreen} centres the panel in a window this tall. */
    static int topPos(int screenHeight) {
        return (screenHeight - RiftBarrelMenu.HEIGHT) / 2;
    }

    static int leftPos(int screenWidth) {
        return (screenWidth - RiftBarrelMenu.WIDTH) / 2;
    }

    /** Whether the pointer is in the trough. Half-open on the far edges, as a fill is. */
    static boolean overBar(int leftPos, int topPos, double mouseX, double mouseY) {
        double x = mouseX - leftPos;
        double y = mouseY - topPos;
        return x >= X && x < X + WIDTH && y >= TOP && y < TOP + HEIGHT;
    }

    /**
     * Which row a pointer at this height means, as a drag would read it.
     *
     * <p>
     * The grip is taken by its middle, so the row is the fraction of the remaining travel the
     * pointer sits at rather than the fraction of the trough. Half the grip's height at each end is
     * therefore a dead band that already reads as the end row &mdash; which is what a scrollbar
     * should do, since the grip cannot move past the trough.
     */
    static int rowFor(int topPos, double mouseY) {
        double within = mouseY - topPos - TOP - GRIP_HEIGHT / 2.0;
        long row = Math.round(within / TRAVEL * RiftBarrelMenu.MAX_SCROLL);
        return (int) Math.clamp(row, 0, RiftBarrelMenu.MAX_SCROLL);
    }

    /** Where the grip's top edge is drawn for a given row. Integer division, as the fill takes. */
    static int gripTop(int topPos, int row) {
        return topPos + TOP + TRAVEL * row / RiftBarrelMenu.MAX_SCROLL;
    }
}
