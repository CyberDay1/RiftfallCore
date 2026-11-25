package com.cyberday1.riftfall.client;

import com.cyberday1.riftfall.lore.LoreNotes;
import com.cyberday1.riftfall.network.NotesPayload;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/**
 * The rows of the Recovered Notes page, in unlock order: a header per tile, then its notes found or missing.
 * A tile the home has not restored (and no note found from it) is one "unknown" header and nothing else; the
 * wilderness lists only what was found. Plain JUnit.
 */
final class RecoveredNotes {
    sealed interface Row permits Header, Entry, More {}

    record Header(String tile, int rank, boolean known, int found, int total) implements Row {}

    record Entry(LoreNotes.Note note, boolean found) implements Row {}

    record More(int missing) implements Row {}

    private RecoveredNotes() {}

    static List<Row> rows(NotesPayload view) {
        var found = new HashSet<>(view.found());
        var known = new HashSet<>(view.known());
        var rows = new ArrayList<Row>();
        for (var tile : LoreNotes.tiles()) {
            // Retired notes stay readable if already found, without leaving an unobtainable slot.
            var notes = tile.notes().stream().filter(n -> !n.retired() || found.contains(n.id())).toList();
            int got = (int) notes.stream().filter(n -> found.contains(n.id())).count();
            if (tile.id().equals(LoreNotes.WILDERNESS)) {
                if (!view.wild() && got == 0) continue;
                rows.add(new Header(tile.id(), tile.rank(), true, got, notes.size()));
                notes.stream().filter(n -> found.contains(n.id())).forEach(n -> rows.add(new Entry(n, true)));
                if (got < notes.size()) rows.add(new More(notes.size() - got));
                continue;
            }
            boolean isKnown = known.contains(tile.id()) || got > 0;
            rows.add(new Header(tile.id(), tile.rank(), isKnown, got, notes.size()));
            if (isKnown) notes.forEach(n -> rows.add(new Entry(n, found.contains(n.id()))));
        }
        return List.copyOf(rows);
    }

    /** Notes found out of every note there is (the pool included). */
    static int found(NotesPayload view) {
        return (int) view.found().stream().filter(id -> LoreNotes.note(id).isPresent()).count();
    }

    static int total(NotesPayload view) {
        return (int) LoreNotes.all().stream().filter(n -> !n.retired() || view.found().contains(n.id())).count();
    }
}
