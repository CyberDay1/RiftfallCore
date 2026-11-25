package com.cyberday1.riftfall.loot;

import com.cyberday1.riftfall.RiftfallCore;
import com.cyberday1.riftfall.gear.RiftMobDrops;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Riftfall's boss rewards: {@code riftfall:entities/rift_boss_arms}, rolled once per player
 * in the rift where it fell. A chest roll rather than the mob's death table, because the boss is any
 * mob on the roster. Normal mob death loot is suppressed in rifts; this separate reward roll is preserved.
 * The table owns every reward chance, including the guaranteed Shattered World Shard (one per player per kill); the
 * one exception is a player's first shard (decision 11, {@link com.cyberday1.riftfall.story.StoryRules#firstShard}),
 * added only when their own roll held none, so it never stacks with the table's and is dormant while the table pays.
 */
public final class RiftBossLoot {

    public static final ResourceKey<LootTable> ARMS =
            ResourceKey.create(Registries.LOOT_TABLE, RiftfallCore.id("entities/rift_boss_arms"));

    private RiftBossLoot() {
    }

    /** Rolls the table at {@code boss}'s position; {@code killer} is the loot context's entity when there is one. */
    public static ObjectArrayList<ItemStack> roll(ServerLevel level, Mob boss, @Nullable Entity killer) {
        LootTable table = level.getServer().reloadableRegistries().getLootTable(ARMS);
        LootParams.Builder params = new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, boss.position());
        if (killer != null) {
            params.withParameter(LootContextParams.THIS_ENTITY, killer);
        }
        return table.getRandomItems(params.create(LootContextParamSets.CHEST));
    }

    /**
     * One roll per player in the rift, each with that player as the context's entity (so their class weights it) and
     * each drop owned by them: an {@code ItemEntity} target only its owner can pick up. With nobody there to own it,
     * the old single roll for the killer.
     */
    public static void drop(ServerLevel level, Mob boss, @Nullable Entity killer) {
        List<ServerPlayer> party = participants(level);
        List<UUID> fallen = com.cyberday1.riftfall.rift.RiftLevels.isRiftWorld(level)
                ? fallenRollers(com.cyberday1.riftfall.boss.BossFallen.of(level),
                        party.stream().map(ServerPlayer::getUUID).toList())
                : List.of();
        if (party.isEmpty() && fallen.isEmpty()) {
            roll(level, boss, killer).forEach(stack -> reward(boss, stamp(stack, level, boss)));
            return;
        }
        dropFor(level, boss, party);
        dropForFallen(level, boss, fallen);
    }

    /** The fallen who still need a roll: anyone back in the rift and alive already rolls with the party. */
    static List<UUID> fallenRollers(Collection<UUID> fallen, Collection<UUID> present) {
        List<UUID> out = new ArrayList<>();
        for (UUID id : fallen) {
            if (!present.contains(id) && !out.contains(id)) out.add(id);
        }
        return out;
    }

    /**
     * One roll each for party members who died in the fight and are not back in the rift. Online and alive: into
     * their inventory. Offline or still dead: unowned at the boss, for the party to carry out. Returns what spawned.
     */
    public static List<ItemEntity> dropForFallen(ServerLevel level, Mob boss, List<UUID> fallen) {
        List<ItemEntity> spawned = new ArrayList<>();
        for (UUID id : fallen) {
            ServerPlayer player = find(level.getServer(), id);
            Delivery route = delivery(false, player != null && player.isAlive() && !player.hasDisconnected());
            ObjectArrayList<ItemStack> rolled = roll(level, boss, player);
            var shard = com.cyberday1.riftfall.init.RiftfallItems.SHATTERED_WORLD_SHARD.get();
            if (player != null && rolled.stream().noneMatch(stack -> stack.is(shard))
                    && com.cyberday1.riftfall.story.StoryBridge.firstShardOwed(player, level)) {
                rolled.add(new ItemStack(shard));
            }
            boolean sent = false;
            for (ItemStack stack : rolled) {
                stamp(stack, level, boss);
                if (player != null) com.cyberday1.riftfall.rift.RiftRecaps.found(level, player, stack);
                if (route == Delivery.INVENTORY) {
                    sent |= !stack.isEmpty();
                    ItemEntity overflow = give(player, stack);
                    if (overflow != null) spawned.add(overflow);
                    continue;
                }
                ItemEntity dropped = reward(boss, stack);
                if (dropped != null) spawned.add(dropped);
            }
            if (sent) {
                player.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
                        "message." + RiftfallCore.MODID + ".boss_share_sent"));
            }
        }
        return spawned;
    }

    /** A connected player by id, in any level: the player list first, then each level's own list. */
    @Nullable
    static ServerPlayer find(net.minecraft.server.MinecraftServer server, UUID id) {
        ServerPlayer listed = server.getPlayerList().getPlayer(id);
        if (listed != null) return listed;
        for (ServerLevel each : server.getAllLevels()) {
            if (each.getPlayerByUUID(id) instanceof ServerPlayer player) return player;
        }
        return null;
    }

    /** Where one player's share goes. */
    public enum Delivery {
        /** Owned, where the boss fell: the player can walk to it. */
        AT_BOSS,
        /** Into their inventory, the overflow owned at their feet: they are online but cannot reach the boss. */
        INVENTORY,
        /** Unowned, where the boss fell: nobody could otherwise collect it. */
        UNLOCKED
    }

    /** The share's route: owned at the boss when the player can reach it, else sent to them, else unlocked. */
    static Delivery delivery(boolean reachesBoss, boolean online) {
        if (reachesBoss) return Delivery.AT_BOSS;
        return online ? Delivery.INVENTORY : Delivery.UNLOCKED;
    }

    /**
     * Each player's own roll plus the guaranteed first shard for a player whose roll had none and who is still owed
     * it. Owned at the boss for a player who can reach it; sent to anyone locked out of the room (see
     * {@link #delivery}). Returns what was spawned.
     */
    public static List<ItemEntity> dropFor(ServerLevel level, Mob boss, List<ServerPlayer> party) {
        List<ItemEntity> spawned = new ArrayList<>();
        Reach reach = Reach.of(level, boss);
        for (ServerPlayer player : party) {
            ObjectArrayList<ItemStack> rolled = roll(level, boss, player);
            var shard = com.cyberday1.riftfall.init.RiftfallItems.SHATTERED_WORLD_SHARD.get();
            if (rolled.stream().noneMatch(stack -> stack.is(shard))
                    && com.cyberday1.riftfall.story.StoryBridge.firstShardOwed(player, level)) {
                rolled.add(new ItemStack(shard));
            }
            Delivery route = delivery(reach.reaches(level, player), !player.hasDisconnected());
            boolean sent = false;
            for (ItemStack stack : rolled) {
                stamp(stack, level, boss);
                com.cyberday1.riftfall.rift.RiftRecaps.found(level, player, stack);
                if (route == Delivery.INVENTORY) {
                    sent |= !stack.isEmpty();
                    ItemEntity overflow = give(player, stack);
                    if (overflow != null) spawned.add(overflow);
                    continue;
                }
                ItemEntity dropped = reward(boss, stack);
                if (dropped != null) {
                    if (route == Delivery.AT_BOSS) dropped.setTarget(player.getUUID());
                    spawned.add(dropped);
                }
            }
            if (sent) {
                player.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
                        "message." + RiftfallCore.MODID + ".boss_share_sent"));
            }
        }
        return spawned;
    }

    /** Puts a stack in the player's inventory; what does not fit drops at their feet, owned. */
    @Nullable
    private static ItemEntity give(ServerPlayer player, ItemStack stack) {
        player.getInventory().add(stack);
        if (stack.isEmpty()) return null;
        ServerLevel level = player.serverLevel();
        ItemEntity left = new ItemEntity(level, player.getX(), player.getY(), player.getZ(), stack.copy());
        left.setTarget(player.getUUID());
        left.setNoPickUpDelay();
        stack.setCount(0);
        return RiftMobDrops.reward(() -> level.addFreshEntity(left)) ? left : null;
    }

    /**
     * Who can walk to the boss's drops: with the boss inside its room, only a player on the room's side of the
     * doorway. Anywhere else (no room, a boss that walked out, a probe's stand) everyone in the level, as before.
     */
    private record Reach(@Nullable net.minecraft.world.level.levelgen.structure.BoundingBox room,
            java.util.Optional<net.minecraft.core.BlockPos> pad, List<net.minecraft.core.BlockPos> gate) {

        static Reach of(ServerLevel level, Mob boss) {
            var room = com.cyberday1.riftfall.boss.RiftBossArena.room(level);
            if (room == null || !room.isInside(boss.blockPosition())) {
                return new Reach(null, java.util.Optional.empty(), List.of());
            }
            // Only read markers already walked; walking them here would build the room's chunks.
            var state = com.cyberday1.riftfall.rift.RiftLevelState.peek(level);
            if (state == null || !state.bossResolved()) {
                return new Reach(room, java.util.Optional.empty(), List.of());
            }
            return new Reach(room, com.cyberday1.riftfall.boss.RiftBossArena.pad(level),
                    com.cyberday1.riftfall.boss.RiftBossArena.gate(level));
        }

        boolean reaches(ServerLevel level, ServerPlayer player) {
            if (room == null) return true;
            return player.level() == level && com.cyberday1.riftfall.boss.BossGatePull.inside(
                    player.getX(), player.getY(), player.getZ(), room, pad, gate, 0.0);
        }
    }

    /** Everyone in the rift who is playing it: a rift level is one run, so this is the party that is still in. */
    public static List<ServerPlayer> participants(ServerLevel level) {
        return level.players().stream().filter(p -> p.isAlive() && !p.isSpectator()).toList();
    }

    /** A Shattered World Shard from this kill carries the fallen world's core. */
    static ItemStack stamp(ItemStack stack, ServerLevel level, Mob boss) {
        if (stack.is(com.cyberday1.riftfall.init.RiftfallItems.SHATTERED_WORLD_SHARD.get()))
            com.cyberday1.riftfall.lore.ShardOrigin.at(level, boss).ifPresent(origin ->
                    stack.set(com.cyberday1.riftfall.init.RiftfallDataComponents.SHARD_ORIGIN.get(), origin));
        return stack;
    }

    private static ItemEntity reward(Mob boss, ItemStack stack) {
        return RiftMobDrops.reward(() -> boss.spawnAtLocation(stack));
    }
}
