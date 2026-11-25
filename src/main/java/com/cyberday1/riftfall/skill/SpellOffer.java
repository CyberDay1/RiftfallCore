package com.cyberday1.riftfall.skill;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * A Spell Engine offer with every ungranted ability taken out of all five places the engine reads
 * one from, and each passive kept once. Generic over the engine's types so it tests without the
 * engine; {@link RiftSpellGate} binds the real ones. Returns the same instance when nothing is removed.
 */
final class SpellOffer {

    /** {@code SpellContainerSource.Result} as five reads and one rebuild. */
    interface Shape<R, C, H, S> {
        C active(R result);

        List<H> actives(R result);

        List<H> passives(R result);

        List<H> modifiers(R result);

        /** What {@code getFirstSourceOfSpell} walks: the container check before every cast. */
        List<S> sources(R result);

        @Nullable ResourceLocation id(H spell);

        List<String> ids(C container);

        C withIds(C container, List<String> ids);

        C container(S source);

        S withContainer(S source, C container);

        R rebuild(C active, List<H> actives, List<H> passives, List<H> modifiers, List<S> sources);
    }

    private SpellOffer() {
    }

    static <R, C, H, S> R keep(R result, Shape<R, C, H, S> shape, Predicate<ResourceLocation> granted) {
        C active = container(shape.active(result), shape, granted);
        List<H> actives = spells(shape.actives(result), shape, granted);
        List<H> passives = distinct(spells(shape.passives(result), shape, granted), shape);
        List<H> modifiers = spells(shape.modifiers(result), shape, granted);
        List<S> sources = shape.sources(result);
        List<S> kept = new ArrayList<>(sources.size());
        boolean changed = active != shape.active(result) || actives != shape.actives(result)
                || passives != shape.passives(result) || modifiers != shape.modifiers(result);
        for (S source : sources) {
            C held = shape.container(source);
            C cut = container(held, shape, granted);
            kept.add(cut == held ? source : shape.withContainer(source, cut));
            changed |= cut != held;
        }
        return changed ? shape.rebuild(active, actives, passives, modifiers, kept) : result;
    }

    /** Unparseable ids are dropped: an id the gate cannot read is one it cannot have granted. */
    static List<String> ids(List<String> ids, Predicate<ResourceLocation> granted) {
        List<String> kept = new ArrayList<>(ids.size());
        for (String id : ids) {
            ResourceLocation parsed = id == null ? null : ResourceLocation.tryParse(id);
            if (parsed != null && granted.test(parsed)) {
                kept.add(id);
            }
        }
        return kept;
    }

    /** A passive two sources carry would fire twice per trigger; it is kept once, first source first. */
    private static <R, C, H, S> List<H> distinct(List<H> spells, Shape<R, C, H, S> shape) {
        List<H> kept = new ArrayList<>(spells.size());
        Set<ResourceLocation> seen = new HashSet<>();
        for (H spell : spells) {
            if (seen.add(shape.id(spell))) {
                kept.add(spell);
            }
        }
        return kept.size() == spells.size() ? spells : kept;
    }

    private static <R, C, H, S> C container(C container, Shape<R, C, H, S> shape, Predicate<ResourceLocation> granted) {
        if (container == null) {
            return null;
        }
        List<String> held = shape.ids(container);
        if (held == null) {
            return container;
        }
        List<String> kept = ids(held, granted);
        return kept.size() == held.size() ? container : shape.withIds(container, kept);
    }

    private static <R, C, H, S> List<H> spells(List<H> spells, Shape<R, C, H, S> shape, Predicate<ResourceLocation> granted) {
        List<H> kept = new ArrayList<>(spells.size());
        for (H spell : spells) {
            ResourceLocation id = shape.id(spell);
            if (id != null && granted.test(id)) {
                kept.add(spell);
            }
        }
        return kept.size() == spells.size() ? spells : kept;
    }
}
