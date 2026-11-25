package com.cyberday1.riftfall.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns ATTRIBUTION.md into the credits roll's data file, the one source CreditsScreen reads.
 * Plain Java and Gson, so the drift test can re-run it without a game.
 */
public final class AttributionCredits {
    public static final String FILE = "ATTRIBUTION.md";
    /** Under the resource pack root. */
    public static final String OUTPUT = "assets/riftfall/credits/credits.json";
    public static final String KEY = "screen.riftfall.credits.";
    private static final String SKY = "screen.riftfall.sky_credits.";

    private static final Pattern TABLE_ROW = Pattern.compile("^\\|\\s*([^|\\s]+)\\s*\\|\\s*(\\d+)\\s*\\|\\s*$");
    private static final Pattern BUILDER_HEADING = Pattern.compile("^(\\S+)\\s*\\((\\d+)\\)$");
    private static final Pattern SUMMARY = Pattern.compile("\\*\\*(\\d+) authored pieces\\*\\* across \\*\\*(\\d+) builders\\*\\*");
    private static final Pattern TICKED = Pattern.compile("`([^`]+)`");
    private static final Pattern NUMBERED = Pattern.compile("^\\d+([a-z]*)$");
    private static final Set<String> TAGS = Set.of("pb", "v2", "temp", "template");
    private static final Set<String> PLACEHOLDERS = Set.of("namehere");
    private static final Map<String, String> TYPOS = Map.ofEntries(
            Map.entry("sculc", "sculk"), Map.entry("skulk", "sculk"), Map.entry("skult", "sculk"),
            Map.entry("lampost", "lamppost"), Map.entry("pris", "prismarine"),
            Map.entry("fallingfoor", "falling floor"), Map.entry("opoen", "open"), Map.entry("forst", "forest"),
            Map.entry("labrynth", "labyrinth"), Map.entry("chadelier", "chandelier"),
            Map.entry("outdoordinning", "outdoor dining"), Map.entry("hobit", "hobbit"), Map.entry("pat", "path"));

    private AttributionCredits() {}

    /** The table's builders in order, with counts; what the drift test holds the JSON to. */
    public record Parsed(Map<String, Integer> table, int statedTotal, int statedBuilders,
                         Map<String, List<String>> files, Map<String, Integer> headingCounts,
                         Map<String, List<String>> derived,
                         List<String> undetermined, List<Section> notes, String permission, String licence,
                         List<String> builtHere, List<Section> models) {}

    public record Section(String title, List<String> lines) {}

    /** A markdown file as headings over paragraphs, bullets and table rows. */
    private record Block(int level, String title, List<String> paragraphs, List<String> bullets, List<String> rows,
                         List<Block> children) {
        Block(int level, String title) { this(level, title, new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>()); }

        Block child(String prefix) {
            for (var c : children) if (c.title.toLowerCase(Locale.ROOT).startsWith(prefix)) return c;
            return null;
        }
    }

    public static Path locate() {
        Path here = Paths.get("").toAbsolutePath();
        for (int i = 0; i < 6 && here != null; i++, here = here.getParent()) {
            Path candidate = here.resolve(FILE);
            if (Files.isRegularFile(candidate) && Files.isDirectory(here.resolve("src/main/resources"))) return candidate;
        }
        throw new IllegalStateException("Could not find " + FILE + " above " + Paths.get("").toAbsolutePath());
    }

    public static JsonObject generate(Path attribution) {
        try {
            return roll(parse(Files.readString(attribution, StandardCharsets.UTF_8)));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static Parsed parse(String markdown) {
        List<Block> docs = blocks(markdown);
        Block structure = null, models = null;
        for (var doc : docs) {
            String title = doc.title.toLowerCase(Locale.ROOT);
            if (structure == null && title.contains("structure")) structure = doc;
            else if (models == null && title.contains("model")) models = doc;
        }
        if (structure == null) throw new IllegalStateException(FILE + ": no structure-templates section");

        Map<String, Integer> table = new LinkedHashMap<>();
        Block builders = require(structure, "builders");
        for (var row : builders.rows) {
            Matcher m = TABLE_ROW.matcher(row);
            if (m.matches()) table.put(m.group(1), Integer.parseInt(m.group(2)));
        }
        if (table.isEmpty()) throw new IllegalStateException(FILE + ": the Builders table is empty");
        Set<String> handles = handles(table.keySet());

        Map<String, List<String>> files = new LinkedHashMap<>();
        Map<String, Integer> headingCounts = new LinkedHashMap<>();
        Map<String, List<String>> derived = new LinkedHashMap<>();
        for (var builder : require(structure, "pieces by builder").children) {
            Matcher m = BUILDER_HEADING.matcher(builder.title);
            if (!m.matches()) throw new IllegalStateException(FILE + ": unreadable builder heading '" + builder.title + "'");
            List<String> listed = new ArrayList<>();
            List<String> notes = new ArrayList<>();
            for (var bullet : builder.bullets) readBullet(bullet, listed, notes, handles);
            files.put(m.group(1), listed);
            derived.put(m.group(1), notes);
            headingCounts.put(m.group(1), Integer.parseInt(m.group(2)));
        }

        int total = -1, count = -1;
        String permission = null, licence = null;
        List<String> builtHere = List.of();
        for (var p : structure.paragraphs) {
            Matcher m = SUMMARY.matcher(p);
            if (m.find()) {
                total = Integer.parseInt(m.group(1));
                count = Integer.parseInt(m.group(2));
            } else if (p.startsWith("Templates Riftfall Core builds itself")) builtHere = builtHere(p);
            else if (permission == null && p.contains("permission")) permission = prose(p, false, handles);
            else if (licence == null && p.toLowerCase(Locale.ROOT).contains("licen")) licence = prose(p, false, handles);
        }
        if (total < 0) throw new IllegalStateException(FILE + ": no '**N authored pieces** across **M builders**' line");
        if (permission == null || licence == null) throw new IllegalStateException(FILE + ": the permission or licence paragraph is missing");
        if (builtHere.isEmpty()) throw new IllegalStateException(FILE + ": the 'Templates Riftfall Core builds itself' paragraph is missing");

        List<String> undetermined = new ArrayList<>();
        Block unknown = structure.child("undetermined");
        if (unknown != null) for (var b : unknown.bullets) undetermined.addAll(ticked(b));

        List<Section> notes = new ArrayList<>();
        for (var section : structure.children) {
            String t = section.title.toLowerCase(Locale.ROOT);
            if (t.startsWith("builders") || t.startsWith("pieces by builder") || t.startsWith("undetermined")
                    || t.startsWith("convention")) continue;
            List<String> lines = new ArrayList<>();
            section.paragraphs.forEach(p -> lines.add(prose(p, true, handles)));
            section.bullets.forEach(b -> lines.add(prose(b, true, handles)));
            for (var sub : section.children) {
                lines.add(prose(sub.title, true, handles));
                sub.paragraphs.forEach(p -> lines.add(prose(p, true, handles)));
                sub.bullets.forEach(b -> lines.add(prose(b, true, handles)));
            }
            lines.removeIf(String::isBlank);
            if (!lines.isEmpty()) notes.add(new Section(prose(section.title, true, handles), lines));
        }

        List<Section> modelCredits = new ArrayList<>();
        if (models != null) {
            for (var model : models.children) {
                List<String> lines = new ArrayList<>();
                for (var b : model.bullets) {
                    if (b.startsWith("**Licence:**") || b.startsWith("**License:**")) {
                        String licenceText = prose(b.substring(b.indexOf(":**") + 3), false, handles);
                        int url = licenceText.indexOf(": http");
                        lines.add((url > 0 ? licenceText.substring(0, url) : licenceText).trim());
                    }
                }
                modelCredits.add(new Section(prose(model.title, true, handles), lines));
            }
        }
        return new Parsed(table, total, count, files, headingCounts, derived, undetermined, notes, permission, licence,
                builtHere, modelCredits);
    }

    /** The roll, top to bottom. Fixed parts here; everything about the pieces from the parse. */
    static JsonObject roll(Parsed parsed) {
        int sum = parsed.table.values().stream().mapToInt(Integer::intValue).sum();
        if (sum != parsed.statedTotal || parsed.table.size() != parsed.statedBuilders) {
            throw new IllegalStateException(FILE + " says " + parsed.statedTotal + " pieces by " + parsed.statedBuilders
                    + " builders, but its table adds up to " + sum + " by " + parsed.table.size());
        }
        Set<String> handles = handles(parsed.table.keySet());
        JsonArray roll = new JsonArray();
        roll.add(entry("title", null, "Riftfall"));
        roll.add(entry("line", KEY + "created_by", null));
        roll.add(entry("name", null, "CyberDay1"));
        roll.add(gap());
        roll.add(entry("heading", KEY + "wotr", null));
        roll.add(entry("note", null, parsed.permission));
        roll.add(entry("note", null, parsed.licence));
        JsonObject total = entry("line", KEY + "total", null);
        JsonArray args = new JsonArray();
        args.add(String.valueOf(parsed.statedTotal));
        args.add(String.valueOf(parsed.statedBuilders));
        total.add("args", args);
        roll.add(total);
        roll.add(gap());
        parsed.table.forEach((name, pieces) -> {
            JsonObject builder = new JsonObject();
            builder.addProperty("kind", "builder");
            builder.addProperty("name", name);
            builder.addProperty("pieces", pieces);
            JsonArray builds = new JsonArray();
            names(parsed.files.getOrDefault(name, List.of()), handles).forEach(builds::add);
            builder.add("builds", builds);
            List<String> derivedNotes = parsed.derived.getOrDefault(name, List.of());
            if (!derivedNotes.isEmpty()) {
                JsonArray notes = new JsonArray();
                derivedNotes.forEach(notes::add);
                builder.add("notes", notes);
            }
            roll.add(builder);
        });
        if (!parsed.undetermined.isEmpty()) {
            roll.add(gap());
            roll.add(entry("heading", KEY + "unidentified", null));
            JsonObject list = new JsonObject();
            list.addProperty("kind", "list");
            JsonArray items = new JsonArray();
            names(parsed.undetermined, handles).forEach(items::add);
            list.add("items", items);
            roll.add(list);
            roll.add(entry("note", KEY + "unidentified.note", null));
        }
        for (var note : parsed.notes) {
            roll.add(gap());
            roll.add(entry("heading", null, note.title));
            note.lines.forEach(line -> roll.add(entry("note", null, line)));
        }
        roll.add(gap());
        roll.add(entry("heading", KEY + "built_for", null));
        parsed.builtHere.forEach(item -> roll.add(entry("line", null, item)));
        if (!parsed.models.isEmpty()) {
            roll.add(gap());
            roll.add(entry("heading", KEY + "models", null));
            for (var model : parsed.models) {
                roll.add(entry("line", null, model.title));
                model.lines.forEach(line -> roll.add(entry("note", null, line)));
            }
        }
        roll.add(gap());
        roll.add(entry("heading", KEY + "sky", null));
        for (var key : new String[] {"stars", "stars_map", "carina"}) roll.add(entry("line", SKY + key, null));
        for (var key : new String[] {"adapted", "no_endorsement"}) roll.add(entry("note", SKY + key, null));
        roll.add(entry("end", KEY + "end", null));

        JsonObject out = new JsonObject();
        out.addProperty("source", FILE);
        out.addProperty("total_pieces", parsed.statedTotal);
        out.addProperty("builder_count", parsed.statedBuilders);
        out.add("roll", roll);
        return out;
    }

    /** Distinct readable names, alphabetical. */
    static List<String> names(List<String> files, Set<String> handles) {
        Map<String, String> byKey = new TreeMap<>();
        for (var file : files) {
            String name = readable(file, handles);
            if (name.isEmpty()) continue;
            // Near-duplicates share a key; the spaced spelling reads better than "Twocaves".
            byKey.merge(name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", ""), name,
                    (kept, other) -> other.split(" ").length > kept.split(" ").length ? other : kept);
        }
        List<String> out = new ArrayList<>(byKey.values());
        out.sort(String.CASE_INSENSITIVE_ORDER);
        return out;
    }

    /** {@code pris_fountain_pb-2-k_jyu.nbt} to "Prismarine Fountain"; empty for a placeholder name. */
    static String readable(String file, Set<String> handles) {
        String base = file.substring(file.lastIndexOf('/') + 1);
        if (base.endsWith(".nbt")) base = base.substring(0, base.length() - 4);
        String[] tokens = base.toLowerCase(Locale.ROOT).split("[-_.]+");
        int cut = -1;
        for (int i = 1; i < tokens.length && cut < 0; i++) {
            Matcher m = NUMBERED.matcher(tokens[i]);
            if (m.matches() && (m.group(1).isEmpty() || handles.contains(m.group(1)))) cut = i;
        }
        List<String> words = new ArrayList<>(List.of(tokens).subList(0, cut < 0 ? tokens.length : cut));
        if (cut < 0) {
            while (words.size() > 1 && handles.contains(words.get(words.size() - 1))) words.remove(words.size() - 1);
            while (words.size() > 1 && handles.contains(words.get(0))) words.remove(0);
            int last = words.size() - 1;
            String trimmed = words.get(last).replaceAll("\\d+$", "");
            if (!trimmed.isEmpty()) words.set(last, trimmed);
        }
        words.removeIf(w -> w.isEmpty() || TAGS.contains(w));
        StringBuilder out = new StringBuilder();
        for (var word : words) {
            if (PLACEHOLDERS.contains(word)) return "";
            for (var part : TYPOS.getOrDefault(word, word).split(" ")) {
                if (out.length() > 0) out.append(' ');
                out.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
            }
        }
        return out.toString();
    }

    /** Builder handles and their parts, as file names carry them ({@code wh4i3_kiara} also as wh4i3, kiara). */
    static Set<String> handles(java.util.Collection<String> builders) {
        Set<String> out = new java.util.HashSet<>();
        for (var builder : builders) {
            String handle = builder.toLowerCase(Locale.ROOT);
            out.add(handle);
            out.addAll(List.of(handle.split("_")));
        }
        return out;
    }

    /** "the approach landing (`...`), the boss arena ..." to ["Approach landing", "Boss arena", ...]. */
    private static List<String> builtHere(String paragraph) {
        String list = paragraph.substring(paragraph.indexOf(':') + 1);
        list = list.replaceAll("`[^`]*`", "").replaceAll("\\([^)]*\\)", "");
        List<String> out = new ArrayList<>();
        for (var raw : list.split(",")) {
            String item = raw.trim().replaceAll("[.\\s]+$", "").replaceFirst("^and\\s+", "").replaceFirst("^the\\s+", "");
            String previous;
            do {
                previous = item;
                item = item.replaceAll("\\s+(under|written|by|in|from)$", "").trim();
            } while (!item.equals(previous));
            if (item.isEmpty() || item.matches("(?i)(written|by|under)(\\s.*)?")) continue;
            out.add(Character.toUpperCase(item.charAt(0)) + item.substring(1));
        }
        return out;
    }

    /** Markdown to a plain line: no emphasis, no URLs; .nbt paths become readable names. */
    static String prose(String markdown, boolean keepTicked, Set<String> handles) {
        String text = markdown.replaceAll("\\s*\\(<[^>]+>\\)", "").replaceAll("<(https?://[^>]+)>", "$1")
                .replaceAll("\\[([^]]+)]\\([^)]+\\)", "$1").replace("**", "");
        if (!keepTicked) {
            StringBuilder kept = new StringBuilder();
            for (var sentence : text.split("(?<=[.!?])\\s+")) {
                if (sentence.contains("`")) continue;
                if (kept.length() > 0) kept.append(' ');
                kept.append(sentence);
            }
            text = kept.toString();
        }
        Matcher m = TICKED.matcher(text);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String inner = m.group(1);
            m.appendReplacement(out, Matcher.quoteReplacement(inner.endsWith(".nbt") ? readable(inner, handles) : inner));
        }
        m.appendTail(out);
        return out.toString().replaceAll("\\s+", " ").trim();
    }

    /**
     * Files outside parentheses count as pieces. A parenthesis that names another .nbt is a derived-room note
     * ("a modified copy of ..."), kept as "Name Variant: note"; one that names none ("currently not placed") is dropped.
     */
    private static void readBullet(String bullet, List<String> listed, List<String> notes, Set<String> handles) {
        StringBuilder outside = new StringBuilder();
        int depth = 0, open = 0;
        boolean ticked = false;
        for (int i = 0; i < bullet.length(); i++) {
            char c = bullet.charAt(i);
            if (c == '`') ticked = !ticked;
            if (!ticked && c == '(' && depth++ == 0) open = i;
            else if (!ticked && c == ')' && depth > 0 && --depth == 0) {
                String inner = bullet.substring(open + 1, i);
                List<String> before = ticked(outside.toString());
                if (inner.contains(".nbt`") && !before.isEmpty()) {
                    String file = before.get(before.size() - 1);
                    notes.add(variantName(file, handles) + ": " + prose(inner, true, handles));
                }
            } else if (depth == 0) outside.append(c);
        }
        for (var t : ticked(outside.toString())) if (t.endsWith(".nbt")) listed.add(t);
    }

    /** {@code hub-hub-bub-4-mine.nbt} to "Hub Hub Bub Mine": the readable name plus any non-handle tail. */
    static String variantName(String file, Set<String> handles) {
        String name = readable(file, handles);
        String base = file.substring(file.lastIndexOf('/') + 1).replaceFirst("\\.nbt$", "");
        String[] tokens = base.toLowerCase(Locale.ROOT).split("[-_.]+");
        StringBuilder out = new StringBuilder(name);
        boolean tail = false;
        for (int i = 1; i < tokens.length; i++) {
            if (!tail) {
                tail = NUMBERED.matcher(tokens[i]).matches();
                continue;
            }
            String t = tokens[i];
            if (handles.contains(t) || TAGS.contains(t) || t.matches("\\d+")) continue;
            out.append(' ').append(Character.toUpperCase(t.charAt(0))).append(t.substring(1));
        }
        return out.toString();
    }

    private static List<String> ticked(String line) {
        List<String> out = new ArrayList<>();
        Matcher m = TICKED.matcher(line);
        while (m.find()) out.add(m.group(1));
        return out;
    }

    private static Block require(Block parent, String prefix) {
        Block block = parent.child(prefix);
        if (block == null) throw new IllegalStateException(FILE + ": no '" + prefix + "' section");
        return block;
    }

    private static JsonObject entry(String kind, String key, String text) {
        JsonObject e = new JsonObject();
        e.addProperty("kind", kind);
        if (key != null) e.addProperty("key", key);
        if (text != null) e.addProperty("text", text);
        return e;
    }

    private static JsonObject gap() {
        return entry("gap", null, null);
    }

    private static List<Block> blocks(String markdown) {
        List<Block> docs = new ArrayList<>();
        Block[] open = new Block[4];
        StringBuilder paragraph = new StringBuilder();
        boolean inBullet = false;
        for (String raw : markdown.replace("\r", "").split("\n", -1)) {
            String line = raw.strip();
            int level = line.startsWith("### ") ? 3 : line.startsWith("## ") ? 2 : line.startsWith("# ") ? 1 : 0;
            Block current = open[3] != null ? open[3] : open[2] != null ? open[2] : open[1];
            if (level > 0 || line.isEmpty() || line.startsWith("- ") || line.startsWith("|") || line.startsWith(">")) {
                if (paragraph.length() > 0 && current != null) current.paragraphs.add(paragraph.toString());
                paragraph.setLength(0);
            }
            if (level > 0) {
                Block block = new Block(level, line.substring(level + 1).trim());
                if (level == 1) docs.add(block);
                else if (open[level - 1] != null) open[level - 1].children.add(block);
                open[level] = block;
                for (int l = level + 1; l < 4; l++) open[l] = null;
                inBullet = false;
            } else if (current == null || line.isEmpty() || line.startsWith(">")) {
                inBullet = false;
            } else if (line.startsWith("- ")) {
                current.bullets.add(line.substring(2).trim());
                inBullet = true;
            } else if (line.startsWith("|")) {
                current.rows.add(line);
            } else if (inBullet && raw.startsWith(" ")) {
                int last = current.bullets.size() - 1;
                current.bullets.set(last, current.bullets.get(last) + " " + line);
            } else {
                inBullet = false;
                if (paragraph.length() > 0) paragraph.append(' ');
                paragraph.append(line);
            }
        }
        Block current = open[3] != null ? open[3] : open[2] != null ? open[2] : open[1];
        if (paragraph.length() > 0 && current != null) current.paragraphs.add(paragraph.toString());
        return docs;
    }
}
