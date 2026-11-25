package com.cyberday1.riftfall.lore;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * The core and world names a shard carries: one generator per rift theme, after the v3 canon sheet's patterns
 * ("Shard and boss names"). Pure and seeded, so one rift seed always names the same world and the same core.
 * Names are English proper nouns, stored on the shard as written.
 */
public final class ShardNames {
    /** {@code core} is lower-case "the ..." so it reads mid-sentence; the tooltip capitalises it. */
    public record Named(String core, String world) {
        /** True when the core already names its world (ender's "the Wellhead of Ost"), so "of Ost" is dropped. */
        public boolean coreHoldsWorld() { return core.endsWith(" " + world); }
    }

    private static final long SALT = 0x5A4D_C0DE_F1A3_E000L;

    private record Pick(long seed) {
        String of(int slot, List<String> words) { return words.get((int) Math.floorMod(mix(seed + slot), (long) words.size())); }
        boolean coin(int slot) { return (mix(seed + slot) & 1) == 0; }
    }

    // Word lists avoid the canon worlds' own names, so a run never seems to revisit Penlow Reach or the Kept Stair.
    private static final List<String> FIELDS = List.of("Barley", "Flax", "Heather", "Bracken", "Rye", "Furze", "Sorrel", "Thistle");
    private static final List<String> PARISHES = List.of("Hobbin", "Wenlow", "Marby", "Tolley", "Fennick", "Brackley", "Oddham", "Cowden");
    private static final List<String> STONES = List.of("Cassel", "Ambry", "Wending", "Holm", "Mallory", "Tibbet", "Ewell", "Crouch");
    private static final List<String> REACHES = List.of("Ardle", "Corve", "Brindle", "Hask", "Tolland", "Merrow", "Sorby", "Kell");
    private static final List<String> LANDS = List.of("Ash", "Cinder", "Slag", "Char", "Clinker", "Soot", "Scorch", "Brand");
    private static final List<String> BANKED = List.of("Ember", "Coal", "Glow", "Brand", "Heat", "Cinder");
    private static final List<String> ROADS = List.of("First", "Second", "Fourth", "Fifth", "Sixth", "Seventh", "Ninth", "Twelfth");
    private static final List<String> KINGS = List.of("Tarrow", "Edric", "Mallen", "Osk", "Varne", "Callow", "Brede", "Aster");
    private static final List<String> HILLS = List.of("Maddern", "Orrin", "Lisk", "Pellam", "Sennet", "Abery", "Coom", "Tallow");
    private static final List<String> SEAMS = List.of("Gaunt", "Deep", "Low", "Black", "Narrow", "Old", "Grey", "Bitter");
    private static final List<String> KEPT = List.of("Hollow", "Vault", "Crypt", "Gallery", "Undercroft", "Hall", "Ossuary", "Deep");
    private static final List<String> BASINS = List.of("Low", "Far", "Still", "Hollow", "Black", "Sunken", "Wide", "Silent");
    private static final List<String> TERRACES = List.of("Ochre", "Amber", "Gilt", "High", "Saffron", "Burnt", "Dun", "Long");
    private static final List<String> COASTS = List.of("Saltmere", "Shingle", "Wrackmouth", "Gullstone", "Brinehold", "Kelpmoor", "Ebbmere", "Dunlin");
    private static final List<String> WEAVES = List.of("Pale", "Grey", "Silver", "Mould", "Dim", "Soft", "Still", "Low");
    private static final List<String> RIDGES = List.of("Harrow", "Hoar", "Rime", "Starke", "Cold", "Bleak", "Sleet", "Thane");
    private static final List<String> CITIES = List.of("Ost", "Vey", "Isk", "Carrow", "Aume", "Sarn", "Quell", "Ivre");

    // Core names for the themes that had one fixed name; the first of each list is that name.
    private static final List<String> MELTS = List.of("the First Melt", "the Last Pour", "the Old Anvil", "the Founding Iron", "the Long Heat");
    private static final List<String> LAMPS = List.of("the Under-Lamp", "the Low Star", "the Night Glass", "the Hill Light", "the Morning Lamp");
    private static final List<String> LODES = List.of("the Heartstone", "the Deep Lode", "the Lowstone", "the Mother Vein", "the Quiet Lode");
    private static final List<String> KEEPSAKES = List.of("the Kept Flame", "the Kept Name", "the Kept Bell", "the Kept Lamp", "the Kept Vigil");
    private static final List<String> HINGES = List.of("the Hinge", "the Latch", "the Still Point", "the Threshold", "the Keystone");
    private static final List<String> NOONS = List.of("the Noonstone", "the High Sun", "the Long Day", "the Sunwell", "the Gilt Hour");
    private static final List<String> TIDES = List.of("the Tidestone", "the Saltstone", "the Sea Bell", "the Harbour Light", "the Ninth Wave");
    private static final List<String> KNOTS = List.of("the Knot", "the First Thread", "the Old Loom", "the Last Stitch");
    private static final List<String> ROOTS = List.of("the White Root", "the Winter Seed", "the Long Frost", "the First Snow", "the Cold Hearth");

    private static final Map<String, Function<Pick, Named>> THEMES = Map.ofEntries(
            Map.entry("default", p -> new Named("the " + p.of(1, STONES) + " Stone",
                    p.coin(0) ? "the " + p.of(2, FIELDS).toLowerCase(java.util.Locale.ROOT) + " country" : p.of(3, PARISHES) + " parish")),
            Map.entry("foundry", p -> new Named(p.of(4, MELTS), p.of(1, REACHES) + " Reach")),
            Map.entry("infernal", p -> new Named(p.coin(0) ? "the Old Burn" : "the Banked " + p.of(2, BANKED),
                    "the " + p.of(1, LANDS) + "lands")),
            Map.entry("ruins", p -> {
                String crown = p.of(1, KINGS);
                String world = p.coin(0) ? "the " + p.of(2, ROADS) + " Road" : "the Old Kingdom of " + other(p, 3, KINGS, crown);
                return new Named("the Crown of " + crown, world);
            }),
            Map.entry("celestial", p -> new Named(p.of(4, LAMPS), p.of(1, HILLS) + " Hill")),
            Map.entry("cave", p -> new Named(p.of(4, LODES), "the " + p.of(1, SEAMS) + " Seams")),
            Map.entry("catacombs", p -> new Named(p.of(4, KEEPSAKES), "the Kept " + p.of(1, KEPT))),
            Map.entry("void", p -> new Named(p.of(4, HINGES), "the " + p.of(1, BASINS) + " Basin")),
            Map.entry("solar", p -> new Named(p.of(4, NOONS), "the " + p.of(1, TERRACES) + " Terraces")),
            Map.entry("tidal", p -> new Named(p.of(4, TIDES), "the " + p.of(1, COASTS) + " coast")),
            Map.entry("mycelial", p -> new Named(p.of(4, KNOTS), "the " + p.of(1, WEAVES) + " Weave")),
            Map.entry("glacial", p -> new Named(p.of(4, ROOTS), "the " + p.of(1, RIDGES) + " Ridge")),
            Map.entry("ender", p -> {
                String city = p.of(1, CITIES);
                return new Named("the Wellhead of " + city, city);
            }));

    private ShardNames() {}

    /** The names for a rift of this theme path (e.g. "tidal") built from this seed. Unknown themes name like default. */
    public static Named of(String theme, long seed) {
        var generator = THEMES.getOrDefault(theme, THEMES.get("default"));
        return generator.apply(new Pick(mix(seed ^ SALT ^ theme.hashCode())));
    }

    public static java.util.Set<String> themes() { return THEMES.keySet(); }

    /** A word from the list that is not {@code not}, so a kingdom's crown is never named for the kingdom. */
    private static String other(Pick p, int slot, List<String> words, String not) {
        String word = p.of(slot, words);
        return word.equals(not) ? words.get((words.indexOf(word) + 1) % words.size()) : word;
    }

    /** SplitMix64's finaliser: neighbouring seeds land far apart. */
    private static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }
}
