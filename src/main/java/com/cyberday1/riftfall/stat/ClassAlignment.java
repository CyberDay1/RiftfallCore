package com.cyberday1.riftfall.stat;

import com.cyberday1.riftfall.stat.StatBlock.Primary;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The fixed 40-point stat block each class carries. A class sets it; nothing lets a player move a point.
 *
 * <p>Built through the map constructor, never {@link StatBlock#plus}, so stripping the spend API
 * cannot break it. The block does not grow with level or ascension.
 */
public final class ClassAlignment {

    public static final int POINTS = 40;

    private static final Map<String, StatBlock> ROWS = Map.ofEntries(
            row("arcanist", 2, 2, 15, 6, 10, 3, 2),
            row("cleric", 2, 2, 15, 6, 10, 3, 2),
            row("gunner", 15, 10, 3, 2, 2, 6, 2),
            row("ranger", 15, 10, 3, 2, 2, 6, 2),
            row("phantom", 15, 10, 2, 2, 2, 6, 3),
            row("trickster", 15, 10, 2, 2, 2, 6, 3),
            row("shifter", 15, 3, 6, 10, 2, 2, 2),
            row("reaper", 15, 3, 10, 6, 2, 2, 2),
            row("paladin", 10, 2, 6, 15, 3, 2, 2),
            row("monk", 15, 2, 10, 6, 3, 2, 2),
            row("sword_immortal", 15, 3, 10, 6, 2, 2, 2),
            row("tactician", 10, 2, 15, 6, 2, 3, 2),
            row("summoner", 2, 2, 15, 6, 10, 3, 2));

    /** A class's main and secondary stat: its two highest allocations, which no data declares. */
    public record Pair(Primary main, Primary secondary) {
    }

    private static final Map<String, Pair> PAIRS = pairs();

    private ClassAlignment() {
    }

    /** The class's main and secondary stat, or null for a class with no row. */
    public static @Nullable Pair pair(@Nullable String skillClass) {
        return skillClass == null ? null : PAIRS.get(skillClass);
    }

    /** The top two of a block, refused rather than picked when first or second place is shared. */
    static Pair pairOf(StatBlock block) {
        List<Primary> ranked = new ArrayList<>(List.of(Primary.values()));
        ranked.sort(Comparator.comparingInt((Primary p) -> block.points(p)).reversed());
        int first = block.points(ranked.get(0));
        int second = block.points(ranked.get(1));
        int third = block.points(ranked.get(2));
        if (first == second || second == third) {
            throw new IllegalArgumentException("no unambiguous main and secondary in " + block.points());
        }
        return new Pair(ranked.get(0), ranked.get(1));
    }

    private static Map<String, Pair> pairs() {
        Map<String, Pair> pairs = new HashMap<>();
        ROWS.forEach((skillClass, block) -> pairs.put(skillClass, pairOf(block)));
        return Map.copyOf(pairs);
    }

    /** The class's block, or null for a class with no row ({@code dev}, or none chosen). */
    public static @Nullable StatBlock blockFor(@Nullable String skillClass) {
        return skillClass == null ? null : ROWS.get(skillClass);
    }

    /** What a player should carry: their class's block if it has one, otherwise what is stored. */
    public static StatBlock reconcile(StatBlock stored, @Nullable String skillClass) {
        StatBlock block = blockFor(skillClass);
        return block == null ? stored : block;
    }

    public static Map<String, StatBlock> rowsView() {
        return ROWS;
    }

    private static Map.Entry<String, StatBlock> row(String skillClass, int str, int dex, int intellect,
                                                    int vit, int spi, int agi, int luck) {
        Map<Primary, Integer> points = new EnumMap<>(Primary.class);
        points.put(Primary.STRENGTH, str);
        points.put(Primary.DEXTERITY, dex);
        points.put(Primary.INTELLECT, intellect);
        points.put(Primary.VITALITY, vit);
        points.put(Primary.SPIRIT, spi);
        points.put(Primary.AGILITY, agi);
        points.put(Primary.LUCK, luck);
        return Map.entry(skillClass, new StatBlock(points, POINTS));
    }
}
