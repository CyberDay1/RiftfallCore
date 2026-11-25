package com.cyberday1.riftfall.home.npc.jeweler;

import com.cyberday1.riftfall.RiftfallCore;
import com.cyberday1.riftfall.init.RiftfallItems;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.Map;
import java.util.function.Supplier;

/**
 * The two Apotheosis items with a Riftfall twin become it, 1:1, wherever the player carries them: gem dust to
 * gem grit, a Sigil of Socketing to a socket chisel. Withdrawal is a resident item; the other sigils are
 * acquisition-banned and simply go.
 */
@EventBusSubscriber(modid = RiftfallCore.MODID)
public final class LegacyApotheosisItems {
    public static final ResourceLocation GEM_DUST = ResourceLocation.fromNamespaceAndPath("apotheosis", "gem_dust");
    public static final ResourceLocation SIGIL_OF_SOCKETING = ResourceLocation.fromNamespaceAndPath("apotheosis", "sigil_of_socketing");

    private static final Map<ResourceLocation, Supplier<? extends Item>> TWINS = Map.of(
            GEM_DUST, RiftfallItems.GEM_GRIT, SIGIL_OF_SOCKETING, RiftfallItems.SOCKET_CHISEL);

    private LegacyApotheosisItems() {}

    /** The twin for a legacy stack, same count, or EMPTY. */
    public static ItemStack twin(ItemStack stack) {
        if (stack.isEmpty()) return ItemStack.EMPTY;
        var twin = TWINS.get(BuiltInRegistries.ITEM.getKey(stack.getItem()));
        return twin == null ? ItemStack.EMPTY : new ItemStack(twin.get(), stack.getCount());
    }

    /** Returns whether anything changed. Per tick through {@link com.cyberday1.riftfall.gear.InventoryPass}. */
    public static boolean convert(Player player) {
        if (player.level().isClientSide()) return false;
        boolean changed = false;
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            var twin = twin(inventory.getItem(slot));
            if (twin.isEmpty()) continue;
            inventory.setItem(slot, twin);
            changed = true;
        }
        var carried = twin(player.containerMenu.getCarried());
        if (!carried.isEmpty()) {
            player.containerMenu.setCarried(carried);
            changed = true;
        }
        if (changed) {
            inventory.setChanged();
            player.containerMenu.broadcastChanges();
        }
        return changed;
    }

    @SubscribeEvent
    public static void login(PlayerEvent.PlayerLoggedInEvent event) { convert(event.getEntity()); }
}
