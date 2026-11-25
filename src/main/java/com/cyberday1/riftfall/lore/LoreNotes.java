package com.cyberday1.riftfall.lore;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * The story notes hidden in the Shattered Home: which tile each belongs to, how it looks and how many pages
 * or sign lines it has. The text itself is only in the lang files, keyed by id, so it can be rewritten and
 * translated without touching a tile. Loaded from the jar on both sides; {@code tools/lore/import_notes.py}
 * writes the file from the lore draft.
 */
public final class LoreNotes {
    public static final String WILDERNESS = "wilderness";
    private static final String FILE = "/data/riftfall/lore/notes.json";
    private static final String PREFIX = "lore.riftfall.note.";

    /** How a note looks as an item, which decides its model and whether it has pages or sign lines. */
    public enum Look {
        BOOK, LETTER, CARD, MAP, STICK, CLOCK, SIGN;

        public String id() { return name().toLowerCase(Locale.ROOT); }

        static Look of(String id) { return valueOf(id.toUpperCase(Locale.ROOT)); }
    }

    /** One note. {@code slot} is its number within the tile, the marker's {@code note} value. */
    public record Note(String id, String tile, int rank, int slot, Look look, boolean titled, boolean authored,
                       int pages, int lines) {
        public boolean sign() { return look == Look.SIGN; }
        public boolean wild() { return WILDERNESS.equals(tile); }
        public boolean retired() { return id.equals("sanctuary_2"); }
        public String titleKey() { return PREFIX + id + ".title"; }
        public String authorKey() { return PREFIX + id + ".author"; }
        public String pageKey(int page) { return PREFIX + id + ".page." + page; }
        public String lineKey(int line) { return PREFIX + id + ".line." + line; }
    }

    /** A tile with notes, in unlock order; the wilderness pool is last. */
    public record Tile(String id, int rank, List<Note> notes) {}

    private static final Catalog CATALOG = load();

    private record Catalog(Map<String, Note> notes, List<Tile> tiles, List<Note> wilderness, Map<String, String> english) {}

    private LoreNotes() {}

    public static List<Tile> tiles() { return CATALOG.tiles; }
    public static List<Note> wilderness() { return CATALOG.wilderness; }
    public static Optional<Note> note(String id) { return Optional.ofNullable(CATALOG.notes.get(id)); }
    public static java.util.Collection<Note> all() { return CATALOG.notes.values(); }

    public static Optional<Note> note(String tile, int slot) {
        return CATALOG.tiles.stream().filter(t -> t.id().equals(tile)).flatMap(t -> t.notes().stream())
                .filter(n -> n.slot() == slot).findFirst();
    }

    /** The English for every note key, in file order, for the lang provider. */
    public static Map<String, String> english() { return CATALOG.english; }

    private static Catalog load() {
        try (var stream = LoreNotes.class.getResourceAsStream(FILE)) {
            if (stream == null) throw new IllegalStateException("Missing " + FILE);
            var root = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            if (root.get("version").getAsInt() != 1) throw new IllegalStateException("Unsupported lore notes");
            var notes = new LinkedHashMap<String, Note>();
            var english = new LinkedHashMap<String, String>();
            var tiles = new LinkedHashMap<String, List<Note>>();
            var ranks = new LinkedHashMap<String, Integer>();
            for (var value : root.getAsJsonArray("notes")) {
                var note = read(value.getAsJsonObject(), english);
                tiles.computeIfAbsent(note.tile(), k -> new ArrayList<>()).add(note);
                ranks.merge(note.tile(), note.rank(), Math::min);
                if (notes.put(note.id(), note) != null) throw new IllegalStateException("Duplicate note " + note.id());
            }
            var wild = new ArrayList<Note>();
            for (var value : root.getAsJsonArray("wilderness")) {
                var note = read(value.getAsJsonObject(), english);
                if (!note.wild()) throw new IllegalStateException("Wilderness note outside the pool: " + note.id());
                wild.add(note);
                if (notes.put(note.id(), note) != null) throw new IllegalStateException("Duplicate note " + note.id());
            }
            var ordered = new ArrayList<Tile>();
            tiles.forEach((id, list) -> {
                if (list.stream().map(Note::slot).distinct().count() != list.size())
                    throw new IllegalStateException("Duplicate slot on " + id);
                ordered.add(new Tile(id, ranks.get(id), List.copyOf(list)));
            });
            ordered.sort(java.util.Comparator.comparingInt(Tile::rank));
            ordered.add(new Tile(WILDERNESS, Integer.MAX_VALUE, List.copyOf(wild)));
            return new Catalog(Collections.unmodifiableMap(notes), List.copyOf(ordered), List.copyOf(wild),
                    Collections.unmodifiableMap(english));
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read " + FILE, e);
        }
    }

    private static Note read(JsonObject n, Map<String, String> english) {
        String id = n.get("id").getAsString();
        if (!id.matches("[a-z0-9_]+")) throw new IllegalStateException("Bad note id " + id);
        var look = Look.of(n.get("look").getAsString());
        JsonArray pages = n.has("pages") ? n.getAsJsonArray("pages") : new JsonArray();
        JsonArray lines = n.has("lines") ? n.getAsJsonArray("lines") : new JsonArray();
        int slot = n.get("slot").getAsInt();
        if (look == Look.SIGN ? lines.isEmpty() || lines.size() > 4 || !pages.isEmpty() : pages.isEmpty() || !lines.isEmpty())
            throw new IllegalStateException("Note " + id + " has the wrong text for a " + look.id());
        var note = new Note(id, n.get("tile").getAsString(), n.has("rank") ? n.get("rank").getAsInt() : Integer.MAX_VALUE,
                slot, look, n.has("title"), n.has("author"), pages.size(), lines.size());
        if (n.has("title")) english.put(note.titleKey(), n.get("title").getAsString());
        if (n.has("author")) english.put(note.authorKey(), n.get("author").getAsString());
        for (int i = 0; i < pages.size(); i++) english.put(note.pageKey(i), pages.get(i).getAsString());
        for (int i = 0; i < lines.size(); i++) english.put(note.lineKey(i), lines.get(i).getAsString());
        return note;
    }
}
