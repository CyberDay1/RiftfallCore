package com.cyberday1.riftfall.gear;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForge;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.event.CurioChangeEvent;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;

/**
 * Reads worn signature relics through {@code CuriosApi} and refreshes the active set on Curios' change event
 * (server only: Curios posts it there). Loaded only with Curios.
 */
final class RelicSignatureCurios {

    private RelicSignatureCurios() {
    }

    static void register() {
        RelicSignatures.readWith(RelicSignatureCurios::worn);
        NeoForge.EVENT_BUS.addListener(RelicSignatureCurios::changed);
    }

    private static void changed(CurioChangeEvent event) {
        if (event.getEntity() instanceof ServerPlayer player
                && (signed(event.getFrom()) || signed(event.getTo()))) {
            RelicSignatures.refresh(player);
        }
    }

    private static boolean signed(ItemStack stack) {
        return !stack.isEmpty() && RelicSignatures.of(BuiltInRegistries.ITEM.getKey(stack.getItem())) != null;
    }

    /** Worn stacks only (not cosmetic), in slots Curios has active. */
    static List<RelicSignatures.Worn> worn(Player player) {
        List<RelicSignatures.Worn> out = new ArrayList<>();
        CuriosApi.getCuriosInventory(player).ifPresent(inventory -> {
            for (Map.Entry<String, ICurioStacksHandler> entry : inventory.getCurios().entrySet()) {
                ICurioStacksHandler handler = entry.getValue();
                NonNullList<Boolean> states = handler.getActiveStates();
                for (int i = 0; i < handler.getStacks().getSlots(); i++) {
                    if (i < states.size() && !states.get(i)) continue;
                    ItemStack stack = handler.getStacks().getStackInSlot(i);
                    if (stack.isEmpty()) continue;
                    RelicSignatures.Signature s = RelicSignatures.of(BuiltInRegistries.ITEM.getKey(stack.getItem()));
                    if (s != null) out.add(new RelicSignatures.Worn(entry.getKey(), i, s));
                }
            }
        });
        return out;
    }
}
