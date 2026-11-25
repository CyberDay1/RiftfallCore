package com.cyberday1.riftfall.home.npc.depth.commission;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * One request a resident may post, from {@code data/<ns>/riftfall/commission/<role>/*.json}. {@code min_tier} and
 * {@code max_tier} bound the home tier it is posted at; {@code min_run_tier} and {@code tier_offset} bound the runs
 * that advance it. Rewards are personal and must pass the conversion result rule. A {@code fresh} theme request picks a
 * theme the home did not clear in the last board cycle; {@code variety} counts different themes cleared.
 */
public record CommissionDef(Kind kind, int weight, int minTier, int maxTier, int count, int xp,
                            Optional<ResourceLocation> theme, Optional<ResourceLocation> entityTag,
                            Optional<ResourceLocation> itemTag, int minRunTier, int tierOffset, int minutes,
                            List<ItemStack> rewards, boolean retrain, Optional<String> title, boolean fresh) {
    public enum Kind implements StringRepresentable {
        THEME, BOSS, MOB, TIER, CLEAN, SPEED, DELIVERY, VARIETY;
        public static final Codec<Kind> CODEC = StringRepresentable.fromEnum(Kind::values);
        @Override public String getSerializedName() { return name().toLowerCase(Locale.ROOT); }
    }

    public static final int MIN_XP = 20, MAX_XP = 60;

    public static final Codec<CommissionDef> CODEC = RecordCodecBuilder.create(i -> i.group(
            Kind.CODEC.fieldOf("kind").forGetter(CommissionDef::kind),
            Codec.INT.optionalFieldOf("weight", 10).forGetter(CommissionDef::weight),
            Codec.INT.optionalFieldOf("min_tier", 1).forGetter(CommissionDef::minTier),
            Codec.INT.optionalFieldOf("max_tier", Integer.MAX_VALUE).forGetter(CommissionDef::maxTier),
            Codec.INT.optionalFieldOf("count", 1).forGetter(CommissionDef::count),
            Codec.INT.optionalFieldOf("xp", MIN_XP).forGetter(CommissionDef::xp),
            ResourceLocation.CODEC.optionalFieldOf("theme").forGetter(CommissionDef::theme),
            ResourceLocation.CODEC.optionalFieldOf("entity_tag").forGetter(CommissionDef::entityTag),
            ResourceLocation.CODEC.optionalFieldOf("item_tag").forGetter(CommissionDef::itemTag),
            Codec.INT.optionalFieldOf("min_run_tier", 1).forGetter(CommissionDef::minRunTier),
            Codec.INT.optionalFieldOf("tier_offset", 0).forGetter(CommissionDef::tierOffset),
            Codec.INT.optionalFieldOf("minutes", 0).forGetter(CommissionDef::minutes),
            ItemStack.CODEC.listOf().optionalFieldOf("rewards", List.of()).forGetter(CommissionDef::rewards),
            Codec.BOOL.optionalFieldOf("retrain", false).forGetter(CommissionDef::retrain),
            Codec.STRING.optionalFieldOf("title").forGetter(CommissionDef::title),
            Codec.BOOL.optionalFieldOf("fresh", false).forGetter(CommissionDef::fresh)
    ).apply(i, CommissionDef::new));

    public CommissionDef {
        rewards = List.copyOf(rewards);
    }

    public CommissionDef(Kind kind, int weight, int minTier, int maxTier, int count, int xp, Optional<ResourceLocation> theme,
                         Optional<ResourceLocation> entityTag, Optional<ResourceLocation> itemTag, int minRunTier, int tierOffset,
                         int minutes, List<ItemStack> rewards, boolean retrain, Optional<String> title) {
        this(kind, weight, minTier, maxTier, count, xp, theme, entityTag, itemTag, minRunTier, tierOffset, minutes, rewards,
                retrain, title, false);
    }

    /** Shape rules a file must keep; live checks (themes, tags, reward items) are the pool's. */
    public List<String> problems() {
        var out = new ArrayList<String>();
        if (weight <= 0) out.add("weight must be positive");
        if (minTier < 1 || maxTier < minTier) out.add("bad tier bounds");
        if (count < 1) out.add("count must be at least 1");
        if (xp < MIN_XP || xp > MAX_XP) out.add("xp must be " + MIN_XP + "-" + MAX_XP);
        if (minRunTier < 1) out.add("min_run_tier must be at least 1");
        switch (kind) {
            case MOB -> { if (entityTag.isEmpty()) out.add("mob needs entity_tag"); }
            case SPEED -> { if (minutes <= 0) out.add("speed needs minutes"); }
            case DELIVERY -> { if (itemTag.isEmpty()) out.add("delivery needs item_tag"); }
            default -> {}
        }
        if (kind != Kind.DELIVERY && itemTag.isPresent()) out.add("item_tag is for delivery only");
        if (fresh && (kind != Kind.THEME || theme.isPresent())) out.add("fresh is for theme requests that pick their theme");
        return out;
    }
}
