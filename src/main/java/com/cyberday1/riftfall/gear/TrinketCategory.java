package com.cyberday1.riftfall.gear;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import com.cyberday1.riftfall.RiftfallCore;
import com.cyberday1.riftfall.init.RiftfallDataComponents;

import dev.shadowsoffire.apotheosis.Apoth;
import dev.shadowsoffire.apotheosis.loot.LootCategory;
import dev.shadowsoffire.apothic_attributes.api.ALObjects;
import dev.shadowsoffire.apothic_attributes.compat.CurioEquipmentSlot;
import dev.shadowsoffire.apothic_attributes.modifiers.EntityEquipmentSlot;
import dev.shadowsoffire.apothic_attributes.modifiers.EntitySlotGroup;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.event.CurioAttributeModifierEvent;

/**
 * The Tr column: two loot categories over {@link TrinketSlots#SLOTS}, split at the neck, and a third,
 * {@code riftfall:relic}, over {@link TrinketSlots#RELIC_SLOTS} for silenced relics only (2026-11-20).
 *
 * <p>
 * Apotheosis ships no ring or amulet category, so the design table's Tr rows had nowhere to land.
 * {@code LootCategory} is a code registry rather than a datapack one, so this is the smallest amount
 * of code that makes the column exist: seven Curios slot types, a slot group over each half, and a
 * category per group.
 *
 * <h2>Why two</h2>
 * {@code riftfall:necklace} exists so one affix can be priced for one worn piece.
 * {@code riftfall:trinket} spans six slots a player can fill several of at once, which is right for
 * the fourteen Tr rows and wrong for {@code tiered/scavenging}. The two validators are disjoint
 * &mdash; see {@link TrinketSlots} &mdash; so registry order never decides which one claims an item,
 * and every existing Tr affix simply names both.
 *
 * <p>
 * The slot group is the second half of that guarantee: {@code AttributeAffix#addModifiers} writes
 * with {@code cat.getSlots()}, so a necklace affix only applies while the item is worn at the neck.
 *
 * <p>
 * Loaded only from behind {@link RiftTrinkets}'s three-mod check, on the same terms as
 * {@link RiftLootRarity}.
 */
final class TrinketCategory {

    private static final ResourceLocation TRINKET_ID = RiftfallCore.id("trinket");
    private static final ResourceLocation NECKLACE_ID = RiftfallCore.id("necklace");
    private static final ResourceLocation RELIC_ID = RiftfallCore.id("relic");

    private static final DeferredRegister<EntityEquipmentSlot> SLOT_REGISTRY = DeferredRegister
            .create(ALObjects.BuiltInRegs.ENTITY_EQUIPMENT_SLOT.key(), RiftfallCore.MODID);

    private static final DeferredRegister<EntitySlotGroup> GROUP_REGISTRY = DeferredRegister
            .create(ALObjects.BuiltInRegs.ENTITY_SLOT_GROUP.key(), RiftfallCore.MODID);

    private static final DeferredRegister<LootCategory> CATEGORY_REGISTRY = DeferredRegister
            .create(Apoth.BuiltInRegs.LOOT_CATEGORY.key(), RiftfallCore.MODID);

    /**
     * One holder per Curios slot type, held rather than resolved later, and deliberately: NeoForge
     * patches {@code Holder.Reference} to hash and compare by key, so a set of unbound
     * {@link DeferredHolder}s still answers {@code contains} for the registry's own holder. That is
     * what lets the groups below be built before anything has been registered, which in turn means
     * no registry ordering to get wrong.
     */
    private static final Map<String, Holder<EntityEquipmentSlot>> SLOT_HOLDERS =
            java.util.stream.Stream.concat(TrinketSlots.SLOTS.stream(), TrinketSlots.RELIC_SLOTS.stream())
                    .collect(Collectors.toMap(slot -> slot,
                    slot -> (Holder<EntityEquipmentSlot>) SLOT_REGISTRY
                            .register(slot, () -> new CurioEquipmentSlot(slot))));

    /** The groups Curios' bridge tests our affix modifiers against. */
    private static final EntitySlotGroup TRINKET_GROUP =
            group(TRINKET_ID, TrinketSlots.TRINKET_SLOTS);

    private static final EntitySlotGroup NECKLACE_GROUP =
            group(NECKLACE_ID, TrinketSlots.NECKLACE_SLOTS);

    private static final EntitySlotGroup RELIC_GROUP = group(RELIC_ID, TrinketSlots.RELIC_SLOTS);

    static {
        GROUP_REGISTRY.register(TRINKET_ID.getPath(), () -> TRINKET_GROUP);
        GROUP_REGISTRY.register(NECKLACE_ID.getPath(), () -> NECKLACE_GROUP);
        GROUP_REGISTRY.register(RELIC_ID.getPath(), () -> RELIC_GROUP);
        CATEGORY_REGISTRY.register(TRINKET_ID.getPath(),
                () -> new LootCategory(TrinketSlots::isTrinket, TRINKET_GROUP));
        CATEGORY_REGISTRY.register(NECKLACE_ID.getPath(),
                () -> new LootCategory(TrinketSlots::isNecklace, NECKLACE_GROUP));
        CATEGORY_REGISTRY.register(RELIC_ID.getPath(),
                () -> new LootCategory(TrinketSlots::isRelic, RELIC_GROUP));
    }

    private static EntitySlotGroup group(ResourceLocation id, List<String> slots) {
        return new EntitySlotGroup(id, HolderSet.direct(slots.stream()
                .map(SLOT_HOLDERS::get)
                .toList()));
    }

    private TrinketCategory() {
    }

    static void register(IEventBus modBus) {
        SLOT_REGISTRY.register(modBus);
        GROUP_REGISTRY.register(modBus);
        CATEGORY_REGISTRY.register(modBus);
        // Curios reads its own component, so a trinket's affixes never reach
        // ItemAttributeModifierEvent and AffixTiers' ×T ride would stop at the neckline.
        NeoForge.EVENT_BUS.addListener(EventPriority.LOW, TrinketCategory::scaleCurio);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, TrinketCategory::keyPerSlot);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, TrinketCategory::stripDonorBonuses);
    }

    /** These imported trinkets use rolled stats, not a second set of donor bow bonuses. */
    private static void stripDonorBonuses(CurioAttributeModifierEvent event) {
        if (!DonorProgression.gear(event.getItemStack())) return;
        var removed = new ArrayList<java.util.Map.Entry<Holder<Attribute>, AttributeModifier>>();
        event.getModifiers().forEach((attribute, modifier) -> {
            if (DonorProgression.MODS.contains(modifier.id().getNamespace())) {
                removed.add(java.util.Map.entry(attribute, modifier));
            }
        });
        removed.forEach(entry -> event.removeModifier(entry.getKey(), entry.getValue()));
    }

    /** {@link TrinketSlots#perSlot} on every affix modifier Apothic's HIGH bridge put here. */
    private static void keyPerSlot(CurioAttributeModifierEvent event) {
        SlotContext slot = event.getSlotContext();
        List<Holder<Attribute>> attributes = new ArrayList<>();
        List<AttributeModifier> shared = new ArrayList<>();
        event.getModifiers().forEach((attribute, modifier) -> {
            if (!TrinketSlots.perSlot(modifier.id(), slot.identifier(), slot.index()).equals(modifier.id())) {
                attributes.add(attribute);
                shared.add(modifier);
            }
        });
        for (int i = 0; i < shared.size(); i++) {
            AttributeModifier modifier = shared.get(i);
            event.removeModifier(attributes.get(i), modifier);
            event.addModifier(attributes.get(i), new AttributeModifier(
                    TrinketSlots.perSlot(modifier.id(), slot.identifier(), slot.index()),
                    modifier.amount(), modifier.operation()));
        }
    }

    /** {@link AffixTiers#scale}, on the event a worn curio actually goes through. */
    private static void scaleCurio(CurioAttributeModifierEvent event) {
        Integer tier = event.getItemStack().get(RiftfallDataComponents.RIFT_TIER.get());
        if (tier == null || tier <= 1) {
            return;
        }
        double factor = AffixTiers.tierFactor(tier);
        // Collected first: removeModifier edits the multimap getModifiers is a view of.
        List<Holder<Attribute>> attributes = new ArrayList<>();
        List<AttributeModifier> tiered = new ArrayList<>();
        event.getModifiers().forEach((attribute, modifier) -> {
            if (AffixTiers.isTiered(modifier.id())) {
                attributes.add(attribute);
                tiered.add(modifier);
            }
        });
        for (int i = 0; i < tiered.size(); i++) {
            AttributeModifier modifier = tiered.get(i);
            event.removeModifier(attributes.get(i), modifier);
            event.addModifier(attributes.get(i), new AttributeModifier(modifier.id(),
                    modifier.amount() * factor, modifier.operation()));
        }
    }
}
