package com.cyberday1.riftfall.stat;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.ItemAttributeModifierEvent;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * One crit roll for every hit: {@code apothic_attributes:crit_chance} and {@code crit_damage},
 * which Dexterity and the crit affixes already write. Spell Power's and critical_strike's own
 * rolls are closed by the mixins in {@code riftfall.compat.mixins.json}; this class takes their
 * attributes off items, so a tooltip never offers a crit stat that no longer rolls.
 */
public final class SingleCrit {

    /** The crit attributes whose rolls are closed. Apothic's pair is the survivor and never here. */
    public static final List<ResourceLocation> CLOSED = List.of(
            ResourceLocation.fromNamespaceAndPath("spell_power", "critical_chance"),
            ResourceLocation.fromNamespaceAndPath("spell_power", "critical_damage"),
            ResourceLocation.fromNamespaceAndPath("critical_strike", "chance"),
            ResourceLocation.fromNamespaceAndPath("critical_strike", "damage"));

    /**
     * Simply Swords implicits that roll a second crit on a weapon swing, id to weapon type: the katana's chance to
     * double a hit and the warglaive's chance to strike again (ruled off swings 2026-11-16).
     */
    public static final Map<ResourceLocation, ResourceLocation> CLOSED_IMPLICITS = Map.of(
            ResourceLocation.fromNamespaceAndPath("simplyswords", "katana_double_damage"),
            ResourceLocation.fromNamespaceAndPath("simplyswords", "katana"),
            ResourceLocation.fromNamespaceAndPath("simplyswords", "warglaive_double_strike"),
            ResourceLocation.fromNamespaceAndPath("simplyswords", "warglaive"));

    private SingleCrit() {
    }

    /**
     * Takes the closed implicits out of Simply Swords' definitions by id and by type, a type only while it still holds
     * that definition, so the weapon carries no implicit and its tooltip offers none.
     */
    public static void dropImplicits(Map<ResourceLocation, ?> byId, Map<ResourceLocation, ?> byType) {
        CLOSED_IMPLICITS.forEach((id, type) -> {
            Object definition = byId.remove(id);
            if (definition != null && byType.get(type) == definition) {
                byType.remove(type);
            }
        });
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, SingleCrit::strip);
        if (ModList.get().isLoaded("curios")) {
            SingleCritCurios.register();
        }
    }

    /** Null-safe: Cataclysm's curios post attributes with no registry key, and List.of throws on null. */
    public static boolean isClosed(@Nullable ResourceLocation id) {
        return id != null && CLOSED.contains(id);
    }

    static boolean isClosed(Holder<Attribute> attribute) {
        Optional<ResourceLocation> id = attribute.unwrapKey().map(key -> key.location());
        return id.isPresent() ? isClosed(id.get())
                : isClosed(BuiltInRegistries.ATTRIBUTE.getKey(attribute.value()));
    }

    /** Reads first: any write makes the event copy and rebuild the stack's whole modifier list. */
    static void strip(ItemAttributeModifierEvent event) {
        for (ItemAttributeModifiers.Entry entry : event.getModifiers()) {
            if (isClosed(entry.attribute())) {
                event.removeIf(e -> isClosed(e.attribute()));
                return;
            }
        }
    }
}
