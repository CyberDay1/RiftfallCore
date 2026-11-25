package com.cyberday1.riftfall;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

/**
 * The handful of rift rules an operator is allowed to change.
 *
 * <h2>Why there is so little here</h2>
 * Almost nothing in this mod is a setting, and that is on purpose. The numbers with reasoning
 * attached to them &mdash; the fifth of durability a death spends, the thirty seconds a rift is
 * allowed to stand empty before it is given up on, how far a dungeon grows per tier &mdash; are
 * decisions, and a decision moved into a config file stops being one. The argument for the value
 * goes with it: the next reader finds a number an operator picked rather than a number somebody
 * defended, and there is nothing left to reason from when it needs changing again.
 *
 * <p>
 * What belongs here is the other kind of question, the kind with no defensible answer because it
 * depends on the pack: how harsh a dungeon a server wants to run. Those are settings because two
 * servers can be right about them at once.
 *
 * <h2>Server, not common or client</h2>
 * Every rule this file holds is enforced on the server, decided while a player is standing in a
 * dungeon, and has to be the same for everybody in that dungeon. {@code SERVER} is the type that says
 * so, and it says it in the only way that survives contact with a multiplayer game: the file is sent
 * to every client during the configuration phase and the server's values are the ones in force, so a
 * player editing their own copy changes nothing about the rift they are standing in.
 *
 * <p>
 * It is read from the instance's {@code config} folder, and a copy dropped in a world's
 * {@code serverconfig} folder overrides it for that world alone. So the default is one answer for the
 * installation, and a pack that wants a particular save to run harsher rules than the rest can say so
 * without touching anything else.
 *
 * <p>
 * The type also settles the loading order, which matters more than it looks.
 * {@code ServerLifecycleHooks.handleServerAboutToStart} loads the server configs and the very next
 * thing the server does is load the level, so every value here is available before there is a world
 * for anybody to be standing in &mdash; there is no window in which a rule is read back as its default
 * because the file has not been opened yet.
 */
public final class RiftfallConfig {

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    /**
     * Whether being defeated in a rift costs you what the dungeon gave you.
     *
     * <p>
     * On, dying returns the gear you carried in, worn, and keeps nothing you picked up inside. Off,
     * everything you were carrying comes back worn, which is how a rift behaved before this option
     * existed.
     *
     * <p>
     * The rule it turns on is the one that gives walking out a job. With loot destroyed on defeat, a
     * dungeon's reward is only yours once you have carried it through the door, and the decision to
     * push one room further is a real one. With it off, the only thing a party risks is durability,
     * and there is never a reason to leave early.
     */
    public static final ModConfigSpec.BooleanValue DEFEAT_DESTROYS_LOOT = BUILDER
            .comment("Whether being defeated in a rift destroys what the dungeon gave you.",
                    "On, a death returns the gear you carried in, worn, and keeps nothing you found inside.",
                    "Off, everything you were carrying comes back worn.")
            .define("defeatDestroysLoot", true);

    /**
     * How many players are allowed in one party.
     *
     * <p>
     * Four is the size a dungeon is being tuned for, and it is here rather than in the code because
     * it is the definition of a settings question: a pack running four-player dungeons and a pack
     * running eight-player ones are both right, and neither of them is wrong about the other.
     *
     * <p>
     * One and sixteen are the ends of the range rather than the ends of what is possible, and they
     * are meant as ends. One is a server that wants rifts run alone, which is a coherent thing to
     * want; sixteen is a raid. Beyond that the number stops describing a party.
     *
     * <p>
     * Lowering this under a party that is already bigger than it does not break the party up. The cap
     * is checked when somebody is let in, so an oversized party keeps everybody it has and takes on
     * nobody new. The alternative would delete a player from a group they had already joined, which
     * is not something a config file edit should be able to do.
     */
    public static final ModConfigSpec.IntValue MAX_PARTY_SIZE = BUILDER
            .comment("How many players may be in one party.",
                    "Lowering this leaves parties that are already larger alone; they simply take on nobody new.")
            .defineInRange("maxPartySize", 4, 1, 16);

    /**
     * How long a party may spend in one rift, in minutes.
     *
     * <p>
     * Here because the guidelines put it here: the time limit is the one rule of the dungeon written
     * down as configurable, and forty-five is written down as its default. So this is a setting by
     * decision rather than by the usual test, and the reasoning for the number belongs to whoever set
     * it rather than to this file.
     *
     * <p>
     * Minutes rather than ticks because minutes are the unit the rule is stated in, and it is
     * converted once where it is used. Zero means no limit at all, which is a coherent thing to ask
     * for and is the off switch for the whole clock.
     */
    public static final ModConfigSpec.IntValue RIFT_TIME_LIMIT_MINUTES = BUILDER
            .comment("How long a party may spend in one rift, in minutes.",
                    "When it runs out everybody still inside is put back on their own doorstep and",
                    "charged the death penalty: worn gear, and a quarter of what their kills were worth.",
                    "0 turns the limit off entirely.")
            .defineInRange("riftTimeLimitMinutes", 45, 0, 1440);

    /** The wait between a party member tripping the boss gate and the party being pulled in. */
    public static final ModConfigSpec.IntValue BOSS_GATE_DELAY_SECONDS = BUILDER
            .comment("Seconds between the first party member entering the boss room and the gate closing.",
                    "Every living party member still in the rift is then moved inside.",
                    "0 closes the gate at once and still moves the party in.")
            .defineInRange("bossGateDelaySeconds", 5, 0, 60);

    /**
     * How often a chest marker in an authored room becomes a barrel.
     *
     * <p>
     * The rooms carry 3,335 of these between them and a tier-1 rift stamps a few hundred rooms, so
     * this is the difference between a dungeon with a handful of finds in it and a warehouse. Two per
     * cent, then four, both walked as too little to be worth searching for; thirty-five is what came
     * back from playing it, and is the number the dungeon is now tuned around. It stays a setting
     * because how much a server wants a rift to pay is exactly the question no one answer is right
     * for.
     *
     * <p>
     * A marker that loses this roll becomes nothing at all, not a chest and not a hole.
     */
    public static final ModConfigSpec.DoubleValue BARREL_SPAWN_CHANCE = BUILDER
            .comment("How likely each chest marker in a rift room is to become a loot barrel.",
                    "0 turns loot barrels off entirely; 1 makes every marker one.")
            .defineInRange("barrelSpawnChance", 0.35D, 0.0D, 1.0D);

    /**
     * The four grades of barrel, as relative weights.
     *
     * <p>
     * Weights rather than chances so that the four can be changed one at a time without a fifth
     * number having to be kept summing to one. The defaults read 250 / 125 / 50 / 10, which is
     * roughly 57 / 29 / 12 / 2 percent &mdash; gold rare enough that finding one is the run's story.
     *
     * <p>
     * All four at zero means no barrels, which is a coherent thing to ask for and is handled as
     * such: the markers resolve to nothing rather than the draw failing.
     */
    public static final ModConfigSpec.IntValue BARREL_WEIGHT_SHABBY = weight("barrelWeightShabby", 250,
            "How often a barrel that spawns is the shabby one, relative to the other three.");

    public static final ModConfigSpec.IntValue BARREL_WEIGHT_WOOD = weight("barrelWeightWood", 125,
            "How often a barrel that spawns is the plain wooden one, relative to the other three.");

    public static final ModConfigSpec.IntValue BARREL_WEIGHT_IRON = weight("barrelWeightIron", 50,
            "How often a barrel that spawns is the banded one, relative to the other three.");

    public static final ModConfigSpec.IntValue BARREL_WEIGHT_GOLD = weight("barrelWeightGold", 10,
            "How often a barrel that spawns is the gilded one, relative to the other three.");

    /** Rift loot volume: scales each barrel share's draw count. Shards, gems and the boss reward are not scaled. */
    public static final ModConfigSpec.DoubleValue BARREL_LOOT_SCALE = BUILDER
            .comment("How much loot each rift barrel gives, as a multiple of the designed draw count (about 5 a share).",
                    "1.0 is the full amount, 0.75 a quarter less, 0 only the shard and gem roll.",
                    "Shards, gems and the boss reward are not scaled. Scavenging still multiplies on top.")
            .defineInRange("barrelLootScale", 0.75D, 0.0D, 4.0D);

    /** Gear volume: the chance a barrel draw that lands on gear, a gun or an attachment pays out. Crystals and supplies keep their rate. */
    public static final ModConfigSpec.DoubleValue BARREL_GEAR_SCALE = BUILDER
            .comment("The chance that a rift barrel draw which lands on gear, a gun or an attachment pays it out.",
                    "1.0 pays every one, 0.5 half, 0 none. A draw that misses yields nothing; it is not rerolled,",
                    "so crystals, supplies, shards, gems and the boss reward are not affected.")
            .defineInRange("barrelGearScale", 0.5D, 0.0D, 1.0D);

    private static ModConfigSpec.IntValue weight(String name, int fallback, String comment) {
        return BUILDER.comment(comment, "Set every barrel weight to 0 to place no barrels at all.")
                .defineInRange(name, fallback, 0, Integer.MAX_VALUE);
    }

    /**
     * How much threat a caster earns against a player doing nothing but damage.
     *
     * <p>
     * A ratio of rates, not a weight: 1.0 means a player casting flat out and a player swinging flat
     * out pull a boss equally hard. {@code SkillThreat#weight} turns it into threat per effect-second
     * using two measured anchors, and the reason it is a ratio here is that a ratio is the thing
     * anybody can have an opinion about.
     *
     * <p>
     * It is a setting for this file's stated reason &mdash; how much a server wants casting to be a
     * way of holding a boss is a question two packs can answer differently and both be right. 0
     * turns the leg off without removing it.
     *
     * <p>
     * ⛔ 2.0 rather than 1.0, and 1.0 is worth avoiding deliberately: it is the crossover between the
     * two legs, where the caster and the dealers sit closest, so it maximises both how unpredictable
     * the holder is and how often the boss changes its mind. The useful band is 1.5 to 2.5 &mdash;
     * the effect saturates at about 1.5 and there is nothing above 2.5 to buy.
     */
    public static final ModConfigSpec.DoubleValue SKILL_THREAT_RATIO = BUILDER
            .comment("How hard casting skill effects pulls a rift boss, against dealing damage.",
                    "1.0 means a caster and a dealer working flat out pull equally hard.",
                    "0 turns the skill leg off. Avoid exactly 1.0: it is the crossover between the two",
                    "legs, where the boss changes its mind most often. 1.5 to 2.5 is the useful band.")
            .defineInRange("skillThreatRatio", 2.0D, 0.0D, 10.0D);

    /**
     * The Shifter's forms as Woodwalkers models. Pack-dependent by nature (which mobs a pack ships), so a
     * setting. Without Woodwalkers, or with this off, a form keeps the size-and-dust stance.
     */
    public static final ModConfigSpec.BooleanValue SHIFTER_SHAPES = BUILDER
            .comment("Shifter forms wear a Woodwalkers model while the aspect lasts (needs Woodwalkers).",
                    "The model is visual only: the aspect's stats stay Riftfall's.")
            .define("shifterShapes", true);

    /** First registered id wins; the vanilla mob last so a pack without the modded one still has a model. */
    public static final ModConfigSpec.ConfigValue<List<? extends String>> SHIFTER_BEAR_SHAPES = BUILDER
            .comment("Bear Aspect's model: the first registered entity id in the list.")
            .defineListAllowEmpty("shifterBearShapes",
                    List.of("alexsmobs:grizzly_bear", "naturalist:bear", "minecraft:polar_bear"),
                    () -> "minecraft:polar_bear", o -> o instanceof String);

    public static final ModConfigSpec.ConfigValue<List<? extends String>> SHIFTER_TIGER_SHAPES = BUILDER
            .comment("Tiger Aspect's model: the first registered entity id in the list.")
            .defineListAllowEmpty("shifterTigerShapes",
                    List.of("alexsmobs:tiger", "naturalist:tiger", "minecraft:ocelot"),
                    () -> "minecraft:ocelot", o -> o instanceof String);

    public static final ModConfigSpec.BooleanValue SHIFTER_SHAPE_DUST = BUILDER
            .comment("Whether a form wearing a model also sheds its coloured dust.")
            .define("shifterShapeDust", false);

    /** Each player's level beside their name in the tab list, as a scoreboard objective. */
    public static final ModConfigSpec.BooleanValue TAB_LIST_LEVEL = BUILDER
            .comment("Show each player's Riftfall level beside their name in the tab list.",
                    "Off removes the riftfall_level objective at the next server start.")
            .define("tabListLevel", true);

    /** A "◆ Rift T4" suffix on a player's tab list name while they are in a rift. */
    public static final ModConfigSpec.BooleanValue TAB_LIST_RIFT = BUILDER
            .comment("Add the rift's tier after a player's name in the tab list while they are inside a rift.",
                    "Read live: a change shows the next time that player's tab name refreshes.")
            .define("tabListRift", true);

    /** Built last, because the builder has to have been told about everything above it first. */
    public static final ModConfigSpec SPEC = BUILDER.build();

    private RiftfallConfig() {
    }
}
