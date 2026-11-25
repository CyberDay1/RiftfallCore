package com.cyberday1.riftfall.skill;

import com.cyberday1.riftfall.gear.RelicSignatureCosts;

import com.cyberday1.riftfall.network.RiftSheetPayload;
import com.cyberday1.riftfall.progression.RiftXp;
import com.cyberday1.riftfall.progression.TabLevel;
import com.cyberday1.riftfall.rift.RiftLevels;
import com.cyberday1.riftfall.stat.ClassAlignment;
import com.cyberday1.riftfall.stat.RiftStats;
import com.cyberday1.riftfall.stat.StatBlock;
import com.cyberday1.riftfall.stat.StatModifiers;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * The server verbs every client surface shares: choose a class, spend, equip, unequip, ascend, cast, respec. The
 * command is the first client; keys and screens later call these same methods rather than
 * re-implementing them.
 *
 * <p>Changing the build is refused inside a rift by the dimension the player stands in, not by a
 * run roster. Casting is not refused there. Level is read live from {@link RiftStats}, like the forge.
 * A use the engine started (an item, a melee hit) pays through {@link #pay}, by the same price.
 */
public final class SkillService {

    /** What a slot cast did. The last eight are {@link SkillCasts.Result}'s, by name. */
    public enum Cast {
        BAD_SLOT, EMPTY_SLOT, SLOT_LOCKED, UNKNOWN, NOT_OWNED,
        CAST, ON_COOLDOWN, NO_MANA, NO_ENGINE, UNKNOWN_ABILITY, UNKNOWN_ATTRIBUTE, BAD_RANK, NOT_CASTABLE, NO_TARGET, WRONG_WEAPON
    }

    public record Casting(Cast result, @Nullable SkillDef def, int rank, double cost) {
    }

    /** The seam a test stands in for the engine at. {@code SkillCasts::cast} in production. */
    @FunctionalInterface
    interface Caster {
        SkillCasts.Result cast(ServerLevel level, LivingEntity caster, SkillNode node, int rank, SkillCasts.Price price,
                               @Nullable String skillClass);
    }

    private SkillService() {
    }

    public static int levelOf(ServerPlayer player) {
        return RiftXp.levelFor(RiftStats.of(player.server).xpFor(player.getUUID()), RiftXp.LEVEL_CAP);
    }

    public static SkillBook bookOf(ServerPlayer player) {
        return RiftStats.of(player.server).bookFor(player.getUUID());
    }

    /** The stored class (P-5), or null for a character who has not chosen one yet. */
    public static @Nullable String characterClass(ServerPlayer player) {
        return bookOf(player).skillClass();
    }

    /** Chosen once, outside a rift. How a player is offered the choice is a design call; this is the verb. */
    public static SkillBook.Choose chooseClass(ServerPlayer player, String name) {
        RiftStats stats = RiftStats.of(player.server);
        SkillBook book = stats.bookFor(player.getUUID());
        SkillBook.Choose result = checkChoose(book, name, SkillDefs.table(), player.level().dimension());
        if (result == SkillBook.Choose.OK) {
            stats.setBook(player.getUUID(), book.withClass(name));
            StatBlock block = ClassAlignment.blockFor(name);
            if (block != null) {
                StatModifiers.store(player, block);
            }
            com.cyberday1.riftfall.story.StoryBridge.buildChanged(player);
        }
        return result;
    }

    /**
     * An operator's class reset (ruled 2026-10-11): class, ascension, every rank and the loadout go, and
     * the stat block with them. No kit follows the next choice; the ledger already names this character.
     */
    public static SkillBook.Reset resetClass(ServerPlayer player) {
        RiftStats stats = RiftStats.of(player.server);
        SkillBook book = stats.bookFor(player.getUUID());
        SkillBook.Reset result = checkReset(book, player.level().dimension());
        if (result == SkillBook.Reset.OK) {
            SkillBook next = book.withoutClass();
            stats.setBook(player.getUUID(), next);
            // Attribute points come back free with the class, as the ranks do.
            stats.setAlloc(player.getUUID(), StatBlock.empty(0));
            StatModifiers.store(player, StatBlock.empty(0));
            PassiveModifiers.apply(player.getAttributes(), next, SkillDefs.table());
            SkillSync.cooldowns(player);
        }
        return result;
    }

    /** What a skill respec would answer now; it changes nothing. */
    public static Respec.Quote quoteSkills(ServerPlayer player) {
        RiftStats stats = RiftStats.of(player.server);
        return Respec.quote(stats.xpFor(player.getUUID()), stats.bookFor(player.getUUID()).spent(),
                RiftLevels.isRift(player.level().dimension()));
    }

    /** What a stat respec would answer now: NOTHING until the player has placed attribute points. */
    public static Respec.Quote quoteStats(ServerPlayer player) {
        RiftStats stats = RiftStats.of(player.server);
        UUID id = player.getUUID();
        return Respec.quote(stats.xpFor(id), Respec.allocatedStats(stats.allocFor(id)),
                RiftLevels.isRift(player.level().dimension()));
    }

    /** The player's skill respec: half a level's XP for every rank back as points. Class and ascension stay. */
    public static Respec.Quote respecSkills(ServerPlayer player) {
        Respec.Quote quote = quoteSkills(player);
        if (quote.ok()) {
            RiftStats stats = RiftStats.of(player.server);
            SkillBook next = stats.bookFor(player.getUUID()).respecced();
            pay(player, stats, quote.cost());
            stats.setBook(player.getUUID(), next);
            PassiveModifiers.apply(player.getAttributes(), next, SkillDefs.table());
            SkillSync.cooldowns(player);
        }
        return quote;
    }

    /** The player's stat respec, at the same price: allocated points go back to the pool, the class block stays. */
    public static Respec.Quote respecStats(ServerPlayer player) {
        Respec.Quote quote = quoteStats(player);
        if (quote.ok()) {
            RiftStats stats = RiftStats.of(player.server);
            pay(player, stats, quote.cost());
            stats.setAlloc(player.getUUID(), StatBlock.empty(0));
            StatModifiers.store(player, Respec.statsReset(ClassAlignment.blockFor(characterClass(player))));
        }
        return quote;
    }

    private static void pay(ServerPlayer player, RiftStats stats, long cost) {
        stats.setXp(player.getUUID(), stats.xpFor(player.getUUID()) - cost);
        TabLevel.sync(player);
        if (!(player instanceof FakePlayer)) {
            PacketDistributor.sendToPlayer(player, new RiftSheetPayload(stats.xpFor(player.getUUID()), RiftXp.LEVEL_CAP));
        }
    }

    static SkillBook.Reset checkReset(SkillBook book, ResourceKey<Level> where) {
        if (RiftLevels.isRift(where)) {
            return SkillBook.Reset.IN_RIFT;
        }
        return book.checkReset();
    }

    /** A castable or a passive: one verb, one set of rules. A bought passive is on the player at once. */
    public static SkillBook.Spend spend(ServerPlayer player, String id) {
        RiftStats stats = RiftStats.of(player.server);
        SkillBook book = stats.bookFor(player.getUUID());
        SkillBook.Purchase def = SkillDefs.table().purchase(id);
        int level = levelOf(player);
        SkillBook.Spend result = checkSpend(book, def, level, player.level().dimension());
        if (result == SkillBook.Spend.OK) {
            SkillBook next = book.bought(def, level);
            stats.setBook(player.getUUID(), next);
            PassiveModifiers.apply(player.getAttributes(), next, SkillDefs.table());
        }
        return result;
    }

    public static SkillBook.Equip equip(ServerPlayer player, int slot, String id) {
        RiftStats stats = RiftStats.of(player.server);
        SkillBook book = stats.bookFor(player.getUUID());
        SkillBook.Equip result = checkEquip(book, slot, SkillDefs.get(id), player.level().dimension());
        if (result == SkillBook.Equip.OK) {
            stats.setBook(player.getUUID(), book.equipped(slot, id));
            SkillSync.cooldowns(player);
            com.cyberday1.riftfall.story.StoryBridge.buildChanged(player);
        }
        return result;
    }

    public static SkillBook.Equip unequip(ServerPlayer player, int slot) {
        RiftStats stats = RiftStats.of(player.server);
        SkillBook book = stats.bookFor(player.getUUID());
        SkillBook.Equip result = checkUnequip(book, slot, player.level().dimension());
        if (result == SkillBook.Equip.OK) {
            stats.setBook(player.getUUID(), book.unequipped(slot));
            SkillSync.cooldowns(player);
        }
        return result;
    }

    /** Chosen once, outside a rift, at level 60 or later. Only the operator's class reset undoes it (E-10). */
    public static SkillBook.Ascend ascend(ServerPlayer player, String name) {
        RiftStats stats = RiftStats.of(player.server);
        SkillBook book = stats.bookFor(player.getUUID());
        SkillTable table = SkillDefs.table();
        SkillBook.Ascend result = checkAscend(book, name, table, levelOf(player), player.level().dimension());
        if (result == SkillBook.Ascend.OK) {
            stats.setBook(player.getUUID(), book.ascendedAs(new SkillBook.Ascension(table.classOf(name), name)));
            com.cyberday1.riftfall.story.StoryBridge.buildChanged(player);
        }
        return result;
    }

    /** Every cast surface ends here: the command, the cast keys (V3), and later the wheel. */
    public static Casting castSlot(ServerPlayer player, int slot) {
        Casting casting = cast(player.serverLevel(), player, bookOf(player), slot, RiftMana.max(player),
                SkillDefs::get, SkillCasts::cast);
        if (casting.result() == Cast.CAST) {
            SkillSync.mana(player);
        }
        if (inRange(slot)) {
            SkillSync.cooldown(player, slot);
        }
        return casting;
    }

    /**
     * Riftfall's mana and cooldown for a use of {@code ability} that did not start at {@link #castSlot}
     * (ruled 2026-10-06): the owned node that grants it pays at its rank. Nothing grants it, nothing runs.
     */
    static Casting pay(ServerPlayer player, SkillNode.Engine engine, ResourceLocation ability,
                       Supplier<SkillCasts.Invocation> use) {
        return pay(player, engine, ability, (level, caster, node, riders) -> use.get());
    }

    static Casting pay(ServerPlayer player, SkillNode.Engine engine, ResourceLocation ability,
                       SkillCasts.Invoker use) {
        Casting casting = pay(player.serverLevel(), player, bookOf(player), engine, ability, RiftMana.max(player),
                SkillDefs::get, use);
        if (casting.result() == Cast.CAST) {
            SkillSync.mana(player);
            SkillSync.cooldowns(player);
        }
        return casting;
    }

    static Casting pay(ServerLevel level, LivingEntity caster, SkillBook book, SkillNode.Engine engine,
                       ResourceLocation ability, double maxMana, Function<String, SkillDef> defs,
                       Supplier<SkillCasts.Invocation> use) {
        return pay(level, caster, book, engine, ability, maxMana, defs, (l, c, node, riders) -> use.get());
    }

    static Casting pay(ServerLevel level, LivingEntity caster, SkillBook book, SkillNode.Engine engine,
                       ResourceLocation ability, double maxMana, Function<String, SkillDef> defs,
                       SkillCasts.Invoker use) {
        if (!SkillCasts.eligible(caster)) {
            return new Casting(Cast.NOT_CASTABLE, null, 0, 0.0);
        }
        SkillDef def = AbilityGrants.payer(book, defs, engine, ability);
        if (def == null) {
            return new Casting(Cast.NOT_OWNED, null, 0, 0.0);
        }
        int rank = book.rank(def.id());
        if (!def.cast().holds(rank)) {
            return new Casting(Cast.BAD_RANK, def, rank, 0.0);
        }
        double cost = priced(caster, def.cast(), def.costAt(rank));
        SkillCasts.Result result = SkillCasts.cast(level, caster, def.cast(), rank,
                price(caster, cost, maxMana), book.skillClass(), use);
        return new Casting(Cast.valueOf(result.name()), def, rank, cost);
    }

    static boolean inRange(int slot) {
        return slot >= 0 && slot < SkillBook.SLOTS;
    }

    static SkillBook.Choose checkChoose(SkillBook book, String name, SkillTable table, ResourceKey<Level> where) {
        if (RiftLevels.isRift(where)) {
            return SkillBook.Choose.IN_RIFT;
        }
        return book.checkChoose(name, SkillTable.CLASSES, id -> classOfOwned(table, id));
    }

    /** The class an owned skill belongs to: null for a universal passive or one the table no longer has. */
    static @Nullable String classOfOwned(SkillTable table, String id) {
        SkillBook.Purchase owned = table.purchase(id);
        return owned == null || owned.universal() ? null : owned.skillClass();
    }

    static SkillBook.Spend checkSpend(SkillBook book, SkillBook.@Nullable Purchase def, int level,
                                      ResourceKey<Level> where) {
        if (RiftLevels.isRift(where)) {
            return SkillBook.Spend.IN_RIFT;
        }
        return def == null ? SkillBook.Spend.UNKNOWN : book.checkSpend(def, level);
    }

    static SkillBook.Equip checkEquip(SkillBook book, int slot, @Nullable SkillDef def, ResourceKey<Level> where) {
        if (RiftLevels.isRift(where)) {
            return SkillBook.Equip.IN_RIFT;
        }
        return def == null ? SkillBook.Equip.UNKNOWN : book.checkEquip(slot, def);
    }

    static SkillBook.Ascend checkAscend(SkillBook book, String name, SkillTable table, int level,
                                        ResourceKey<Level> where) {
        if (RiftLevels.isRift(where)) {
            return SkillBook.Ascend.IN_RIFT;
        }
        return book.checkAscend(name, table.classOf(name), level);
    }

    static SkillBook.Equip checkUnequip(SkillBook book, int slot, ResourceKey<Level> where) {
        if (RiftLevels.isRift(where)) {
            return SkillBook.Equip.IN_RIFT;
        }
        if (slot < 0 || slot >= SkillBook.SLOTS) {
            return SkillBook.Equip.BAD_SLOT;
        }
        return book.slot(slot) == null ? SkillBook.Equip.EMPTY_SLOT : SkillBook.Equip.OK;
    }

    /** Everything decided before the engine is asked: what is in the slot, at what rank, for how much. */
    record Plan(@Nullable Cast refusal, @Nullable SkillDef def, int rank, double cost) {
    }

    static Plan plan(SkillBook book, int slot, Function<String, SkillDef> defs) {
        // Refused, never clamped: a key packet can carry any int (the KeyForgeMenu lesson).
        if (!inRange(slot)) {
            return new Plan(Cast.BAD_SLOT, null, 0, 0.0);
        }
        if (slot == SkillBook.ULTIMATE_SLOT && !book.ultimateOpen()) {
            return new Plan(Cast.SLOT_LOCKED, null, 0, 0.0);
        }
        String id = book.slot(slot);
        if (id == null) {
            return new Plan(Cast.EMPTY_SLOT, null, 0, 0.0);
        }
        SkillDef def = defs.apply(id);
        if (def == null) {
            return new Plan(Cast.UNKNOWN, null, 0, 0.0);
        }
        int rank = book.rank(id);
        if (rank < 1) {
            return new Plan(Cast.NOT_OWNED, def, 0, 0.0);
        }
        if (!def.cast().holds(rank)) {
            return new Plan(Cast.BAD_RANK, def, rank, 0.0);
        }
        return new Plan(null, def, rank, def.costAt(rank));
    }

    static Casting cast(ServerLevel level, LivingEntity caster, SkillBook book, int slot,
                        double maxMana, Function<String, SkillDef> defs, Caster engine) {
        if (!SkillCasts.eligible(caster)) {
            return new Casting(Cast.NOT_CASTABLE, null, 0, 0.0);
        }
        Plan plan = plan(book, slot, defs);
        if (plan.refusal() != null) {
            return new Casting(plan.refusal(), plan.def(), plan.rank(), 0.0);
        }
        double cost = priced(caster, plan.def().cast(), plan.cost());
        SkillCasts.Result result = engine.cast(level, caster, plan.def().cast(), plan.rank(),
                price(caster, cost, maxMana), book.skillClass());
        return new Casting(Cast.valueOf(result.name()), plan.def(), plan.rank(), cost);
    }

    /** A node's mana with Unstable Step's surcharge on a movement-family node; refused, never discounted, if short. */
    public static double priced(LivingEntity caster, SkillNode node, double cost) {
        return cost + RelicSignatureCosts.surcharge(caster, node.category() == SkillNode.Category.MOVEMENT);
    }

    /** {@code cost} off Riftfall's bar, all or nothing, and back on a cast that did not happen. */
    private static SkillCasts.Price price(LivingEntity caster, double cost, double maxMana) {
        UUID who = caster.getUUID();
        return new SkillCasts.Price() {
            @Override
            public boolean take() {
                boolean paid = RiftMana.tryCharge(who, cost, maxMana);
                if (paid) {
                    ManaCombat.mark(caster);
                }
                return paid;
            }

            @Override
            public void refund() {
                RiftMana.refund(who, cost, maxMana);
            }
        };
    }
}
