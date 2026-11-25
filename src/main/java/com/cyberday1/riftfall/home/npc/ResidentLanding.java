package com.cyberday1.riftfall.home.npc;

import com.cyberday1.riftfall.block.marker.NpcRole;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;

import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * What each role's native landing lists. Only live services belong here; a role with none shows an empty
 * landing. Shared by both sides: the client reads the keys, the server runs {@link Entry#state} and {@link Entry#action}.
 */
public final class ResidentLanding {
    /** A tile that opens a service page, or a small button that acts on the spot. */
    public enum Kind { SERVICE, ACTION }

    /** State 0 is available; any other value indexes {@code stateKeys} for why not. */
    @FunctionalInterface public interface State { int of(ServerPlayer player, AbstractContainerMenu menu); }
    /** Runs on the server only after access and state 0 have been rechecked. */
    @FunctionalInterface public interface Action { void run(ServerPlayer player, UUID resident, AbstractContainerMenu menu); }

    public record Entry(int id, Kind kind, String titleKey, String hintKey, String noteKey, List<String> stateKeys,
                        Supplier<Item> icon, State state, Action action) {
        public Entry { stateKeys = List.copyOf(stateKeys); }
        public static Entry service(int id, String title, String hint, String note, Supplier<Item> icon, Action open) {
            return new Entry(id, Kind.SERVICE, title, hint, note, List.of(), icon, (p, m) -> 0, open);
        }
        public static Entry action(int id, String title, List<String> stateKeys, State state, Action action) {
            return new Entry(id, Kind.ACTION, title, null, null, stateKeys, () -> null, state, action);
        }
        public String stateKey(int state) { return stateKeys.isEmpty() ? null : stateKeys.get(Math.clamp(state, 0, stateKeys.size() - 1)); }
    }

    public static final int MAX_ENTRIES = 8;

    private ResidentLanding() {}

    public static List<Entry> entries(NpcRole role) {
        var list = switch (role) {
            case BLACKSMITH -> com.cyberday1.riftfall.tool.BlacksmithMenus.LANDING;
            case JEWELER -> com.cyberday1.riftfall.home.npc.jeweler.JewelerMenus.LANDING;
            case QUARRY_SPECIALIST -> com.cyberday1.riftfall.home.npc.quarry.QuarryMenus.LANDING;
            case RIFT_CARTOGRAPHER -> com.cyberday1.riftfall.home.npc.cartographer.CartographerMenus.LANDING;
            default -> List.<Entry>of();
        };
        if (list.size() > MAX_ENTRIES) throw new IllegalStateException("Too many landing entries for " + role.id());
        return list;
    }

    /** The home's rank, XP and best clear for this role; empty outside a home. Read every sync, mutates nothing. */
    public static java.util.Optional<int[]> rank(ServerPlayer player, NpcRole role) {
        var owner = com.cyberday1.riftfall.home.npc.depth.ResidentDepth.homeOf(player);
        if (owner == null) return java.util.Optional.empty();
        var home = com.cyberday1.riftfall.home.npc.depth.HomeResidentDepth.of(player.server).peek(owner).orElse(null);
        if (home == null || !home.roles().containsKey(role)) return java.util.Optional.of(new int[]{0, 0, home == null ? 0 : home.bestCleared()});
        var r = home.roles().get(role);
        return java.util.Optional.of(new int[]{r.rank().ordinal(), r.xp(), home.bestCleared()});
    }
}
