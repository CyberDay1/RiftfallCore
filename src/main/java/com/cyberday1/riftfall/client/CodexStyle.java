package com.cyberday1.riftfall.client;

import com.cyberday1.riftfall.skill.SkillTable.LeverType;
import com.cyberday1.riftfall.stat.StatBlock;
import org.jetbrains.annotations.Nullable;

/** Semantic colours for the stellar character, skills and class pages. Fills are opaque ARGB. */
final class CodexStyle {

    static final int COVER_OUTLINE = 0xFF090D17;

    static final int PAGE = 0xFF111522;

    static final int INK = 0xFFEEEAF6;
    static final int MUTED = 0xFFB5BAD0;
    static final int DIM = 0xFF939BB4;
    static final int ACCENT = 0xFFDEC68D;
    static final int ACCENT_HOT = 0xFFF2DFAE;
    static final int ON_ACCENT = 0xFF111522;
    static final int ON_ACCENT_MUTED = 0xFF242534;
    static final int ON_TAG = 0xFFF6F0E2;
    static final int WARN = 0xFFF2B6A5;
    static final int GOOD = 0xFF9DCBA0;
    static final int BAD = 0xFFF2B6A5;

    static final int TROUGH = 0xFF090D17;
    static final int WELL = 0xFF1D2334;
    static final int WELL_DARK = 0xFF343D52;
    static final int WELL_LIGHT = 0xFF262D40;
    static final int SLOT = 0xFF1D2334;
    static final int SLOT_SHUT = 0xFF171C2B;
    static final int DOTS = 0xFF41455B;
    static final int SELECTED = 0xFF35364A;
    static final int BAND = 0xFF1D2334;
    static final int BAND_EDGE = 0xFF41455B;
    static final int LOCK = 0xFFB5BAD0;
    static final int LOCK_BODY = 0xFF555970;
    static final int TAB_IDLE = 0xFF939BB4;
    static final int COOLING = 0x6EFFFFFF;

    static final int BOOKMARK_IDLE = 0xFF1D2334;

    static final int BUTTON_OFF_OUTLINE = 0xFF343D52;
    static final int BUTTON_OFF = 0xFF1D2334;
    static final int BUTTON_OFF_TEXT = 0xFF939BB4;

    static final int PASSIVE = 0xFF55516F;

    private CodexStyle() {
    }

    /** A lever's tag and chip colour; a passive's when {@code lever} is null. */
    static int lever(@Nullable LeverType lever) {
        if (lever == null) {
            return PASSIVE;
        }
        return switch (lever) {
            case DAMAGE -> 0xFF9A3B2E;
            case DOT -> 0xFFB8742A;
            case CONTROL -> 0xFF3A5FA8;
            case HEAL -> 0xFF3F7A3A;
            case SHIELD -> 0xFF2F8A8A;
            case MOVEMENT -> 0xFF6F7F2A;
            case SUMMON -> 0xFF7A3F8A;
            case BUFF -> 0xFF8A6A1E;
        };
    }

    static int primary(StatBlock.Primary primary) {
        return switch (primary) {
            case STRENGTH -> 0xFF9A3B2E;
            case DEXTERITY -> 0xFFB8742A;
            case INTELLECT -> 0xFF3A5FA8;
            case VITALITY -> 0xFF3F7A3A;
            case SPIRIT -> 0xFF2F8A8A;
            case AGILITY -> 0xFF6F7F2A;
            case LUCK -> 0xFF8A6A1E;
        };
    }
}
