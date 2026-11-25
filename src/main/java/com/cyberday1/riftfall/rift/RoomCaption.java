package com.cyberday1.riftfall.rift;

import com.cyberday1.riftfall.worldgen.layout.RiftPlan;
import com.cyberday1.riftfall.worldgen.level.RiftChunkGenerator;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Says which authored room you are standing in, on the action bar.
 *
 * <p>
 * The room library is 124 files and a rift is a couple of hundred rooms deep, so "this one is wrong"
 * is not a reportable observation until it comes with a name. Nothing about that needs a client mod:
 * the server owns the plan, and the action bar is a packet the server already sends. Naming a room
 * you are standing in is therefore a lookup, not a feature.
 *
 * <h2>Why a toggle rather than always on</h2>
 * The action bar is one line and everything shares it &mdash; a jukebox, an item name, another mod.
 * A permanent caption would take that line away from the game for the sake of a tool. So this is off
 * until somebody asks for it, per player, and is forgotten when they log out: it is a thing you turn
 * on while fixing rooms, not a setting.
 *
 * <p>
 * The same answer is on the F3 screen unconditionally, from
 * {@code RiftChunkGenerator#addDebugScreenInfo}. This exists because F3 covers the world you are
 * trying to look at, which is the wrong trade when the thing you are looking for is a hole in it.
 */
public final class RoomCaption {

    /**
     * How often the caption is refreshed, in ticks. The action bar holds a message for a good deal
     * longer than this, so the caption never blinks; sending it every tick would be sixty packets a
     * second to say the same thing.
     */
    private static final int INTERVAL = 10;

    private static final Set<UUID> WATCHING = ConcurrentHashMap.newKeySet();

    private RoomCaption() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(RoomCaption::tick);
    }

    /** Turns the caption on or off for one player. Returns the state it ended up in. */
    public static boolean toggle(ServerPlayer player) {
        UUID id = player.getUUID();
        if (WATCHING.remove(id)) {
            return false;
        }
        WATCHING.add(id);
        return true;
    }

    /**
     * The room a player is in, or null when they are not in a rift at all or are standing in the seam
     * between two rooms. Asked of the generator rather than of the dimension id, for the same reason
     * {@link RiftSpawns} does: the shipped {@code riftfall:rift} and a datapack's own rift are both
     * rifts and neither is a temporary instance.
     */
    @Nullable
    public static RiftPlan.PlacedRoom roomAt(ServerLevel level, BlockPos pos) {
        return level.getChunkSource().getGenerator() instanceof RiftChunkGenerator generator
                ? generator.roomAt(level, pos)
                : null;
    }

    private static void tick(ServerTickEvent.Post event) {
        if (WATCHING.isEmpty() || event.getServer().getTickCount() % INTERVAL != 0) {
            return;
        }
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            if (WATCHING.contains(player.getUUID())) {
                player.displayClientMessage(caption(player.serverLevel(), player.blockPosition()), true);
            }
        }
    }

    /**
     * The line itself: the template's path, because the namespace is ours on every room and a
     * fifteen-character prefix repeated on every line is fifteen characters of the room's own name
     * that do not fit.
     */
    private static Component caption(ServerLevel level, BlockPos pos) {
        RiftPlan.PlacedRoom room = roomAt(level, pos);
        if (room == null) {
            return Component.literal("no room here").withStyle(ChatFormatting.DARK_GRAY);
        }
        ResourceLocation template = room.template();
        return Component.literal(template.getPath()).withStyle(ChatFormatting.AQUA)
                .append(Component.literal("  depth " + room.depth()).withStyle(ChatFormatting.DARK_AQUA));
    }
}
