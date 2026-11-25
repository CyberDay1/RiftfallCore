package com.cyberday1.riftfall.compat;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModList;
import net.p3pp3rf1y.sophisticatedbackpacks.backpack.wrapper.BackpackWrapper;
import net.p3pp3rf1y.sophisticatedbackpacks.backpack.wrapper.IBackpackWrapper;
import net.p3pp3rf1y.sophisticatedcore.upgrades.UpgradeHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Puts an upgrade into a Sophisticated Backpack's own upgrade slots, through the mod's wrapper, so it is
 * stored where the mod keeps it (its contents storage, keyed by the stack's contents UUID). Names
 * Sophisticated types; every entry point checks {@link #loaded()} first.
 */
public final class BackpackUpgrades {

    public static final String MOD = "sophisticatedbackpacks";

    private BackpackUpgrades() {
    }

    public static boolean loaded() {
        return ModList.get() != null && ModList.get().isLoaded(MOD);
    }

    /** True when the upgrade went into the first free slot that accepts it; the caller hands it over loose otherwise. */
    public static boolean install(ItemStack backpack, ItemStack upgrade) {
        if (!loaded() || !BuiltInRegistries.ITEM.getKey(backpack.getItem()).getNamespace().equals(MOD)) {
            return false;
        }
        return Access.install(backpack, upgrade);
    }

    /** The ids in the backpack's upgrade slots, read back through a fresh wrapper. Empty without the mod. */
    public static List<String> installed(ItemStack backpack) {
        return loaded() && BuiltInRegistries.ITEM.getKey(backpack.getItem()).getNamespace().equals(MOD)
                ? Access.installed(backpack) : List.of();
    }

    /** Processes a copy through the same active upgrades as a ground pickup; returns only unaccepted loot. */
    public static ItemStack pickup(ServerPlayer player, ItemStack stack) {
        ItemStack copy = stack.copy();
        return copy.isEmpty() || !loaded() ? copy : Access.pickup(player, copy);
    }

    /** The class that names Sophisticated types, so loading the outer one never needs them. */
    private static final class Access {

        static ItemStack pickup(ServerPlayer player, ItemStack stack) {
            var remaining = new java.util.concurrent.atomic.AtomicReference<>(stack);
            // Use the mod's inventory provider so worn/Curios slots, enabled upgrades,
            // filters, and linked backpacks follow exactly the native pickup rules.
            net.p3pp3rf1y.sophisticatedbackpacks.util.PlayerInventoryProvider.get().runOnBackpacks(player,
                    (backpack, handler, identifier, slot) -> {
                        var wrapper = net.p3pp3rf1y.sophisticatedbackpacks.backpack.wrapper.BackpackLinkedStorageResolver
                                .resolveForGlobalUpgradeProcessing(player.level(), backpack);
                        remaining.set(net.p3pp3rf1y.sophisticatedcore.util.InventoryHelper
                                .runPickupOnPickupResponseUpgrades(player.level(), player,
                                        wrapper.getUpgradeHandler(), remaining.get(), false));
                        return remaining.get().isEmpty();
                    }, net.p3pp3rf1y.sophisticatedbackpacks.Config.SERVER.nerfsConfig
                            .onlyWornBackpackTriggersUpgrades.get());
            return remaining.get();
        }

        static boolean install(ItemStack backpack, ItemStack upgrade) {
            // A stack with no contents UUID answers a no-op handler with no slots; the mod mints one the same way.
            IBackpackWrapper wrapper = BackpackWrapper.fromStack(backpack);
            if (wrapper.getContentsUuid().isEmpty()) {
                wrapper.setContentsUuid(UUID.randomUUID());
                wrapper = BackpackWrapper.fromStack(backpack);
            }
            UpgradeHandler handler = wrapper.getUpgradeHandler();
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                if (handler.getStackInSlot(slot).isEmpty() && handler.isItemValid(slot, upgrade)
                        && handler.insertItem(slot, upgrade.copy(), false).isEmpty()) {
                    return true;
                }
            }
            return false;
        }

        static List<String> installed(ItemStack backpack) {
            UpgradeHandler handler = BackpackWrapper.fromStack(backpack.copy()).getUpgradeHandler();
            List<String> ids = new ArrayList<>();
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                ItemStack held = handler.getStackInSlot(slot);
                if (!held.isEmpty()) {
                    ids.add(BuiltInRegistries.ITEM.getKey(held.getItem()).toString());
                }
            }
            return ids;
        }
    }
}
