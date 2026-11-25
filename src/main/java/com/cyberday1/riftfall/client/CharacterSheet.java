package com.cyberday1.riftfall.client;

import com.cyberday1.riftfall.network.SkillScreenPayload;
import com.cyberday1.riftfall.skill.SkillBook;
import com.cyberday1.riftfall.skill.SkillTable.LeverType;
import com.cyberday1.riftfall.stat.Derivation;
import com.cyberday1.riftfall.stat.StatBlock;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The character page's arithmetic: the XP bar's fill, how numbers print, what each primary's points
 * buy (from {@link Derivation}, so the page and the stat code cannot disagree), and the class's
 * skill-type mix. No Minecraft screen type, so it is plain JUnit.
 */
final class CharacterSheet {

    /** Channels whose ADD value is a fraction, so print as a percentage like the multiplied ones. */
    private static final Set<String> FRACTIONS = Set.of("crit", "crit_damage", "dodge", "knockback_resist", "scavenging");

    /** One thing a primary buys: a SHEET line key's suffix, the amount, and how many channels it merges. */
    record Effect(String label, double amount, boolean percent, int channels) {
    }

    /** The class's castable base skills per lever, in chip order, and its base passives. */
    record Mix(Map<LeverType, Integer> levers, int passives) {
    }

    private CharacterSheet() {
    }

    /**
     * Pixels of a {@code width}-px trough filled at this much progress.
     *
     * <p>⛔ Full only at full, empty only at empty. A plain floor draws nothing for the first hundred XP
     * of a sixteen-thousand step; a plain round draws a full bar at 99.5%.
     */
    static int barFill(double fraction, int width) {
        if (fraction <= 0.0) {
            return 0;
        }
        if (fraction >= 1.0) {
            return width;
        }
        return (int) Math.clamp((long) (fraction * width), 1L, width - 1L);
    }

    /** A whole number without its ".0"; anything else to one decimal place. */
    static String amount(double value) {
        double tenths = Math.round(value * 10.0) / 10.0;
        return tenths == Math.rint(tenths)
                ? String.valueOf((long) tenths)
                : String.valueOf(tenths);
    }

    /** A fraction as a percentage to one decimal place, for the percent line: 0.036 is "3.6". */
    static String percent(double fraction) {
        return amount(fraction * 100.0);
    }

    /** A line key with its singular form: {@code key_one} for exactly one. */
    static String counted(String key, int count) {
        return count == 1 ? key + "_one" : key;
    }

    /**
     * The ascendancies the sheet's Ascend offers, in the server's order. Empty unless the player could ascend now:
     * a class, level 60, nothing chosen yet, outside a rift. The server decides again on the request.
     */
    static List<String> ascendChoices(SkillScreenPayload view) {
        if (!view.hasClass() || !view.ascension().isEmpty() || view.inRift()
                || view.level() < SkillBook.ASCENSION_LEVEL) {
            return List.of();
        }
        return CodexBrowser.ascendancies(view);
    }

    /** The title line of a refusal's panel, by the action refused. */
    static String refusalTitle(String action) {
        return switch (action) {
            case "ALLOCATE" -> "stage_title";
            case "ASCEND" -> "ascend_title";
            default -> "respec_title";
        };
    }

    /**
     * The line for a refused Confirm ({@code ALLOCATE}), Reset ({@code RESPEC_STATS}) or Ascend ({@code ASCEND});
     * null for anything else.
     */
    static @Nullable String refusal(String action, String result) {
        boolean sheet = action.equals("ALLOCATE") || action.equals("RESPEC_STATS") || action.equals("ASCEND");
        return !sheet || result.isEmpty() || result.equals("OK") ? null
                : "result_" + action.toLowerCase(Locale.ROOT) + "_" + result.toLowerCase(Locale.ROOT);
    }

    /** "12,410": thousands grouped, for XP. */
    static String grouped(long value) {
        return String.format(Locale.ROOT, "%,d", value);
    }

    /**
     * What {@code points} of a primary buy, one entry per channel label in {@link Derivation}'s order.
     * Channels sharing a label (nine magic resists, two spell powers) merge into their mean.
     */
    static List<Effect> effects(StatBlock.Primary primary, double points) {
        Map<String, double[]> merged = new LinkedHashMap<>();
        Map<String, Boolean> percent = new LinkedHashMap<>();
        for (Derivation.Row row : Derivation.of(primary)) {
            String label = label(row.target());
            boolean fraction = row.operation() != AttributeModifier.Operation.ADD_VALUE || FRACTIONS.contains(label);
            double[] sum = merged.computeIfAbsent(label, k -> new double[2]);
            sum[0] += row.amount(points);
            sum[1]++;
            percent.put(label, fraction);
        }
        List<Effect> out = new ArrayList<>();
        merged.forEach((label, sum) -> out.add(new Effect(label, sum[0] / sum[1], percent.get(label), (int) sum[1])));
        return out;
    }

    /** What one more point on top of {@code points} buys, per label: the hover on a primary's +. */
    static List<Effect> nextPoint(StatBlock.Primary primary, double points) {
        List<Effect> now = effects(primary, points);
        List<Effect> next = effects(primary, points + 1);
        List<Effect> out = new ArrayList<>();
        for (int i = 0; i < next.size(); i++) {
            Effect after = next.get(i);
            out.add(new Effect(after.label(), after.amount() - now.get(i).amount(), after.percent(), after.channels()));
        }
        return out;
    }

    /**
     * Attribute points staged on the sheet before Confirm. Staging is free because nothing is sent until
     * Confirm; after it, only the paid stat reset takes points back (D7).
     */
    static final class Staging {

        private final int[] staged = new int[StatBlock.Primary.values().length];

        /** Stages up to {@code count} on {@code primary} within what the pool has left; returns how many went. */
        int add(StatBlock.Primary primary, int count, int pool) {
            int room = pool - total();
            int added = Math.max(0, Math.min(count, room));
            staged[primary.ordinal()] += added;
            return added;
        }

        int get(StatBlock.Primary primary) {
            return staged[primary.ordinal()];
        }

        int total() {
            int total = 0;
            for (int count : staged) {
                total += count;
            }
            return total;
        }

        void clear() {
            java.util.Arrays.fill(staged, 0);
        }

        Map<StatBlock.Primary, Integer> asMap() {
            Map<StatBlock.Primary, Integer> out = new EnumMap<>(StatBlock.Primary.class);
            for (StatBlock.Primary primary : StatBlock.Primary.values()) {
                out.put(primary, staged[primary.ordinal()]);
            }
            return out;
        }
    }

    /** The sheet line for "+2.73 attack damage" or "+4.36% magic resist (9)": one key, number and label as args. */
    static String effectLine(Effect effect) {
        return "effect_line" + (effect.percent() ? "_percent" : "") + (effect.channels() > 2 ? "_channels" : "");
    }

    /** {@link #effectLine}'s arguments: the number, the label, and the channel count when shown. */
    static Object[] effectArgs(Effect effect, Object label) {
        String number = effectNumber(effect.percent() ? effect.amount() * 100.0 : effect.amount());
        return effect.channels() > 2 ? new Object[] {number, label, Integer.toString(effect.channels())}
                : new Object[] {number, label};
    }

    /** Two places, three under a tenth, one from ten up: 2.73, 0.012, 14.2. */
    static String effectNumber(double value) {
        double magnitude = Math.abs(value);
        String format = magnitude >= 10.0 ? "%.1f" : magnitude >= 0.1 ? "%.2f" : "%.3f";
        return String.format(Locale.ROOT, format, value);
    }

    /** The short name a channel is printed under; also its SHEET line key, {@code effect_<label>}. */
    static String label(String target) {
        int colon = target.indexOf(':');
        String path = colon < 0 ? target : target.substring(colon + 1);
        if (path.endsWith("_magic_resist")) {
            return "magic_resist";
        }
        if (target.startsWith("spell_power:") || path.equals("spell_power")) {
            return "spell_power";
        }
        return switch (path) {
            case "generic.armor" -> "armor";
            case "generic.max_health" -> "health";
            case "generic.armor_toughness" -> "armor_toughness";
            case "generic.attack_damage" -> "attack_damage";
            case "generic.attack_knockback" -> "attack_knockback";
            case "generic.attack_speed" -> "attack_speed";
            case "crit_chance" -> "crit";
            case "crit_damage" -> "crit_damage";
            case "max_mana" -> "max_mana";
            case "healing_received" -> "healing_received";
            case "tenacity" -> "tenacity";
            case "generic.movement_speed" -> "move_speed";
            case "dodge_chance" -> "dodge";
            case "generic.knockback_resistance" -> "knockback_resist";
            case "generic.luck" -> "luck";
            case "scavenging" -> "scavenging";
            case "experience_gained" -> "xp";
            default -> path.replace('.', '_');
        };
    }

    /** Every label {@link #label} can answer for the shipped table, so each one gets a lang key. */
    static List<String> effectLabels() {
        List<String> labels = new ArrayList<>();
        for (Derivation.Row row : Derivation.rowsView()) {
            String label = label(row.target());
            if (!labels.contains(label)) {
                labels.add(label);
            }
        }
        return labels;
    }

    /**
     * The class's skill-type mix, counted from the base rows the server sent: "Damage 4, Control 4,
     * Heal 1, Movement 1, 5 passives". Ascendancy and universal rows are not the class.
     */
    static Mix mix(SkillScreenPayload view) {
        Map<LeverType, Integer> counts = new EnumMap<>(LeverType.class);
        int passives = 0;
        for (SkillScreenPayload.Entry entry : view.entries()) {
            if (entry.universal() || !entry.base()) {
                continue;
            }
            if (entry.passive()) {
                passives++;
                continue;
            }
            LeverType lever = SkillFigures.lever(entry);
            if (lever != null) {
                counts.merge(lever, 1, Integer::sum);
            }
        }
        Map<LeverType, Integer> ordered = new LinkedHashMap<>();
        for (LeverType lever : CodexBrowser.CHIP_ORDER) {
            if (counts.containsKey(lever)) {
                ordered.put(lever, counts.get(lever));
            }
        }
        return new Mix(ordered, passives);
    }
}
