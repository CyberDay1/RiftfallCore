package com.cyberday1.riftfall.gear;

import com.cyberday1.riftfall.RiftfallCore;
import com.cyberday1.riftfall.home.npc.jeweler.LegacyApotheosisItems;
import com.cyberday1.riftfall.tool.ToolAppearanceUnlocks;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.TagsUpdatedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * The per-tick inventory sweeps, run as one: the acquisition ban, the Apotheosis twins, then tool discoveries, in
 * that order. Skipped while no slot or the cursor holds a different stack or a different enchantment list than
 * last time, which covers direct list writes; a tag reload clears the record, because the ban is a tag.
 */
@EventBusSubscriber(modid = RiftfallCore.MODID)
public final class InventoryPass {
    /** Server thread only. Weak, so a player entity that left or respawned drops out by itself. */
    private static final Map<ServerPlayer, Seen> SEEN = new WeakHashMap<>();

    private InventoryPass() {}

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void tick(PlayerTickEvent.Pre event) {
        if (event.getEntity() instanceof ServerPlayer player) sweep(player);
    }

    /** The server's own reload only: the client's tag packet arrives on another thread and changes no ban. */
    @SubscribeEvent
    public static void tags(TagsUpdatedEvent event) {
        if (event.getUpdateCause() == TagsUpdatedEvent.UpdateCause.SERVER_DATA_LOAD) SEEN.clear();
    }

    /** Returns whether the sweeps ran. */
    static boolean sweep(ServerPlayer player) {
        var seen = SEEN.get(player);
        if (seen != null && seen.matches(player)) return false;
        AcquisitionBan.cleanPlayer(player);
        LegacyApotheosisItems.convert(player);
        ToolAppearanceUnlocks.scan(player);
        if (seen == null) SEEN.put(player, seen = new Seen());
        seen.record(player);
        return true;
    }

    static void forgetAll() { SEEN.clear(); }

    /** Each slot's stack and enchantment lists by identity; the cursor is the last entry. */
    private static final class Seen {
        private Object[] stacks = new Object[0], enchantments = new Object[0], stored = new Object[0];

        private static ItemStack at(ServerPlayer player, int index, int size) {
            if (index < size) return player.getInventory().getItem(index);
            return player.containerMenu == null ? ItemStack.EMPTY : player.containerMenu.getCarried();
        }

        boolean matches(ServerPlayer player) {
            int size = player.getInventory().getContainerSize();
            if (stacks.length != size + 1) return false;
            for (int i = 0; i <= size; i++) {
                var stack = at(player, i, size);
                if (stack != stacks[i]) return false;
                if (stack.isEmpty()) continue;
                if (stack.get(DataComponents.ENCHANTMENTS) != enchantments[i]
                        || stack.get(DataComponents.STORED_ENCHANTMENTS) != stored[i]) return false;
            }
            return true;
        }

        void record(ServerPlayer player) {
            int size = player.getInventory().getContainerSize();
            if (stacks.length != size + 1) {
                stacks = new Object[size + 1]; enchantments = new Object[size + 1]; stored = new Object[size + 1];
            }
            for (int i = 0; i <= size; i++) {
                var stack = at(player, i, size);
                stacks[i] = stack;
                enchantments[i] = stack.isEmpty() ? null : stack.get(DataComponents.ENCHANTMENTS);
                stored[i] = stack.isEmpty() ? null : stack.get(DataComponents.STORED_ENCHANTMENTS);
            }
        }
    }
}
