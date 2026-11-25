package com.cyberday1.riftfall.lore;

import com.cyberday1.riftfall.block.marker.NpcRole;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The residents' story: each one's personal name, the recognition lines they say once a gate opens (a note read,
 * a home rank, or both), and which of those lines swaps their cheerful lines for the {@code known} ones. Loaded
 * from the jar on both sides; {@code tools/lore/import_residents.py} writes the file from the lore draft.
 */
public final class ResidentStory {
    private static final String FILE = "/data/riftfall/lore/residents.json";
    private static final String PREFIX = "resident.riftfall.";

    /** One recognition line's gate: a note this player has read (empty for none) and a minimum home rank. */
    public record Gate(String note, int rank) {
        public boolean open(Set<String> read, int homeRank) {
            return (note.isEmpty() || read.contains(note)) && homeRank >= rank;
        }
    }

    public record Resident(NpcRole role, List<Gate> lines, int swapAfter) {}

    private record Catalog(Map<NpcRole, Resident> residents, Map<String, String> english) {}

    private static final Catalog CATALOG = load();

    private ResidentStory() {}

    public static String personKey(NpcRole role) { return PREFIX + role.id() + ".person"; }
    public static String noteKey(NpcRole role, int line) { return PREFIX + role.id() + ".note." + line; }
    /** {@code scope} is a role id or {@code any}; {@code trigger} a dialogue trigger id. */
    public static String knownKey(String scope, String trigger, int variant) {
        return PREFIX + scope + ".known." + trigger + "." + variant;
    }

    public static Resident of(NpcRole role) { return CATALOG.residents.get(role); }

    /** The first line not yet said ({@code said} is a bit per line) whose gate is open, or -1. */
    public static int next(NpcRole role, int said, Set<String> read, int homeRank) {
        var lines = of(role).lines();
        for (int i = 0; i < lines.size(); i++)
            if ((said & 1 << i) == 0 && lines.get(i).open(read, homeRank)) return i;
        return -1;
    }

    /** True once the line that triggers this resident's swap has been said. */
    public static boolean known(NpcRole role, int said) { return (said & 1 << of(role).swapAfter()) != 0; }

    /** The English for every key, for the lang provider. */
    public static Map<String, String> english() { return CATALOG.english; }

    private static Catalog load() {
        try (var stream = ResidentStory.class.getResourceAsStream(FILE)) {
            if (stream == null) throw new IllegalStateException("Missing " + FILE);
            var root = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            if (root.get("version").getAsInt() != 1) throw new IllegalStateException("Unsupported resident story");
            var residents = new EnumMap<NpcRole, Resident>(NpcRole.class);
            var english = new LinkedHashMap<String, String>();
            for (var value : root.getAsJsonArray("residents")) {
                var r = value.getAsJsonObject();
                NpcRole role = null;
                for (var candidate : NpcRole.values()) if (candidate.id().equals(r.get("role").getAsString())) role = candidate;
                if (role == null) throw new IllegalStateException("Unknown resident role " + r.get("role"));
                english.put(personKey(role), r.get("person").getAsString());
                var lines = new ArrayList<Gate>();
                for (var line : r.getAsJsonArray("lines")) {
                    var l = line.getAsJsonObject();
                    english.put(noteKey(role, lines.size()), l.get("text").getAsString());
                    lines.add(new Gate(l.get("note").getAsString(), l.get("rank").getAsInt()));
                }
                int swap = r.get("swap_after").getAsInt();
                if (lines.isEmpty() || lines.size() > 30 || swap < 0 || swap >= lines.size())
                    throw new IllegalStateException("Bad recognition lines for " + role.id());
                residents.put(role, new Resident(role, List.copyOf(lines), swap));
            }
            if (residents.size() != NpcRole.values().length) throw new IllegalStateException("A resident has no story");
            for (var value : root.getAsJsonArray("known")) {
                var k = value.getAsJsonObject();
                english.put(PREFIX + k.get("key").getAsString(), k.get("text").getAsString());
            }
            return new Catalog(Collections.unmodifiableMap(residents), Collections.unmodifiableMap(english));
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read " + FILE, e);
        }
    }
}
