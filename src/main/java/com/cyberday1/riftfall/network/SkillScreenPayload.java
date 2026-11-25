package com.cyberday1.riftfall.network;

import com.cyberday1.riftfall.RiftfallCore;
import com.cyberday1.riftfall.rift.RiftKeyItem;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * Everything the skill screen shows, decided on the server: the class's skills with each one's
 * {@code SkillBook.Spend} answer, the loadout, the points, and what the last request did. The character
 * sheet reads the attribute pool and the player's own per-primary points from here too.
 * Rules are never worked out on the client, so the screen cannot disagree with the verbs.
 * An empty string stands for "none" throughout.
 */
public record SkillScreenPayload(String skillClass, String ascension, int level, int available, boolean inRift,
                                 boolean ultimateOpen, List<Entry> entries, List<String> loadout,
                                 String action, String result, Respec skillRespec, Respec statRespec,
                                 int attributePoints, List<Integer> allocated, int riftTier)
        implements CustomPacketPayload {

    public static final Type<SkillScreenPayload> TYPE = new Type<>(RiftfallCore.id("skill_screen"));
    public static final SkillScreenPayload EMPTY = new SkillScreenPayload("", "", 1, 0, false, false,
            List.of(), List.of(), "", "");

    /**
     * What a respec would do now ({@code Respec.Quote}, flattened): the result's name, the XP it costs, the
     * XP still missing, and how many points come back. The screen shows these; the server decides.
     */
    public record Respec(String status, long cost, long shortBy, int refund) {
        public static final Respec NONE = new Respec("NOTHING", 0L, 0L, 0);

        public boolean ok() {
            return "OK".equals(status);
        }
    }

    /** A view outside a rift: hits there are reckoned at the default tier. */
    public SkillScreenPayload(String skillClass, String ascension, int level, int available, boolean inRift,
                              boolean ultimateOpen, List<Entry> entries, List<String> loadout, String action,
                              String result, Respec skillRespec, Respec statRespec, int attributePoints,
                              List<Integer> allocated) {
        this(skillClass, ascension, level, available, inRift, ultimateOpen, entries, loadout, action, result,
                skillRespec, statRespec, attributePoints, allocated, RiftKeyItem.DEFAULT_TIER);
    }

    /** A view with no attribute pool, as the screen was told before attribute points existed. */
    public SkillScreenPayload(String skillClass, String ascension, int level, int available, boolean inRift,
                              boolean ultimateOpen, List<Entry> entries, List<String> loadout, String action,
                              String result, Respec skillRespec, Respec statRespec) {
        this(skillClass, ascension, level, available, inRift, ultimateOpen, entries, loadout, action, result,
                skillRespec, statRespec, 0, List.of());
    }

    /** A view with no respec quotes, as the screen was told before they were sent. */
    public SkillScreenPayload(String skillClass, String ascension, int level, int available, boolean inRift,
                              boolean ultimateOpen, List<Entry> entries, List<String> loadout, String action,
                              String result) {
        this(skillClass, ascension, level, available, inRift, ultimateOpen, entries, loadout, action, result,
                Respec.NONE, Respec.NONE);
    }
    private static final int TEXT = 64;
    private static final int ID = 256;

    /**
     * One skill or passive of the class. {@code status} is the name of what a spend would answer, rift
     * aside. A passive is never equipped and costs no mana.
     */
    public record Entry(String id, String ascendancy, boolean ultimate, boolean passive, int rank, int maxRank,
                        double cost, String status, boolean universal, Facts facts, Guide guide) {

        public Entry(String id, String ascendancy, boolean ultimate, boolean passive, int rank, int maxRank,
                     double cost, String status, boolean universal, Facts facts) {
            this(id, ascendancy, ultimate, passive, rank, maxRank, cost, status, universal, facts, Guide.NONE);
        }

        /** An entry with no node facts, as the screen was told before they were sent. */
        public Entry(String id, String ascendancy, boolean ultimate, boolean passive, int rank, int maxRank,
                     double cost, String status) {
            this(id, ascendancy, ultimate, passive, rank, maxRank, cost, status, false, Facts.NONE);
        }

        public Entry {
            facts = facts == null ? Facts.NONE : facts;
            guide = guide == null ? Guide.NONE : guide;
        }

        public boolean base() {
            return ascendancy.isEmpty();
        }
    }

    /**
     * Server estimates for the held/next ranks (rank 1/top before learning), with complete effect notes. Labels,
     * notes and scaling are lang keys with arguments ({@code GuideLang}), so the client reads its own language;
     * {@code tokens} are the numbers the authored descriptions name. A null scaling means none was worked out.
     */
    public record Guide(List<Figure> figures, List<Component> notes, Map<String, String> tokens, @Nullable Component scaling) {
        public static final Guide NONE = new Guide(List.of(), List.of(), Map.of(), null);
        public Guide {
            figures = List.copyOf(figures);
            notes = List.copyOf(notes);
            tokens = Map.copyOf(tokens);
        }
        public boolean resolved() { return !notes.isEmpty(); }
    }

    public record Figure(Component label, String from, String to) { }

    /**
     * What the node file says, for the detail page. A castable fills the first eleven, a passive the
     * last two. Cooldowns are ticks at the held rank, the next one (the held one at max) and the
     * highest; mana is the cost at the next and the highest rank. {@code leverSeconds} is DoT, Summon
     * and Buff's second rate. {@code powerMultiplier} is the node's, 1.0 for a passive. {@code bonusBase}
     * is a passive's once-only base (0 for most).
     */
    public record Facts(String ability, String engine, String powerAttribute, String lever, double leverRate,
                        double leverSeconds, int cooldownNow, int cooldownNext, int cooldownMax, double costNext,
                        double costMax, String attribute, double bonusPerRank, double powerMultiplier,
                        double bonusBase) {

        public static final Facts NONE = new Facts("", "", "", "", 0.0, 0.0, 0, 0, 0, 0.0, 0.0, "", 0.0);

        /** Facts with no passive base. */
        public Facts(String ability, String engine, String powerAttribute, String lever, double leverRate,
                     double leverSeconds, int cooldownNow, int cooldownNext, int cooldownMax, double costNext,
                     double costMax, String attribute, double bonusPerRank, double powerMultiplier) {
            this(ability, engine, powerAttribute, lever, leverRate, leverSeconds, cooldownNow, cooldownNext,
                    cooldownMax, costNext, costMax, attribute, bonusPerRank, powerMultiplier, 0.0);
        }

        /** Facts for a node at its ability's own power. */
        public Facts(String ability, String engine, String powerAttribute, String lever, double leverRate,
                     double leverSeconds, int cooldownNow, int cooldownNext, int cooldownMax, double costNext,
                     double costMax, String attribute, double bonusPerRank) {
            this(ability, engine, powerAttribute, lever, leverRate, leverSeconds, cooldownNow, cooldownNext,
                    cooldownMax, costNext, costMax, attribute, bonusPerRank, 1.0);
        }

        public static Facts passive(String attribute, double bonusPerRank) {
            return passive(attribute, bonusPerRank, 0.0);
        }

        public static Facts passive(String attribute, double bonusPerRank, double bonusBase) {
            return new Facts("", "", "", "", 0.0, 0.0, 0, 0, 0, 0.0, 0.0, attribute, bonusPerRank, 1.0, bonusBase);
        }

        /** A passive's bonus at {@code rank}, as {@code SkillRanks#passiveBonus} reckons it: base + step x rank. */
        public double bonusAt(int rank) {
            return rank <= 0 ? 0.0 : bonusBase + bonusPerRank * rank;
        }
    }

    public SkillScreenPayload {
        entries = List.copyOf(entries);
        loadout = List.copyOf(loadout);
        skillRespec = skillRespec == null ? Respec.NONE : skillRespec;
        statRespec = statRespec == null ? Respec.NONE : statRespec;
        allocated = List.copyOf(allocated);
    }

    /** The player's own points on one primary, the class block not included. Empty reads as none. */
    public int allocated(int ordinal) {
        return ordinal < allocated.size() ? allocated.get(ordinal) : 0;
    }

    public boolean hasClass() {
        return !skillClass.isEmpty();
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, SkillScreenPayload> STREAM_CODEC =
            StreamCodec.of(SkillScreenPayload::write, SkillScreenPayload::read);

    private static void write(RegistryFriendlyByteBuf buf, SkillScreenPayload p) {
        buf.writeUtf(p.skillClass, TEXT);
        buf.writeUtf(p.ascension, TEXT);
        buf.writeVarInt(p.level);
        buf.writeVarInt(p.available);
        buf.writeBoolean(p.inRift);
        buf.writeBoolean(p.ultimateOpen);
        buf.writeVarInt(p.entries.size());
        for (Entry e : p.entries) {
            buf.writeUtf(e.id, TEXT);
            buf.writeUtf(e.ascendancy, TEXT);
            buf.writeBoolean(e.ultimate);
            buf.writeBoolean(e.passive);
            buf.writeVarInt(e.rank);
            buf.writeVarInt(e.maxRank);
            buf.writeDouble(e.cost);
            buf.writeUtf(e.status, TEXT);
            buf.writeBoolean(e.universal);
            writeFacts(buf, e.facts);
            writeGuide(buf, e.guide);
        }
        buf.writeVarInt(p.loadout.size());
        p.loadout.forEach(id -> buf.writeUtf(id, TEXT));
        buf.writeUtf(p.action, TEXT);
        buf.writeUtf(p.result, TEXT);
        writeRespec(buf, p.skillRespec);
        writeRespec(buf, p.statRespec);
        buf.writeVarInt(p.attributePoints);
        buf.writeVarInt(p.allocated.size());
        p.allocated.forEach(buf::writeVarInt);
        buf.writeVarInt(p.riftTier);
    }

    private static void writeRespec(RegistryFriendlyByteBuf buf, Respec r) {
        buf.writeUtf(r.status, TEXT);
        buf.writeVarLong(r.cost);
        buf.writeVarLong(r.shortBy);
        buf.writeVarInt(r.refund);
    }

    private static Respec readRespec(RegistryFriendlyByteBuf buf) {
        return new Respec(buf.readUtf(TEXT), buf.readVarLong(), buf.readVarLong(), buf.readVarInt());
    }

    private static void writeFacts(RegistryFriendlyByteBuf buf, Facts f) {
        buf.writeUtf(f.ability, ID);
        buf.writeUtf(f.engine, TEXT);
        buf.writeUtf(f.powerAttribute, ID);
        buf.writeUtf(f.lever, TEXT);
        buf.writeDouble(f.leverRate);
        buf.writeDouble(f.leverSeconds);
        buf.writeVarInt(f.cooldownNow);
        buf.writeVarInt(f.cooldownNext);
        buf.writeVarInt(f.cooldownMax);
        buf.writeDouble(f.costNext);
        buf.writeDouble(f.costMax);
        buf.writeUtf(f.attribute, ID);
        buf.writeDouble(f.bonusPerRank);
        buf.writeDouble(f.powerMultiplier);
        buf.writeDouble(f.bonusBase);
    }

    private static Facts readFacts(RegistryFriendlyByteBuf buf) {
        return new Facts(buf.readUtf(ID), buf.readUtf(TEXT), buf.readUtf(ID), buf.readUtf(TEXT), buf.readDouble(),
                buf.readDouble(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readDouble(),
                buf.readDouble(), buf.readUtf(ID), buf.readDouble(), buf.readDouble(), buf.readDouble());
    }

    private static void writeGuide(RegistryFriendlyByteBuf buf, Guide guide) {
        buf.writeVarInt(guide.figures.size());
        for (Figure figure : guide.figures) {
            ComponentSerialization.STREAM_CODEC.encode(buf, figure.label);
            buf.writeUtf(figure.from, 128);
            buf.writeUtf(figure.to, 128);
        }
        buf.writeVarInt(guide.notes.size());
        guide.notes.forEach(note -> ComponentSerialization.STREAM_CODEC.encode(buf, note));
        buf.writeVarInt(guide.tokens.size());
        guide.tokens.forEach((key, value) -> { buf.writeUtf(key, 64); buf.writeUtf(value, 512); });
        buf.writeBoolean(guide.scaling != null);
        if (guide.scaling != null) ComponentSerialization.STREAM_CODEC.encode(buf, guide.scaling);
    }

    private static int bounded(RegistryFriendlyByteBuf buf, int limit) {
        int size = buf.readVarInt();
        if (size < 0 || size > limit) throw new IllegalArgumentException("Invalid skill guide size: " + size);
        return size;
    }

    private static Guide readGuide(RegistryFriendlyByteBuf buf) {
        var figures = new java.util.ArrayList<Figure>();
        int count = bounded(buf, 64);
        for (int i = 0; i < count; i++) figures.add(new Figure(ComponentSerialization.STREAM_CODEC.decode(buf), buf.readUtf(128), buf.readUtf(128)));
        var notes = new java.util.ArrayList<Component>();
        count = bounded(buf, 128);
        for (int i = 0; i < count; i++) notes.add(ComponentSerialization.STREAM_CODEC.decode(buf));
        var tokens = new java.util.LinkedHashMap<String, String>();
        count = bounded(buf, 64);
        for (int i = 0; i < count; i++) tokens.put(buf.readUtf(64), buf.readUtf(512));
        return new Guide(figures, notes, tokens, buf.readBoolean() ? ComponentSerialization.STREAM_CODEC.decode(buf) : null);
    }

    private static SkillScreenPayload read(RegistryFriendlyByteBuf buf) {
        String skillClass = buf.readUtf(TEXT);
        String ascension = buf.readUtf(TEXT);
        int level = buf.readVarInt();
        int available = buf.readVarInt();
        boolean inRift = buf.readBoolean();
        boolean ultimateOpen = buf.readBoolean();
        int count = buf.readVarInt();
        Entry[] entries = new Entry[count];
        for (int i = 0; i < count; i++) {
            entries[i] = new Entry(buf.readUtf(TEXT), buf.readUtf(TEXT), buf.readBoolean(), buf.readBoolean(),
                    buf.readVarInt(), buf.readVarInt(), buf.readDouble(), buf.readUtf(TEXT), buf.readBoolean(),
                    readFacts(buf), readGuide(buf));
        }
        int slots = buf.readVarInt();
        String[] loadout = new String[slots];
        for (int i = 0; i < slots; i++) {
            loadout[i] = buf.readUtf(TEXT);
        }
        String action = buf.readUtf(TEXT);
        String result = buf.readUtf(TEXT);
        Respec skillRespec = readRespec(buf);
        Respec statRespec = readRespec(buf);
        int attributePoints = buf.readVarInt();
        int primaries = bounded(buf, 16);
        Integer[] allocated = new Integer[primaries];
        for (int i = 0; i < primaries; i++) {
            allocated[i] = buf.readVarInt();
        }
        return new SkillScreenPayload(skillClass, ascension, level, available, inRift, ultimateOpen,
                List.of(entries), List.of(loadout), action, result, skillRespec, statRespec, attributePoints,
                List.of(allocated), buf.readVarInt());
    }

    @Override
    public Type<SkillScreenPayload> type() {
        return TYPE;
    }
}
