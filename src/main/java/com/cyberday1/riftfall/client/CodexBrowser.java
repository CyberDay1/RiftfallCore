package com.cyberday1.riftfall.client;

import com.cyberday1.riftfall.network.SkillRequestPayload;
import com.cyberday1.riftfall.network.SkillScreenPayload;
import com.cyberday1.riftfall.skill.SkillBook;
import com.cyberday1.riftfall.skill.SkillTable.LeverType;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * The skills spread's state and what it lists: Base, Ascendancy and Universal tabs, each split into
 * Actives and Passives bands; type chips that filter actives; the All, Owned and Can learn footer; the
 * open skill; and equip mode. Decides nothing the server decides: clicks become {@link SkillRequestPayload}s.
 * No Minecraft type, so it is plain JUnit; the screen only draws what {@link #rows} returns.
 */
final class CodexBrowser {

    enum Tab {
        BASE, ASCENDANCY, UNIVERSAL
    }

    enum Filter {
        ALL, OWNED, CAN_LEARN
    }

    /** The chips' order on the page, which is not the file format's. */
    static final List<LeverType> CHIP_ORDER = List.of(LeverType.DAMAGE, LeverType.DOT, LeverType.CONTROL,
            LeverType.HEAL, LeverType.SHIELD, LeverType.MOVEMENT, LeverType.SUMMON, LeverType.BUFF);

    /** One thing the browser page draws, top to bottom. */
    sealed interface Row permits Band, Sub, Skill, Strip, Note {
    }

    enum BandKind {
        ACTIVES, PASSIVES, UNIVERSAL, CHOSEN, OPEN, SHUT, CLOSED
    }

    /** A tinted divider. {@code name} is an ascendancy for the last four kinds; counts are all and shown. */
    record Band(BandKind kind, String name, int total, int shown) implements Row {
    }

    enum SubKind {
        ULTIMATE, ACTIVES, PASSIVES
    }

    /** A dotted sub-heading inside an ascendancy. */
    record Sub(SubKind kind, int total, int shown) implements Row {
    }

    /** One skill as a full row. {@code locked} when a spend could not reach it yet or ever. */
    record Skill(SkillScreenPayload.Entry entry, boolean locked) implements Row {
    }

    /** A preview's icons, one per skill, before the ascendancy is chosen. */
    record Strip(List<SkillScreenPayload.Entry> entries) implements Row {
    }

    /** A SKILLS line key, with the names it lists (closed ascendancies) where it has any. */
    record Note(String line, List<String> names) implements Row {
        Note(String line) {
            this(line, List.of());
        }
    }

    private SkillScreenPayload view = SkillScreenPayload.EMPTY;
    private Tab tab = Tab.BASE;
    private @Nullable LeverType chip;
    private Filter filter = Filter.ALL;
    private Filter beforeEquip = Filter.ALL;
    private @Nullable String expanded;
    private @Nullable String selected;
    private @Nullable String picking;
    private int scroll;
    private boolean noticeRead;

    /** Takes a new view; a selection or pick the view no longer lists, or can no longer equip, is dropped. */
    void accept(SkillScreenPayload next) {
        view = next;
        noticeRead = false;
        if (selected != null && entry(selected) == null) {
            selected = null;
        }
        if (picking != null && (entry(picking) == null || !canEquip(entry(picking)))) {
            cancelEquip();
        }
        if (expanded != null && !ascendancies(view).contains(expanded)) {
            expanded = null;
        }
    }

    SkillScreenPayload view() {
        return view;
    }

    Tab tab() {
        return tab;
    }

    void tab(Tab next) {
        if (next != tab) {
            tab = next;
            scroll = 0;
            if (next == Tab.UNIVERSAL) {
                chip = null;
            }
        }
    }

    @Nullable LeverType chip() {
        return chip;
    }

    /** One chip filters; the same chip again clears it. */
    void toggleChip(LeverType lever) {
        chip = lever == chip ? null : lever;
        scroll = 0;
    }

    Filter filter() {
        return filter;
    }

    void filter(Filter next) {
        filter = next;
        scroll = 0;
    }

    /** The ascendancy whose preview is open: the one asked for, else the first. */
    @Nullable String expanded() {
        List<String> names = ascendancies(view);
        if (expanded != null && names.contains(expanded)) {
            return expanded;
        }
        return names.isEmpty() ? null : names.get(0);
    }

    void expand(String ascendancy) {
        expanded = ascendancy;
    }

    int scroll() {
        return scroll;
    }

    void scroll(int rows, int rowCount) {
        scroll = Math.max(0, Math.min(scroll + rows, Math.max(0, rowCount - 1)));
    }

    // -- the open skill -------------------------------------------------------------------------

    void select(String id) {
        if (entry(id) != null) {
            cancelEquip();
            selected = id;
            noticeRead = true;
        }
    }

    /** "< Loadout": back to the loadout leaf. */
    void back() {
        selected = null;
    }

    @Nullable String selected() {
        return selected;
    }

    @Nullable SkillScreenPayload.Entry selectedEntry() {
        return selected == null ? null : entry(selected);
    }

    @Nullable SkillScreenPayload.Entry entry(String id) {
        for (SkillScreenPayload.Entry entry : view.entries()) {
            if (entry.id().equals(id)) {
                return entry;
            }
        }
        return null;
    }

    boolean canRankUp(SkillScreenPayload.Entry entry) {
        return entry.status().equals(SkillBook.Spend.OK.name()) && !view.inRift();
    }

    @Nullable SkillRequestPayload rankUp() {
        SkillScreenPayload.Entry entry = selectedEntry();
        return entry != null && canRankUp(entry) ? SkillRequestPayload.spend(entry.id()) : null;
    }

    /** Owned, castable, outside a rift, and an ultimate only once its slot is open. */
    boolean canEquip(SkillScreenPayload.Entry entry) {
        return !entry.passive() && entry.rank() >= 1 && !view.inRift() && (!entry.ultimate() || view.ultimateOpen());
    }

    // -- equip mode ------------------------------------------------------------------------------

    /** Picks up the open skill: the valid slots light up and the list shows what can go in one. */
    boolean startEquip() {
        SkillScreenPayload.Entry entry = selectedEntry();
        if (entry == null || !canEquip(entry)) {
            return false;
        }
        picking = entry.id();
        selected = null;
        beforeEquip = filter;
        filter = Filter.OWNED;
        scroll = 0;
        return true;
    }

    /** Picks a different owned row while already holding one. */
    boolean pick(String id) {
        SkillScreenPayload.Entry entry = entry(id);
        if (picking == null || entry == null || !canEquip(entry)) {
            return false;
        }
        picking = id;
        return true;
    }

    void cancelEquip() {
        if (picking != null) {
            picking = null;
            filter = beforeEquip;
        }
    }

    /** "Read page": the held skill's detail, leaving equip mode. */
    void readPicked() {
        String id = picking;
        cancelEquip();
        if (id != null) {
            select(id);
        }
    }

    @Nullable String picking() {
        return picking;
    }

    @Nullable SkillScreenPayload.Entry pickingEntry() {
        return picking == null ? null : entry(picking);
    }

    /** The ultimate slot takes ultimates and nothing else; the eight take everything else. */
    boolean slotTakes(int slot) {
        SkillScreenPayload.Entry entry = pickingEntry();
        if (entry == null || slot < 0 || slot >= SkillBook.SLOTS) {
            return false;
        }
        return entry.ultimate() ? slot == SkillBook.ULTIMATE_SLOT : slot < SkillBook.ACTIVE_SLOTS;
    }

    /**
     * A click on a loadout slot. Secondary empties it. Primary in equip mode puts the held skill there;
     * otherwise a filled slot opens its skill's page. Null when nothing goes to the server.
     */
    @Nullable SkillRequestPayload clickSlot(int slot, boolean secondary) {
        if (slot < 0 || slot >= SkillBook.SLOTS) {
            return null;
        }
        if (secondary) {
            return slotHolds(slot).isEmpty() || view.inRift() ? null : SkillRequestPayload.equip(slot, "");
        }
        if (picking != null) {
            if (!slotTakes(slot)) {
                return null;
            }
            String id = picking;
            cancelEquip();
            return SkillRequestPayload.equip(slot, id);
        }
        if (!slotHolds(slot).isEmpty()) {
            select(slotHolds(slot));
        }
        return null;
    }

    String slotHolds(int slot) {
        return slot >= 0 && slot < view.loadout().size() ? view.loadout().get(slot) : "";
    }

    /** The slot a skill sits in, or -1. */
    int slotOf(String id) {
        return view.loadout().indexOf(id);
    }

    /** A refusal from the last request, until the player opens something; else null. */
    @Nullable String notice() {
        String result = view.result();
        return noticeRead || result.isEmpty() || result.equals(SkillBook.Spend.OK.name()) ? null : result;
    }

    // -- what the page lists ----------------------------------------------------------------------

    List<Row> rows() {
        return rows(view, tab, chip, filter, expanded());
    }

    /** The chips a class gets: only the levers its own castables use, in {@link #CHIP_ORDER}. */
    static List<LeverType> chips(SkillScreenPayload view) {
        Set<LeverType> used = new LinkedHashSet<>();
        for (SkillScreenPayload.Entry entry : view.entries()) {
            LeverType lever = SkillFigures.lever(entry);
            if (!entry.universal() && !entry.passive() && lever != null) {
                used.add(lever);
            }
        }
        return CHIP_ORDER.stream().filter(used::contains).toList();
    }

    /** The class's ascendancies, in the server's order. */
    static List<String> ascendancies(SkillScreenPayload view) {
        Set<String> names = new LinkedHashSet<>();
        for (SkillScreenPayload.Entry entry : view.entries()) {
            if (!entry.ascendancy().isEmpty()) {
                names.add(entry.ascendancy());
            }
        }
        return List.copyOf(names);
    }

    static boolean passes(Filter filter, SkillScreenPayload.Entry entry) {
        return switch (filter) {
            case ALL -> true;
            case OWNED -> entry.rank() > 0;
            case CAN_LEARN -> entry.status().equals(SkillBook.Spend.OK.name());
        };
    }

    static boolean locked(SkillScreenPayload.Entry entry) {
        return entry.status().equals(SkillBook.Spend.NOT_ASCENDED.name())
                || entry.status().equals(SkillBook.Spend.OTHER_ASCENDANCY.name());
    }

    static List<Row> rows(SkillScreenPayload view, Tab tab, @Nullable LeverType chip, Filter filter,
                          @Nullable String expanded) {
        List<Row> rows = new ArrayList<>();
        if (!view.hasClass()) {
            rows.add(new Note("class_none"));
            return rows;
        }
        switch (tab) {
            case BASE -> {
                List<SkillScreenPayload.Entry> base = of(view, e -> !e.universal() && e.base());
                actives(rows, of(base, e -> !e.passive()), chip, filter);
                passives(rows, of(base, SkillScreenPayload.Entry::passive), filter);
            }
            case UNIVERSAL -> {
                List<SkillScreenPayload.Entry> universal = of(view, SkillScreenPayload.Entry::universal);
                List<SkillScreenPayload.Entry> shown = of(universal, e -> passes(filter, e));
                rows.add(new Band(BandKind.UNIVERSAL, "", universal.size(), shown.size()));
                shown.forEach(e -> rows.add(new Skill(e, false)));
                if (shown.isEmpty() && !universal.isEmpty()) {
                    rows.add(new Note("nothing_matches"));
                }
                rows.add(new Note("universal_note"));
            }
            case ASCENDANCY -> ascendancy(rows, view, chip, filter, expanded);
        }
        return rows;
    }

    private static void ascendancy(List<Row> rows, SkillScreenPayload view, @Nullable LeverType chip, Filter filter,
                                   @Nullable String expanded) {
        List<String> names = ascendancies(view);
        if (names.isEmpty()) {
            rows.add(new Note("asc_none"));
            return;
        }
        if (!view.ascension().isEmpty()) {
            String chosen = view.ascension();
            List<SkillScreenPayload.Entry> mine = of(view, e -> e.ascendancy().equals(chosen));
            rows.add(new Band(BandKind.CHOSEN, chosen, mine.size(), mine.size()));
            List<SkillScreenPayload.Entry> ultimates = of(mine, e -> e.ultimate() && passes(filter, e));
            rows.add(new Sub(SubKind.ULTIMATE, (int) mine.stream().filter(SkillScreenPayload.Entry::ultimate).count(),
                    ultimates.size()));
            ultimates.forEach(e -> rows.add(new Skill(e, false)));
            List<SkillScreenPayload.Entry> actives = of(mine, e -> !e.passive() && !e.ultimate());
            List<SkillScreenPayload.Entry> shown = of(actives, e -> chipPasses(chip, e) && passes(filter, e));
            rows.add(new Sub(SubKind.ACTIVES, actives.size(), shown.size()));
            if (actives.isEmpty()) {
                rows.add(new Note("actives_none"));
            }
            shown.forEach(e -> rows.add(new Skill(e, false)));
            List<SkillScreenPayload.Entry> passives = of(mine, SkillScreenPayload.Entry::passive);
            List<SkillScreenPayload.Entry> passivesShown = of(passives, e -> passes(filter, e));
            rows.add(new Sub(SubKind.PASSIVES, passives.size(), passivesShown.size()));
            if (passives.isEmpty()) {
                rows.add(new Note("passives_none"));
            }
            passivesShown.forEach(e -> rows.add(new Skill(e, false)));
            List<String> closed = names.stream().filter(n -> !n.equals(chosen)).toList();
            if (!closed.isEmpty()) {
                rows.add(new Band(BandKind.CLOSED, "", closed.size(), closed.size()));
                rows.add(new Note("closed_names", closed));
            }
            return;
        }
        rows.add(new Note("choose_at"));
        String open = expanded != null && names.contains(expanded) ? expanded : names.get(0);
        for (String name : names) {
            List<SkillScreenPayload.Entry> group = of(view, e -> e.ascendancy().equals(name));
            if (!name.equals(open)) {
                rows.add(new Band(BandKind.SHUT, name, group.size(), group.size()));
                continue;
            }
            rows.add(new Band(BandKind.OPEN, name, group.size(), group.size()));
            of(group, e -> e.ultimate() && passes(filter, e)).forEach(e -> rows.add(new Skill(e, true)));
            List<SkillScreenPayload.Entry> actives = of(group, e -> !e.passive() && !e.ultimate());
            List<SkillScreenPayload.Entry> shown = of(actives, e -> chipPasses(chip, e) && passes(filter, e));
            rows.add(new Sub(SubKind.ACTIVES, actives.size(), shown.size()));
            if (actives.isEmpty()) {
                rows.add(new Note("actives_none"));
            } else if (!shown.isEmpty()) {
                rows.add(new Strip(shown));
            }
            List<SkillScreenPayload.Entry> passives = of(group, SkillScreenPayload.Entry::passive);
            List<SkillScreenPayload.Entry> passivesShown = of(passives, e -> passes(filter, e));
            rows.add(new Sub(SubKind.PASSIVES, passives.size(), passivesShown.size()));
            if (passives.isEmpty()) {
                rows.add(new Note("passives_none"));
            } else if (!passivesShown.isEmpty()) {
                rows.add(new Strip(passivesShown));
            }
        }
    }

    private static void actives(List<Row> rows, List<SkillScreenPayload.Entry> actives, @Nullable LeverType chip,
                                Filter filter) {
        List<SkillScreenPayload.Entry> shown = of(actives, e -> chipPasses(chip, e) && passes(filter, e));
        rows.add(new Band(BandKind.ACTIVES, "", actives.size(), shown.size()));
        if (actives.isEmpty()) {
            rows.add(new Note("actives_none"));
        } else if (shown.isEmpty()) {
            rows.add(new Note("nothing_matches"));
        }
        shown.forEach(e -> rows.add(new Skill(e, false)));
    }

    private static void passives(List<Row> rows, List<SkillScreenPayload.Entry> passives, Filter filter) {
        List<SkillScreenPayload.Entry> shown = of(passives, e -> passes(filter, e));
        rows.add(new Band(BandKind.PASSIVES, "", passives.size(), shown.size()));
        if (passives.isEmpty()) {
            rows.add(new Note("passives_none"));
        } else if (shown.isEmpty()) {
            rows.add(new Note("nothing_matches"));
        }
        shown.forEach(e -> rows.add(new Skill(e, false)));
    }

    private static boolean chipPasses(@Nullable LeverType chip, SkillScreenPayload.Entry entry) {
        return chip == null || chip == SkillFigures.lever(entry);
    }

    private static List<SkillScreenPayload.Entry> of(SkillScreenPayload view, Predicate<SkillScreenPayload.Entry> test) {
        return of(view.entries(), test);
    }

    private static List<SkillScreenPayload.Entry> of(List<SkillScreenPayload.Entry> entries,
                                                     Predicate<SkillScreenPayload.Entry> test) {
        return entries.stream().filter(test).toList();
    }
}
