package com.cyberday1.riftfall.skill;

import com.cyberday1.riftfall.RiftfallCore;
import com.cyberday1.riftfall.init.RiftfallAttributes;
import com.cyberday1.riftfall.progression.RiftTier;
import com.cyberday1.riftfall.progression.XpAwards;
import com.cyberday1.riftfall.stat.AttributePoints;
import com.cyberday1.riftfall.stat.RiftStats;
import com.cyberday1.riftfall.stat.StatBlock;
import com.cyberday1.riftfall.stat.StatService;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * {@code /riftfall stats} shows the attribute pool; {@code /riftfall stats add <primary> <count>} places
 * points, permission 0. The rig drives this; players use the sheet. Both end at {@link StatService#allocate}.
 */
public final class StatCommand {

    private static final String PREFIX = "message." + RiftfallCore.MODID + ".stats_";

    static final String POOL = PREFIX + "pool";
    static final String PLACED = PREFIX + "placed";
    static final String PLACED_NONE = PREFIX + "placed_none";
    static final String ADDED = PREFIX + "added";
    static final String ADDED_ONE = PREFIX + "added_one";
    static final String IN_RIFT = PREFIX + "in_rift";
    static final String NO_CLASS = PREFIX + "no_class";
    static final String BAD_REQUEST = PREFIX + "bad_request";
    static final String UNKNOWN = PREFIX + "unknown";
    static final String NOT_ENOUGH = PREFIX + "not_enough";
    /** What a player is told when the Unseated's ten and its XP arrive. */
    public static final String UNSEATED = PREFIX + "unseated";

    /** Every key above with its English, which the language provider generates from. */
    public static final Map<String, String> ENGLISH = Map.ofEntries(
            Map.entry(POOL, "You have %s attribute points to place, of %s earned."),
            Map.entry(PLACED, "Placed: %s."),
            Map.entry(PLACED_NONE, "Placed: none yet."),
            Map.entry(ADDED, "Placed %s points on %s. %s left to place."),
            Map.entry(ADDED_ONE, "Placed %s point on %s. %s left to place."),
            Map.entry(IN_RIFT, "You can only place attribute points outside a rift."),
            Map.entry(NO_CLASS, "Choose a class before placing attribute points."),
            Map.entry(BAD_REQUEST, "Place at least one point."),
            Map.entry(UNKNOWN, "%s is not a primary: use strength, dexterity, intellect, vitality, spirit, agility or luck."),
            Map.entry(NOT_ENOUGH, "You have %s attribute points to place; that needs %s."),
            Map.entry(UNSEATED, "+10 attribute points and %s XP for containing the Unseated."));

    private static final String PRIMARY = "primary";
    private static final String COUNT = "count";

    private StatCommand() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> subtree() {
        return Commands.literal("stats")
                .executes(context -> show(context.getSource()))
                .then(Commands.literal("add")
                        .then(Commands.argument(PRIMARY, StringArgumentType.word())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                        Arrays.stream(StatBlock.Primary.values()).map(StatBlock.Primary::key), builder))
                                .then(Commands.argument(COUNT, IntegerArgumentType.integer(1, 1024))
                                        .executes(context -> add(context.getSource(),
                                                StringArgumentType.getString(context, PRIMARY),
                                                IntegerArgumentType.getInteger(context, COUNT))))));
    }

    private static int show(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        RiftStats stats = RiftStats.of(player.server);
        int available = StatService.available(stats, player.getUUID());
        int earned = StatService.earned(stats, player.getUUID());
        source.sendSuccess(() -> Component.translatable(POOL, available, earned), false);
        StatBlock alloc = stats.allocFor(player.getUUID());
        source.sendSuccess(() -> alloc.spent() == 0 ? Component.translatable(PLACED_NONE)
                : Component.translatable(PLACED, placed(alloc)), false);
        return Math.max(0, available);
    }

    /** "Strength 30, Vitality 4", in the attributes' own translated names. */
    private static Component placed(StatBlock alloc) {
        MutableComponent out = Component.empty();
        boolean first = true;
        for (StatBlock.Primary primary : StatBlock.Primary.values()) {
            int points = alloc.points(primary);
            if (points <= 0) {
                continue;
            }
            if (!first) {
                out.append(", ");
            }
            out.append(name(primary)).append(" " + points);
            first = false;
        }
        return out;
    }

    private static int add(CommandSourceStack source, String key, int count) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        StatBlock.Primary primary = primary(key);
        if (primary == null) {
            source.sendFailure(Component.translatable(UNKNOWN, key));
            return 0;
        }
        AttributePoints.Allocate result = StatService.allocate(player, Map.of(primary, count));
        RiftStats stats = RiftStats.of(player.server);
        int available = StatService.available(stats, player.getUUID());
        switch (result) {
            case OK -> {
                source.sendSuccess(() -> Component.translatable(count == 1 ? ADDED_ONE : ADDED, count, name(primary),
                        available), false);
                if (!(player instanceof FakePlayer)) {
                    PacketDistributor.sendToPlayer(player, SkillScreenService.view(player, "ALLOCATE", "OK"));
                }
                return count;
            }
            case IN_RIFT -> source.sendFailure(Component.translatable(IN_RIFT));
            case NO_CLASS -> source.sendFailure(Component.translatable(NO_CLASS));
            case BAD_REQUEST -> source.sendFailure(Component.translatable(BAD_REQUEST));
            case NOT_ENOUGH_POINTS -> source.sendFailure(Component.translatable(NOT_ENOUGH, Math.max(0, available), count));
        }
        return 0;
    }

    /** {@link #awardUnseated(ServerPlayer, int)} at the player's own tier. */
    public static boolean awardUnseated(ServerPlayer player) {
        return awardUnseated(player, RiftTier.of(SkillService.levelOf(player)));
    }

    /**
     * The Unseated's ten points and three clears of XP at {@code tier}, once: tells the player and refreshes
     * their sheet only on the grant. Returns whether this call granted them.
     */
    public static boolean awardUnseated(ServerPlayer player, int tier) {
        if (!StatService.grantUnseatedBonus(player)) {
            return false;
        }
        long xp = XpAwards.unseatedXp(tier);
        XpAwards.award(player.server, player.getUUID(), xp);
        player.sendSystemMessage(Component.translatable(UNSEATED, String.format(Locale.ROOT, "%,d", xp)));
        if (!(player instanceof FakePlayer)) {
            PacketDistributor.sendToPlayer(player, SkillScreenService.view(player, "", ""));
        }
        return true;
    }

    static StatBlock.Primary primary(String key) {
        String wanted = key.toLowerCase(Locale.ROOT);
        for (StatBlock.Primary primary : StatBlock.Primary.values()) {
            if (primary.key().equals(wanted)) {
                return primary;
            }
        }
        return null;
    }

    private static Component name(StatBlock.Primary primary) {
        return Component.translatable(RiftfallAttributes.descriptionId(RiftfallCore.id(primary.key())));
    }

    /** The keys a player can be shown, for the test that every one was generated. */
    static List<String> keys() {
        return List.copyOf(ENGLISH.keySet());
    }
}
