package com.cyberday1.riftfall.client;

import com.cyberday1.riftfall.network.SkillScreenPayload;
import com.cyberday1.riftfall.skill.PassiveGuide;
import com.cyberday1.riftfall.skill.SkillBook;
import com.cyberday1.riftfall.skill.SkillTable.LeverType;
import com.cyberday1.riftfall.stat.Derivation;
import com.cyberday1.riftfall.stat.StatBlock;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The detail page's numbers: the synced node facts and the ability's coefficients put through the
 * same lever arithmetic as {@code SkillRanks}. Two columns, never a full rank table: held and next,
 * or rank 1 and the top before the first point, or the held rank alone once it is the top.
 */
final class SkillFigures {

    /** One row: a SKILLS line key for the label, then the value in each column. */
    record Row(String label, String from, String to) {
        boolean changes() {
            return !from.equals(to);
        }
    }

    /** Which ranks the two columns show; {@code single} at the top rank. */
    record Columns(int from, int to, boolean single) {
    }

    private SkillFigures() {
    }

    static @Nullable LeverType lever(SkillScreenPayload.Entry entry) {
        String key = entry.facts().lever();
        for (LeverType type : LeverType.values()) {
            if (type.key().equals(key)) {
                return type;
            }
        }
        return null;
    }

    static Columns columns(SkillScreenPayload.Entry entry) {
        int max = entry.maxRank();
        int rank = Math.min(entry.rank(), max);
        if (rank >= max) {
            return new Columns(max, max, true);
        }
        return rank == 0 ? new Columns(1, max, false) : new Columns(rank, rank + 1, false);
    }

    /** {@code SkillRanks}' linear factor for the levers that scale a magnitude; 1 for the rest. */
    static double factor(@Nullable LeverType lever, double rate, int rank) {
        if (lever == null) {
            return 1.0;
        }
        return switch (lever) {
            case DAMAGE, DOT, HEAL, SHIELD, BUFF -> 1.0 + rate * rank;
            default -> 1.0;
        };
    }

    /** The point price of the next rank; 0 at the top. */
    static int nextPoints(SkillScreenPayload.Entry entry) {
        if (entry.rank() >= entry.maxRank()) {
            return 0;
        }
        return SkillBook.rankCost(kind(entry), entry.rank() + 1);
    }

    static SkillBook.Kind kind(SkillScreenPayload.Entry entry) {
        return entry.passive() ? SkillBook.Kind.PASSIVE : entry.ultimate() ? SkillBook.Kind.ULTIMATE : SkillBook.Kind.ACTIVE;
    }

    /** A castable's rows: its lever's figure where it has one, then mana and cooldown. */
    static List<Row> rows(SkillScreenPayload.Entry entry, AbilityFacts pack) {
        SkillScreenPayload.Facts f = entry.facts();
        AbilityFacts ability = pack.scaled(f.powerMultiplier());
        Columns c = columns(entry);
        LeverType lever = lever(entry);
        List<Row> rows = new ArrayList<>();
        String figure = figureLabel(lever, ability);
        if (figure != null) {
            rows.add(new Row(figure, figure(lever, f.leverRate(), ability, c.from()),
                    figure(lever, f.leverRate(), ability, c.to())));
        }
        if (lever == LeverType.DOT || lever == LeverType.SUMMON || lever == LeverType.BUFF) {
            rows.add(new Row("fig_time", plusSeconds(f.leverSeconds() * c.from()), plusSeconds(f.leverSeconds() * c.to())));
        }
        double manaFrom = entry.rank() == 0 ? f.costNext() : entry.cost();
        double manaTo = entry.rank() == 0 ? f.costMax() : c.single() ? entry.cost() : f.costNext();
        rows.add(new Row("mana", CharacterSheet.amount(manaFrom), CharacterSheet.amount(manaTo)));
        int coolFrom = entry.rank() == 0 ? f.cooldownNext() : f.cooldownNow();
        int coolTo = entry.rank() == 0 ? f.cooldownMax() : c.single() ? f.cooldownNow() : f.cooldownNext();
        rows.add(new Row("cooldown", seconds(coolFrom), seconds(coolTo)));
        return rows;
    }

    /** "760%, 13.3s": the top rank, shown under two columns that stop short of it. */
    static @Nullable String atMax(SkillScreenPayload.Entry entry, AbilityFacts pack) {
        Columns c = columns(entry);
        AbilityFacts ability = pack.scaled(entry.facts().powerMultiplier());
        if (c.single() || c.to() == entry.maxRank()) {
            return null;
        }
        LeverType lever = lever(entry);
        String cooldown = seconds(entry.facts().cooldownMax());
        return figureLabel(lever, ability) == null ? cooldown
                : figure(lever, entry.facts().leverRate(), ability, entry.maxRank()) + ", " + cooldown;
    }

    /** A passive's one row: what it adds at each column's rank. */
    static Row passiveRow(SkillScreenPayload.Entry entry) {
        Columns c = columns(entry);
        return new Row("fig_bonus", plus(entry.facts().bonusAt(c.from())), plus(entry.facts().bonusAt(c.to())));
    }

    /** The unit a passive's value reads in: the guide's, else its attribute's. */
    static String passiveUnit(SkillScreenPayload.Entry entry) {
        return entry.guide().tokens().getOrDefault(PassiveGuide.ROW_UNIT, PassiveGuide.unit(entry.facts().attribute()));
    }

    /** What a passive raises: an attribute id, or a kind with a {@code passive_stat_} line. */
    static String passiveStat(SkillScreenPayload.Entry entry) {
        return entry.guide().tokens().getOrDefault(PassiveGuide.ROW_STAT, entry.facts().attribute());
    }

    static @Nullable String figureLabel(@Nullable LeverType lever, AbilityFacts ability) {
        if (lever == null) {
            return null;
        }
        return switch (lever) {
            case DAMAGE, DOT -> "fig_damage";
            case HEAL -> ability.heal() > 0.0 || ability.damage() <= 0.0 ? "fig_heal" : "fig_damage";
            case SHIELD -> "fig_shield";
            case BUFF -> "fig_buff";
            case SUMMON -> "fig_owner";
            default -> null;
        };
    }

    /**
     * The lever's figure at {@code rank}: the ability's coefficient times the factor as a percentage of
     * the power attribute when the coefficient is known, else the factor itself ("x1.7").
     */
    static String figure(@Nullable LeverType lever, double rate, AbilityFacts ability, int rank) {
        if (lever == LeverType.SUMMON) {
            return plus(rate * rank * 100.0) + "%";
        }
        double factor = factor(lever, rate, rank);
        double coefficient = switch (lever == null ? LeverType.MOVEMENT : lever) {
            case DAMAGE, DOT -> ability.damage();
            case HEAL -> ability.heal() > 0.0 ? ability.heal() : ability.damage();
            default -> 0.0;
        };
        return coefficient > 0.0 ? percent(coefficient * factor) : "x" + trimmed(factor);
    }

    /** 6.46 to "646%". */
    static String percent(double fraction) {
        return Math.round(fraction * 100.0) + "%";
    }

    /** Ticks as seconds to a tenth, half up on the exact tick count: 291 ticks is "14.6s". */
    static String seconds(int ticks) {
        int tenths = (ticks + 1) / 2;
        return tenths / 10 + "." + tenths % 10 + "s";
    }

    static String plus(double value) {
        return (value >= 0.0 ? "+" : "") + CharacterSheet.amount(value);
    }

    private static String plusSeconds(double seconds) {
        return plus(seconds) + "s";
    }

    /** Two places, trailing zeros dropped: 1.7, 1.25, 2. */
    static String trimmed(double value) {
        String text = String.format(Locale.ROOT, "%.2f", value);
        text = text.contains(".") ? text.replaceAll("0+$", "").replaceAll("\\.$", "") : text;
        return text;
    }

    /**
     * The primary a power attribute belongs to: a Riftfall primary itself, or the primary whose
     * {@link Derivation} row writes it. Null when no primary does.
     */
    static @Nullable StatBlock.Primary primaryOf(String attribute) {
        for (StatBlock.Primary primary : StatBlock.Primary.values()) {
            if (attribute.equals("riftfall:" + primary.key())) {
                return primary;
            }
        }
        for (Derivation.Row row : Derivation.rowsView()) {
            if (row.target().equals(attribute)) {
                return row.primary();
            }
        }
        return null;
    }
}
