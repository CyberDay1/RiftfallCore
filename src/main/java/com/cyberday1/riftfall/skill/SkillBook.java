package com.cyberday1.riftfall.skill;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * One character's class, purchases, loadout and ascendancy. The class is identity only (P-5): it
 * gates what can be bought and taken, and nothing else.
 *
 * <p>The balance is not here. Points earned are {@link #earned} of the live level and points spent
 * are {@link #spent} of the ranks, so the two cannot drift and a rate change needs no migration.
 * Pure: no player, no server, so every rule is testable as a function.
 */
public record SkillBook(Map<String, Owned> ranks, List<String> loadout, @Nullable Ascension ascension,
                        @Nullable String skillClass) {

    /** Ruled 2026-09-22, "lock in 3 for now"; every level earns it, level 1 included (2026-10-11). */
    public static final int POINTS_PER_LEVEL = 3;
    public static final int ACTIVE_SLOTS = 8;
    /** The ninth slot, index 8. */
    public static final int ULTIMATE_SLOT = ACTIVE_SLOTS;
    public static final int SLOTS = ACTIVE_SLOTS + 1;
    /** Ruled 2026-09-21: the ascendancy choice is taken after level 60. */
    public static final int ASCENSION_LEVEL = 60;

    public static final SkillBook EMPTY = new SkillBook(Map.of(), Collections.nCopies(SLOTS, null), null, null);

    private static final String RANKS = "ranks";
    private static final String LOADOUT = "loadout";
    private static final String ID = "id";
    private static final String KIND = "kind";
    private static final String RANK = "rank";
    private static final String SLOT = "slot";
    private static final String ASCENSION = "ascension";
    private static final String CLASS = "class";
    private static final String NAME = "name";
    private static final String CHARACTER_CLASS = "character_class";

    /** What a purchase is. The ruled rank ceilings and prices live here and nowhere else. */
    public enum Kind {
        ACTIVE(10, 1),
        ULTIMATE(5, 3),
        /** Ruled 2026-10-02: "same page as points on skills", 1 point a rank, 5 ranks. */
        PASSIVE(5, 1);

        private final int maxRank;
        private final int pointsPerRank;

        Kind(int maxRank, int pointsPerRank) {
            this.maxRank = maxRank;
            this.pointsPerRank = pointsPerRank;
        }

        public int maxRank() {
            return maxRank;
        }

        String key() {
            return name().toLowerCase(Locale.ROOT);
        }

        static Kind byKey(String key) {
            for (Kind kind : values()) {
                if (kind.key().equals(key)) {
                    return kind;
                }
            }
            return null;
        }
    }

    /** Anything a point buys: a castable {@link SkillDef} or a {@link PassiveDef}. */
    public interface Purchase {
        /** The class of a passive any class may buy. */
        String UNIVERSAL = "universal";

        String id();

        Kind kind();

        String skillClass();

        @Nullable String ascendancy();

        default boolean isAscendancy() {
            return ascendancy() != null;
        }

        default boolean universal() {
            return UNIVERSAL.equals(skillClass());
        }
    }

    /** A rank held, with the kind it was bought as, so the book prices itself without the table. */
    public record Owned(Kind kind, int rank) {
    }

    /** The one ascendancy chosen, with its class, so the book enforces both without the table. */
    public record Ascension(String skillClass, String name) {
        public Ascension {
            if (skillClass == null || skillClass.isBlank() || name == null || name.isBlank()) {
                throw new IllegalArgumentException("an ascension needs a class and a name");
            }
        }
    }

    public enum Spend {
        OK, UNKNOWN, IN_RIFT, MAX_RANK, NO_CLASS, WRONG_CLASS, NOT_ASCENDED, OTHER_ASCENDANCY, NOT_ENOUGH_POINTS
    }

    public enum Equip {
        OK, UNKNOWN, IN_RIFT, BAD_SLOT, SLOT_LOCKED, WRONG_SLOT, NOT_OWNED, EMPTY_SLOT
    }

    public enum Ascend {
        OK, UNKNOWN, IN_RIFT, ALREADY_CHOSEN, TOO_LOW, NO_CLASS, WRONG_CLASS
    }

    public enum Choose {
        OK, UNKNOWN, IN_RIFT, ALREADY_CHOSEN, OWNS_OTHER_CLASS
    }

    /** An admin's class reset. Ruled 2026-10-11 (E-10): it takes the ascension with it. */
    public enum Reset {
        OK, IN_RIFT, NO_CLASS
    }

    public SkillBook(Map<String, Owned> ranks, List<String> loadout) {
        this(ranks, loadout, null, null);
    }

    public SkillBook(Map<String, Owned> ranks, List<String> loadout, @Nullable Ascension ascension) {
        this(ranks, loadout, ascension, null);
    }

    public SkillBook {
        Map<String, Owned> copy = new LinkedHashMap<>();
        ranks.forEach((id, owned) -> {
            if (owned == null || owned.kind() == null || owned.rank() < 1) {
                throw new IllegalArgumentException(id + ": an owned skill needs a kind and a rank of at least 1");
            }
            copy.put(id, owned);
        });
        if (loadout.size() != SLOTS) {
            throw new IllegalArgumentException("a loadout has " + SLOTS + " slots, was " + loadout.size());
        }
        ranks = Collections.unmodifiableMap(copy);
        loadout = Collections.unmodifiableList(new ArrayList<>(loadout));
        if (skillClass != null && skillClass.isBlank()) {
            skillClass = null;
        }
        // An ascension already fixed the class, so a book with one and no class has that one.
        if (ascension != null && skillClass == null) {
            skillClass = ascension.skillClass();
        }
        if (ascension != null && !ascension.skillClass().equals(skillClass)) {
            throw new IllegalArgumentException("a " + skillClass + " cannot hold a " + ascension.skillClass()
                    + " ascension");
        }
    }

    /**
     * Derived from the live level, never stored, so the 2026-10-11 change (level 1 earns its share)
     * reached every saved character at once: a level-N book has exactly {@code 3N}, with nothing to
     * migrate and nothing to grant twice.
     */
    public static int earned(int level) {
        return POINTS_PER_LEVEL * level;
    }

    /** The price of buying {@code rank} itself. One function, so escalating costs are one edit. */
    public static int rankCost(Kind kind, int rank) {
        return kind.pointsPerRank;
    }

    public static int costTo(Kind kind, int rank) {
        int total = 0;
        for (int step = 1; step <= rank; step++) {
            total += rankCost(kind, step);
        }
        return total;
    }

    public int spent() {
        return ranks.values().stream().mapToInt(owned -> costTo(owned.kind(), owned.rank())).sum();
    }

    /** Negative when a lowered rate has left the book overspent; nothing is deleted to fix it. */
    public int available(int level) {
        return earned(level) - spent();
    }

    public int rank(String id) {
        Owned owned = ranks.get(id);
        return owned == null ? 0 : owned.rank();
    }

    /** What is in a slot, or null. Q-1, ruled 2026-09-27: the ultimate is equipped like the rest. */
    public @Nullable String slot(int slot) {
        return loadout.get(slot);
    }

    public boolean ascended() {
        return ascension != null;
    }

    /** Slot 8 opens with the ascension, which cannot be taken before level 60. */
    public boolean ultimateOpen() {
        return ascended();
    }

    public boolean hasClass() {
        return skillClass != null;
    }

    /**
     * Whether this book may take {@code name} as its class, once. {@code classOfOwned} gives the
     * class of an owned skill, or null for one no longer in the table. Owning another class's skill
     * refuses the choice, so choosing can never leave a book holding two classes' ranks.
     */
    public Choose checkChoose(String name, Set<String> classes, Function<String, String> classOfOwned) {
        if (!classes.contains(name)) {
            return Choose.UNKNOWN;
        }
        if (hasClass()) {
            return Choose.ALREADY_CHOSEN;
        }
        for (String id : ranks.keySet()) {
            String owned = classOfOwned.apply(id);
            if (owned != null && !owned.equals(name)) {
                return Choose.OWNS_OTHER_CLASS;
            }
        }
        return Choose.OK;
    }

    public SkillBook withClass(String chosen) {
        if (hasClass()) {
            throw new IllegalArgumentException("already a " + skillClass);
        }
        return new SkillBook(ranks, loadout, ascension, chosen);
    }

    public Reset checkReset() {
        return hasClass() ? Reset.OK : Reset.NO_CLASS;
    }

    /**
     * The class, its ascension, every rank and every slot taken away: points are derived, so emptying the
     * map is the refund (E-10). The character is a blank book that may choose again.
     */
    public SkillBook withoutClass() {
        if (checkReset() != Reset.OK) {
            throw new IllegalArgumentException("cannot reset: " + checkReset());
        }
        return EMPTY;
    }

    /** The skill respec (E-10): every rank refunded and every slot emptied; class and ascension stay. */
    public SkillBook respecced() {
        return new SkillBook(Map.of(), Collections.nCopies(SLOTS, null), ascension, skillClass);
    }

    /** Whether this book may take the named ascendancy; {@code ascendancyClass} is null when no loaded skill names it. */
    public Ascend checkAscend(String name, @Nullable String ascendancyClass, int level) {
        if (ascendancyClass == null) {
            return Ascend.UNKNOWN;
        }
        if (ascended()) {
            return Ascend.ALREADY_CHOSEN;
        }
        if (level < ASCENSION_LEVEL) {
            return Ascend.TOO_LOW;
        }
        if (!hasClass()) {
            return Ascend.NO_CLASS;
        }
        return skillClass.equals(ascendancyClass) ? Ascend.OK : Ascend.WRONG_CLASS;
    }

    public SkillBook ascendedAs(Ascension chosen) {
        if (ascended()) {
            throw new IllegalArgumentException("already ascended as " + ascension.name());
        }
        return new SkillBook(ranks, loadout, chosen, skillClass);
    }

    /**
     * A base skill is never refused for level; the brake is points. Nothing is bought before a class.
     * A universal passive skips the class match and nothing else.
     */
    public Spend checkSpend(Purchase def, int level) {
        int next = rank(def.id()) + 1;
        if (next > def.kind().maxRank()) {
            return Spend.MAX_RANK;
        }
        if (!hasClass()) {
            return Spend.NO_CLASS;
        }
        if (!def.universal() && !skillClass.equals(def.skillClass())) {
            return Spend.WRONG_CLASS;
        }
        if (def.isAscendancy()) {
            if (!ascended()) {
                return Spend.NOT_ASCENDED;
            }
            if (!ascension.name().equals(def.ascendancy())) {
                return Spend.OTHER_ASCENDANCY;
            }
        }
        return available(level) < rankCost(def.kind(), next) ? Spend.NOT_ENOUGH_POINTS : Spend.OK;
    }

    public SkillBook bought(Purchase def, int level) {
        Spend check = checkSpend(def, level);
        if (check != Spend.OK) {
            throw new IllegalArgumentException("cannot buy " + def.id() + ": " + check);
        }
        Map<String, Owned> next = new LinkedHashMap<>(ranks);
        next.put(def.id(), new Owned(def.kind(), rank(def.id()) + 1));
        return new SkillBook(next, loadout, ascension, skillClass);
    }

    public Equip checkEquip(int slot, SkillDef def) {
        if (slot < 0 || slot >= SLOTS) {
            return Equip.BAD_SLOT;
        }
        if (slot == ULTIMATE_SLOT && !ultimateOpen()) {
            return Equip.SLOT_LOCKED;
        }
        if ((slot == ULTIMATE_SLOT) != (def.kind() == Kind.ULTIMATE)) {
            return Equip.WRONG_SLOT;
        }
        return rank(def.id()) < 1 ? Equip.NOT_OWNED : Equip.OK;
    }

    /** Puts a skill in a slot, taking it out of any other slot it was in. */
    public SkillBook equipped(int slot, String id) {
        List<String> next = new ArrayList<>(loadout);
        for (int other = 0; other < SLOTS; other++) {
            if (id.equals(next.get(other))) {
                next.set(other, null);
            }
        }
        next.set(slot, id);
        return new SkillBook(ranks, next, ascension, skillClass);
    }

    public SkillBook unequipped(int slot) {
        List<String> next = new ArrayList<>(loadout);
        next.set(slot, null);
        return new SkillBook(ranks, next, ascension, skillClass);
    }

    public boolean isEmpty() {
        return ranks.isEmpty() && loadout.stream().allMatch(id -> id == null) && ascension == null
                && skillClass == null;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        ListTag owned = new ListTag();
        ranks.forEach((id, held) -> {
            CompoundTag entry = new CompoundTag();
            entry.putString(ID, id);
            entry.putString(KIND, held.kind().key());
            entry.putInt(RANK, held.rank());
            owned.add(entry);
        });
        tag.put(RANKS, owned);
        ListTag slots = new ListTag();
        for (int slot = 0; slot < SLOTS; slot++) {
            if (loadout.get(slot) != null) {
                CompoundTag entry = new CompoundTag();
                entry.putInt(SLOT, slot);
                entry.putString(ID, loadout.get(slot));
                slots.add(entry);
            }
        }
        tag.put(LOADOUT, slots);
        if (skillClass != null) {
            tag.putString(CHARACTER_CLASS, skillClass);
        }
        if (ascension != null) {
            CompoundTag chosen = new CompoundTag();
            chosen.putString(CLASS, ascension.skillClass());
            chosen.putString(NAME, ascension.name());
            tag.put(ASCENSION, chosen);
        }
        return tag;
    }

    /**
     * Reads a book, dropping what it cannot read and never throwing: a throw here takes the whole
     * stats file, XP included. A rank above its kind's ceiling is kept, since a ruling can move and
     * progression is not trimmed. An absent tag is {@link #EMPTY}, which is how a v2 record reads.
     * An ascension missing its class or name is dropped, which leaves the choice open, not taken.
     * A book written before P-5 has no class: it reads unclassed, or as its ascension's class. A
     * stored class that disagrees with the ascension yields to it, the one spends already enforced.
     */
    public static SkillBook load(CompoundTag tag) {
        Map<String, Owned> ranks = new LinkedHashMap<>();
        ListTag owned = tag.getList(RANKS, Tag.TAG_COMPOUND);
        for (int index = 0; index < owned.size(); index++) {
            CompoundTag entry = owned.getCompound(index);
            Kind kind = Kind.byKey(entry.getString(KIND));
            String id = entry.getString(ID);
            int rank = entry.getInt(RANK);
            if (kind != null && !id.isEmpty() && rank > 0) {
                ranks.put(id, new Owned(kind, rank));
            }
        }
        List<String> loadout = new ArrayList<>(Collections.nCopies(SLOTS, null));
        ListTag slots = tag.getList(LOADOUT, Tag.TAG_COMPOUND);
        for (int index = 0; index < slots.size(); index++) {
            CompoundTag entry = slots.getCompound(index);
            int slot = entry.getInt(SLOT);
            Owned held = ranks.get(entry.getString(ID));
            if (slot >= 0 && slot < SLOTS && held != null && held.kind() != Kind.PASSIVE
                    && (slot == ULTIMATE_SLOT) == (held.kind() == Kind.ULTIMATE)
                    && !loadout.contains(entry.getString(ID))) {
                loadout.set(slot, entry.getString(ID));
            }
        }
        Ascension ascension = null;
        CompoundTag chosen = tag.getCompound(ASCENSION);
        if (!chosen.getString(CLASS).isBlank() && !chosen.getString(NAME).isBlank()) {
            ascension = new Ascension(chosen.getString(CLASS), chosen.getString(NAME));
        }
        String skillClass = ascension != null ? ascension.skillClass() : tag.getString(CHARACTER_CLASS);
        return new SkillBook(ranks, loadout, ascension, skillClass);
    }
}
