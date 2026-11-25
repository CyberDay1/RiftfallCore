package com.cyberday1.riftfall.compat;

import com.cyberday1.riftfall.gear.RiftArms;
import com.mojang.logging.LogUtils;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.builder.AmmoItemBuilder;
import com.tacz.guns.api.item.builder.AttachmentItemBuilder;
import com.tacz.guns.api.item.builder.GunItemBuilder;
import com.tacz.guns.api.item.gun.FireMode;
import com.tacz.guns.resource.index.CommonGunIndex;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Builds the TaCZ stacks a rift drops. Names TaCZ types; reached only behind {@code GunnerGuns.loaded()}.
 * A gun comes loaded, a round chambered, with one spare magazine of its own ammo.
 */
public final class TaczLoot {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();

    private TaczLoot() {
    }

    /** The gun, loaded, then its spare ammo; empty if the gun pack has no such gun. */
    public static List<ItemStack> gun(ResourceLocation id, HolderLookup.Provider registries) {
        CommonGunIndex index = TimelessAPI.getCommonGunIndex(id).orElse(null);
        if (index == null) {
            warn("gun", id);
            return List.of();
        }
        GunData data = index.getGunData();
        List<FireMode> modes = data.getFireModeSet();
        int magazine = Math.max(0, data.getAmmoAmount());
        ItemStack gun = GunItemBuilder.create()
                .setId(id)
                .setFireMode(modes == null || modes.isEmpty() ? FireMode.SEMI : modes.get(0))
                .setAmmoCount(magazine)
                .setAmmoInBarrel(magazine > 0)
                .build(registries);
        ItemStack ammo = AmmoItemBuilder.create()
                .setId(data.getAmmoId())
                .setCount(RiftArms.spare(magazine))
                .build();
        return ammo.isEmpty() ? List.of(gun) : List.of(gun, ammo);
    }

    /** One attachment; empty if the gun pack has no such attachment. */
    public static List<ItemStack> attachment(ResourceLocation id) {
        if (TimelessAPI.getCommonAttachmentIndex(id).isEmpty()) {
            warn("attachment", id);
            return List.of();
        }
        return List.of(AttachmentItemBuilder.create().setId(id).build());
    }

    /** Every gun and attachment id the loaded gun packs define. */
    public static Set<String> gunIds() {
        Set<String> ids = new java.util.TreeSet<>();
        TimelessAPI.getAllCommonGunIndex().forEach(e -> ids.add(e.getKey().toString()));
        return ids;
    }

    public static Set<String> attachmentIds() {
        Set<String> ids = new java.util.TreeSet<>();
        TimelessAPI.getAllCommonAttachmentIndex().forEach(e -> ids.add(e.getKey().toString()));
        return ids;
    }

    private static void warn(String kind, ResourceLocation id) {
        if (WARNED.add(kind + id)) {
            LOGGER.warn("Rift arms: no {} {} in the loaded gun packs, so that draw gives nothing.", kind, id);
        }
    }
}
