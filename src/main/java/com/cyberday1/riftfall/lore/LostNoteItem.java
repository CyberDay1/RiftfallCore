package com.cyberday1.riftfall.lore;

import com.cyberday1.riftfall.init.RiftfallDataComponents;
import com.cyberday1.riftfall.init.RiftfallItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A story note found in the home. The stack names its note; title, author and pages are lang keys, and the book
 * content (which lecterns and the vanilla book view read) is rebuilt from the catalog, so a rewrite never needs
 * a stack replaced. Using it reads it.
 */
public class LostNoteItem extends Item {
    public LostNoteItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    public static ItemStack of(LoreNotes.Note note) {
        var stack = new ItemStack(RiftfallItems.LOST_NOTE.get());
        stack.set(RiftfallDataComponents.NOTE_ID.get(), note.id());
        refresh(stack);
        return stack;
    }

    public static Optional<LoreNotes.Note> note(ItemStack stack) {
        var id = stack.get(RiftfallDataComponents.NOTE_ID.get());
        return id == null ? Optional.empty() : LoreNotes.note(id);
    }

    /** Translatable pages for the vanilla book view; true when the stack changed. */
    public static boolean refresh(ItemStack stack) {
        var note = note(stack);
        if (note.isEmpty() || note.get().sign()) return false;
        var current = stack.get(DataComponents.WRITTEN_BOOK_CONTENT);
        if (current != null && current.pages().size() == note.get().pages()) return false;
        var pages = new ArrayList<Filterable<Component>>();
        for (int i = 0; i < note.get().pages(); i++) pages.add(Filterable.passThrough(Component.translatable(note.get().pageKey(i))));
        stack.set(DataComponents.WRITTEN_BOOK_CONTENT,
                new WrittenBookContent(Filterable.passThrough(note.get().id()), "", 0, pages, true));
        return true;
    }

    /** The note's title, or its look's plain name when the draft gave it none. */
    public static Component title(LoreNotes.Note note) {
        return Component.translatable(note.titled() ? note.titleKey() : "item.riftfall.lost_note." + note.look().id());
    }

    @Override
    public Component getName(ItemStack stack) {
        return note(stack).map(LostNoteItem::title).orElseGet(() -> super.getName(stack));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        note(stack).ifPresent(note -> {
            if (note.authored()) lines.add(Component.translatable(note.authorKey()).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
            lines.add(Component.translatable("item.riftfall.lost_note.hint").withStyle(ChatFormatting.DARK_GRAY));
        });
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        var stack = player.getItemInHand(hand);
        var note = note(stack);
        if (note.isEmpty()) return InteractionResultHolder.pass(stack);
        if (player instanceof ServerPlayer server) NoteFinds.read(server, note.get(), true);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (!level.isClientSide() && level.getGameTime() % 40 == 0) refresh(stack);
    }
}
