package com.cyberday1.riftfall.skill;

import com.cyberday1.riftfall.RiftfallCore;
import com.cyberday1.riftfall.gear.StarterKits;
import com.cyberday1.riftfall.progression.RiftTier;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.logging.LogUtils;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/**
 * {@code /riftfall skill}: points, class, spend, equip, unequip, loadout, ascend, cast. Permission 0, beside
 * {@code claim} and {@code party}, so every step of the economy is reachable by a player with no
 * screen. A thin shell over {@link SkillService}; every refusal says why.
 */
public final class SkillCommand {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final String PREFIX = "message." + RiftfallCore.MODID + ".skill_";

    static final String POINTS = PREFIX + "points";
    static final String OVERSPENT = PREFIX + "overspent";
    static final String MANA = PREFIX + "mana";
    static final String OWNED = PREFIX + "owned";
    static final String NONE_OWNED = PREFIX + "none_owned";
    static final String SPENT = PREFIX + "spent";
    static final String SPENT_ONE = PREFIX + "spent_one";
    static final String UNKNOWN = PREFIX + "unknown";
    static final String SPEND_IN_RIFT = PREFIX + "spend_in_rift";
    static final String NOT_ENOUGH = PREFIX + "not_enough";
    static final String MAX_RANK = PREFIX + "max_rank";
    static final String NOT_ASCENDED = PREFIX + "not_ascended";
    static final String OTHER_ASCENDANCY = PREFIX + "other_ascendancy";
    static final String WRONG_CLASS = PREFIX + "wrong_class";
    static final String EQUIPPED = PREFIX + "equipped";
    static final String EQUIP_IN_RIFT = PREFIX + "equip_in_rift";
    static final String NOT_OWNED = PREFIX + "not_owned";
    static final String WRONG_SLOT_ULTIMATE = PREFIX + "wrong_slot_ultimate";
    static final String WRONG_SLOT_ACTIVE = PREFIX + "wrong_slot_active";
    static final String SLOT_LOCKED = PREFIX + "slot_locked";
    static final String BAD_SLOT = PREFIX + "bad_slot";
    static final String UNEQUIPPED = PREFIX + "unequipped";
    static final String SLOT_EMPTY = PREFIX + "slot_empty";
    static final String SLOT_NAME = PREFIX + "slot_name";
    static final String SLOT_ULTIMATE = PREFIX + "slot_ultimate";
    static final String LOADOUT = PREFIX + "loadout";
    static final String LOADOUT_SLOT = PREFIX + "loadout_slot";
    static final String LOADOUT_EMPTY = PREFIX + "loadout_empty";
    static final String LOADOUT_LOCKED = PREFIX + "loadout_locked";
    static final String CAST = PREFIX + "cast";
    static final String ON_COOLDOWN = PREFIX + "on_cooldown";
    static final String NO_MANA = PREFIX + "no_mana";
    static final String NO_ENGINE = PREFIX + "no_engine";
    static final String WRONG_WEAPON = PREFIX + "wrong_weapon";
    static final String BROKEN = PREFIX + "broken";
    static final String ASCENSION = PREFIX + "ascension";
    static final String NOT_YET_ASCENDED = PREFIX + "not_yet_ascended";
    static final String ASCENDED = PREFIX + "ascended";
    static final String ASCEND_IN_RIFT = PREFIX + "ascend_in_rift";
    static final String ASCEND_UNKNOWN = PREFIX + "ascend_unknown";
    static final String ASCEND_ALREADY = PREFIX + "ascend_already";
    static final String ASCEND_TOO_LOW = PREFIX + "ascend_too_low";
    static final String ASCEND_WRONG_CLASS = PREFIX + "ascend_wrong_class";
    static final String ASCEND_NO_CLASS = PREFIX + "ascend_no_class";
    static final String NO_CLASS = PREFIX + "no_class";
    static final String CLASS_IS = PREFIX + "class_is";
    static final String CLASS_NONE = PREFIX + "class_none";
    static final String CLASS_CHOSEN = PREFIX + "class_chosen";
    static final String CLASS_IN_RIFT = PREFIX + "class_in_rift";
    static final String CLASS_UNKNOWN = PREFIX + "class_unknown";
    static final String CLASS_ALREADY = PREFIX + "class_already";
    static final String CLASS_OWNS_OTHER = PREFIX + "class_owns_other";
    /** A class name behind its English article; a language without one writes these as the bare name. */
    static final String CLASS_A = PREFIX + "class_a";
    static final String CLASS_AN = PREFIX + "class_an";
    static final String NO_TARGET = PREFIX + "no_target";

    /** Every key above with its English, which the language provider generates from. */
    public static final Map<String, String> ENGLISH = Map.ofEntries(
            Map.entry(POINTS, "Level %s, tier %s: %s points earned, %s spent, %s to spend."),
            Map.entry(OVERSPENT, "You have spent %s more points than your level gives. New ranks wait until you have earned them back."),
            Map.entry(MANA, "Mana: %s / %s."),
            Map.entry(OWNED, "  %s: rank %s of %s"),
            Map.entry(NONE_OWNED, "You own no skills yet. Buy one on the Skills page (J)."),
            Map.entry(SPENT, "%s is now rank %s. %s points left to spend."),
            Map.entry(SPENT_ONE, "%s is now rank %s. %s point left to spend."),
            Map.entry(UNKNOWN, "There is no skill called %s."),
            Map.entry(SPEND_IN_RIFT, "Skill points can only be spent outside a rift."),
            Map.entry(NOT_ENOUGH, "The next rank of %s costs %s points; you have %s to spend."),
            Map.entry(MAX_RANK, "%s is already at its highest rank, %s."),
            Map.entry(NOT_ASCENDED, "%s is an ascendancy skill. Choose an ascendancy first, at level %s or later."),
            Map.entry(OTHER_ASCENDANCY, "%s belongs to the %s ascendancy, and you ascended as %s."),
            Map.entry(WRONG_CLASS, "%s is %s skill, and you are %s."),
            Map.entry(EQUIPPED, "%s is in %s."),
            Map.entry(EQUIP_IN_RIFT, "Your loadout is locked inside a rift. Change it before you go in."),
            Map.entry(NOT_OWNED, "You do not own %s yet. Buy it on the Skills page (J)."),
            Map.entry(WRONG_SLOT_ULTIMATE, "%s is an ultimate, so it only goes in the ultimate slot."),
            Map.entry(WRONG_SLOT_ACTIVE, "Only an ultimate goes in the ultimate slot, and %s is not one."),
            Map.entry(SLOT_LOCKED, "The ultimate slot opens when you ascend, at level %s or later."),
            Map.entry(BAD_SLOT, "There is no such slot."),
            Map.entry(UNEQUIPPED, "%s is empty now."),
            Map.entry(SLOT_EMPTY, "%s has nothing in it."),
            Map.entry(SLOT_NAME, "slot %s"),
            Map.entry(SLOT_ULTIMATE, "the ultimate slot"),
            Map.entry(LOADOUT, "Loadout:"),
            Map.entry(LOADOUT_SLOT, "  %s: %s"),
            Map.entry(LOADOUT_EMPTY, "  %s: empty"),
            Map.entry(LOADOUT_LOCKED, "  %s: opens when you ascend"),
            Map.entry(CAST, "Cast %s for %s mana. %s left."),
            Map.entry(ON_COOLDOWN, "%s is ready again in %s seconds."),
            Map.entry(NO_MANA, "%s costs %s mana; you have %s."),
            Map.entry(NO_ENGINE, "%s cannot be cast: the mod it casts through is not installed."),
            Map.entry(WRONG_WEAPON, "Equip your class weapon in your main hand: %s."),
            Map.entry(BROKEN, "%s could not be cast. That is a fault in the skill, not something you did."),
            Map.entry(NO_TARGET, "No valid target in range."),
            Map.entry(ASCENSION, "Ascendancy: %s, of the %s class."),
            Map.entry(NOT_YET_ASCENDED, "No ascendancy chosen yet. The choice opens at level %s."),
            Map.entry(ASCENDED, "You have ascended as %s. Its skills are open to you, and the other %s ascendancies are closed for good."),
            Map.entry(ASCEND_IN_RIFT, "You can only choose an ascendancy outside a rift."),
            Map.entry(ASCEND_UNKNOWN, "There is no ascendancy called %s."),
            Map.entry(ASCEND_ALREADY, "You have already ascended as %s. The choice is permanent."),
            Map.entry(ASCEND_TOO_LOW, "Ascension opens at level %s; you are level %s."),
            Map.entry(ASCEND_WRONG_CLASS, "%s is %s ascendancy, and you are %s."),
            Map.entry(ASCEND_NO_CLASS, "Choose a class before an ascendancy, on the Classes bookmark (J)."),
            Map.entry(NO_CLASS, "Choose a class before buying skills, on the Classes bookmark (J)."),
            Map.entry(CLASS_IS, "Class: %s."),
            Map.entry(CLASS_NONE, "No class chosen yet. Choose one on the Classes bookmark (J)."),
            Map.entry(CLASS_CHOSEN, "You are %s. The choice is permanent."),
            Map.entry(CLASS_IN_RIFT, "You can only choose a class outside a rift."),
            Map.entry(CLASS_UNKNOWN, "There is no class called %s."),
            Map.entry(CLASS_ALREADY, "You are already %s. The choice is permanent."),
            Map.entry(CLASS_OWNS_OTHER, "You already own skills of another class, so you cannot become %s."),
            Map.entry(CLASS_A, "a %s"),
            Map.entry(CLASS_AN, "an %s"));

    private static final String SKILL = "skill";
    private static final String ASCENDANCY = "ascendancy";
    private static final String CLASS = "class";
    private static final int DEV_PERMISSION = 2;
    private static final String SLOT = "slot";

    private SkillCommand() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> subtree() {
        return Commands.literal("skill")
                .executes(context -> points(context.getSource()))
                .then(Commands.literal("points")
                        .executes(context -> points(context.getSource())))
                .then(Commands.literal("class")
                        .executes(context -> showClass(context.getSource()))
                        .then(Commands.argument(CLASS, StringArgumentType.word())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                        SkillTable.CLASSES.stream().filter(c -> !c.equals(SkillTable.DEV_CLASS)), builder))
                                .executes(context -> chooseClass(context.getSource(),
                                        StringArgumentType.getString(context, CLASS).toLowerCase(Locale.ROOT)))))
                .then(Commands.literal("spend")
                        .then(skill(true).executes(context -> spend(context.getSource(), skillOf(context)))))
                .then(Commands.literal("equip")
                        .then(slot().then(skill(false).executes(context ->
                                equip(context.getSource(), slotOf(context), skillOf(context)))))
                        .then(Commands.literal("ultimate").then(skill(false).executes(context ->
                                equip(context.getSource(), SkillBook.ULTIMATE_SLOT, skillOf(context))))))
                .then(Commands.literal("unequip")
                        .then(slot().executes(context -> unequip(context.getSource(), slotOf(context))))
                        .then(Commands.literal("ultimate")
                                .executes(context -> unequip(context.getSource(), SkillBook.ULTIMATE_SLOT))))
                .then(Commands.literal("loadout")
                        .executes(context -> loadout(context.getSource())))
                .then(Commands.literal("ascend")
                        .then(Commands.argument(ASCENDANCY, StringArgumentType.word())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(offered(
                                        context.getSource(), SkillDefs.table().ascendancies(), SkillDefs.table()::classOf), builder))
                                .executes(context -> ascend(context.getSource(),
                                        StringArgumentType.getString(context, ASCENDANCY).toLowerCase(Locale.ROOT)))))
                .then(Commands.literal("cast")
                        .then(slot().executes(context -> cast(context.getSource(), slotOf(context))))
                        .then(Commands.literal("ultimate")
                                .executes(context -> cast(context.getSource(), SkillBook.ULTIMATE_SLOT))));
    }

    /** Spend suggests passives too; equip suggests castables only. */
    private static RequiredArgumentBuilder<CommandSourceStack, String> skill(boolean passives) {
        return Commands.argument(SKILL, StringArgumentType.word())
                .suggests((context, builder) -> {
                    SharedSuggestionProvider.suggest(offered(context.getSource(), SkillDefs.ids(), SkillCommand::classOfSkill), builder);
                    return passives ? SharedSuggestionProvider.suggest(offered(context.getSource(),
                            SkillDefs.table().passives().keySet(), SkillCommand::classOfSkill), builder)
                            : builder.buildFuture();
                });
    }

    /** Dev-class skills and ascendancies are test content, offered to operators only. */
    private static List<String> offered(CommandSourceStack source, Collection<String> ids, Function<String, String> classOf) {
        boolean operator = source.hasPermission(DEV_PERMISSION);
        return ids.stream().filter(id -> operator || !SkillTable.DEV_CLASS.equals(classOf.apply(id))).toList();
    }

    private static String classOfSkill(String id) {
        SkillBook.Purchase purchase = SkillDefs.table().purchase(id);
        return purchase == null ? null : purchase.skillClass();
    }

    /** Display names for chat: the authored spell name ({@link SkillNames}), else the id made readable. */
    public static Component skillName(String id) {
        return SkillNames.of(id);
    }

    public static Component className(String id) {
        return displayName("class", id);
    }

    /** "a Monk", "an Arcanist": the class name with its article, for a sentence that names what someone is. */
    public static Component classWithArticle(String id) {
        if (id == null || id.isEmpty()) {
            return Component.empty();
        }
        String path = id.substring(id.indexOf(':') + 1);
        return Component.translatable(startsWithVowel(readable(path)) ? CLASS_AN : CLASS_A, className(id));
    }

    static boolean startsWithVowel(String name) {
        return !name.isEmpty() && "AEIOUaeiou".indexOf(name.charAt(0)) >= 0;
    }

    public static Component ascendancyName(String id) {
        return displayName("ascendancy", id);
    }

    private static Component displayName(String kind, String id) {
        if (id == null || id.isEmpty()) {
            return Component.empty();
        }
        String path = id.substring(id.indexOf(':') + 1);
        return Component.translatableWithFallback(kind + "." + RiftfallCore.MODID + "." + path, readable(path));
    }

    /** {@code riftfall:monk_palm_strike} or {@code monk_palm_strike} to "Monk Palm Strike". */
    public static String readable(String id) {
        StringBuilder out = new StringBuilder();
        for (String word : id.substring(id.indexOf(':') + 1).split("[_/.]+")) {
            if (word.isEmpty()) {
                continue;
            }
            if (!out.isEmpty()) {
                out.append(' ');
            }
            out.append(word.substring(0, 1).toUpperCase(Locale.ROOT)).append(word.substring(1));
        }
        return out.toString();
    }

    /** Slots are numbered 1 to 8 for players and 0 to 7 inside. */
    private static RequiredArgumentBuilder<CommandSourceStack, Integer> slot() {
        return Commands.argument(SLOT, IntegerArgumentType.integer(1, SkillBook.ACTIVE_SLOTS));
    }

    private static String skillOf(CommandContext<CommandSourceStack> context) {
        return StringArgumentType.getString(context, SKILL).toLowerCase(Locale.ROOT);
    }

    private static int slotOf(CommandContext<CommandSourceStack> context) {
        return IntegerArgumentType.getInteger(context, SLOT) - 1;
    }

    private static Component slotName(int slot) {
        return slot == SkillBook.ULTIMATE_SLOT
                ? Component.translatable(SLOT_ULTIMATE)
                : Component.translatable(SLOT_NAME, slot + 1);
    }

    private static String mana(double amount) {
        return String.format(Locale.ROOT, "%.1f", amount);
    }

    private static int points(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        SkillBook book = SkillService.bookOf(player);
        int level = SkillService.levelOf(player);
        int available = book.available(level);
        source.sendSuccess(() -> Component.translatable(POINTS, level, RiftTier.of(level),
                SkillBook.earned(level), book.spent(), available), false);
        if (available < 0) {
            source.sendSuccess(() -> Component.translatable(OVERSPENT, -available), false);
        }
        source.sendSuccess(() -> Component.translatable(MANA, mana(RiftMana.current(player)),
                mana(RiftMana.max(player))), false);
        source.sendSuccess(() -> classLine(book), false);
        SkillBook.Ascension ascension = book.ascension();
        source.sendSuccess(() -> ascension == null
                ? Component.translatable(NOT_YET_ASCENDED, SkillBook.ASCENSION_LEVEL)
                : Component.translatable(ASCENSION, ascendancyName(ascension.name()), className(ascension.skillClass())), false);
        if (book.ranks().isEmpty()) {
            source.sendSuccess(() -> Component.translatable(NONE_OWNED), false);
        }
        book.ranks().forEach((id, owned) -> source.sendSuccess(() ->
                Component.translatable(OWNED, skillName(id), owned.rank(), owned.kind().maxRank()), false));
        return Math.max(0, available);
    }

    private static int spend(CommandSourceStack source, String id) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        SkillBook.Spend result = SkillService.spend(player, id);
        SkillBook book = SkillService.bookOf(player);
        int level = SkillService.levelOf(player);
        SkillBook.Purchase def = SkillDefs.table().purchase(id);
        switch (result) {
            case OK -> {
                int left = book.available(level);
                source.sendSuccess(() -> Component.translatable(left == 1 ? SPENT_ONE : SPENT, skillName(id), book.rank(id), left),
                        false);
                return book.rank(id);
            }
            case IN_RIFT -> source.sendFailure(Component.translatable(SPEND_IN_RIFT));
            case UNKNOWN -> source.sendFailure(Component.translatable(UNKNOWN, id));
            case MAX_RANK -> source.sendFailure(Component.translatable(MAX_RANK, skillName(id), def.kind().maxRank()));
            case NOT_ASCENDED -> source.sendFailure(Component.translatable(NOT_ASCENDED, skillName(id), SkillBook.ASCENSION_LEVEL));
            case OTHER_ASCENDANCY -> source.sendFailure(Component.translatable(OTHER_ASCENDANCY, skillName(id),
                    ascendancyName(def.ascendancy()), ascendancyName(book.ascension().name())));
            case NO_CLASS -> source.sendFailure(Component.translatable(NO_CLASS));
            case WRONG_CLASS -> source.sendFailure(Component.translatable(WRONG_CLASS, skillName(id),
                    classWithArticle(def.skillClass()), classWithArticle(book.skillClass())));
            case NOT_ENOUGH_POINTS -> source.sendFailure(Component.translatable(NOT_ENOUGH, skillName(id),
                    SkillBook.rankCost(def.kind(), book.rank(id) + 1), book.available(level)));
        }
        return 0;
    }

    private static int equip(CommandSourceStack source, int slot, String id) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        SkillBook.Equip result = SkillService.equip(player, slot, id);
        if (result == SkillBook.Equip.OK) {
            source.sendSuccess(() -> Component.translatable(EQUIPPED, skillName(id), slotName(slot)), false);
            return 1;
        }
        source.sendFailure(refusal(result, slot, id));
        return 0;
    }

    private static int unequip(CommandSourceStack source, int slot) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        SkillBook.Equip result = SkillService.unequip(player, slot);
        if (result == SkillBook.Equip.OK) {
            source.sendSuccess(() -> Component.translatable(UNEQUIPPED, slotName(slot)), false);
            return 1;
        }
        source.sendFailure(refusal(result, slot, null));
        return 0;
    }

    private static Component refusal(SkillBook.Equip result, int slot, String id) {
        return switch (result) {
            case IN_RIFT -> Component.translatable(EQUIP_IN_RIFT);
            case UNKNOWN -> Component.translatable(UNKNOWN, id);
            case NOT_OWNED -> Component.translatable(NOT_OWNED, skillName(id));
            case SLOT_LOCKED -> Component.translatable(SLOT_LOCKED, SkillBook.ASCENSION_LEVEL);
            case WRONG_SLOT -> slot == SkillBook.ULTIMATE_SLOT
                    ? Component.translatable(WRONG_SLOT_ACTIVE, skillName(id))
                    : Component.translatable(WRONG_SLOT_ULTIMATE, skillName(id));
            case EMPTY_SLOT -> Component.translatable(SLOT_EMPTY, slotName(slot));
            case BAD_SLOT, OK -> Component.translatable(BAD_SLOT);
        };
    }

    private static int loadout(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        SkillBook book = SkillService.bookOf(player);
        boolean open = book.ultimateOpen();
        source.sendSuccess(() -> Component.translatable(LOADOUT), false);
        for (int slot = 0; slot < SkillBook.SLOTS; slot++) {
            Component name = slotName(slot);
            String id = book.slot(slot);
            Component line;
            if (slot == SkillBook.ULTIMATE_SLOT && !open) {
                line = Component.translatable(LOADOUT_LOCKED, name);
            } else if (id == null) {
                line = Component.translatable(LOADOUT_EMPTY, name);
            } else {
                line = Component.translatable(LOADOUT_SLOT, name, skillName(id));
            }
            source.sendSuccess(() -> line, false);
        }
        return (int) book.loadout().stream().filter(id -> id != null).count();
    }

    private static int cast(CommandSourceStack source, int slot) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        SkillService.Casting casting = SkillService.castSlot(player, slot);
        Component line = castLine(player, slot, casting);
        if (casting.result() == SkillService.Cast.CAST) {
            source.sendSuccess(() -> line, false);
            return 1;
        }
        source.sendFailure(line);
        return 0;
    }

    /** What a slot cast says, shared by the command and the cast keys' action-bar line. */
    public static Component castLine(ServerPlayer player, int slot, SkillService.Casting casting) {
        String id = casting.def() != null ? casting.def().id()
                : SkillService.inRange(slot) ? String.valueOf(SkillService.bookOf(player).slot(slot)) : "";
        return switch (casting.result()) {
            case CAST -> Component.translatable(CAST, skillName(id), mana(casting.cost()), mana(RiftMana.current(player)));
            case BAD_SLOT -> Component.translatable(BAD_SLOT);
            case EMPTY_SLOT -> Component.translatable(SLOT_EMPTY, slotName(slot));
            case SLOT_LOCKED -> Component.translatable(SLOT_LOCKED, SkillBook.ASCENSION_LEVEL);
            case UNKNOWN -> Component.translatable(UNKNOWN, id);
            case NOT_OWNED -> Component.translatable(NOT_OWNED, skillName(id));
            case ON_COOLDOWN -> Component.translatable(ON_COOLDOWN, skillName(id),
                    mana(SkillCooldowns.remaining(player.getUUID(), id, player.level().getGameTime()) / 20.0));
            case NO_MANA -> Component.translatable(NO_MANA, skillName(id), mana(casting.cost()), mana(RiftMana.current(player)));
            case NO_ENGINE -> Component.translatable(NO_ENGINE, skillName(id));
            case WRONG_WEAPON -> Component.translatable(WRONG_WEAPON,
                    SkillWeapons.families(casting.def() == null ? SkillService.characterClass(player) : casting.def().skillClass()));
            case NO_TARGET -> Component.translatable(NO_TARGET);
            case UNKNOWN_ABILITY, UNKNOWN_ATTRIBUTE, BAD_RANK, NOT_CASTABLE -> {
                // The player's line stays plain; the cause goes to the log for bug reports.
                LOGGER.warn("Skill {} could not be cast for {}: {}", id, player.getScoreboardName(), casting.result());
                yield Component.translatable(BROKEN, skillName(id));
            }
        };
    }

    private static int ascend(CommandSourceStack source, String name) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        SkillBook.Ascend result = SkillService.ascend(player, name);
        SkillBook book = SkillService.bookOf(player);
        switch (result) {
            case OK -> {
                source.sendSuccess(() -> Component.translatable(ASCENDED, ascendancyName(name),
                        className(book.ascension().skillClass())), true);
                return 1;
            }
            case IN_RIFT -> source.sendFailure(Component.translatable(ASCEND_IN_RIFT));
            case UNKNOWN -> source.sendFailure(Component.translatable(ASCEND_UNKNOWN, name));
            case ALREADY_CHOSEN -> source.sendFailure(Component.translatable(ASCEND_ALREADY, ascendancyName(book.ascension().name())));
            case TOO_LOW -> source.sendFailure(Component.translatable(ASCEND_TOO_LOW, SkillBook.ASCENSION_LEVEL,
                    SkillService.levelOf(player)));
            case NO_CLASS -> source.sendFailure(Component.translatable(ASCEND_NO_CLASS));
            case WRONG_CLASS -> source.sendFailure(Component.translatable(ASCEND_WRONG_CLASS, ascendancyName(name),
                    classWithArticle(SkillDefs.table().classOf(name)), classWithArticle(SkillService.characterClass(player))));
        }
        return 0;
    }

    private static Component classLine(SkillBook book) {
        return book.hasClass()
                ? Component.translatable(CLASS_IS, className(book.skillClass()))
                : Component.translatable(CLASS_NONE);
    }

    /** Reports the class and opens the class screen, where an unclassed character can choose. */
    private static int showClass(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        SkillBook book = SkillService.bookOf(player);
        source.sendSuccess(() -> classLine(book), false);
        ClassSelectService.offer(player);
        return book.hasClass() ? 1 : 0;
    }

    private static int chooseClass(CommandSourceStack source, String name) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        // The dev class holds the placeholder skills; to a non-operator it does not exist.
        if (name.equals(SkillTable.DEV_CLASS) && !source.hasPermission(DEV_PERMISSION)) {
            source.sendFailure(Component.translatable(CLASS_UNKNOWN, name));
            return 0;
        }
        SkillBook.Choose result = SkillService.chooseClass(player, name);
        switch (result) {
            case OK -> {
                source.sendSuccess(() -> Component.translatable(CLASS_CHOSEN, classWithArticle(name)), true);
                if (ClassSelectService.offered().contains(name)) {
                    // The same once-per-character kit the class screen gives.
                    StarterKits.onClassChosen(player, name);
                }
                return 1;
            }
            case IN_RIFT -> source.sendFailure(Component.translatable(CLASS_IN_RIFT));
            case UNKNOWN -> source.sendFailure(Component.translatable(CLASS_UNKNOWN, name));
            case ALREADY_CHOSEN -> source.sendFailure(Component.translatable(CLASS_ALREADY,
                    classWithArticle(SkillService.characterClass(player))));
            case OWNS_OTHER_CLASS -> source.sendFailure(Component.translatable(CLASS_OWNS_OTHER, classWithArticle(name)));
        }
        return 0;
    }

    /** The keys a player can be shown, for the test that every one was generated. */
    static List<String> keys() {
        return List.copyOf(ENGLISH.keySet());
    }
}
