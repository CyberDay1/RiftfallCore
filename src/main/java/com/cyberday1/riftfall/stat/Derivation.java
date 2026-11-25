package com.cyberday1.riftfall.stat;

import com.cyberday1.riftfall.RiftfallCore;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What a point of a primary buys, in the pack's own attribute channels.
 *
 * <h2>One anchor, and every other number derived from it</h2>
 * By ruling, a point of Vitality buys <b>0.2</b> of {@code minecraft:generic.armor}. That
 * channel's measured gear band is {@link #ANCHOR_BAND}, so the ruling is really a ratio:
 * {@link #PER_BAND} of a channel's band per point. Every other coefficient is that ratio times its
 * own measured band, so there is exactly one design number here and the rest are measurements.
 *
 * <h2>Saturating, not linear</h2>
 * A point's worth falls as points pile up: {@code band x share x p / (p + K)}, the first point at
 * the anchor's rate and the ceiling at {@code band x share}. Attribute points can put hundreds on
 * one primary, which the linear table would have turned into four T20 kits.
 *
 * <h2>⛔ A band is a measurement and these are copies of one</h2>
 * The {@code band} on each row is what {@code /riftfallprobe budget} read at
 * {@link #BAND_TIER}. The rig re-reads the band's spread over seeds and fails if a row has left
 * it, because a coefficient carried forward without the measurement that produced it becomes
 * whatever the next reader assumes.
 *
 * <h2>Strength is a share of the swing</h2>
 * Ruled 2026-11-08: "strength should be an input into the weapons and skills damage calc". Its
 * attack damage row is {@link #STRENGTH_DAMAGE_BAND}, an {@code add_multiplied_total} share of the
 * whole swing, so it scales each weapon's own damage instead of erasing the gap between them. That
 * band is calibrated rather than priced by the gear census, as are the two Charge rows
 * ({@link #calibrated}); the rig holds each to its own spelling instead of to a seed spread.
 */
public final class Derivation {

    /** The ruling: one point of Vitality buys this much armour. The only design number here. */
    public static final double ANCHOR_ARMOUR_PER_POINT = 0.2D;

    /** The measured gear band the anchor was priced against. */
    public static final double ANCHOR_BAND = 26.3D;

    /** The tier every band on every row was measured at. */
    public static final int BAND_TIER = 8;

    /** The anchor as a ratio: what one point buys, per unit of the channel's own band. */
    public static final double PER_BAND = ANCHOR_ARMOUR_PER_POINT / ANCHOR_BAND;

    /**
     * The ceiling a primary approaches on each channel, as a share of that channel's band (D1, ruled
     * 2026-11-08: 1.0, so a primary never beats a full mythic kit on its channel). A third spelling
     * of the constant {@code ProbeCommand} and {@code checks.py} hold.
     */
    public static final double STAT_SHARE = 1.0D;

    /**
     * The points at which a default-share row reaches half its ceiling: the old saturation formula,
     * unfloored. Chosen so the first point still buys {@link #PER_BAND} of the band.
     */
    public static final double HALF_POINT = STAT_SHARE / PER_BAND;

    /** The old saturation count, kept as the probes' fixture size. No longer a cap. */
    public static final int REFERENCE_POINTS = 19;

    /**
     * Ruled 2026-09-26: a point of Intellect buys at most this much {@code riftfall:max_mana}.
     * A ceiling on the derived coefficient, not a second rate; {@code DerivationTest} and the
     * rig's {@code mana-kit} check hold every row to it.
     */
    public static final double MAX_MANA_PER_INTELLECT = 5.0D;

    /** Strength's channel. */
    public static final String STRENGTH_TARGET = "minecraft:generic.attack_damage";

    /**
     * Strength's ceiling as a share of the swing: weapon, gear and every other multiplier, times
     * {@code 1 + 0.75 x p / (p + K)}. Not a gear measurement. Calibrated on the attribute points S6
     * measurement: a focused 615 at T20 cuts the median boss TTK to x0.47, where the flat +19.71 cut
     * it to x0.50 on the same rig, and no longer favours fast weapons. The first point is +0.57%,
     * the 15-point class block x1.077, 615 points x1.618.
     */
    public static final double STRENGTH_DAMAGE_BAND = 0.75D;

    /** Dexterity's Charge row: the bank bows and guns spend. */
    public static final String CHARGE_BANK_TARGET = "riftfall:max_charge";

    /**
     * Its ceiling in melee-seconds over the base 10. Calibrated, not measured: no gear rolls Charge. A 610-point
     * focus is +4.94 (+49%), the class block's 10 is +0.42.
     */
    public static final double CHARGE_BANK_BAND = 6.0D;

    /** Agility's Charge row: the refill a second in combat. */
    public static final String CHARGE_REFILL_TARGET = "riftfall:charge_regen";

    /** Its ceiling over the base 0.5, calibrated the same way. A 606-point focus is +0.226 (+45%), the block's 6 is +0.012. */
    public static final double CHARGE_REFILL_BAND = 0.275D;

    /** Whether a target works on anything, or works only under a condition worth knowing. */
    public enum Standing {
        USABLE,
        CONDITIONAL
    }

    /**
     * One primary's contribution to one attribute channel.
     *
     * @param band      what a full mythic kit adds to this channel at {@link #BAND_TIER}
     * @param condition empty for {@link Standing#USABLE}, and never empty otherwise
     * @param share     the row's ceiling as a share of its band; {@link #STAT_SHARE} unless D3 says otherwise
     */
    public record Row(StatBlock.Primary primary, String target,
            AttributeModifier.Operation operation, double band,
            Standing standing, String condition, double share) {

        public Row(StatBlock.Primary primary, String target, AttributeModifier.Operation operation,
                double band, Standing standing, String condition) {
            this(primary, target, operation, band, standing, condition, STAT_SHARE);
        }

        /** What the first point buys. Derived, so it cannot drift from the anchor. */
        public double coefficient() {
            return band * PER_BAND * share / STAT_SHARE;
        }

        /** {@code band x share x p / (p + K)}: the first point is {@link #coefficient}, the ceiling {@code band x share}. */
        public double amount(double points) {
            return coefficient() * effective(points);
        }

        /** Deterministic, because respec removes by this and by nothing wider. */
        public ResourceLocation id() {
            return RiftfallCore.id(StatModifiers.PREFIX + primary.key() + "/"
                    + target.replace(':', '.'));
        }

        /** The channel, or null when the mod that owns it is not installed. */
        public @Nullable Holder<Attribute> attribute() {
            return BuiltInRegistries.ATTRIBUTE
                    .getHolder(ResourceLocation.parse(target))
                    .map(reference -> (Holder<Attribute>) reference)
                    .orElse(null);
        }
    }

    /** D3: Vitality's health row at four times the default share. */
    public static final double VITALITY_HEALTH_SHARE = 4.0D;

    /** D3: Vitality's armour row at a quarter of it, so its first point is 0.05 rather than the anchor's 0.2. */
    public static final double VITALITY_ARMOUR_SHARE = 0.25D;

    private static final AttributeModifier.Operation ADD = AttributeModifier.Operation.ADD_VALUE;

    private static final AttributeModifier.Operation MULTIPLIED =
            AttributeModifier.Operation.ADD_MULTIPLIED_BASE;

    /**
     * Strength's operation. Total, not base: the swing is multiplied once, after the weapon, the
     * gear's flat and percent affixes, a Strength effect and a skill's rank lever, so its factor
     * is the same whatever else the player carries, and a reader can divide it back out exactly.
     */
    private static final AttributeModifier.Operation MULTIPLIED_TOTAL =
            AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL;

    /**
     * ⛔ Iron's splits magic resistance nine ways, so one design row is nine written rows.
     *
     * <p>
     * The bands differ per school because the pool's nine {@code flat/school_resist} affixes roll
     * on different slot counts, not because the design wanted nine numbers.
     */
    private static final Map<String, Double> MAGIC_RESIST = magicResist();

    private static Map<String, Double> magicResist() {
        Map<String, Double> bands = new LinkedHashMap<>();
        bands.put("blood", 0.5737D);
        bands.put("eldritch", 0.5962D);
        bands.put("ender", 0.5775D);
        bands.put("evocation", 0.5850D);
        bands.put("fire", 0.5887D);
        bands.put("holy", 0.5925D);
        bands.put("ice", 0.5962D);
        bands.put("lightning", 0.5962D);
        bands.put("nature", 0.5887D);
        return bands;
    }

    private static final List<Row> ROWS = rows();

    private static List<Row> rows() {
        List<Row> table = new ArrayList<>();

        // Vitality: what keeps a player standing. The anchor row is the first one. Health-led (D3,
        // ruled 2026-11-08): health at 4x the share, armour at a quarter.
        table.add(new Row(StatBlock.Primary.VITALITY, "minecraft:generic.armor", ADD, ANCHOR_BAND,
                Standing.CONDITIONAL,
                "the pack replaces vanilla's armour formula through apothic_attributes' combat "
                        + "rules and every #minecraft:bypasses_armor type skips it; the ceiling is "
                        + "200 on this pack rather than vanilla's 30, measured off the live "
                        + "registry", VITALITY_ARMOUR_SHARE));
        table.add(new Row(StatBlock.Primary.VITALITY, "minecraft:generic.max_health", ADD, 20.0D,
                Standing.USABLE, "", VITALITY_HEALTH_SHARE));
        table.add(new Row(StatBlock.Primary.VITALITY, "minecraft:generic.armor_toughness", ADD,
                8.0950D, Standing.CONDITIONAL,
                "feeds only Apothic's toughness formula, whose effect on pierce and shred caps at "
                        + "0.6; the ceiling is 100 on this pack rather than vanilla's 20"));

        // Strength: what a swing is worth, as a share of it (ruled 2026-11-08). Was a flat add on
        // the measured 23.925 band, which gave every weapon the same +19.71 at 615 points.
        table.add(new Row(StatBlock.Primary.STRENGTH, STRENGTH_TARGET, MULTIPLIED_TOTAL,
                STRENGTH_DAMAGE_BAND, Standing.USABLE, ""));
        table.add(new Row(StatBlock.Primary.STRENGTH, "minecraft:generic.attack_knockback", ADD,
                0.5000D, Standing.USABLE, ""));

        // Dexterity: how often and how hard a swing lands rather than how big it is.
        // Attack speed 0.5, the 40-seed spread max since the 10-22 pack (was 0.8): the one native
        // 0.0-speed sword the floor lifted to 0.8 left the pack, the slowest left is 0.3.
        table.add(new Row(StatBlock.Primary.DEXTERITY, "minecraft:generic.attack_speed", ADD,
                0.5000D, Standing.USABLE, ""));
        table.add(new Row(StatBlock.Primary.DEXTERITY, "apothic_attributes:crit_chance", ADD,
                0.1000D, Standing.CONDITIONAL,
                "gated by #apothic_attributes:cannot_critically_strike, which is near-empty on "
                        + "this pack, so it works on nearly everything rather than on everything"));
        table.add(new Row(StatBlock.Primary.DEXTERITY, "apothic_attributes:crit_damage", ADD,
                0.1500D, Standing.CONDITIONAL, "the same near-empty gate tag as crit_chance"));
        // The Charge bank: a bow's or a gun's burst, as attack speed is a swing's rate.
        table.add(new Row(StatBlock.Primary.DEXTERITY, CHARGE_BANK_TARGET, ADD, CHARGE_BANK_BAND,
                Standing.CONDITIONAL, "spent only by a bow, a crossbow or a TaCZ gun"));

        // Intellect: the caster rows. The bar Riftfall charges, and power on two spell stacks.
        // The mana band is helmet + chest at 8.75 each since gear mana was halved (2026-11-08).
        table.add(new Row(StatBlock.Primary.INTELLECT, "riftfall:max_mana", ADD, 17.5D,
                Standing.USABLE, ""));
        table.add(new Row(StatBlock.Primary.INTELLECT, "irons_spellbooks:spell_power", MULTIPLIED,
                0.3000D, Standing.USABLE, ""));
        table.add(new Row(StatBlock.Primary.INTELLECT, "spell_power:generic", MULTIPLIED, 0.3000D,
                Standing.USABLE, ""));

        // Spirit: warding and sustain.
        for (Map.Entry<String, Double> school : MAGIC_RESIST.entrySet()) {
            table.add(new Row(StatBlock.Primary.SPIRIT,
                    "irons_spellbooks:" + school.getKey() + "_magic_resist", MULTIPLIED,
                    school.getValue(), Standing.USABLE, ""));
        }
        table.add(new Row(StatBlock.Primary.SPIRIT, "apothic_attributes:healing_received",
                MULTIPLIED, 0.1000D, Standing.USABLE, ""));
        table.add(new Row(StatBlock.Primary.SPIRIT, "mrpg_lib:tenacity", ADD, 12.0D,
                Standing.CONDITIONAL,
                "crowd-control resistance and not damage, counted against a base of 100, and read "
                        + "by mrpg_lib alone"));

        // Agility: moving, and not being moved. 0.08 on every seed since the 09-28 pack resync.
        table.add(new Row(StatBlock.Primary.AGILITY, "minecraft:generic.movement_speed", MULTIPLIED,
                0.0800D, Standing.USABLE, ""));
        table.add(new Row(StatBlock.Primary.AGILITY, "irons_lib:dodge_chance", ADD, 0.0300D,
                Standing.USABLE, ""));
        table.add(new Row(StatBlock.Primary.AGILITY, "minecraft:generic.knockback_resistance", ADD,
                0.1200D, Standing.USABLE, ""));
        // The Charge refill: sustained ranged fire, which kiting pays for.
        table.add(new Row(StatBlock.Primary.AGILITY, CHARGE_REFILL_TARGET, ADD, CHARGE_REFILL_BAND,
                Standing.CONDITIONAL, "refills only the Charge a bow, a crossbow or a TaCZ gun spends"));

        // Luck: what a rift hands back.
        table.add(new Row(StatBlock.Primary.LUCK, "minecraft:generic.luck", ADD, 2.0D,
                Standing.USABLE, ""));
        table.add(new Row(StatBlock.Primary.LUCK, "riftfall:scavenging", ADD, 0.2500D,
                Standing.USABLE, ""));
        table.add(new Row(StatBlock.Primary.LUCK, "apothic_attributes:experience_gained", MULTIPLIED,
                0.1500D, Standing.USABLE, ""));

        return List.copyOf(table);
    }

    private Derivation() {
    }

    /**
     * Raw points as the linear table would have counted them: {@code p x K / (p + K)}, with K
     * {@link #HALF_POINT}. Slope 1 at zero, never above K. Every reader of raw points goes through this.
     */
    public static double effective(double points) {
        return points > 0.0D ? points * HALF_POINT / (points + HALF_POINT) : 0.0D;
    }

    /** Strength's attack damage row: the multiplier weapon swings and physical skills carry. */
    public static Row strengthDamage() {
        for (Row row : ROWS) {
            if (row.primary() == StatBlock.Primary.STRENGTH && row.target().equals(STRENGTH_TARGET)) {
                return row;
            }
        }
        throw new IllegalStateException("the table has no Strength damage row");
    }

    /** What {@code points} of Strength multiply a swing by: {@code 1 + band x p / (p + K)}. */
    public static double strengthMultiplier(double points) {
        return 1.0D + strengthDamage().amount(points);
    }

    /** Whether a row's band is calibrated rather than a gear measurement, so no seed spread can hold it. */
    public static boolean calibrated(Row row) {
        return row.target().equals(STRENGTH_TARGET) && row.primary() == StatBlock.Primary.STRENGTH
                || row.target().equals(CHARGE_BANK_TARGET) || row.target().equals(CHARGE_REFILL_TARGET);
    }

    public static List<Row> rowsView() {
        return ROWS;
    }

    /** The rows one primary writes. Never null, and never empty: the table is total. */
    public static List<Row> of(StatBlock.Primary primary) {
        List<Row> mine = new ArrayList<>();
        for (Row row : ROWS) {
            if (row.primary() == primary) {
                mine.add(row);
            }
        }
        return List.copyOf(mine);
    }
}
