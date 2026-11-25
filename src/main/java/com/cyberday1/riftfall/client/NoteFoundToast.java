package com.cyberday1.riftfall.client;

import com.cyberday1.riftfall.lore.LoreNotes;
import com.cyberday1.riftfall.lore.LostNoteItem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import net.minecraft.network.chat.Component;

/** "Note recovered" with the note's title (or first line), on the Codex's flat stellar palette. */
public final class NoteFoundToast implements Toast {
    private static final long SHOWN = 5000L;
    private static final int WIDTH = 176;
    private final LoreNotes.Note note;

    NoteFoundToast(LoreNotes.Note note) {
        this.note = note;
    }

    @Override public int width() { return WIDTH; }

    @Override
    public Visibility render(GuiGraphics graphics, ToastComponent toasts, long time) {
        var font = toasts.getMinecraft().font;
        graphics.fill(0, 0, WIDTH, height(), CodexStyle.COVER_OUTLINE);
        graphics.fill(1, 1, WIDTH - 1, height() - 1, CodexStyle.PAGE);
        graphics.fill(1, 1, 3, height() - 1, CodexStyle.ACCENT);
        graphics.renderFakeItem(LostNoteItem.of(note), 7, 8);
        int x = 29, w = WIDTH - x - 5;
        graphics.drawString(font, Component.translatable("toast.riftfall.note_found"), x, 5, CodexStyle.ACCENT, false);
        graphics.drawString(font, fit(font, NotesClient.label(note), w), x, 15, CodexStyle.INK, false);
        graphics.drawString(font, fit(font, Component.translatable("toast.riftfall.note_found.hint"), w), x, 24, CodexStyle.DIM, false);
        return time >= SHOWN * toasts.getNotificationDisplayTimeMultiplier() ? Visibility.HIDE : Visibility.SHOW;
    }

    @Override public int height() { return 34; }

    private static Component fit(net.minecraft.client.gui.Font font, Component line, int width) {
        if (font.width(line) <= width) return line;
        return Component.literal(font.substrByWidth(line, width - font.width("..")).getString() + "..");
    }
}
