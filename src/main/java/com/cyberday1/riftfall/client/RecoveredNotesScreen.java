package com.cyberday1.riftfall.client;

import com.cyberday1.riftfall.lore.LoreNotes;
import com.cyberday1.riftfall.lore.LostNoteItem;
import com.cyberday1.riftfall.network.NotesRequestPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The Codex's Recovered Notes spread. Left page: every tile in unlock order with its notes found or missing
 * ({@link RecoveredNotes}), unrestored tiles as unknown ground. Right page: a found note, read in place.
 */
public class RecoveredNotesScreen extends ProgressionScreen {
    private static final int L = ProgressionFrame.LEFT;
    private static final int R = ProgressionFrame.RIGHT;
    private static final int T = ProgressionFrame.TOP;
    private static final int W = ProgressionFrame.PAGE_WIDTH;
    private static final int LIST_TOP = T + 26;
    private static final int ROW = 11;
    private static final int LIST_BOTTOM = ProgressionFrame.BOTTOM - 12;
    private static final int TEXT_TOP = T + 34;
    private static final int PITCH = 10;

    private int scroll;
    private @Nullable LoreNotes.Note open;
    private int page;

    public RecoveredNotesScreen() {
        super(ProgressionTab.NOTES);
    }

    @Override
    protected void init() {
        super.init();
        NotesClient.asked();
        PacketDistributor.sendToServer(NotesRequestPayload.INSTANCE);
    }

    private MutableComponent line(String key, Object... args) {
        return Component.translatable(tab.key(key), args);
    }

    private static int visibleRows() {
        return (LIST_BOTTOM - LIST_TOP) / ROW;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int rows = RecoveredNotes.rows(NotesClient.latest()).size();
        scroll = Math.max(0, Math.min(Math.max(0, rows - visibleRows()), scroll - (int) Math.signum(scrollY) * 3));
        return true;
    }

    @Override
    protected void renderPages(GuiGraphics graphics, float partialTick) {
        var view = NotesClient.latest();
        text(graphics, bold(line("heading")), L, T, CodexStyle.ACCENT);
        text(graphics, fit(line("found_of", RecoveredNotes.found(view), RecoveredNotes.total(view)), W), L, T + 11, CodexStyle.MUTED);
        dotted(graphics, L, L + W, T + 22, CodexStyle.DOTS);
        if (!NotesClient.answered()) {
            text(graphics, line("loading"), L, LIST_TOP, CodexStyle.MUTED);
        } else {
            list(graphics, RecoveredNotes.rows(view));
        }
        if (open != null && view.found().contains(open.id())) reader(graphics, open);
        else wrapped(graphics, line("pick_hint"), R, T, W, PITCH, 8, CodexStyle.MUTED);
    }

    private void list(GuiGraphics graphics, List<RecoveredNotes.Row> rows) {
        scroll = Math.max(0, Math.min(scroll, Math.max(0, rows.size() - visibleRows())));
        int shown = Math.min(visibleRows(), rows.size() - scroll);
        for (int i = 0; i < shown; i++) row(graphics, rows.get(scroll + i), LIST_TOP + i * ROW);
        if (scroll + shown < rows.size()) text(graphics, fit(line("scroll_more"), W), L, LIST_BOTTOM + 2, CodexStyle.DIM);
        else if (rows.stream().noneMatch(r -> r instanceof RecoveredNotes.Entry e && e.found()))
            text(graphics, fit(line("none_yet"), W), L, LIST_BOTTOM + 2, CodexStyle.DIM);
    }

    private void row(GuiGraphics graphics, RecoveredNotes.Row row, int y) {
        switch (row) {
            case RecoveredNotes.Header h -> {
                if (!h.known()) {
                    text(graphics, line("unknown_tile"), L, y + 1, CodexStyle.DIM);
                    textRight(graphics, line("rank", h.rank()), L + W, y + 1, CodexStyle.DIM);
                    return;
                }
                text(graphics, fit(bold(line("tile_" + h.tile())), W - 30), L, y + 1, CodexStyle.ACCENT);
                textRight(graphics, line("count", h.found(), h.total()), L + W, y + 1, CodexStyle.MUTED);
            }
            case RecoveredNotes.Entry e -> {
                if (!e.found()) {
                    text(graphics, fit(line("missing"), W - 8), L + 8, y + 1, CodexStyle.DIM);
                    return;
                }
                boolean selected = open != null && open.id().equals(e.note().id());
                boolean hover = hot(L, y, L + W, y + ROW - 1, button -> {
                    if (button != PRIMARY) return false;
                    open = e.note();
                    page = 0;
                    return true;
                });
                if (selected || hover) graphics.fill(L, y, L + W, y + ROW - 1, selected ? CodexStyle.SELECTED : CodexStyle.WELL_LIGHT);
                text(graphics, fit(NotesClient.label(e.note()), W - 10), L + 8, y + 1, CodexStyle.INK);
            }
            case RecoveredNotes.More m -> text(graphics, fit(line("more_out_there", m.missing()), W - 8), L + 8, y + 1, CodexStyle.DIM);
        }
    }

    private void reader(GuiGraphics graphics, LoreNotes.Note note) {
        var pages = NotesClient.pages(note);
        page = Math.max(0, Math.min(page, pages.size() - 1));
        int y = wrapped(graphics, bold(LostNoteItem.title(note)), R, T, W, PITCH, 2, CodexStyle.ACCENT);
        if (note.authored()) text(graphics, fit(Component.translatable(note.authorKey()), W), R, y + 1, CodexStyle.MUTED);
        dotted(graphics, R, R + W, T + 30, CodexStyle.DOTS);
        int maxLines = (ProgressionFrame.BOTTOM - 24 - TEXT_TOP) / PITCH;
        List<FormattedCharSequence> lines = font.split(pages.get(page), W);
        for (int i = 0; i < Math.min(maxLines, lines.size()); i++) {
            var seq = lines.get(i);
            int x = note.sign() ? R + (W - font.width(seq)) / 2 : R;
            graphics.drawString(font, seq, x, TEXT_TOP + i * PITCH, CodexStyle.INK, false);
        }
        if (pages.size() <= 1) return;
        int by = ProgressionFrame.BOTTOM - 18;
        button(graphics, R, by, 24, Component.literal("<"), page > 0, () -> page--);
        button(graphics, R + W - 24, by, 24, Component.literal(">"), page < pages.size() - 1, () -> page++);
        textCenter(graphics, line("page", page + 1, pages.size()), R + W / 2, by + 5, CodexStyle.MUTED);
    }
}
