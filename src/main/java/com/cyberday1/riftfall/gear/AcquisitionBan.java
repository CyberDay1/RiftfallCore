package com.cyberday1.riftfall.gear;

import com.cyberday1.riftfall.RiftfallCore;
import com.cyberday1.riftfall.init.RiftfallTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffers;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import java.util.List;

/** A small, explicit item ban. No dimension, rarity, gear-classifier or creative-mode exception. */
@EventBusSubscriber(modid = RiftfallCore.MODID)
public final class AcquisitionBan {
    private AcquisitionBan() { }

    public static boolean blocked(ItemStack stack) {
        return !stack.isEmpty() && stack.is(RiftfallTags.ACQUISITION_BANNED);
    }

    public static ItemStack allowed(ItemStack stack) {
        return blocked(stack) ? ItemStack.EMPTY : stack;
    }

    public static void stripLoot(List<ItemStack> loot) { loot.removeIf(AcquisitionBan::blocked); }

    public static void stripOffers(MerchantOffers offers) {
        offers.removeIf(offer -> blockedTrade(offer.getResult()));
    }

    public static boolean blockedTrade(ItemStack stack) {
        return blocked(stack) || DonorProgression.gear(stack);
    }

    /** Includes saved item entities, custom reward spawns, fishing and drops bypassing loot tables. */
    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void spawned(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide() && event.getEntity() instanceof ItemEntity item && blocked(item.getItem())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void deathDrops(LivingDropsEvent event) {
        event.getDrops().removeIf(item -> blocked(item.getItem()));
    }

    /** A mod may change an existing entity's stack after its join event. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void pickup(ItemEntityPickupEvent.Pre event) {
        if (blocked(event.getItemEntity().getItem())) {
            event.setCanPickup(TriState.FALSE);
            event.getItemEntity().discard();
        }
    }

    @SubscribeEvent
    public static void login(PlayerEvent.PlayerLoggedInEvent event) { cleanPlayer(event.getEntity()); }

    /**
     * Closes direct list writes, restored inventories and grants which bypass the normal setters. Per tick through
     * {@link InventoryPass}.
     */
    public static void cleanPlayer(Player player) {
        if (player.level().isClientSide()) return;
        boolean changed = false;
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (blocked(inventory.getItem(slot))) {
                inventory.setItem(slot, ItemStack.EMPTY);
                changed = true;
                continue;
            }
            var cleaned = FortuneGrant.clean(inventory.getItem(slot)); // Fortune is Blacksmith-only
            if (cleaned != null) {
                if (cleaned != inventory.getItem(slot)) inventory.setItem(slot, cleaned);
                changed = true;
            }
        }
        if (blocked(player.containerMenu.getCarried())) {
            player.containerMenu.setCarried(ItemStack.EMPTY);
            changed = true;
        } else {
            var cleaned = FortuneGrant.clean(player.containerMenu.getCarried());
            if (cleaned != null) {
                if (cleaned != player.containerMenu.getCarried()) player.containerMenu.setCarried(cleaned);
                changed = true;
            }
        }
        if (changed) {
            inventory.setChanged();
            player.containerMenu.broadcastChanges();
        }
    }

    /** An altar already placed in an old world must not remain an alternate way to use the banned item. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void useBlock(PlayerInteractEvent.RightClickBlock event) {
        if (blocked(event.getItemStack()) || blocked(new ItemStack(event.getLevel().getBlockState(event.getPos()).getBlock()))) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
        }
    }
}
