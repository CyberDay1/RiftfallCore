package com.cyberday1.riftfall.lore;

import com.cyberday1.riftfall.RiftfallCore;
import com.cyberday1.riftfall.home.HomeAccess;
import com.cyberday1.riftfall.home.ShatteredHomeLayout;
import com.cyberday1.riftfall.home.ShatteredHomeLevels;
import com.cyberday1.riftfall.home.ShatteredHomeProgress;
import com.cyberday1.riftfall.home.npc.depth.ResidentProgress;
import com.cyberday1.riftfall.init.RiftfallItems;
import com.cyberday1.riftfall.network.NoteFoundPayload;
import com.cyberday1.riftfall.network.NoteOpenPayload;
import com.cyberday1.riftfall.network.NotesPayload;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import org.jetbrains.annotations.Nullable;
import java.util.Optional;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Which story notes each player has found. Found means read: a note used from the hand, opened on its lectern,
 * read off its frame, or a sign note looked at up close or clicked. Stored per player in ResidentProgress;
 * later work gates on {@link #found(MinecraftServer, UUID, String)} or listens for {@link NoteFoundEvent}.
 */
@EventBusSubscriber(modid = RiftfallCore.MODID)
public final class NoteFinds {
    /** Posted the first time a player reads a note. */
    public static final class NoteFoundEvent extends Event {
        private final ServerPlayer player;
        private final LoreNotes.Note note;
        public NoteFoundEvent(ServerPlayer player, LoreNotes.Note note) { this.player = player; this.note = note; }
        public ServerPlayer player() { return player; }
        public LoreNotes.Note note() { return note; }
    }

    private static final double SIGN_READ_RANGE = 4.5;
    /** Where note payloads go; the rig probe swaps it to watch them. */
    public static java.util.function.BiConsumer<ServerPlayer, net.minecraft.network.protocol.common.custom.CustomPacketPayload> SEND =
            PacketDistributor::sendToPlayer;

    private NoteFinds() {}

    public static boolean found(MinecraftServer server, UUID player, String id) {
        return ResidentProgress.of(server).player(player).notes().contains(id);
    }

    public static Set<String> found(MinecraftServer server, UUID player) {
        return ResidentProgress.of(server).player(player).notes();
    }

    /** Records a read and, when {@code open}, shows the note. True when it was new to this player. */
    public static boolean read(ServerPlayer player, LoreNotes.Note note, boolean open) {
        if (open && !note.sign()) SEND.accept(player, new NoteOpenPayload(note.id()));
        var progress = ResidentProgress.of(player.server);
        if (!progress.player(player.getUUID()).readNote(note.id())) return false;
        progress.setDirty();
        SEND.accept(player, new NoteFoundPayload(note.id()));
        NeoForge.EVENT_BUS.post(new NoteFoundEvent(player, note));
        return true;
    }

    /** What the Recovered Notes page shows this player. */
    public static NotesPayload payload(ServerPlayer player) {
        var server = player.server;
        var found = found(server, player.getUUID());
        var known = new LinkedHashSet<String>();
        boolean wild = false;
        var level = server.getLevel(ShatteredHomeLevels.key(HomeAccess.homeOwner(server, player.getUUID())));
        if (level != null) {
            var progress = ShatteredHomeProgress.of(level);
            if (ShatteredHomeLayout.supports(progress.layoutVersion())) {
                for (var grid : progress.unlockedTiles()) {
                    var tile = ShatteredHomeLayout.at(progress.layoutVersion(), grid);
                    if (tile.isPresent()) known.add(tile.get().id());
                    else wild = true;
                }
            }
        }
        for (var id : found) {
            var note = LoreNotes.note(id);
            if (note.isEmpty()) continue;
            if (note.get().wild()) wild = true;
            else known.add(note.get().tile());
        }
        return new NotesPayload(found.stream().toList(), known.stream().toList(), wild);
    }

    public static void view(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, payload(player));
    }

    @SubscribeEvent
    public static void rightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        var framed = framedItem(event.getLevel().getBlockEntity(event.getPos())).filter(s -> s.is(RiftfallItems.LOST_NOTE.get()));
        if (framed.isPresent()) {
            // Fast Item Frames turns a frame into a block; read it there too, never turn or take it.
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
        }
        if (!(event.getEntity() instanceof ServerPlayer player) || event.getHand() != InteractionHand.MAIN_HAND) return;
        var be = player.level().getBlockEntity(event.getPos());
        if (framed.isPresent()) {
            LostNoteItem.note(framed.get()).ifPresent(note -> read(player, note, true));
        } else if (be instanceof LecternBlockEntity lectern && lectern.getBook().is(RiftfallItems.LOST_NOTE.get())) {
            LostNoteItem.refresh(lectern.getBook());
            LostNoteItem.note(lectern.getBook()).ifPresent(note -> read(player, note, false));
        } else if (be instanceof SignBlockEntity sign) {
            NotePlacement.signNote(sign).ifPresent(note -> read(player, note, false));
        }
    }

    private static final java.util.Map<Class<?>, Optional<java.lang.reflect.Method>> FRAME_ITEM = new java.util.concurrent.ConcurrentHashMap<>();

    /** The item in a Fast Item Frames block (the mod is optional, so read through reflection). */
    public static Optional<ItemStack> framedItem(@Nullable BlockEntity be) {
        if (be == null || !be.getClass().getName().startsWith("fuzs.fastitemframes.")) return Optional.empty();
        var getter = FRAME_ITEM.computeIfAbsent(be.getClass(), c -> {
            try { return Optional.of(c.getMethod("getItem")); } catch (NoSuchMethodException e) { return Optional.empty(); }
        });
        try {
            return getter.isPresent() && getter.get().invoke(be) instanceof ItemStack stack ? Optional.of(stack) : Optional.empty();
        } catch (ReflectiveOperationException e) {
            return Optional.empty();
        }
    }

    /** A framed note is read where it hangs; the click never turns the frame. */
    @SubscribeEvent
    public static void interactFrame(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getTarget() instanceof ItemFrame frame) || !frame.getItem().is(RiftfallItems.LOST_NOTE.get())) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (event.getEntity() instanceof ServerPlayer player && event.getHand() == InteractionHand.MAIN_HAND)
            LostNoteItem.note(frame.getItem()).ifPresent(note -> read(player, note, true));
    }

    /** Signs are read by looking at them; checked twice a second in homes only. */
    @SubscribeEvent
    public static void tick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % 10 != 0
                || !ShatteredHomeLevels.isShatteredHome(player.level().dimension()) || player.isSpectator()) return;
        HitResult hit = player.pick(SIGN_READ_RANGE, 1.0F, false);
        if (hit.getType() != HitResult.Type.BLOCK) return;
        if (player.level().getBlockEntity(((BlockHitResult) hit).getBlockPos()) instanceof SignBlockEntity sign)
            NotePlacement.signNote(sign).ifPresent(note -> {
                if (!found(player.server, player.getUUID(), note.id())) read(player, note, false);
            });
    }
}
