package com.cyberday1.riftfall.gear;

import com.cyberday1.riftfall.init.RiftfallDataComponents;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A rift-dropped gun's own gear stat: the rift tier it dropped at and a rarity, stamped as
 * {@code riftfall:gun_grade}. TaCZ guns are not Apotheosis gear, so this stands in for the weapon floor and the
 * rarity roll. The bullet hook multiplies every hit by {@link #factor}.
 *
 * <p>
 * One pull's damage = TaCZ's damage x {@code damage_multiplier} (the global re-baseline) x the band's
 * {@code damage_scale} x {@link #tierFactor} x {@link #rarityFactor}. The first two live in
 * {@code data/riftfall/rift_arms/guns.json}; the tier curve is the weapon floor's own sword row, so a gun climbs with
 * tier exactly as a sword does. A gun with no grade (creative, or dropped before this) reads as tier 1 common.
 *
 * <p>
 * {@code shown} is the one-pull damage the tooltip prints. The server writes it when it stamps the gun and refreshes it
 * while the gun is held, because the client has no copy of the datapack's multipliers. {@code stats}, the rarity's
 * handling bonuses ({@link Stats}), rides the stack for the same reason: the client builds its own TaCZ cache.
 */
public record GunGrade(int tier, int rarity, float shown, Stats stats) {

    /** A gun with no grade, and the Gunner's starter gun: tier 1 common. */
    public static final GunGrade BASELINE = new GunGrade(1, 0, 0.0F);

    /** The rarity damage factors, common to mythic, when guns.json names none (ruled 2026-11-08). */
    public static final double[] DEFAULT_RARITY_FACTORS = {1.00, 1.08, 1.16, 1.28, 1.40};

    /** The rarity handling defaults, common to mythic, when guns.json names none (ruled 2026-11-08). */
    public static final double[] DEFAULT_FIRE_RATE_BONUS = {0.00, 0.03, 0.06, 0.09, 0.12};
    public static final double[] DEFAULT_RECOIL_CUT = {0.00, 0.08, 0.16, 0.24, 0.32};
    public static final double[] DEFAULT_RELOAD_CUT = {0.00, 0.05, 0.10, 0.15, 0.20};
    public static final double[] DEFAULT_ADS_CUT = {0.00, 0.05, 0.10, 0.15, 0.20};
    public static final double[] DEFAULT_SPREAD_CUT = {0.00, 0.05, 0.10, 0.15, 0.20};

    /** Apotheosis' own rarity colours (data/apotheosis/rarities), so a gun reads like the rest of the gear. */
    static final int[] COLOURS = {0x808080, 0x33FF33, 0x5555FF, 0xBB00BB, 0xED7014};

    public static final Codec<GunGrade> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ExtraCodecs.POSITIVE_INT.fieldOf("tier").forGetter(GunGrade::tier),
            Codec.intRange(0, RarityLadder.RARITIES.length - 1).fieldOf("rarity").forGetter(GunGrade::rarity),
            Codec.FLOAT.optionalFieldOf("shown", 0.0F).forGetter(GunGrade::shown),
            Stats.CODEC.optionalFieldOf("stats", Stats.NONE).forGetter(GunGrade::stats)
    ).apply(instance, GunGrade::new));

    public static final StreamCodec<ByteBuf, GunGrade> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, GunGrade::tier,
            ByteBufCodecs.VAR_INT, GunGrade::rarity,
            ByteBufCodecs.FLOAT, GunGrade::shown,
            Stats.STREAM_CODEC, GunGrade::stats,
            GunGrade::new);

    private static final ResourceLocation GUN = ResourceLocation.fromNamespaceAndPath("tacz", "modern_kinetic_gun");

    public GunGrade {
        tier = Math.max(1, tier);
        rarity = Math.clamp(rarity, 0, RarityLadder.RARITIES.length - 1);
        stats = stats == null ? Stats.NONE : stats;
    }

    public GunGrade(int tier, int rarity, float shown) {
        this(tier, rarity, shown, Stats.NONE);
    }

    public GunGrade withShown(float damage) {
        return new GunGrade(tier, rarity, damage, stats);
    }

    public GunGrade withStats(Stats handling) {
        return new GunGrade(tier, rarity, shown, handling);
    }

    /**
     * A rarity's handling bonuses, as fractions: fire rate up; recoil (both axes), reload time, aim-down-sights time
     * and spread down. All zero at common. Read off guns.json's {@code grade} ({@link RiftArms.Grading#stats}).
     */
    public record Stats(float fireRate, float recoil, float reload, float ads, float spread) {

        public static final Stats NONE = new Stats(0.0F, 0.0F, 0.0F, 0.0F, 0.0F);
        /** No cut takes a stat below a tenth of TaCZ's own. */
        public static final float MAX_CUT = 0.9F;
        /** TaCZ clamps rpm at 1200 anyway; this keeps a typo from reading as a thousandfold. */
        public static final float MAX_BONUS = 4.0F;

        public static final Codec<Stats> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.FLOAT.optionalFieldOf("fire_rate", 0.0F).forGetter(Stats::fireRate),
                Codec.FLOAT.optionalFieldOf("recoil", 0.0F).forGetter(Stats::recoil),
                Codec.FLOAT.optionalFieldOf("reload", 0.0F).forGetter(Stats::reload),
                Codec.FLOAT.optionalFieldOf("ads", 0.0F).forGetter(Stats::ads),
                Codec.FLOAT.optionalFieldOf("spread", 0.0F).forGetter(Stats::spread)
        ).apply(instance, Stats::new));

        public static final StreamCodec<ByteBuf, Stats> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.FLOAT, Stats::fireRate,
                ByteBufCodecs.FLOAT, Stats::recoil,
                ByteBufCodecs.FLOAT, Stats::reload,
                ByteBufCodecs.FLOAT, Stats::ads,
                ByteBufCodecs.FLOAT, Stats::spread,
                Stats::new);

        public Stats {
            fireRate = Math.clamp(fireRate, 0.0F, MAX_BONUS);
            recoil = Math.clamp(recoil, 0.0F, MAX_CUT);
            reload = Math.clamp(reload, 0.0F, MAX_CUT);
            ads = Math.clamp(ads, 0.0F, MAX_CUT);
            spread = Math.clamp(spread, 0.0F, MAX_CUT);
        }

        public boolean none() {
            return equals(NONE);
        }

        public double rpmFactor() {
            return 1.0 + fireRate;
        }

        public double recoilFactor() {
            return 1.0 - recoil;
        }

        /** How fast a reload runs against TaCZ's clock: a 20% shorter reload runs at 1.25x. */
        public double reloadRate() {
            return 1.0 / (1.0 - reload);
        }

        public double adsFactor() {
            return 1.0 - ads;
        }

        public double spreadFactor() {
            return 1.0 - spread;
        }

        /** One tooltip line per stat that moves: "+9% Fire Rate", "-24% Recoil". */
        public List<Component> lines() {
            List<Component> lines = new ArrayList<>(5);
            line(lines, "fire_rate", fireRate, "+");
            line(lines, "recoil", recoil, "-");
            line(lines, "reload", reload, "-");
            line(lines, "ads", ads, "-");
            line(lines, "spread", spread, "-");
            return lines;
        }

        private static void line(List<Component> lines, String stat, float value, String sign) {
            long percent = Math.round(value * 100.0);
            if (percent != 0L) {
                lines.add(Component.translatable("tooltip.riftfall.gun_stat." + stat, sign + percent + "%")
                        .withStyle(ChatFormatting.BLUE));
            }
        }
    }

    // ---- the numbers ----

    /** The weapon floor's sword row at {@code tier} over its tier-1 value: 1.00 at T1, 1.31 at T5, 5.99 at T50. */
    public static double tierFactor(int tier) {
        WeaponFloor.Melee sword = WeaponFloor.Melee.SWORD;
        return WeaponFloor.meleeDamageAt(Math.max(1, tier), sword) / WeaponFloor.meleeDamageAt(1, sword);
    }

    public static double rarityFactor(int rarity, double[] factors) {
        double[] f = factors == null || factors.length == 0 ? DEFAULT_RARITY_FACTORS : factors;
        return f[Math.clamp(rarity, 0, f.length - 1)];
    }

    /** The grade's own share: tier times rarity, 1.0 at tier 1 common. */
    public double gradeFactor(double[] rarityFactors) {
        return tierFactor(tier) * rarityFactor(rarity, rarityFactors);
    }

    /** The whole bullet multiplier: global re-baseline x band scale x grade. */
    public double factor(double damageMultiplier, double bandScale, double[] rarityFactors) {
        return Math.max(0.0, damageMultiplier) * Math.max(0.0, bandScale) * gradeFactor(rarityFactors);
    }

    // ---- stacks ----

    public static boolean isGun(ItemStack stack) {
        return !stack.isEmpty() && GUN.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()));
    }

    /** The stack's grade; {@link #BASELINE} for a gun that has none. */
    public static GunGrade of(ItemStack stack) {
        GunGrade grade = stack.get(RiftfallDataComponents.GUN_GRADE.get());
        return grade == null ? BASELINE : grade;
    }

    /** Reads a graded gun's one-pull damage; set by the TaCZ compat when TaCZ is loaded. */
    private static volatile java.util.function.ToDoubleFunction<ItemStack> shotReader = stack -> 0.0;

    public static void setShotReader(java.util.function.ToDoubleFunction<ItemStack> reader) {
        shotReader = reader;
    }

    /**
     * Writes a grade on a gun stack, with the damage its tooltip shows and its rarity's handling bonuses; anything
     * else comes back untouched.
     */
    public static ItemStack stamp(ItemStack stack, int tier, int rarity) {
        if (isGun(stack)) {
            GunGrade grade = new GunGrade(tier, rarity, 0.0F, RiftArms.grading().stats(rarity));
            stack.set(RiftfallDataComponents.GUN_GRADE.get(), grade);
            float shown = (float) (Math.round(shotReader.applyAsDouble(stack) * 10.0) / 10.0);
            stack.set(RiftfallDataComponents.GUN_GRADE.get(), grade.withShown(shown));
        }
        return stack;
    }

    /**
     * The Fitter's re-temper: the same gun at {@code tier}, its rarity and handling kept, the tooltip damage refreshed.
     * A stack with no grade comes back untouched.
     */
    public static ItemStack retier(ItemStack stack, int tier) {
        GunGrade old = read(stack);
        if (old != null) {
            GunGrade grade = new GunGrade(tier, old.rarity(), 0.0F, old.stats());
            stack.set(RiftfallDataComponents.GUN_GRADE.get(), grade);
            float shown = (float) (Math.round(shotReader.applyAsDouble(stack) * 10.0) / 10.0);
            stack.set(RiftfallDataComponents.GUN_GRADE.get(), grade.withShown(shown));
        }
        return stack;
    }

    /** A rift drop's grade: the tier, and a rarity off the rifts' own ladder ({@link RarityLadder}). */
    public static int rollRarity(int tier, int roll) {
        return RarityLadder.index(tier, roll);
    }

    // ---- the tooltip ----

    public static String rarityKey(int rarity) {
        String id = RarityLadder.RARITIES[Math.clamp(rarity, 0, RarityLadder.RARITIES.length - 1)];
        return "rarity." + id.replace(':', '.');
    }

    public static String rarityName(int rarity) {
        String id = RarityLadder.RARITIES[Math.clamp(rarity, 0, RarityLadder.RARITIES.length - 1)];
        String path = id.substring(id.indexOf(':') + 1);
        return path.substring(0, 1).toUpperCase(Locale.ROOT) + path.substring(1);
    }

    public int colour() {
        return COLOURS[Math.clamp(rarity, 0, COLOURS.length - 1)];
    }

    /**
     * The lines under the name: rarity and tier in the rarity's colour, the one-pull damage when known, then the
     * rarity's handling bonuses (none at common).
     */
    public List<Component> lines() {
        List<Component> lines = new ArrayList<>(7);
        Style colour = Style.EMPTY.withColor(TextColor.fromRgb(colour()));
        lines.add(Component.translatable("tooltip.riftfall.gun_grade",
                Component.translatableWithFallback(rarityKey(rarity), rarityName(rarity)), tier).withStyle(colour));
        if (shown > 0.0F) {
            lines.add(Component.translatable("tooltip.riftfall.gun_damage", String.format(Locale.ROOT, "%.1f", shown))
                    .withStyle(ChatFormatting.DARK_GREEN));
        }
        lines.addAll(stats.lines());
        return lines;
    }

    /** The name line in the rarity's colour, as Apotheosis colours the rest of the gear. */
    public MutableComponent colourName(Component name) {
        return name.copy().withStyle(Style.EMPTY.withColor(TextColor.fromRgb(colour())));
    }

    /** For a probe or a log: "T3 rare". */
    @Override
    public String toString() {
        return "T" + tier + " " + rarityName(rarity).toLowerCase(Locale.ROOT) + (shown > 0.0F ? " " + shown : "");
    }

    public static @Nullable GunGrade read(ItemStack stack) {
        return stack.get(RiftfallDataComponents.GUN_GRADE.get());
    }
}
