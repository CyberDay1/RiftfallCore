package com.cyberday1.riftfall.client;

/**
 * Where the Rift Codex book sits, how far it shrinks on a small screen, and which bookmark a point is on.
 * Book space is GUI pixels from the cover's top-left corner, the frame every page draws in.
 * Pure arithmetic, so the layout is plain JUnit; sizes are the approved renders' (380x240 at GUI scale 3).
 */
final class ProgressionFrame {

    static final int NO_TAB = -1;

    static final int WIDTH = 380;
    static final int HEIGHT = 240;
    /** Bookmarks hang off the right edge, tucked three pixels under the cover. */
    static final int BOOKMARK_X = WIDTH - 3;
    static final int BOOKMARK_WIDTH = 24;
    static final int BOOKMARK_HEIGHT = 28;
    static final int BOOKMARK_TOP = 18;
    static final int BOOKMARK_STEP = 34;
    static final int OUTER_WIDTH = BOOKMARK_X + BOOKMARK_WIDTH;
    /** The book sits this far left of centre, so cover and bookmarks together look centred. */
    static final int NUDGE = 8;

    static final int PAGE_INSET = 7;
    static final int GUTTER_X = 187;
    static final int LEFT = 14;
    static final int RIGHT = 198;
    static final int TOP = 12;
    static final int PAGE_WIDTH = 168;
    /** The pages' last drawable line, for buttons and the footer. */
    static final int BOTTOM = HEIGHT - PAGE_INSET - 5;

    /** Room left round the book, so it never touches the window edge. */
    static final int SPARE = 4;

    private ProgressionFrame() {
    }

    /** 1 when the book fits; smaller only when the screen cannot hold it at its own size. */
    static float scale(int screenWidth, int screenHeight) {
        return ScreenFit.scale(screenWidth, screenHeight, OUTER_WIDTH + NUDGE, HEIGHT, SPARE, 0.25F);
    }

    static int left(int screenWidth, float scale) {
        return Math.max(0, Math.round((screenWidth - WIDTH * scale) / 2.0F - NUDGE * scale));
    }

    static int top(int screenHeight, float scale) {
        return Math.max(0, Math.round((screenHeight - HEIGHT * scale) / 2.0F));
    }

    static int bookmarkY(int index) {
        return BOOKMARK_TOP + index * BOOKMARK_STEP;
    }

    /** The bookmark under a book-space point, or {@link #NO_TAB}. The part under the cover is the cover's. */
    static int bookmarkAt(double x, double y, int count) {
        if (x < WIDTH || x >= OUTER_WIDTH) {
            return NO_TAB;
        }
        for (int index = 0; index < count; index++) {
            int top = bookmarkY(index);
            if (y >= top && y < top + BOOKMARK_HEIGHT) {
                return index;
            }
        }
        return NO_TAB;
    }
}
