package com.cyberday1.riftfall.skill;

import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The melee hits a paid cast has bought. A melee skill's hit arrives later, on the client's packet, so
 * the cast that paid leaves its strikes here and each reported hit spends one. A hit with none left
 * pays Riftfall's price on its own. Names no mod type.
 */
public final class PaidHits {

    /** How long a paid cast's strikes stay claimable: 5 s, well past any attack's own duration. */
    public static final long WINDOW_TICKS = 100L;

    public static final PaidHits MELEE = new PaidHits();

    private record Bought(int hits, long until) {
    }

    private final Map<UUID, Map<ResourceLocation, Bought>> bought = new ConcurrentHashMap<>();

    /** A cast of {@code ability} was paid at {@code now}; it replaces whatever an earlier one left. */
    public void paid(UUID player, ResourceLocation ability, int hits, long now) {
        if (hits > 0) {
            bought.computeIfAbsent(player, p -> new HashMap<>()).put(ability, new Bought(hits, now + WINDOW_TICKS));
        }
    }

    /** Spends one paid strike of {@code ability}, or answers false when none is left in the window. */
    public boolean spend(UUID player, ResourceLocation ability, long now) {
        Map<ResourceLocation, Bought> mine = bought.get(player);
        Bought left = mine == null ? null : mine.get(ability);
        if (left == null) {
            return false;
        }
        if (now > left.until() || left.hits() <= 0) {
            mine.remove(ability);
            return false;
        }
        if (left.hits() == 1) {
            mine.remove(ability);
        } else {
            mine.put(ability, new Bought(left.hits() - 1, left.until()));
        }
        return true;
    }

    public void forget(UUID player) {
        bought.remove(player);
    }
}
