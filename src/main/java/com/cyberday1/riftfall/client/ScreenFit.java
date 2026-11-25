package com.cyberday1.riftfall.client;

/**
 * How far a fixed-size screen shrinks to fit the window: the Rift Codex book's rule ({@link ProgressionFrame}),
 * shared with the resident pages ({@link FittedContainerScreen}). Pure arithmetic, in GUI pixels.
 */
final class ScreenFit {

    /** The smallest a resident page draws: the 8 px font stays 5.6 GUI px tall. Past it the page's foot is cut instead. */
    static final float PAGE_MIN = 0.7F;

    private ScreenFit() {
    }

    /** A resident page's scale ({@link FittedContainerScreen}): the book's margin, {@link #PAGE_MIN} at the least. */
    static float page(int screenWidth, int screenHeight, int imageWidth, int imageHeight) {
        return scale(screenWidth, screenHeight, imageWidth, imageHeight, ProgressionFrame.SPARE, PAGE_MIN);
    }

    /** 1 when the content fits with {@code spare} to spare; smaller only when it cannot, never below {@code min}. */
    static float scale(int screenWidth, int screenHeight, int contentWidth, int contentHeight, int spare, float min) {
        float wide = (screenWidth - spare) / (float) contentWidth;
        float tall = (screenHeight - spare) / (float) contentHeight;
        return Math.max(min, Math.min(1.0F, Math.min(wide, tall)));
    }

    /** Where content starts along one axis, in the scaled frame, centred; 0 when it still overflows at {@code min}. */
    static int origin(int screen, int content, float scale) {
        return Math.max(0, Math.round((screen / scale - content) / 2.0F));
    }
}
