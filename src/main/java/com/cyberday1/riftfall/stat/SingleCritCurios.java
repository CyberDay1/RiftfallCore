package com.cyberday1.riftfall.stat;

import net.minecraft.core.Holder;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import top.theillusivec4.curios.api.event.CurioAttributeModifierEvent;

import java.util.ArrayList;
import java.util.List;

/** {@link SingleCrit}'s strip for worn curios, which never reach {@code ItemAttributeModifierEvent}. */
final class SingleCritCurios {

    private SingleCritCurios() {
    }

    static void register() {
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, SingleCritCurios::strip);
    }

    private static void strip(CurioAttributeModifierEvent event) {
        // Collected first: removeModifier edits the multimap getModifiers is a view of.
        List<Holder<Attribute>> attributes = new ArrayList<>();
        List<AttributeModifier> closed = new ArrayList<>();
        event.getModifiers().forEach((attribute, modifier) -> {
            if (SingleCrit.isClosed(attribute)) {
                attributes.add(attribute);
                closed.add(modifier);
            }
        });
        for (int i = 0; i < closed.size(); i++) {
            event.removeModifier(attributes.get(i), closed.get(i));
        }
    }
}
