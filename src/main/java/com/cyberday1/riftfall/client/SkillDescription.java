package com.cyberday1.riftfall.client;

import java.util.IllegalFormatException;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Resolve authored prose without exposing template braces or inventing missing numbers. */
final class SkillDescription {
    private static final Pattern TOKEN = Pattern.compile("\\{([^{}]+)}");
    /** A deferred stage's damage: {@code {release_damage|300}}, {@code {counter_damage|900|arcane}}. */
    private static final Pattern STAGE = Pattern.compile("(release|counter)_damage\\|(\\d+(?:\\.\\d+)?)(?:\\|([a-z]+))?");
    /** A shield pool: {@code {shield}}, or {@code {shield_per_enemy}} for one counted enemy's share. */
    private static final Pattern SHIELD = Pattern.compile("shield(?:_per_enemy)?");
    private static final Pattern FALLBACK_TOKEN = Pattern.compile("\\{(" + STAGE.pattern() + "|" + SHIELD.pattern() + ")}");
    private SkillDescription() { }

    /** A lang value as {@code I18n.get} formats it, or as written when it has a bare {@code %} that would print "Format error". */
    static String text(String raw) {
        try {
            return String.format(raw);
        } catch (IllegalFormatException e) {
            return raw;
        }
    }

    /**
     * {@code damage} phrases a deferred stage's estimate from its amount and damage-type id ("" when untyped);
     * {@code coefficient} phrases the token's own base percentage and type, used when there is no estimate.
     * {@code shield} phrases a shield pool from its points, or says what it grows with when handed "".
     * All answer in the reader's language.
     */
    static String resolve(String prose, Map<String, String> values, BiFunction<String, String, String> damage,
                          BiFunction<String, String, String> coefficient, UnaryOperator<String> shield) {
        // The engine's tooltip cannot resolve a different spell, so the token carries the coefficient;
        // the Codex has that stage's ranked estimate from the server.
        return TOKEN.matcher(prose).replaceAll(match -> Matcher.quoteReplacement(
                token(match.group(1), values, damage, coefficient, shield)));
    }

    /** Only the stage and shield tokens, with no estimate: for prose whose other tokens someone else fills. */
    static String fallbacks(String prose, BiFunction<String, String, String> coefficient, UnaryOperator<String> shield) {
        return FALLBACK_TOKEN.matcher(prose).replaceAll(match -> Matcher.quoteReplacement(
                token(match.group(1), Map.of(), (amount, type) -> amount, coefficient, shield)));
    }

    private static String token(String token, Map<String, String> values, BiFunction<String, String, String> damage,
                                BiFunction<String, String, String> coefficient, UnaryOperator<String> shield) {
        if (SHIELD.matcher(token).matches()) return shield.apply(values.getOrDefault(token, ""));
        Matcher stage = STAGE.matcher(token);
        if (!stage.matches()) return values.getOrDefault(token, "[see effect details]");
        String amount = values.get(stage.group(1) + "_damage");
        if (amount != null) return damage.apply(amount, values.getOrDefault(stage.group(1) + "_damage_type", "physical"));
        return coefficient.apply(stage.group(2), stage.group(3) == null ? "" : stage.group(3));
    }
}
