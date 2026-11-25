package com.cyberday1.riftfall.client;

import com.cyberday1.riftfall.lore.LoreNotes;
import com.cyberday1.riftfall.network.NotesPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** The client's copy of the Recovered Notes view, and the book view for a note the server says to show. */
public final class NotesClient {
    private static NotesPayload latest = NotesPayload.EMPTY;
    private static boolean answered;

    private NotesClient() {}

    public static void told(NotesPayload payload) {
        latest = payload;
        answered = true;
    }

    static NotesPayload latest() { return latest; }
    static boolean answered() { return answered; }
    static void asked() { answered = false; }

    static void forget() {
        latest = NotesPayload.EMPTY;
        answered = false;
    }

    /** A note's text as book pages, or its sign lines as one page. */
    public static List<Component> pages(LoreNotes.Note note) {
        var pages = new ArrayList<Component>();
        if (note.sign()) {
            var text = Component.empty();
            for (int i = 0; i < note.lines(); i++) {
                if (i > 0) text.append("\n");
                text.append(Component.translatable(note.lineKey(i)));
            }
            pages.add(text);
        } else {
            for (int i = 0; i < note.pages(); i++) pages.add(Component.translatable(note.pageKey(i)));
        }
        return pages;
    }

    /** The note's title, or the start of its text when the draft gave it none. */
    public static Component label(LoreNotes.Note note) {
        if (note.titled()) return Component.translatable(note.titleKey());
        var first = Component.translatable(note.sign() ? note.lineKey(0) : note.pageKey(0)).getString();
        int cut = first.indexOf('\n');
        return Component.literal(cut < 0 ? first : first.substring(0, cut));
    }

    /** A first read: the "Note recovered" toast. */
    public static void found(String id) {
        LoreNotes.note(id).ifPresent(note -> Minecraft.getInstance().getToasts().addToast(new NoteFoundToast(note)));
    }

    public static void open(String id) {
        LoreNotes.note(id).ifPresent(note ->
                Minecraft.getInstance().setScreen(new BookViewScreen(new BookViewScreen.BookAccess(pages(note)))));
    }
}
