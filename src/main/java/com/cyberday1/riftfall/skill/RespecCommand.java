package com.cyberday1.riftfall.skill;

import com.cyberday1.riftfall.RiftfallCore;
import com.cyberday1.riftfall.gear.StarterKit;
import com.cyberday1.riftfall.gear.StarterKits;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/**
 * {@code /riftfall respec skills|stats [confirm]} for players, and the operator's {@code /riftfall class
 * reset <player>} and {@code /riftfall kit give <player> [class]}. Thin shells over {@link SkillService}
 * and {@link StarterKits}; without {@code confirm} a respec only states its price.
 */
public final class RespecCommand {

    private static final String PREFIX = "message." + RiftfallCore.MODID + ".respec_";

    static final String QUOTE_SKILLS = PREFIX + "quote_skills";
    static final String QUOTE_STATS = PREFIX + "quote_stats";
    static final String DONE_SKILLS = PREFIX + "done_skills";
    static final String DONE_STATS = PREFIX + "done_stats";
    static final String IN_RIFT = PREFIX + "in_rift";
    static final String NOTHING_SKILLS = PREFIX + "nothing_skills";
    static final String NOTHING_STATS = PREFIX + "nothing_stats";
    static final String NOT_ENOUGH_XP = PREFIX + "not_enough_xp";
    static final String CLASS_RESET = PREFIX + "class_reset";
    static final String CLASS_RESET_NONE = PREFIX + "class_reset_none";
    static final String CLASS_RESET_IN_RIFT = PREFIX + "class_reset_in_rift";
    static final String KIT_NO_CLASS = PREFIX + "kit_no_class";

    /** Every key above with its English, which the language provider generates from. */
    public static final Map<String, String> ENGLISH = Map.ofEntries(
            Map.entry(QUOTE_SKILLS, "Refunding %s skill points costs %s XP. Use Reset skill points on the Skills page (J)."),
            Map.entry(QUOTE_STATS, "Refunding %s stat points costs %s XP. Use Reset stats on the Character page (J)."),
            Map.entry(DONE_SKILLS, "Refunded %s skill points for %s XP. Your class and ascension are unchanged."),
            Map.entry(DONE_STATS, "Refunded %s stat points for %s XP."),
            Map.entry(IN_RIFT, "You can only respec outside a rift."),
            Map.entry(NOTHING_SKILLS, "You have no skill points spent, so there is nothing to reset."),
            Map.entry(NOTHING_STATS, "You have no stat points allocated, so there is nothing to reset."),
            Map.entry(NOT_ENOUGH_XP, "A respec costs %s XP from your progress into this level; you need %s more."),
            Map.entry(CLASS_RESET, "%s has no class or ascension now; every skill point was refunded."),
            Map.entry(CLASS_RESET_NONE, "%s has no class to reset."),
            Map.entry(CLASS_RESET_IN_RIFT, "%s is inside a rift; reset them outside one."),
            Map.entry(KIT_NO_CLASS, "%s has no class; name the kit's class."));

    static final int OPERATOR = 2;
    private static final String TARGET = "player";
    private static final String CLASS = "class";

    private RespecCommand() {
    }

    /** Permission 0: a player's own respec. */
    public static LiteralArgumentBuilder<CommandSourceStack> respec() {
        return Commands.literal("respec")
                .then(half("skills", SkillService::quoteSkills, SkillService::respecSkills, QUOTE_SKILLS, DONE_SKILLS,
                        NOTHING_SKILLS))
                .then(half("stats", SkillService::quoteStats, SkillService::respecStats, QUOTE_STATS, DONE_STATS,
                        NOTHING_STATS));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> half(String name,
            Function<ServerPlayer, Respec.Quote> quote, Function<ServerPlayer, Respec.Quote> act, String quoteKey,
            String doneKey, String nothingKey) {
        return Commands.literal(name)
                .executes(context -> answer(context.getSource(), quote, quoteKey, nothingKey, false))
                .then(Commands.literal("confirm")
                        .executes(context -> answer(context.getSource(), act, doneKey, nothingKey, true)));
    }

    private static int answer(CommandSourceStack source, Function<ServerPlayer, Respec.Quote> verb, String okKey,
                              String nothingKey, boolean acted) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        Respec.Quote quote = verb.apply(player);
        switch (quote.result()) {
            case OK -> {
                source.sendSuccess(() -> Component.translatable(okKey, quote.refund(), quote.cost()), false);
                if (acted && !(player instanceof FakePlayer)) {
                    PacketDistributor.sendToPlayer(player, SkillScreenService.view(player, "", ""));
                }
                return acted ? quote.refund() : 1;
            }
            case IN_RIFT -> source.sendFailure(Component.translatable(IN_RIFT));
            case NOTHING -> source.sendFailure(Component.translatable(nothingKey));
            case NOT_ENOUGH_XP -> source.sendFailure(Component.translatable(NOT_ENOUGH_XP, quote.cost(), quote.shortBy()));
        }
        return 0;
    }

    /** Operator only: the one way a class changes. Players have no path to it. */
    public static LiteralArgumentBuilder<CommandSourceStack> classReset() {
        return Commands.literal("class")
                .requires(source -> source.hasPermission(OPERATOR))
                .then(Commands.literal("reset")
                        .then(Commands.argument(TARGET, EntityArgument.player())
                                .executes(context -> resetClass(context.getSource(),
                                        EntityArgument.getPlayer(context, TARGET)))));
    }

    private static int resetClass(CommandSourceStack source, ServerPlayer target) {
        Component name = target.getDisplayName();
        switch (SkillService.resetClass(target)) {
            case OK -> {
                source.sendSuccess(() -> Component.translatable(CLASS_RESET, name), true);
                if (!(target instanceof FakePlayer)) {
                    PacketDistributor.sendToPlayer(target, SkillScreenService.view(target, "", ""));
                }
                ClassSelectService.offer(target);
                return 1;
            }
            case NO_CLASS -> source.sendFailure(Component.translatable(CLASS_RESET_NONE, name));
            case IN_RIFT -> source.sendFailure(Component.translatable(CLASS_RESET_IN_RIFT, name));
        }
        return 0;
    }

    /** Operator only: a class's full kit, common set included, whatever the once-only ledger says. */
    public static LiteralArgumentBuilder<CommandSourceStack> kit() {
        return Commands.literal("kit")
                .requires(source -> source.hasPermission(OPERATOR))
                .then(Commands.literal("give")
                        .then(Commands.argument(TARGET, EntityArgument.player())
                                .executes(context -> giveKit(context.getSource(),
                                        EntityArgument.getPlayer(context, TARGET), null))
                                .then(Commands.argument(CLASS, StringArgumentType.word())
                                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                                StarterKits.loaded().kits().keySet(), builder))
                                        .executes(context -> giveKit(context.getSource(),
                                                EntityArgument.getPlayer(context, TARGET),
                                                StringArgumentType.getString(context, CLASS).toLowerCase(Locale.ROOT))))));
    }

    private static int giveKit(CommandSourceStack source, ServerPlayer target, String named) {
        String skillClass = named != null ? named : SkillService.characterClass(target);
        if (skillClass == null) {
            source.sendFailure(Component.translatable(KIT_NO_CLASS, target.getDisplayName()));
            return 0;
        }
        StarterKit kit = StarterKits.kit(skillClass);
        if (kit == null) {
            source.sendFailure(Component.translatable(StarterKits.NONE, skillClass));
            return 0;
        }
        int stacks = StarterKits.give(target, kit).size();
        source.sendSuccess(() -> Component.translatable(StarterKits.GIVEN, target.getDisplayName(), skillClass,
                stacks), true);
        return stacks;
    }

    /** The keys a player can be shown, for the test that every one was generated. */
    static List<String> keys() {
        return List.copyOf(ENGLISH.keySet());
    }
}
