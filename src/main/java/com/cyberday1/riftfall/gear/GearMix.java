package com.cyberday1.riftfall.gear;

import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * How a gear draw splits between armour, weapons, accessories and tools, and how an accessory draw splits between
 * kinds of accessory. Pure numbers, no Minecraft.
 *
 * <p>
 * A draw picks a group by {@link #weight(Group)}, then a bucket uniformly inside the group; an accessory then picks a
 * kind by {@link #kindWeight(String)} and an item uniformly inside the kind. Weights are per kind, never per item
 * count, so the jewellery mods' hundreds of rings cannot crowd the other kinds out.
 */
public final class GearMix {

    public enum Group { ARMOUR, WEAPON, ACCESSORY, TOOL }

    /** Relative weights. Armour and weapons dominate; accessories are about 4.6% of gear draws. */
    private static final Map<Group, Integer> WEIGHTS = new EnumMap<>(Map.of(
            Group.ARMOUR, 37,
            Group.WEAPON, 46,
            Group.ACCESSORY, 4,
            Group.TOOL, 0));

    /**
     * The accessory kinds, in the order an item with several slot tags is filed under. Read off the curios and
     * accessories slot tags; anything else (a generic {@code curio} tag, a mod's own slot) is {@link #OTHER}.
     */
    public static final List<String> KINDS = List.of(
            "ring", "necklace", "belt", "charm", "bracelet", "hands", "back", "head", "body", "feet");

    public static final String OTHER = "other";

    /**
     * Every {@code riftfall:relic} item, whatever its slot tag, so the 86 share one kind's weight. Not in
     * {@link #KINDS}: it is decided by the item's category, not its slot tags ({@link GearPool#curioKind}).
     */
    public static final String RELIC = "relic";

    /** Every kind weighs the same; edit here to tilt it (assumed default). */
    private static final int KIND_WEIGHT = 10;

    /**
     * Ruled 2026-11-20: relics about a quarter of curio draws. A share, not a weight, because the pool's
     * kind count moves with the pack (five beside relics on 11-20, nine on 10-11).
     */
    static final double RELIC_SHARE = 0.25D;

    private GearMix() {
    }

    public static int weight(Group group) {
        return WEIGHTS.getOrDefault(group, 0);
    }

    public static int kindWeight(String kind) {
        return KIND_WEIGHT;
    }

    /** A kind's weight among {@code present}: the relic kind takes {@link #RELIC_SHARE} of the total. */
    public static int kindWeight(String kind, Collection<String> present) {
        if (!RELIC.equals(kind)) return KIND_WEIGHT;
        long others = present.stream().filter(k -> !RELIC.equals(k)).count();
        return Math.max(1, (int) Math.round(KIND_WEIGHT * others * RELIC_SHARE / (1.0D - RELIC_SHARE)));
    }

    /** The summed weight of the groups present. */
    public static int total(Collection<Group> present) {
        return present.stream().mapToInt(GearMix::weight).sum();
    }

    /** The group {@code roll} lands on, {@code roll} in {@code [0, total(present))}, in declaration order. */
    public static Group pick(Collection<Group> present, int roll) {
        int remaining = roll;
        Group last = null;
        for (Group group : Group.values()) {
            if (!present.contains(group) || weight(group) <= 0) {
                continue;
            }
            last = group;
            remaining -= weight(group);
            if (remaining < 0) {
                return group;
            }
        }
        return last;
    }

    public static int kindTotal(Collection<String> present) {
        return present.stream().mapToInt(kind -> kindWeight(kind, present)).sum();
    }

    /** The kind {@code roll} lands on among {@code present}, in sorted order. */
    public static String pickKind(Collection<String> present, int roll) {
        int remaining = roll;
        String last = null;
        for (String kind : new java.util.TreeSet<>(present)) {
            last = kind;
            remaining -= kindWeight(kind, present);
            if (remaining < 0) {
                return kind;
            }
        }
        return last;
    }

    /** The group a pool bucket belongs to. */
    public static Group group(GearPool.Slot slot) {
        return switch (slot) {
            case HEAD, CHEST, LEGS, FEET -> Group.ARMOUR;
            case CURIO -> Group.ACCESSORY;
            case TOOL -> Group.TOOL;
            default -> Group.WEAPON;
        };
    }

    /**
     * The kind an accessory is filed under, from its slot-tag paths ({@code rings}, {@code necklace}, ...). Plurals
     * and the accessories mod's spellings fold onto one kind; the first kind in {@link #KINDS} order wins.
     */
    public static String kind(Collection<String> slotTagPaths) {
        String best = OTHER;
        int bestIndex = Integer.MAX_VALUE;
        for (String path : slotTagPaths) {
            String kind = normalise(path);
            int index = KINDS.indexOf(kind);
            if (index >= 0 && index < bestIndex) {
                best = kind;
                bestIndex = index;
            }
        }
        return best;
    }

    static String normalise(String path) {
        String p = path.toLowerCase(java.util.Locale.ROOT);
        int slash = p.lastIndexOf('/');
        if (slash >= 0) {
            p = p.substring(slash + 1);
        }
        return switch (p) {
            case "ring", "rings" -> "ring";
            case "necklace", "necklaces", "amulet", "face" -> "necklace";
            case "belt", "belts", "waist" -> "belt";
            case "charm", "charms", "talisman", "trinket" -> "charm";
            case "bracelet", "bracelets", "wrist" -> "bracelet";
            case "hands", "hand", "gloves" -> "hands";
            case "back", "cape", "cloak" -> "back";
            case "head", "hat" -> "head";
            case "body", "chest" -> "body";
            case "feet", "shoes", "anklet" -> "feet";
            default -> OTHER;
        };
    }
}
