package com.cyberday1.riftfall.rift;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Function;

/**
 * What one entrant's run came to, built once when the run settles and handed to every layer through
 * {@link RiftSettledEvent}. Server facts only: nothing here comes from a client.
 */
public record RiftRunReport(UUID player, UUID home, int tier, Optional<ResourceLocation> theme, RiftRun.Verdict verdict,
                            boolean died, boolean escaped, boolean expired, int kills, Map<ResourceLocation, Integer> typeKills,
                            List<ResourceLocation> bosses, int elapsedTicks, Set<UUID> party, Set<UUID> homeMates,
                            boolean partyDied) {
    public RiftRunReport {
        theme = Objects.requireNonNull(theme);
        typeKills = Map.copyOf(typeKills);
        bosses = List.copyOf(bosses);
        party = Set.copyOf(party);
        homeMates = Set.copyOf(homeMates);
    }

    public boolean cleared() { return verdict == RiftRun.Verdict.CLEARED; }

    /** Kill credits whose entity type is in {@code tag}. */
    public int kills(TagKey<EntityType<?>> tag) {
        int total = 0;
        for (var entry : typeKills.entrySet()) {
            var type = BuiltInRegistries.ENTITY_TYPE.getOptional(entry.getKey());
            if (type.isPresent() && type.get().is(tag)) total += entry.getValue();
        }
        return total;
    }

    public int bosses(@Nullable TagKey<EntityType<?>> tag) {
        if (tag == null) return bosses.size();
        int total = 0;
        for (var id : bosses) {
            var type = BuiltInRegistries.ENTITY_TYPE.getOptional(id);
            if (type.isPresent() && type.get().is(tag)) total++;
        }
        return total;
    }

    /** One report per entrant. {@code homeOf} names each player's home (the team home, or themselves). */
    public static List<RiftRunReport> of(RiftRun run, RiftRun.Verdict verdict, Function<UUID, UUID> homeOf) {
        var party = Set.copyOf(run.entrants());
        var homes = new HashMap<UUID, UUID>();
        for (var player : party) homes.put(player, homeOf.apply(player));
        boolean partyDied = !run.died().isEmpty();
        var reports = new ArrayList<RiftRunReport>();
        for (var player : party) {
            var home = homes.get(player);
            var mates = new HashSet<UUID>();
            homes.forEach((other, otherHome) -> { if (otherHome.equals(home)) mates.add(other); });
            reports.add(new RiftRunReport(player, home, run.tier(), Optional.ofNullable(run.theme()), verdict,
                    run.died().contains(player), run.escaped().contains(player), run.expired(), run.kills(player),
                    run.typeKills(player), run.bosses(player), run.elapsed(), party, mates, partyDied));
        }
        reports.sort(Comparator.comparing(RiftRunReport::player));
        return List.copyOf(reports);
    }
}
