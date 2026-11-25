package com.cyberday1.riftfall.skill;

import com.cyberday1.riftfall.gear.StarterKits;
import com.cyberday1.riftfall.network.ClassRequestPayload;
import com.cyberday1.riftfall.network.ClassSelectPayload;
import com.cyberday1.riftfall.rift.RiftLevels;
import com.cyberday1.riftfall.stat.ClassAlignment;
import com.cyberday1.riftfall.stat.StatBlock;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * The class screen's server half: builds the list of classes from {@link ClassAlignment} and the live
 * table, and answers a choice through {@link SkillService#chooseClass}. The screen opens only when asked for; a
 * character with no class is told the command at each login (FirstLoginGuide). The client never decides whether a choice is allowed.
 */
public final class ClassSelectService {

    /** Opens the class screen (SkillCommand#showClass); the login line and the quest book name it. */
    public static final String COMMAND = "/riftfall skill class";
    public static final String REMINDER = "message.riftfall.class.reminder";
    public static final String REMINDER_HOVER = "message.riftfall.class.reminder.hover";

    private ClassSelectService() {
    }

    /** The classes a player may be offered: the catalogue's, never {@code dev}, in name order. */
    public static List<String> offered() {
        Set<String> sorted = new TreeSet<>(SkillTable.CLASSES);
        sorted.remove(SkillTable.DEV_CLASS);
        return List.copyOf(sorted);
    }

    /** Pure: what the screen shows for {@code book}. Statuses are the verb's own answers. */
    public static ClassSelectPayload view(SkillBook book, SkillTable table, boolean inRift, boolean open,
                                          String result) {
        List<ClassSelectPayload.Option> options = new ArrayList<>();
        for (String id : offered()) {
            SkillBook.Choose status = inRift ? SkillBook.Choose.IN_RIFT
                    : book.checkChoose(id, SkillTable.CLASSES, owned -> SkillService.classOfOwned(table, owned));
            options.add(new ClassSelectPayload.Option(id, stats(id), status.name(), actives(table, id),
                    passives(table, id), ascendancies(table, id)));
        }
        return new ClassSelectPayload(open, book.hasClass() ? book.skillClass() : "", inRift, result, options);
    }

    static List<Integer> stats(String id) {
        StatBlock block = ClassAlignment.blockFor(id);
        List<Integer> stats = new ArrayList<>();
        for (StatBlock.Primary primary : StatBlock.Primary.values()) {
            stats.add(block == null ? 0 : block.points(primary));
        }
        return stats;
    }

    /** The class's base castables, ultimates aside (they are ascendancy skills), by id. */
    static List<String> actives(SkillTable table, String id) {
        return table.byId().values().stream()
                .filter(def -> def.skillClass().equals(id) && !def.isAscendancy())
                .map(SkillDef::id)
                .sorted()
                .toList();
    }

    static int passives(SkillTable table, String id) {
        return (int) table.passives().values().stream()
                .filter(def -> def.skillClass().equals(id) && !def.isAscendancy())
                .count();
    }

    static List<String> ascendancies(SkillTable table, String id) {
        return table.ascendancyClass().entrySet().stream()
                .filter(entry -> entry.getValue().equals(id))
                .map(Map.Entry::getKey)
                .sorted()
                .toList();
    }

    /**
     * The screen's choice. Only an offered class reaches the verb: {@code dev} and anything unknown are
     * {@code UNKNOWN} here, because the command's operator check for {@code dev} lives in the command.
     */
    public static SkillBook.Choose choose(ServerPlayer player, String id) {
        if (!offered().contains(id)) {
            return SkillBook.Choose.UNKNOWN;
        }
        return SkillService.chooseClass(player, id);
    }

    public static ClassSelectPayload view(ServerPlayer player, boolean open, String result) {
        return view(SkillService.bookOf(player), SkillDefs.table(), RiftLevels.isRift(player.level().dimension()),
                open, result);
    }

    /** The serverbound handler's body, public so the rig can drive it with a fake player. */
    public static ClassSelectPayload handle(ServerPlayer player, ClassRequestPayload request) {
        String result = "";
        if (request.kind() == ClassRequestPayload.Kind.CHOOSE) {
            SkillBook.Choose chosen = choose(player, request.id());
            result = chosen.name();
            if (chosen == SkillBook.Choose.OK) {
                // Once per character, whatever later happens to the class.
                StarterKits.onClassChosen(player, request.id());
            }
            if (chosen == SkillBook.Choose.OK && !(player instanceof FakePlayer)) {
                // The book and the sheet read the class from the skill view; tell them now.
                PacketDistributor.sendToPlayer(player, SkillScreenService.view(player, "", ""));
            }
        }
        ClassSelectPayload view = view(player, false, result);
        send(player, view);
        return view;
    }

    /** Opens the screen on the player's client. */
    public static void offer(ServerPlayer player) {
        send(player, view(player, true, ""));
    }

    private static void send(ServerPlayer player, ClassSelectPayload view) {
        if (!(player instanceof FakePlayer)) {
            PacketDistributor.sendToPlayer(player, view);
        }
    }

    /** The login line for the unclassed (FirstLoginGuide sends it), with the command clickable. */
    public static Component reminder() {
        Component command = Component.literal(COMMAND).withStyle(style -> style.withColor(ChatFormatting.GOLD)
                .withUnderlined(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, COMMAND))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.translatable(REMINDER_HOVER))));
        return Component.translatable(REMINDER, command);
    }

    /** What a login would decide for this player; public for the rig. */
    public static boolean shouldOfferFor(ServerPlayer player) {
        return shouldOffer(SkillService.bookOf(player), RiftLevels.isRift(player.level().dimension()));
    }

    static boolean shouldOffer(SkillBook book, boolean inRift) {
        return !book.hasClass() && !inRift;
    }
}
