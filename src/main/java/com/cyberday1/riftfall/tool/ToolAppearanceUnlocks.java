package com.cyberday1.riftfall.tool;

import com.cyberday1.riftfall.RiftfallCore;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/** Per-player discoveries, saved separately from tools and retained after death. */
@EventBusSubscriber(modid = RiftfallCore.MODID)
public final class ToolAppearanceUnlocks {
    static final String KEY = "riftfall_tool_appearances";
    private ToolAppearanceUnlocks() {}

    public static boolean unlocked(ServerPlayer player, ToolAppearances.Skin skin) {
        return skin != null && (!skin.requiresDiscovery() || discoveries(player).getBoolean(skin.item().toString()));
    }

    private static CompoundTag discoveries(Player player) {
        return player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).getCompound(KEY);
    }

    static boolean record(ServerPlayer player, ToolAppearances.Skin skin) {
        if (skin == null || !skin.requiresDiscovery() || !ToolAppearances.ALL.contains(skin) || unlocked(player, skin)) return false;
        var persisted = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        var found = persisted.getCompound(KEY);
        found.putBoolean(skin.item().toString(), true);
        persisted.put(KEY, found);
        player.getPersistentData().put(Player.PERSISTED_NBT_TAG, persisted);
        player.displayClientMessage(Component.translatable(ToolLang.APPEARANCE_UNLOCKED, skin.displayName())
                .withStyle(ChatFormatting.GOLD), false);
        return true;
    }

    public static void discover(ServerPlayer player, ItemStack stack) {
        if (stack.isEmpty() || LootingToolItem.isTool(stack)) return;
        // Match the original registered item, never a name, appearance tag or preview.
        record(player, ToolAppearances.find(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()));
    }

    /** Per tick through {@link com.cyberday1.riftfall.gear.InventoryPass}. */
    public static void scan(ServerPlayer player) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++)
            discover(player, player.getInventory().getItem(slot));
        // A chest transfer may put the weapon on the player's cursor before an inventory slot.
        // Never inspect the container's other slots: browsing somebody's chest is not acquisition.
        if (player.containerMenu != null) discover(player, player.containerMenu.getCarried());
    }

    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) scan(player);
    }
    @SubscribeEvent public static void pickup(ItemEntityPickupEvent.Post event) {
        if (event.getPlayer() instanceof ServerPlayer player) discover(player, event.getOriginalStack());
    }
    @SubscribeEvent public static void crafted(PlayerEvent.ItemCraftedEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) discover(player, event.getCrafting());
    }
    @SubscribeEvent public static void clone(PlayerEvent.Clone event) {
        var old = discoveries(event.getOriginal());
        if (old.isEmpty()) return;
        var data = event.getEntity().getPersistentData();
        var persisted = data.getCompound(Player.PERSISTED_NBT_TAG);
        var found = persisted.getCompound(KEY);
        found.merge(old.copy());
        persisted.put(KEY, found);
        data.put(Player.PERSISTED_NBT_TAG, persisted);
    }
}
