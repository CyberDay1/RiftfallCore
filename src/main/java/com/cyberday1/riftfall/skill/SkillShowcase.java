package com.cyberday1.riftfall.skill;

import com.cyberday1.riftfall.RiftfallCore;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * {@code /riftfall showcase <class>}, operators only: a row of target dummies in front of the caller, then each of the
 * class's castable skills cast on them in turn through the engine, named as it goes. No ownership, mana, cooldown or
 * weapon check. The caller is put back on the spot before each cast; the dummies are reset and removed at the end.
 */
public final class SkillShowcase {

    private static final String TAG = "riftfall_showcase";
    private static final String KEY = "message." + RiftfallCore.MODID + ".showcase_";
    private static final int DUMMIES = 5;
    private static final double DISTANCE = 4.0, SPACING = 2.2;
    private static final int GAP = 60, LONG_GAP = 110, LEAD_IN = 30;
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();

    private SkillShowcase() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(SkillShowcase::tick);
        NeoForge.EVENT_BUS.addListener(SkillShowcase::loggedOut);
        NeoForge.EVENT_BUS.addListener(SkillShowcase::stopping);
        NeoForge.EVENT_BUS.addListener(SkillShowcase::joined);
    }

    public static LiteralArgumentBuilder<CommandSourceStack> subtree() {
        return Commands.literal("showcase").requires(source -> source.hasPermission(2))
                .then(Commands.literal("stop").executes(context -> stop(context.getSource())))
                .then(Commands.argument("class", StringArgumentType.word())
                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(SkillTable.CLASSES, builder))
                        .executes(context -> start(context.getSource(), StringArgumentType.getString(context, "class"))));
    }

    private record Step(SkillDef def, int gap) {
    }

    private static final class Session {
        final ServerLevel level;
        final Vec3 anchor;
        final float yaw;
        final List<Step> steps;
        final List<Mob> dummies = new ArrayList<>();
        final List<Vec3> slots = new ArrayList<>();
        int index = -1, wait = LEAD_IN;

        Session(ServerLevel level, Vec3 anchor, float yaw, List<Step> steps) {
            this.level = level;
            this.anchor = anchor;
            this.yaw = yaw;
            this.steps = steps;
        }
    }

    /** The class's castable skills: base first, then each ascendancy, actives before its ultimate. */
    static List<SkillDef> roster(SkillTable table, String skillClass) {
        return table.byId().values().stream()
                .filter(def -> def.skillClass().equals(skillClass))
                .sorted(Comparator.comparing((SkillDef def) -> def.ascendancy() == null ? "" : def.ascendancy())
                        .thenComparing(def -> def.kind() == SkillBook.Kind.ULTIMATE)
                        .thenComparing(SkillDef::id))
                .toList();
    }

    private static int start(CommandSourceStack source, String skillClass) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        if (!SkillTable.CLASSES.contains(skillClass)) {
            source.sendFailure(text("unknown", "There is no class called %s.", skillClass));
            return 0;
        }
        if (player.serverLevel().getDifficulty() == Difficulty.PEACEFUL) {
            source.sendFailure(text("peaceful", "The showcase needs a difficulty above Peaceful: its dummies are monsters."));
            return 0;
        }
        List<Step> steps = roster(SkillDefs.table(), skillClass).stream()
                .map(def -> new Step(def, def.kind() == SkillBook.Kind.ULTIMATE || zone(def) ? LONG_GAP : GAP)).toList();
        if (steps.isEmpty()) {
            source.sendFailure(text("empty", "%s has no castable skills.", SkillCommand.className(skillClass)));
            return 0;
        }
        open(player, steps);
        source.sendSuccess(() -> text("start", "Showcasing %s: %s skills, one every few seconds. /riftfall showcase stop ends it.",
                SkillCommand.className(skillClass), steps.size()), false);
        return steps.size();
    }

    /** Spawns the dummies in front of {@code player} and registers the session; any earlier one ends first. */
    private static Session open(ServerPlayer player, List<Step> steps) {
        end(player.getUUID());
        Session session = new Session(player.serverLevel(), player.position(), player.getYRot(), steps);
        Vec3 forward = Vec3.directionFromRotation(0, session.yaw), side = new Vec3(-forward.z, 0, forward.x);
        for (int i = 0; i < DUMMIES; i++) {
            Vec3 at = session.anchor.add(forward.scale(DISTANCE)).add(side.scale((i - (DUMMIES - 1) / 2.0) * SPACING));
            Mob dummy = EntityType.HUSK.create(session.level);
            if (dummy == null) continue;
            dummy.moveTo(at.x, at.y, at.z, session.yaw + 180, 0);
            dummy.setNoAi(true);
            dummy.setSilent(true);
            dummy.addTag(TAG);
            dummy.setCustomName(text("dummy", "Target dummy"));
            var health = dummy.getAttribute(Attributes.MAX_HEALTH);
            if (health != null) health.setBaseValue(1000.0);
            dummy.setHealth(dummy.getMaxHealth());
            session.slots.add(at);
            session.dummies.add(dummy);
            session.level.addFreshEntity(dummy);
        }
        SESSIONS.put(player.getUUID(), session);
        return session;
    }

    /** One rehearsed cast: the node, the engine's answer, and the dummies standing when it was cast. */
    public record Rehearsal(String node, String result, int dummies) {
    }

    /** The whole roster at once, no waits, for a probe; the session is ended and its dummies gone on return. */
    public static List<Rehearsal> rehearse(ServerPlayer player, String skillClass) {
        List<Step> steps = roster(SkillDefs.table(), skillClass).stream().map(def -> new Step(def, 0)).toList();
        Session session = open(player, steps);
        List<Rehearsal> out = new ArrayList<>();
        try {
            for (Step step : steps) {
                // No server ticks pass here, so run the last travel out (a run ends itself by 65 steps).
                for (int i = 0; i < 80 && (SkillTravel.active(player) || TravelCast.active(player.getUUID())); i++)
                    SkillTravel.tick();
                session.index++;
                reset(session);
                int standing = (int) session.dummies.stream().filter(d -> !d.isRemoved()).count();
                SkillCasts.Invocation result = cast(session, player, step.def());
                out.add(new Rehearsal(step.def().id(), result == null ? "NO_ENGINE" : result.name(), standing));
            }
        } finally {
            end(player.getUUID());
        }
        return out;
    }

    /** Showcase dummies still in {@code level}: none once every session there has ended. */
    public static long strays(ServerLevel level) {
        long n = 0;
        for (var entity : level.getAllEntities()) if (entity.getTags().contains(TAG) && !entity.isRemoved()) n++;
        return n;
    }

    private static boolean zone(SkillDef def) {
        return def.cast().id().contains("trap") || def.cast().id().contains("field");
    }

    private static int stop(CommandSourceStack source) throws CommandSyntaxException {
        boolean had = end(source.getPlayerOrException().getUUID());
        source.sendSuccess(() -> had ? text("stopped", "Showcase stopped.") : text("none", "No showcase is running."), false);
        return had ? 1 : 0;
    }

    private static boolean end(UUID player) {
        Session session = SESSIONS.remove(player);
        if (session == null) return false;
        for (Mob dummy : session.dummies) dummy.discard();
        return true;
    }

    private static void tick(ServerTickEvent.Post event) {
        if (SESSIONS.isEmpty()) return;
        for (UUID id : List.copyOf(SESSIONS.keySet())) {
            Session session = SESSIONS.get(id);
            ServerPlayer player = event.getServer().getPlayerList().getPlayer(id);
            if (player == null || !player.isAlive() || player.serverLevel() != session.level) { end(id); continue; }
            if (--session.wait > 0) continue;
            session.index++;
            if (session.index >= session.steps.size()) {
                player.sendSystemMessage(text("done", "Showcase finished."));
                end(id);
                continue;
            }
            Step step = session.steps.get(session.index);
            session.wait = step.gap();
            reset(session);
            cast(session, player, step.def());
        }
    }

    /** Dummies back on their marks, whole and clean; the caller back on the spot, facing the middle one. */
    private static void reset(Session session) {
        for (int i = 0; i < session.dummies.size(); i++) {
            Mob dummy = session.dummies.get(i);
            if (dummy.isRemoved()) continue;
            Vec3 at = session.slots.get(i);
            dummy.removeAllEffects();
            dummy.clearFire();
            dummy.setDeltaMovement(Vec3.ZERO);
            dummy.teleportTo(at.x, at.y, at.z);
            dummy.setHealth(dummy.getMaxHealth());
        }
    }

    private static SkillCasts.Invocation cast(Session session, ServerPlayer player, SkillDef def) {
        Vec3 middle = session.slots.get(session.slots.size() / 2);
        Vec3 eye = session.anchor.add(0, player.getEyeHeight(), 0);
        Vec3 look = middle.add(0, 1.0, 0).subtract(eye);
        float pitch = (float) -Math.toDegrees(Math.atan2(look.y, look.horizontalDistance()));
        player.teleportTo(session.level, session.anchor.x, session.anchor.y, session.anchor.z, session.yaw, pitch);
        // The engine aims from the server's copy: set it outright, whatever the connection does with the teleport.
        player.moveTo(session.anchor.x, session.anchor.y, session.anchor.z, session.yaw, pitch);
        player.setYHeadRot(session.yaw);
        player.setDeltaMovement(Vec3.ZERO);
        Component name = SkillCommand.skillName(def.id());
        Component where = def.ascendancy() == null ? SkillCommand.className(def.skillClass())
                : SkillCommand.ascendancyName(def.ascendancy());
        Component line = text("step", "%s/%s  %s (%s)", session.index + 1, session.steps.size(), name, where);
        player.displayClientMessage(line, true);
        player.sendSystemMessage(line);
        SkillNode node = def.cast();
        if (!SkillCasts.engineAvailable(node.engine())) {
            player.sendSystemMessage(text("no_engine", "  %s casts through a mod that is not installed.", name));
            return null;
        }
        SkillCasts.Invocation result = SkillCasts.atRankedPower(player, node, 1, () -> node.engine() == SkillNode.Engine.SPELL_ENGINE
                ? RiftSpellEngine.cast(session.level, player, node, SkillCasts.Riders.of(node, 1))
                : RiftIronsCast.cast(session.level, player, node, SkillCasts.Riders.of(node, 1)));
        if (result != SkillCasts.Invocation.CAST) {
            player.sendSystemMessage(text("refused", "  %s did not cast: %s.", name, result.name()));
        }
        return result;
    }

    private static void loggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        end(event.getEntity().getUUID());
    }

    private static void stopping(ServerStoppingEvent event) {
        for (UUID id : List.copyOf(SESSIONS.keySet())) end(id);
    }

    /** A dummy read back from a save after a crash belongs to no showcase: it never rejoins the world. */
    private static void joined(EntityJoinLevelEvent event) {
        if (!event.loadedFromDisk() || !event.getEntity().getTags().contains(TAG)) return;
        event.setCanceled(true);
    }

    private static Component text(String key, String english, Object... args) {
        return Component.translatableWithFallback(KEY + key, english, args);
    }
}
