package com.cyberday1.riftfall.skill;

/**
 * Which Spell Engine versions Riftfall can talk to. Presence is not enough: 1.10 removed
 * {@code internals.SpellHelper}, so an engine on the wrong side of that throws
 * {@code NoClassDefFoundError} on the first cast. Names no Spell Engine type.
 */
public final class SpellEngineCompat {

    /** Inclusive: the version the build compiles against, {@code deps.spell_engine}. */
    static final int[] MIN = {1, 10, 9};
    /** Exclusive: the next minor, assumed to break the API again as 1.10 did. */
    static final int[] BELOW = {1, 11, 0};

    private SpellEngineCompat() {
    }

    /** True only for a version inside [MIN, BELOW). Null or unparseable is unsupported. */
    public static boolean supports(String version) {
        int[] v = parse(version);
        return v != null && compare(v, MIN) >= 0 && compare(v, BELOW) < 0;
    }

    /** The accepted range as text, {@code [1.10.9, 1.11.0)}. */
    public static String range() {
        return "[" + dotted(MIN) + ", " + dotted(BELOW) + ")";
    }

    private static String dotted(int[] version) {
        StringBuilder out = new StringBuilder();
        for (int part : version) {
            out.append(out.length() == 0 ? "" : ".").append(part);
        }
        return out.toString();
    }

    /** The leading dotted numbers: {@code "1.10.8+1.21.1"} is {1, 10, 8}. */
    static int[] parse(String version) {
        if (version == null) {
            return null;
        }
        int end = 0;
        while (end < version.length()
                && (Character.isDigit(version.charAt(end)) || version.charAt(end) == '.')) {
            end++;
        }
        String[] parts = version.substring(0, end).split("\\.");
        if (parts.length == 0 || parts[0].isEmpty()) {
            return null;
        }
        int[] out = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            if (parts[i].isEmpty()) {
                return null;
            }
            try {
                out[i] = Integer.parseInt(parts[i]);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return out;
    }

    static int compare(int[] a, int[] b) {
        for (int i = 0; i < Math.max(a.length, b.length); i++) {
            int x = i < a.length ? a[i] : 0;
            int y = i < b.length ? b[i] : 0;
            if (x != y) {
                return Integer.compare(x, y);
            }
        }
        return 0;
    }
}
