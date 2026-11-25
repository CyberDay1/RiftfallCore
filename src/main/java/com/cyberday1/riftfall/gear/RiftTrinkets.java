package com.cyberday1.riftfall.gear;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;

/**
 * The guard in front of {@link TrinketCategory}, on the pattern {@link RiftLootRarity} uses.
 *
 * <p>
 * Three mods rather than one, and all three are load-bearing: Apotheosis owns the loot category
 * registry, Apothic Attributes owns the equipment-slot registries a category is built from, and
 * Curios owns the slots themselves and the event a worn trinket's attributes travel on. Any one
 * missing and there is no Tr column to build, so the class that names them is never loaded.
 */
public final class RiftTrinkets {

    private RiftTrinkets() {
    }

    public static void register(IEventBus modBus) {
        ModList mods = ModList.get();
        RelicStats.register(modBus);
        RelicSignatures.register();
        RelicJump.register();
        RelicWallCling.register();
        RelicSignatureStats.register();
        RelicSignatureProcs.register();
        RelicSignatureCosts.register();
        RelicStackSync.register();
        if (mods.isLoaded("curios")) {
            RelicCurios.register();
            RelicSignatureCurios.register();
        }
        if (mods.isLoaded("apotheosis") && mods.isLoaded("apothic_attributes")
                && mods.isLoaded("curios")) {
            TrinketCategory.register(modBus);
        }
    }
}
