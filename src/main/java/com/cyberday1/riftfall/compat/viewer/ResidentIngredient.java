package com.cyberday1.riftfall.compat.viewer;

import com.cyberday1.riftfall.RiftfallCore;
import com.cyberday1.riftfall.block.marker.NpcRole;
import com.cyberday1.riftfall.home.npc.ResidentAppearance;
import com.cyberday1.riftfall.home.npc.ResidentLanding;
import com.cyberday1.riftfall.home.npc.ResidentLang;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.Arrays;
import java.util.List;

/**
 * A home resident as a recipe-viewer ingredient: the catalyst of that resident's services. Only residents with live
 * services are listed; one with none would be an ingredient that leads nowhere.
 */
public record ResidentIngredient(NpcRole role) {
    public static final List<ResidentIngredient> SERVICED = Arrays.stream(NpcRole.values())
            .filter(r -> !ResidentLanding.entries(r).isEmpty()).map(ResidentIngredient::new).toList();

    public static final Codec<ResidentIngredient> CODEC = Codec.STRING.comapFlatMap(id -> Arrays.stream(NpcRole.values())
            .filter(r -> r.id().equals(id)).findFirst().map(r -> DataResult.success(new ResidentIngredient(r)))
            .orElseGet(() -> DataResult.error(() -> "Unknown resident " + id)), r -> r.role().id());

    public static ResidentIngredient of(NpcRole role) { return new ResidentIngredient(role); }

    /** Stable across sessions: bookmarks and the viewer's index key on it. */
    public String uid() { return RiftfallCore.MODID + ":resident/" + role.id(); }

    public ResourceLocation id() { return ResourceLocation.fromNamespaceAndPath(RiftfallCore.MODID, "resident/" + role.id()); }

    public String nameKey() { return ResidentLang.name(role); }

    /** The bundled CNPC skin; present only with CustomNPCs installed. */
    public ResourceLocation skin() { return ResourceLocation.parse(ResidentAppearance.forRole(role).texture()); }

    /** Drawn when the skin is missing: the first landing tile's icon, else the held item. */
    public Item icon() {
        var entries = ResidentLanding.entries(role);
        for (var entry : entries) {
            var item = entry.icon().get();
            if (item != null && item != Items.AIR) return item;
        }
        return ResidentAppearance.forRole(role).heldItem();
    }
}
