package com.cyberday1.riftfall.skill;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * The Spell Engine PASSIVE spells a book holds through its spell passives, each with the rank it was
 * bought at. Names no engine type, so it tests without one; {@link RiftPassiveSpells} binds it.
 */
public final class PassiveSpells {

    /** One owned spell passive. Its power is the node's bonus at the rank, handed to the spell as a multiplier. */
    public record Grant(PassiveDef def, int rank) {
        public double power() {
            return def.amountAt(rank);
        }
    }

    /** A CUSTOM impact {@link RiftPassiveSpells} registers: the caster's current hit, cut by the passive's power. */
    public static final ResourceLocation GUARD = ResourceLocation.fromNamespaceAndPath("riftfall", "guard");
    /** A CUSTOM impact: the caster's other skills' cooldowns, shortened by the passive's power in seconds. */
    public static final ResourceLocation HASTEN = ResourceLocation.fromNamespaceAndPath("riftfall", "hasten");
    /** A CUSTOM impact: Riftfall mana back to the caster, the passive's power as a fraction of the max. */
    public static final ResourceLocation RESTORE_MANA = ResourceLocation.fromNamespaceAndPath("riftfall", "restore_mana");
    /** A CUSTOM impact: the node's surge terms raised by the passive's power for the surge's seconds. */
    public static final ResourceLocation SURGE = ResourceLocation.fromNamespaceAndPath("riftfall", "surge");

    private PassiveSpells() {
    }

    /** By spell id. Ranks past a node's top count as its top; two nodes on one spell: the lower id. */
    public static Map<ResourceLocation, Grant> of(SkillBook book, Function<String, PassiveDef> passives) {
        Map<ResourceLocation, Grant> granted = new LinkedHashMap<>();
        book.ranks().keySet().stream().sorted().forEach(id -> {
            PassiveDef def = passives.apply(id);
            int rank = book.rank(id);
            if (def != null && def.grantsSpell() && rank >= 1) {
                granted.putIfAbsent(def.spell(), new Grant(def, Math.min(rank, def.node().maxRank())));
            }
        });
        return granted.isEmpty() ? Map.of() : Collections.unmodifiableMap(granted);
    }

    /** Seconds of power as whole ticks, the unit {@link SkillCooldowns} keeps. */
    public static int ticksOf(double seconds) {
        return (int) Math.round(seconds * 20.0);
    }

    /** Whether hasten may move a timer: a castable node's, never a passive's proc timer (Rhythm's own included). */
    public static boolean hastens(SkillTable table, String nodeId) {
        return table.get(nodeId) != null;
    }

    public static @Nullable Grant of(SkillBook book, Function<String, PassiveDef> passives, ResourceLocation spell) {
        return of(book, passives).get(spell);
    }
}
