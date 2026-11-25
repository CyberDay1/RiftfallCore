package com.cyberday1.riftfall.tool;

import com.cyberday1.riftfall.RiftfallCore;
import com.cyberday1.riftfall.block.barrel.RiftBarrelBlock;
import com.cyberday1.riftfall.block.barrel.RiftBarrelBlockEntity;
import com.cyberday1.riftfall.home.HomeProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.ItemAttributeModifierEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import java.util.*;

@EventBusSubscriber(modid = RiftfallCore.MODID)
public final class LootingToolEffects {
    private LootingToolEffects() {}
    private static final Map<UUID, Chain> CHAINS = new HashMap<>();
    private static final ThreadLocal<Boolean> CHECKING_ACCESS = ThreadLocal.withInitial(() -> false);
    public static boolean personalBreak(Player player, BlockState state) {
        return !player.isSpectator() && LootingToolItem.isTool(player.getMainHandItem()) && state.getBlock() instanceof RiftBarrelBlock;
    }
    @SubscribeEvent public static void attributes(ItemAttributeModifierEvent event) {
        double reach = ToolUpgrades.bonus(event.getItemStack(), ToolStat.BLOCK_REACH);
        if (reach > 0) event.addModifier(Attributes.BLOCK_INTERACTION_RANGE,
                new AttributeModifier(RiftfallCore.id("looting_pick_reach"), reach, AttributeModifier.Operation.ADD_VALUE),
                EquipmentSlotGroup.MAINHAND);
    }
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void breakBarrel(BlockEvent.BreakEvent event) {
        if (CHECKING_ACCESS.get()) return;
        if (!personalBreak(event.getPlayer(), event.getState())) return;
        // This hook is reached only if earlier protection listeners allowed the attempt.
        event.setCanceled(true);
        if (!(event.getPlayer() instanceof ServerPlayer player) || HomeProtection.denied(player.level(), player)
                || !player.canInteractWithBlock(event.getPos(), 1)) return;
        if (!(player.serverLevel().getBlockEntity(event.getPos()) instanceof RiftBarrelBlockEntity barrel)) return;
        var result = collect(player, barrel);
        if (!result.remaining() && (result.fresh() || result.moved() > 0)) {
            int targets = (int) ToolUpgrades.bonus(player.getMainHandItem(), ToolStat.CHAIN);
            if (targets > 0) CHAINS.put(player.getUUID(), new Chain(player, barrel.getBlockPos(), targets));
        }
    }
    private static RiftBarrelBlockEntity.Collection collect(ServerPlayer player, RiftBarrelBlockEntity barrel) {
        var result = barrel.collect(player);
        // Send the personal model mark even when vanilla's cancelled-break correction restores the block.
        player.connection.send(new net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket(barrel.getBlockPos(), barrel.getBlockState()));
        player.connection.send(ClientboundBlockEntityDataPacket.create(barrel));
        if (result.moved() > 0 || result.fresh()) {
            player.playNotifySound(SoundEvents.WOOD_BREAK, SoundSource.BLOCKS, .65f, 1.15f);
        }
        if (result.remaining()) player.displayClientMessage(Component.translatable(ToolLang.PICK_INVENTORY_FULL), true);
        return result;
    }

    /** Finite loaded-barrel graph, processed over ticks so high chain counts do not stall a server. */
    private static final class Chain {
        final ServerLevel level;
        final ItemStack tool;
        final double range;
        final ArrayList<BlockPos> candidates = new ArrayList<>();
        final ArrayDeque<BlockPos> frontier = new ArrayDeque<>();
        int left;
        BlockPos current;
        int cursor;
        Chain(ServerPlayer player, BlockPos origin, int targets) {
            level = player.serverLevel(); tool = player.getMainHandItem().copy(); left = targets;
            range = 3 + ToolUpgrades.bonus(tool, ToolStat.CHAIN_REACH);
            frontier.add(origin.immutable());
            // Only the player's loaded view area; never generate chunks to seek another target.
            int distance = player.getServer().getPlayerList().getViewDistance();
            var center = player.chunkPosition();
            for (int x = center.x - distance; x <= center.x + distance; x++) {
                for (int z = center.z - distance; z <= center.z + distance; z++) {
                    var chunk = level.getChunkSource().getChunk(x, z, ChunkStatus.FULL, false);
                    if (!(chunk instanceof LevelChunk full)) continue;
                    for (var be : full.getBlockEntities().values()) {
                        if (be instanceof RiftBarrelBlockEntity barrel && !be.getBlockPos().equals(origin)
                                && barrel.hasRemainingFor(player.getUUID())) candidates.add(be.getBlockPos().immutable());
                    }
                }
            }
            candidates.sort(Comparator.comparingDouble(p -> p.distSqr(origin)));
        }
        boolean tick(ServerPlayer player) {
            if (!player.isAlive() || player.isSpectator() || player.serverLevel() != level
                    || !ItemStack.matches(tool, player.getMainHandItem()) || HomeProtection.denied(level, player)) return false;
            // Bound both collection work and candidate comparisons, without limiting installed upgrades.
            int collected = 0, comparisons = 0;
            while (left > 0 && collected < 8 && comparisons++ < 512) {
                if (current == null) {
                    if (frontier.isEmpty()) return false;
                    current = frontier.removeFirst(); cursor = 0;
                }
                if (cursor >= candidates.size()) { current = null; continue; }
                var target = candidates.get(cursor++);
                if (target == null || current.distSqr(target) > range * range || !level.hasChunkAt(target)) continue;
                if (!(level.getBlockEntity(target) instanceof RiftBarrelBlockEntity barrel)
                        || !barrel.hasRemainingFor(player.getUUID())) continue;
                if (!visible(level, player, current, target) || !mayChain(player, barrel)) continue;
                candidates.set(cursor - 1, null);
                var result = collect(player, barrel);
                if (result.remaining()) return false;
                if (result.fresh() || result.moved() > 0) {
                    left--; collected++; frontier.addLast(target);
                }
            }
            return left > 0 && (current != null || !frontier.isEmpty());
        }
    }
    private static boolean visible(ServerLevel level, Player player, BlockPos from, BlockPos to) {
        var start = Vec3.atCenterOf(from);
        var end = Vec3.atCenterOf(to);
        // Begin just beyond the source barrel's collision box.
        var delta = end.subtract(start);
        // A long upgraded link can cross an unloaded chunk even when both ends are loaded.
        int steps = (int) Math.ceil(delta.length());
        for (int step = 0; step <= steps; step++) {
            var pos = BlockPos.containing(start.lerp(end, step / (double) Math.max(1, steps)));
            if (level.getChunkSource().getChunk(pos.getX() >> 4, pos.getZ() >> 4, ChunkStatus.FULL, false) == null) return false;
        }
        double longest = Math.max(Math.abs(delta.x), Math.max(Math.abs(delta.y), Math.abs(delta.z)));
        if (longest == 0) return false;
        start = start.add(delta.scale(.501 / longest));
        var hit = level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        return hit.getType() == HitResult.Type.MISS || hit.getBlockPos().equals(to);
    }
    private static boolean mayChain(ServerPlayer player, RiftBarrelBlockEntity barrel) {
        // Give claims/protection mods the same veto as a manual break, without recursively collecting.
        var event = new BlockEvent.BreakEvent(player.serverLevel(), barrel.getBlockPos(), barrel.getBlockState(), player);
        CHECKING_ACCESS.set(true);
        try { net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(event); }
        finally { CHECKING_ACCESS.remove(); }
        return !event.isCanceled();
    }
    @SubscribeEvent public static void tick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        var chain = CHAINS.get(player.getUUID());
        if (chain != null && !chain.tick(player)) CHAINS.remove(player.getUUID());
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) { CHAINS.remove(event.getEntity().getUUID()); }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) { CHAINS.clear(); }
}
