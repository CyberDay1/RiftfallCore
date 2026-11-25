package com.cyberday1.riftfall.mob;

import com.cyberday1.riftfall.RiftfallCore;
import com.cyberday1.riftfall.theme.RiftTheme;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;

import java.util.Optional;
import java.util.List;

/**
 * What a rift's spawners are allowed to make: a universal roster plus one per theme.
 *
 * <p>
 * Both are entity-type tags rather than anything this mod writes down, which is the only shape that
 * works for a mod that is modpack glue. Riftfall Core has no hard dependency on anything, so the
 * mobs worth putting in a rift are exactly the ones it cannot name: a pack that ships one bestiary
 * wants a different roster from a pack that ships another, and a tag lets each of them say so in a
 * JSON file with no code and no compile-time reference. Tags also drop unknown ids silently, so a
 * roster naming a mod the pack later removes degrades to a shorter roster rather than to a crash.
 *
 * <h2>Universal, and flavour</h2>
 * {@link #UNIVERSAL} is the roster every rift draws from whatever it looks like &mdash; the things
 * that belong in a rift because it is a rift. Beside it, each theme has a roster of its own, and a
 * spawner in a themed rift draws from that one {@value #FLAVOUR_SHARE_PERCENT} times in a hundred.
 *
 * <p>
 * The theme's tag id is derived from the theme's id rather than declared in the theme file:
 * {@code riftfall:cave} is served by {@code riftfall:rift_mobs/cave}, in the theme's own namespace,
 * so a pack that adds a theme adds the matching roster beside it and nothing has to be registered.
 * A theme with no roster file, or an empty one, is simply a rift that draws entirely from the
 * universal list.
 */
public final class RiftMobs {

    /** The roster every rift draws from, whatever it is themed as. */
    public static final TagKey<EntityType<?>> UNIVERSAL =
            TagKey.create(Registries.ENTITY_TYPE, RiftfallCore.id("rift_mobs"));

    /**
     * How often a spawner in a themed rift reaches for the theme's own roster rather than the
     * universal one.
     *
     * <p>
     * Well under half on purpose. The theme is meant to be the accent on a rift rather than the whole
     * of it: past a half, two rifts of different themes stop having anything in common and the
     * universal roster stops meaning what its name says.
     */
    static final int FLAVOUR_SHARE_PERCENT = 40;

    private RiftMobs() {
    }

    /** The roster belonging to one theme, which may be a tag nobody has written a file for. */
    public static TagKey<EntityType<?>> forTheme(ResourceKey<RiftTheme> theme) {
        ResourceLocation id = theme.location();
        return TagKey.create(Registries.ENTITY_TYPE,
                ResourceLocation.fromNamespaceAndPath(id.getNamespace(), "rift_mobs/" + id.getPath()));
    }

    /**
     * One entity type for a rift of this theme, or empty if neither roster names anything.
     *
     * <p>
     * Empty is a real answer rather than a failure: a pack that has not filled the tags in gets a
     * rift with no mobs in it, which is what it asked for, and the caller says so once rather than
     * this throwing once per spawner.
     */
    public static Optional<EntityType<?>> pick(ResourceKey<RiftTheme> theme, RandomSource random) {
        return pick(theme, random, true);
    }

    public static Optional<EntityType<?>> pick(ResourceKey<RiftTheme> theme, RandomSource random, boolean allowElite) {
        List<EntityType<?>> universal = eligible(UNIVERSAL, allowElite);
        List<EntityType<?>> flavour = eligible(forTheme(theme), allowElite);
        boolean useFlavour = !flavour.isEmpty() && (universal.isEmpty()
                || random.nextInt(100) < FLAVOUR_SHARE_PERCENT);
        return weightedPick(useFlavour ? flavour : universal, random);
    }

    static Optional<EntityType<?>> weightedPick(List<EntityType<?>> candidates, RandomSource random) {
        int total = candidates.stream().mapToInt(RiftMobBalance::weight).sum();
        if (total == 0) return Optional.empty();
        int roll = random.nextInt(total);
        for (var type : candidates) {
            roll -= RiftMobBalance.weight(type);
            if (roll < 0) return Optional.of(type);
        }
        throw new IllegalStateException("Mob weights changed during selection");
    }

    private static List<EntityType<?>> eligible(TagKey<EntityType<?>> tag, boolean allowElite) {
        var roster = roster(tag);
        return roster == null ? List.of() : roster.stream().map(Holder::value)
                .filter(type -> allowElite || !RiftMobBalance.elite(type)).toList();
    }

    /** The tag's contents, or null when the tag is absent or empty. Empty and absent are the same here. */
    private static HolderSet.Named<EntityType<?>> roster(TagKey<EntityType<?>> tag) {
        return BuiltInRegistries.ENTITY_TYPE.getTag(tag).filter(set -> set.size() > 0).orElse(null);
    }
}
