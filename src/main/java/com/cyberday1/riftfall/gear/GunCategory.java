package com.cyberday1.riftfall.gear;

import com.cyberday1.riftfall.RiftfallCore;
import dev.shadowsoffire.apotheosis.Apoth;
import dev.shadowsoffire.apotheosis.loot.LootCategory;
import dev.shadowsoffire.apothic_attributes.api.ALObjects;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * {@code riftfall:gun}: a loot category over TaCZ's gun item, held in the main hand, so a gun can carry Apotheosis
 * sockets and the gems in them apply (2026-11-24). It names the item by id, so it needs no TaCZ classes.
 *
 * <p>
 * A gun takes no Apotheosis rarity or affixes: {@link RiftRarity#supports} leaves guns out, and its grade
 * ({@link GunGrade}) stays the gun's rarity. Only sockets come from the rarity rules, at drop
 * ({@link RiftLootRarity#socketGun}).
 */
public final class GunCategory {

    private static final DeferredRegister<LootCategory> CATEGORY_REGISTRY = DeferredRegister
            .create(Apoth.BuiltInRegs.LOOT_CATEGORY.key(), RiftfallCore.MODID);

    static {
        CATEGORY_REGISTRY.register("gun", () -> new LootCategory(GunGrade::isGun, ALObjects.EquipmentSlotGroups.MAINHAND));
    }

    private GunCategory() {
    }

    /** Call only with Apotheosis and Apothic Attributes loaded. */
    public static void register(IEventBus modBus) {
        CATEGORY_REGISTRY.register(modBus);
    }
}
