package com.cyberday1.riftfall.client;

import com.cyberday1.riftfall.RiftfallCore;
import com.cyberday1.riftfall.stat.StatBlock;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * Where everything on the class spread sits, in book space, and the two facts the screen reads off a
 * class's stat list. Pure, so a test can hold every line inside the pages at the book's own size;
 * the book itself scales down on a small screen ({@link ProgressionFrame#scale}), so nothing here
 * depends on the GUI scale.
 */
final class ClassSelectLayout {

    static final int L = ProgressionFrame.LEFT;
    static final int R = ProgressionFrame.RIGHT;
    static final int T = ProgressionFrame.TOP;
    static final int W = ProgressionFrame.PAGE_WIDTH;
    static final int BOTTOM = ProgressionFrame.BOTTOM;
    static final int LINE = 9;

    // Left page: heading, sub line, the list, a footer line.
    static final int SUB_Y = T + 11;
    static final int RULE_Y = T + 22;
    static final int LIST_TOP = T + 26;
    static final int ROW = 13;
    static final int ICON = 12;
    static final int FOOTER_Y = BOTTOM - 12;

    // Right page: the class's item beside its name, then everything below it.
    static final int HEAD_ICON = 16;
    static final int HEAD_ICON_Y = T - 3;
    static final int HEAD_NAME_X = R + HEAD_ICON + 3;
    static final int BLURB_Y = T + 15;
    static final int BLURB_LINES = 2;
    static final int WEAPON_Y = T + 34;
    static final int RULE2_Y = T + 45;
    static final int STATS_Y = T + 48;
    static final int STAT_COLUMN = W / StatBlock.Primary.values().length;
    static final int BAR_Y = STATS_Y + 20;
    static final int BAR_MAX = STAT_COLUMN - 6;
    static final int SKILLS_HEAD_Y = T + 75;
    static final int SKILLS_Y = T + 86;
    /**
     * Rows the base actives and the ascendancies share, at {@link #LINE} each: ten actives in five and
     * five ascendancies in three, or more for either when long names cannot pair evenly; the
     * ascendancy heading follows wherever the actives end.
     */
    static final int NAME_ROWS = 9;
    /** Below the last name row: the ascendancy heading's gap, and its own line before the names. */
    static final int HEAD_GAP = 3;
    static final int HEAD_LINE = 11;
    static final int STATUS_Y = T + 186;
    static final int BUTTON_H = 18;
    static final int BUTTON_Y = BOTTOM - BUTTON_H;
    static final int COLUMN = W / 2;

    private ClassSelectLayout() {
    }

    static int rowY(int index) {
        return LIST_TOP + index * ROW;
    }

    /** How many class rows fit above the footer. */
    static int rowsThatFit() {
        return (FOOTER_Y - 2 - LIST_TOP) / ROW;
    }

    /** The largest stat's index, ties to the earlier primary; -1 for an empty list. */
    static int main(List<Integer> stats) {
        return rank(stats, -1);
    }

    /** The second largest stat's index, skipping {@link #main}. */
    static int second(List<Integer> stats) {
        return rank(stats, main(stats));
    }

    private static int rank(List<Integer> stats, int skip) {
        int best = -1;
        for (int i = 0; i < stats.size(); i++) {
            if (i != skip && (best < 0 || stats.get(i) > stats.get(best))) {
                best = i;
            }
        }
        return best;
    }

    /** Space between a name grid's two columns. */
    static final int COLUMN_GAP = 4;

    /** One row of a name grid: the left name's index, and the right one's or -1 (the left then has the row). */
    record GridRow(int left, int right) {
    }

    /** A name grid: its rows, where its second column starts from the page's left edge, and the names it left out. */
    record Grid(List<GridRow> rows, int second, int hidden) {
        /** Rows drawn, with the "+N more" line, and at least the one line an empty list's "none yet" takes. */
        int height() {
            return Math.max(1, rows.size() + (hidden > 0 ? 1 : 0));
        }
    }

    /** The ascendancy heading's y when the actives take {@code skillRows} rows. */
    static int ascendancyHeadY(int skillRows) {
        return SKILLS_Y + skillRows * LINE + HEAD_GAP;
    }

    /**
     * The two name grids of the class page from their names' widths, sharing {@link #NAME_ROWS}: the
     * actives take what they need, leaving the ascendancies at least their even split, and the
     * ascendancies take the rest.
     */
    static Grid[] nameGrids(int[] actives, int[] ascendancies) {
        Grid skills = grid(actives, NAME_ROWS - Math.max(1, (ascendancies.length + 1) / 2));
        Grid ascended = grid(ascendancies, NAME_ROWS - skills.height());
        return new Grid[] {skills, ascended};
    }

    /**
     * Lays out names of these widths in two columns, at most {@code maxRows} rows. Down then across, the
     * page's own reading, whenever that fits: the second column starts at the page's middle, or just past
     * the first column's widest name. When it cannot fit (a pair of long names wider than the page), each
     * long name is paired with the longest short one that fits beside it, longest first, with the second
     * column placed where that takes fewest rows; rows keep the names' order by their left name. A list
     * that still needs more rows than it has shows what fits, and {@code hidden} counts the rest.
     */
    static Grid grid(int[] widths, int maxRows) {
        int n = widths.length;
        int perColumn = (n + 1) / 2;
        if (perColumn <= maxRows) {
            int first = 0;
            int second = 0;
            for (int i = 0; i < n; i++) {
                if (i < perColumn) {
                    first = Math.max(first, widths[i]);
                } else {
                    second = Math.max(second, widths[i]);
                }
            }
            int x = Math.max(COLUMN, first + COLUMN_GAP);
            if (x + second <= W) {
                List<GridRow> rows = new ArrayList<>();
                for (int i = 0; i < perColumn; i++) {
                    rows.add(new GridRow(i, i + perColumn < n ? i + perColumn : -1));
                }
                return new Grid(rows, x, 0);
            }
        }
        Grid best = null;
        for (int x = (W + COLUMN_GAP + 1) / 2; x <= W; x++) {
            List<GridRow> rows = paired(widths, x);
            if (best == null || rows.size() < best.rows().size()) {
                best = new Grid(rows, x, 0);
            }
        }
        if (best.rows().size() <= maxRows) {
            return best;
        }
        List<GridRow> kept = new ArrayList<>(best.rows().subList(0, Math.max(0, maxRows - 1)));
        int shown = 0;
        for (GridRow row : kept) {
            shown += row.right() < 0 ? 1 : 2;
        }
        return new Grid(kept, best.second(), n - shown);
    }

    /** Longest first: each name that fits the left column takes the longest unplaced name that fits the right. */
    private static List<GridRow> paired(int[] widths, int second) {
        Integer[] order = new Integer[widths.length];
        for (int i = 0; i < order.length; i++) {
            order[i] = i;
        }
        Arrays.sort(order, Comparator.comparingInt((Integer i) -> -widths[i]).thenComparingInt(i -> i));
        boolean[] placed = new boolean[widths.length];
        List<GridRow> rows = new ArrayList<>();
        for (int a : order) {
            if (placed[a]) {
                continue;
            }
            placed[a] = true;
            int partner = -1;
            if (widths[a] <= second - COLUMN_GAP) {
                for (int b : order) {
                    if (!placed[b] && widths[b] <= W - second) {
                        partner = b;
                        break;
                    }
                }
            }
            if (partner >= 0) {
                placed[partner] = true;
            }
            rows.add(new GridRow(a, partner));
        }
        rows.sort(Comparator.comparingInt(GridRow::left));
        return rows;
    }

    /** An item sprite's size in texels. */
    static final int SPRITE = 16;

    /**
     * How many screen pixels to draw a class item at in a box {@code boxPixels} wide: the largest whole
     * multiple of 16 that fits, so every texel is the same whole number of pixels (a 12-unit box is 24
     * pixels at GUI scale 2 and draws the item at 16; 36 at scale 3 draws 32). A box under 16 pixels
     * gets its own size, since nothing smaller than the sprite can be crisp.
     */
    static int crispPixels(int boxPixels) {
        return boxPixels < SPRITE ? Math.max(1, boxPixels) : boxPixels / SPRITE * SPRITE;
    }

    /** A stat's bar length: the class's top stat fills the column. */
    static int bar(int value, int top) {
        return top <= 0 ? 0 : Math.max(0, Math.min(BAR_MAX, Math.round(BAR_MAX * value / (float) top)));
    }

    /** The per-class lines the screen reads, each under {@code class.riftfall.<id>.}. */
    static final java.util.List<String> CLASS_PARTS = java.util.List.of("role", "blurb", "weapon", "glyph");

    static String classKey(String id, String part) {
        return "class." + RiftfallCore.MODID + "." + id + "." + part;
    }
}
