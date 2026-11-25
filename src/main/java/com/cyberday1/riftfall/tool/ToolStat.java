package com.cyberday1.riftfall.tool;

import net.minecraft.network.chat.Component;

import java.util.Locale;

/** Direct, permanent bonuses per rune strength. Existing saved ranks retain their values. */
public enum ToolStat {
    MINING_SPEED("mining_speed", "Mining speed", RuneGrade.COMMON, 5, "+%s%%",
            "Faster mining and personal barrel breaking."),
    CHAIN("chain", "Chain targets", RuneGrade.UNCOMMON, 1, "+%s extra",
            "Automatically collects nearby barrels. Base link range: 3 blocks."),
    RARITY("rarity", "Loot rarity", RuneGrade.RARE, 1, "+%s%%",
            "Improves barrel gear rarity odds."),
    QUANTITY("quantity", "Loot quantity", RuneGrade.COMMON, 2, "+%s%%",
            "More loot draws when first opening a barrel."),
    BLOCK_REACH("block_reach", "Block reach", RuneGrade.RARE, .1, "+%s blocks",
            "Increases block interaction distance while held."),
    CHAIN_REACH("chain_reach", "Chain reach", RuneGrade.EXOTIC, .25, "+%s blocks",
            "Increases distance between chained barrels.");

    /** {@link #label}, {@link #bonusFormat} and {@link #description} are the English; players see the keys. */
    public final String key, label, bonusFormat, description;
    public final RuneGrade minimum;
    public final double perRank;
    ToolStat(String key, String label, RuneGrade minimum, double perRank, String bonusFormat, String description) {
        this.key = key; this.label = label; this.minimum = minimum; this.perRank = perRank;
        this.bonusFormat = bonusFormat; this.description = description;
    }
    /** The bonus number alone, trailing zeros trimmed: "0.3", "15". */
    public String amount(int ranks) {
        double value = Math.max(0, ranks) * perRank;
        return String.format(Locale.ROOT, "%.2f", value).replaceAll("\\.?0+$", "");
    }
    public Component bonus(int ranks) { return Component.translatable(bonusKey(), amount(ranks)); }
    public Component displayName() { return Component.translatable(nameKey()); }
    public Component describe() { return Component.translatable(descriptionKey()); }
    public String nameKey() { return ToolLang.STAT + key; }
    public String bonusKey() { return nameKey() + ".bonus"; }
    public String descriptionKey() { return nameKey() + ".description"; }
}
