package com.cyberday1.riftfall.home;

import com.cyberday1.riftfall.RiftfallCore;
import com.cyberday1.riftfall.rift.RiftParties;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.neoforged.fml.ModList;
import java.util.*;

/** One live permission rule for commands, game events and OpenPAC claim checks. */
public final class HomeAccess {
    private HomeAccess() {}
    public static boolean teamsAvailable() {
        return ModList.get() != null && ModList.get().isLoaded("openpartiesandclaims");
    }

    public static UUID homeOwner(MinecraftServer server, UUID player) {
        return teamsAvailable() ? HomeTeams.homeOwner(server, player) : player;
    }

    public static UUID owner(ResourceLocation dimension) {
        if (dimension == null || !dimension.getNamespace().equals(RiftfallCore.MODID)
                || !dimension.getPath().startsWith(ShatteredHomeLevels.PREFIX)) return null;
        try { return UUID.fromString(dimension.getPath().substring(ShatteredHomeLevels.PREFIX.length())); }
        catch (IllegalArgumentException ignored) { return null; }
    }

    public static boolean canInteract(MinecraftServer server, UUID owner, UUID visitor) {
        if (owner == null) return true;
        if (visitor == null) return false;
        if (owner.equals(visitor)) return true;
        Set<UUID> residents = teamsAvailable() ? HomeTeams.residents(server, owner) : Set.of(owner);
        if (residents.contains(visitor) || HomeAccessData.of(server).allied(owner, visitor)) return true;
        if (teamsAvailable() && HomeTeams.isAllied(server, owner, visitor)) return true;
        var party = RiftParties.of(server).of(visitor);
        return party != null && party.members().stream().anyMatch(residents::contains);
    }
}
