package com.cyberday1.riftfall.tool;

import com.cyberday1.riftfall.RiftfallCore;
import com.cyberday1.riftfall.home.npc.GearMutation;
import com.cyberday1.riftfall.home.npc.smith.ToolWorkMenu;
import com.cyberday1.riftfall.home.npc.smith.ToolWorkRules;
import com.cyberday1.riftfall.home.npc.smith.ToolWorkService;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.enchantment.Enchantment;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/**
 * Op tools for the Looting Pick in a player's main hand: set or add stat ranks, set Fortune, toggle modes, show, reset.
 * Every write goes through the services' own edits and {@link GearMutation}; costs, ranks and the pick level are skipped.
 */
public final class PickCommand {
    private static final String KEY = "message." + RiftfallCore.MODID + ".pick_command.";
    public static final String NOT_PICK = KEY + "not_pick", OUT_OF_RANGE = KEY + "out_of_range", UNKNOWN_STAT = KEY + "unknown_stat",
            UNKNOWN_MODE = KEY + "unknown_mode", NO_FORTUNE = KEY + "no_fortune", REFUSED = KEY + "refused",
            STAT_SET = KEY + "stat_set", FORTUNE_SET = KEY + "fortune_set", MODE_ON = KEY + "mode_on", MODE_OFF = KEY + "mode_off",
            RESET = KEY + "reset", INFO = KEY + "info", INFO_STAT = KEY + "info_stat", INFO_FORTUNE = KEY + "info_fortune",
            INFO_MODES = KEY + "info_modes", INFO_NO_MODES = KEY + "info_no_modes";

    public static final Map<String, String> ENGLISH;

    static {
        Map<String, String> en = new LinkedHashMap<>();
        en.put(NOT_PICK, "%s is not holding a Riftfall Looting Pick");
        en.put(OUT_OF_RANGE, "That would exceed the supported range for %s");
        en.put(UNKNOWN_STAT, "No pick stat called %s");
        en.put(UNKNOWN_MODE, "No tool mode called %s");
        en.put(NO_FORTUNE, "Fortune is not registered on this server");
        en.put(REFUSED, "The pick refused the edit");
        en.put(STAT_SET, "%s: %s set to %s ranks");
        en.put(FORTUNE_SET, "%s: Fortune set to %s");
        en.put(MODE_ON, "%s: %s mode added");
        en.put(MODE_OFF, "%s: %s mode removed");
        en.put(RESET, "%s: the pick is reset to a fresh one (appearance kept)");
        en.put(INFO, "%s's pick: level %s");
        en.put(INFO_STAT, "  %s: %s ranks (%s)");
        en.put(INFO_FORTUNE, "  Fortune: %s");
        en.put(INFO_MODES, "  Modes: %s");
        en.put(INFO_NO_MODES, "  Modes: none");
        ENGLISH = Collections.unmodifiableMap(en);
    }

    public enum Result { OK, NOT_PICK, OUT_OF_RANGE, REFUSED }

    private PickCommand() {}

    // ---- the edits, pure over a stack ----

    static Result checked(ItemStack pick, GearMutation.Policy policy, java.util.function.Consumer<ItemStack> edit) {
        if (!LootingToolItem.isTool(pick) || pick.getCount() != 1) return Result.NOT_PICK;
        var r = GearMutation.apply(pick, policy, edit);
        return r == GearMutation.Result.EDITED || r == GearMutation.Result.UNCHANGED ? Result.OK : Result.REFUSED;
    }

    /** Sets {@code stat} to {@code ranks}; the pick level is left alone (it counts installed runes and prices the next). */
    public static Result setStat(ItemStack pick, ToolStat stat, long ranks) {
        if (!LootingToolItem.isTool(pick)) return Result.NOT_PICK;
        if (ranks < 0 || ranks > Integer.MAX_VALUE) return Result.OUT_OF_RANGE;
        return checked(pick, ToolUpgrades.POLICY, ToolUpgrades.rankEdit(stat, (int) ranks, ToolUpgrades.level(pick)));
    }

    public static Result addStat(ItemStack pick, ToolStat stat, long ranks) {
        if (!LootingToolItem.isTool(pick)) return Result.NOT_PICK;
        return setStat(pick, stat, (long) ToolUpgrades.ranks(pick, stat) + ranks);
    }

    public static Result setFortune(ItemStack pick, Holder<Enchantment> fortune, int level) {
        if (level < 0 || level > ToolWorkRules.FORTUNE_MAX) return Result.OUT_OF_RANGE;
        return checked(pick, ToolWorkService.FORTUNE_POLICY, s -> ToolWorkService.setFortune(s, fortune, level));
    }

    public static Result setMode(ItemStack pick, ToolMode mode, boolean on) {
        return checked(pick, ToolWorkService.MODE_POLICY, s -> ToolWorkService.setMode(s, mode, on));
    }

    /** Level 1, no stats, no modes, no Fortune; appearance, name and other data stay. */
    public static Result reset(ItemStack pick, Holder<Enchantment> fortune) {
        if (!LootingToolItem.isTool(pick)) return Result.NOT_PICK;
        var upgrades = checked(pick, ToolUpgrades.POLICY, s -> CustomData.update(DataComponents.CUSTOM_DATA, s, tag -> {
            tag.put(LootingToolItem.UPGRADES_KEY, new net.minecraft.nbt.CompoundTag());
            tag.putInt(LootingToolItem.LEVEL_KEY, 1);
        }));
        if (upgrades != Result.OK || fortune == null) return upgrades;
        return setFortune(pick, fortune, 0);
    }

    public static ToolStat stat(String name) {
        return Arrays.stream(ToolStat.values()).filter(s -> s.key.equals(name)).findFirst().orElse(null);
    }

    public static ToolMode mode(String name) {
        return Arrays.stream(ToolMode.values()).filter(m -> m.name().toLowerCase(Locale.ROOT).equals(name)).findFirst().orElse(null);
    }

    // ---- the command tree ----

    @FunctionalInterface private interface Edit { int run(CommandContext<CommandSourceStack> c, ServerPlayer target) throws CommandSyntaxException; }

    /** {@code then} a node that runs on the caller, and the same node with {@code <targets>} after it. */
    private static <T extends ArgumentBuilder<CommandSourceStack, T>> T both(T node, Edit edit) {
        return node.executes(c -> edit.run(c, c.getSource().getPlayerOrException()))
                .then(Commands.argument("targets", EntityArgument.players()).executes(c -> {
                    int ok = 0;
                    for (var p : EntityArgument.getPlayers(c, "targets")) ok += edit.run(c, p);
                    return ok;
                }));
    }

    public static LiteralArgumentBuilder<CommandSourceStack> subtree() {
        var stats = Arrays.stream(ToolStat.values()).map(s -> s.key).toList();
        var modes = Arrays.stream(ToolMode.values()).map(m -> m.name().toLowerCase(Locale.ROOT)).toList();
        return Commands.literal("pick").requires(source -> source.hasPermission(2))
                .then(both(Commands.literal("info"), (c, p) -> info(c.getSource(), p)))
                .then(both(Commands.literal("reset"), (c, p) -> report(c.getSource(), p,
                        reset(p.getMainHandItem(), ToolWorkMenu.fortune(c.getSource().getServer())), "", () -> Component.translatable(RESET, p.getDisplayName()))))
                .then(Commands.literal("stat").then(Commands.argument("stat", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(stats, b))
                        .then(both(Commands.argument("ranks", IntegerArgumentType.integer(0)), (c, p) ->
                                stat(c, p, s -> setStat(p.getMainHandItem(), s, IntegerArgumentType.getInteger(c, "ranks")))))))
                .then(Commands.literal("add").then(Commands.argument("stat", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(stats, b))
                        .then(both(Commands.argument("ranks", IntegerArgumentType.integer(0)), (c, p) ->
                                stat(c, p, s -> addStat(p.getMainHandItem(), s, IntegerArgumentType.getInteger(c, "ranks")))))))
                .then(Commands.literal("fortune").then(both(Commands.argument("level", IntegerArgumentType.integer(0, ToolWorkRules.FORTUNE_MAX)), (c, p) -> {
                    var fortune = ToolWorkMenu.fortune(c.getSource().getServer());
                    if (fortune == null) { c.getSource().sendFailure(Component.translatable(NO_FORTUNE)); return 0; }
                    int level = IntegerArgumentType.getInteger(c, "level");
                    return report(c.getSource(), p, setFortune(p.getMainHandItem(), fortune, level), "Fortune",
                            () -> Component.translatable(FORTUNE_SET, p.getDisplayName(), level));
                })))
                .then(Commands.literal("mode").then(Commands.argument("mode", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(modes, b))
                        .then(both(Commands.argument("on", BoolArgumentType.bool()), (c, p) -> {
                            String name = StringArgumentType.getString(c, "mode");
                            var mode = mode(name);
                            if (mode == null) { c.getSource().sendFailure(Component.translatable(UNKNOWN_MODE, name)); return 0; }
                            boolean on = BoolArgumentType.getBool(c, "on");
                            return report(c.getSource(), p, setMode(p.getMainHandItem(), mode, on), name, () -> Component.translatable(on ? MODE_ON : MODE_OFF,
                                    p.getDisplayName(), Component.translatable(com.cyberday1.riftfall.home.npc.smith.ToolWorkLang.mode(mode))));
                        }))));
    }

    private static int stat(CommandContext<CommandSourceStack> c, ServerPlayer p, Function<ToolStat, Result> edit) {
        String name = StringArgumentType.getString(c, "stat");
        var stat = stat(name);
        if (stat == null) { c.getSource().sendFailure(Component.translatable(UNKNOWN_STAT, name)); return 0; }
        return report(c.getSource(), p, edit.apply(stat), name, () -> Component.translatable(STAT_SET, p.getDisplayName(),
                stat.displayName(), ToolUpgrades.ranks(p.getMainHandItem(), stat)));
    }

    private static int report(CommandSourceStack source, ServerPlayer p, Result result, String what, java.util.function.Supplier<Component> ok) {
        switch (result) {
            case OK -> { p.getInventory().setChanged(); source.sendSuccess(ok, true); return 1; }
            case NOT_PICK -> source.sendFailure(Component.translatable(NOT_PICK, p.getDisplayName()));
            case OUT_OF_RANGE -> source.sendFailure(Component.translatable(OUT_OF_RANGE, what));
            default -> source.sendFailure(Component.translatable(REFUSED));
        }
        return 0;
    }

    private static int info(CommandSourceStack source, ServerPlayer p) {
        var pick = p.getMainHandItem();
        if (!LootingToolItem.isTool(pick)) { source.sendFailure(Component.translatable(NOT_PICK, p.getDisplayName())); return 0; }
        List<Component> lines = new java.util.ArrayList<>();
        lines.add(Component.translatable(INFO, p.getDisplayName(), ToolUpgrades.level(pick)));
        for (var stat : ToolStat.values())
            lines.add(Component.translatable(INFO_STAT, stat.displayName(), ToolUpgrades.ranks(pick, stat), stat.bonus(ToolUpgrades.ranks(pick, stat))));
        var fortune = ToolWorkMenu.fortune(source.getServer());
        lines.add(Component.translatable(INFO_FORTUNE, ToolWorkService.grantedFortune(pick, fortune)));
        Collection<ToolMode> modes = ToolMode.of(pick);
        if (modes.isEmpty()) lines.add(Component.translatable(INFO_NO_MODES));
        else {
            var names = Component.empty();
            for (var mode : modes) {
                if (!names.getSiblings().isEmpty()) names.append(", ");
                names.append(Component.translatable(com.cyberday1.riftfall.home.npc.smith.ToolWorkLang.mode(mode)));
            }
            lines.add(Component.translatable(INFO_MODES, names));
        }
        for (var line : lines) source.sendSuccess(() -> line, false);
        return 1;
    }
}
