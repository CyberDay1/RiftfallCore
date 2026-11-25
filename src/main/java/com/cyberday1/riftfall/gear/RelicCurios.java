package com.cyberday1.riftfall.gear;

import java.util.List;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.event.CurioAttributeModifierEvent;

/**
 * Puts {@link RelicStats} on a worn relic. {@code NORMAL}: after Apothic's {@code HIGH} bridge (the affixes) and
 * before {@code TrinketCategory#scaleCurio} at {@code LOW}, which rides the tiered lines. The silence empties the
 * item's own map before this event fires, so these lines are on the surviving path. Loaded only with Curios.
 */
final class RelicCurios {

    private RelicCurios() {
    }

    static void register() {
        NeoForge.EVENT_BUS.addListener(EventPriority.NORMAL, RelicCurios::addFixed);
    }

    private static void addFixed(CurioAttributeModifierEvent event) {
        ItemStack stack = event.getItemStack();
        List<RelicStats.Line> lines = RelicStats.lines(stack);
        if (lines.isEmpty() || RelicStats.suspended()) return;
        SlotContext slot = event.getSlotContext();
        ResourceLocation item = BuiltInRegistries.ITEM.getKey(stack.getItem());
        for (int n = 0; n < lines.size(); n++) {
            RelicStats.Line line = lines.get(n);
            int index = n;
            BuiltInRegistries.ATTRIBUTE.getHolder(line.attribute()).ifPresent(attribute -> event.addModifier(attribute,
                    new AttributeModifier(RelicStats.id(item, index, line.tiered(), slot.identifier(), slot.index()),
                            line.amount(), line.operation())));
        }
    }
}
