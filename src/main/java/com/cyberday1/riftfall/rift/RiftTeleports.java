package com.cyberday1.riftfall.rift;

import com.cyberday1.riftfall.RiftfallCore;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.CommandEvent;
import net.neoforged.neoforge.event.entity.EntityTeleportEvent;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Nothing carries a player into or out of a rift unless Riftfall sent them, and inside one the items
 * that move you do not work.
 *
 * <p>
 * At the boundary the cause of a teleport is not readable &mdash; the travel event carries the entity
 * and the target dimension and nothing else &mdash; so this is a permit and not a classifier: our own
 * crossings say so a moment before they happen, and everything unpermitted is refused. Inside a rift
 * the cause <em>is</em> readable by event subclass, so a pearl, a fruit, vanilla's {@code /tp} and a
 * skill are told apart by name.
 *
 * <p>
 * Players only, and skills are exempt: both are recorded readings rather than omissions.
 */
public final class RiftTeleports {

    private static final String DENIED_BOUNDARY =
            "message." + RiftfallCore.MODID + ".teleport_denied_boundary";
    private static final String DENIED_INSIDE =
            "message." + RiftfallCore.MODID + ".teleport_denied_inside";

    /**
     * How many ticks a permit is good for. One, because every Riftfall crossing teleports on the tick
     * it asks: the portal path returns a destination and vanilla calls {@code changeDimension} on the
     * next line, and every other caller teleports itself.
     */
    static final long PERMIT_TICKS = 1L;

    /** The permission level {@code /tp} itself needs, which is what "operator" means here. */
    static final int OPERATOR_LEVEL = 2;

    /** Vanilla's own cooldown on a chorus fruit, which cancelling the teleport skips. */
    private static final int FRUIT_COOLDOWN_TICKS = 20;

    /**
     * Teleport events whose cause is a skill, named by class so that recognising one costs no
     * dependency on the mod firing it. Iron's is the only cause-bearing skill teleport in the pack;
     * everything else moves a player by a call that fires nothing at all, so it is neither permitted
     * here nor refused anywhere.
     */
    private static final Set<String> SKILL_EVENTS =
            Set.of("io.redspace.ironsspellbooks.api.events.SpellTeleportEvent");

    /**
     * Items allowed to move a player inside a rift. Registered rather than listed, so the trinket
     * that may come later is one call and not an edit to this class.
     */
    private static final List<Predicate<ItemStack>> ALLOWED = new ArrayList<>();

    private static final Permits PERMITS = new Permits();

    /** The tick an operator who is a player last dispatched a command on; see {@link #noteCommand}. */
    private static long operatorCommandTick = Long.MIN_VALUE;

    private RiftTeleports() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(RiftTeleports::boundary);
        NeoForge.EVENT_BUS.addListener(RiftTeleports::inside);
        NeoForge.EVENT_BUS.addListener(RiftTeleports::noteCommand);
        NeoForge.EVENT_BUS.addListener(RiftTeleports::forgetAll);
    }

    // ---------------------------------------------------------------------------
    // The permit
    // ---------------------------------------------------------------------------

    /**
     * Says that the crossing this entity is about to make is ours, so the boundary lets it through.
     *
     * <p>
     * ⛔ Take it immediately before the teleport and never earlier: a permit is good for one tick, and
     * one tick is all any Riftfall crossing needs.
     */
    public static void permit(Entity entity, ResourceKey<Level> target) {
        MinecraftServer server = entity.getServer();
        if (server != null) {
            PERMITS.grant(entity.getUUID(), target, server.getTickCount());
        }
    }

    /** The same, for a destination already wrapped up as a level. */
    public static void permit(Entity entity, ServerLevel target) {
        permit(entity, target.dimension());
    }

    /**
     * Lets an item move a player inside a rift. The seam the ruling asks for: "we may add our own
     * enderpearl item later as a trinket but for now they are disabled."
     *
     * <p>
     * A worn trinket is a different question and is answered elsewhere &mdash; it would move the
     * player by its own code rather than through an item teleport event, so it takes out a
     * {@link #permit} at its own call site and is recognised from {@link RiftAccessories} or
     * {@link RiftCurios} there.
     */
    public static void allow(Predicate<ItemStack> source) {
        ALLOWED.add(source);
    }

    /** Whether any registered source claims this item. Empty stacks are nobody's. */
    static boolean allowed(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        for (Predicate<ItemStack> source : ALLOWED) {
            if (source.test(stack)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Permits outstanding, which nothing but a test has any business asking. A permit that is never
     * spent is pruned by the next grant rather than leaking, and cannot be spent after its tick.
     */
    static Permits permits() {
        return PERMITS;
    }

    // ---------------------------------------------------------------------------
    // The boundary
    // ---------------------------------------------------------------------------

    /**
     * Whether a move crosses the rift boundary: the levels differ and one of them is an instance.
     *
     * <p>
     * Same-level is deliberately not a crossing. A thrown pearl travels to the level it is already in,
     * so reading it as one would make the boundary answer for something the item rule owns.
     */
    static boolean crossesTheBoundary(ResourceKey<Level> from, ResourceKey<Level> to) {
        return !from.equals(to) && (RiftLevels.isRift(from) || RiftLevels.isRift(to));
    }

    private static void boundary(EntityTravelToDimensionEvent event) {
        // ⚠ Players only. Q-T7 is open, and a rule written about players is not one to widen onto pets
        // and thrown items on my own initiative -- especially when a non-player crossing mostly does
        // not reach this event at all.
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ResourceKey<Level> to = event.getDimension();
        if (!crossesTheBoundary(player.level().dimension(), to)) {
            return;
        }
        if (PERMITS.spend(player.getUUID(), to, player.server.getTickCount())) {
            return;
        }
        event.setCanceled(true);
        RiftPortalBlock.notice(player, DENIED_BOUNDARY);
    }

    // ---------------------------------------------------------------------------
    // Inside a rift
    // ---------------------------------------------------------------------------

    /** What moved the player, as far as the event layer can tell. */
    enum Cause {
        PEARL, FRUIT, VANILLA_COMMAND, SKILL, OTHER
    }

    /**
     * Reads the cause off the event class.
     *
     * <p>
     * ⛔⛔ {@code getClass() ==} and not {@code instanceof} for the command, and that one character is
     * the whole of the {@code /tpa} clause: FTB Essentials' teleport <em>extends</em>
     * {@code TeleportCommand}, so {@code instanceof} would permit every {@code /tpa}, {@code /home},
     * {@code /warp} and {@code /back} in the pack. Subclasses fall through to {@code OTHER}, which is
     * refused.
     */
    static Cause causeOf(EntityTeleportEvent event) {
        if (event.getClass() == EntityTeleportEvent.TeleportCommand.class) {
            return Cause.VANILLA_COMMAND;
        }
        if (event instanceof EntityTeleportEvent.EnderPearl) {
            return Cause.PEARL;
        }
        if (event instanceof EntityTeleportEvent.ChorusFruit) {
            return Cause.FRUIT;
        }
        return isSkill(event.getClass()) ? Cause.SKILL : Cause.OTHER;
    }

    /** Walks the class up to the event root, so a mod's own subclass of a skill event still reads. */
    private static boolean isSkill(Class<?> type) {
        for (Class<?> at = type; at != null && at != EntityTeleportEvent.class; at = at.getSuperclass()) {
            if (SKILL_EVENTS.contains(at.getName())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether a teleport inside a rift is refused. Every clause is one sentence of the ruling: skills
     * work as normal, an operator who is a player may use commands, pearls and fruit do not work
     * unless the source is one of ours, and anything else is teleportation and is blocked.
     */
    static boolean refusedInsideARift(Cause cause, boolean operatorCommand, boolean permittedItem) {
        return switch (cause) {
            case SKILL -> false;
            case VANILLA_COMMAND -> !operatorCommand;
            case PEARL, FRUIT -> !permittedItem;
            case OTHER -> true;
        };
    }

    /**
     * Whether the command being dispatched was typed by an operator who is a player.
     *
     * <p>
     * ⛔ Both halves, and neither implies the other. The console carries permission level 4 and is not
     * a player, so a permission check on its own permits exactly what the ruling excludes &mdash;
     * "i want this to only be explicitly someone w/ op and is a player."
     */
    static boolean operatorAtTheKeyboard(boolean isPlayer, boolean hasOperatorPermission) {
        return isPlayer && hasOperatorPermission;
    }

    private static void noteCommand(CommandEvent event) {
        CommandSourceStack source = event.getParseResults().getContext().getSource();
        // The root source, before /execute can rewrite whose entity it is: this fires once per
        // dispatch and the rewriting happens during it.
        operatorCommandTick = operatorAtTheKeyboard(source.getPlayer() != null,
                source.hasPermission(OPERATOR_LEVEL))
                        ? source.getServer().getTickCount()
                        : Long.MIN_VALUE;
    }

    private static void inside(EntityTeleportEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        long now = player.server.getTickCount();
        Cause cause = causeOf(event);
        boolean operator = cause == Cause.VANILLA_COMMAND && operatorCommandTick == now;
        if (operator) {
            // The carve-out has to reach the boundary as well: /tp across it fires the travel event a
            // moment later, and that event has no idea a command asked for the move. Anywhere, because
            // the teleport event does not carry the level it is going to.
            PERMITS.grant(player.getUUID(), null, now);
        }
        if (!RiftLevels.isRift(player.level().dimension())) {
            return;
        }
        ItemStack behind = itemBehind(event, player);
        if (!refusedInsideARift(cause, operator, allowed(behind))) {
            return;
        }
        event.setCanceled(true);
        ItemStack back = refundFor(cause, behind);
        give(player, back);
        if (cause == Cause.FRUIT && !back.isEmpty()) {
            player.getCooldowns().addCooldown(back.getItem(), FRUIT_COOLDOWN_TICKS);
        }
        RiftPortalBlock.notice(player, DENIED_INSIDE);
    }

    /**
     * The item that is moving the player, or an empty stack when the cause is not an item. Taken from
     * the pearl itself and from the stack being eaten rather than named, so a modded pearl or fruit is
     * the item it actually is.
     */
    static ItemStack itemBehind(EntityTeleportEvent event, ServerPlayer player) {
        if (event instanceof EntityTeleportEvent.EnderPearl pearl) {
            return pearl.getPearlEntity().getItem();
        }
        return causeOf(event) == Cause.FRUIT ? player.getUseItem() : ItemStack.EMPTY;
    }

    /**
     * What a refusal hands back. The ruling on Q-T2: a denial must not take the item.
     *
     * <p>
     * Both costs are vanilla's and both are measured. The pearl is discarded after the guarded block,
     * so cancelling loses it; the fruit is eaten before the loop, so cancelling eats it. One each,
     * because one is what the throw and the bite spent.
     *
     * <p>
     * ⛔ A command and a skill get nothing, and {@code OTHER} gets nothing because it is somebody
     * else's mechanism and there is no item here to name.
     */
    static ItemStack refundFor(Cause cause, ItemStack behind) {
        return switch (cause) {
            case PEARL -> behind.isEmpty() ? ItemStack.EMPTY : behind.copyWithCount(1);
            case FRUIT -> new ItemStack(behind.isEmpty() ? Items.CHORUS_FRUIT : behind.getItem());
            case VANILLA_COMMAND, SKILL, OTHER -> ItemStack.EMPTY;
        };
    }

    /** Puts a stack back, on the floor if it will not fit. */
    private static void give(ServerPlayer player, ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        ItemStack back = stack.copyWithCount(1);
        if (!player.getInventory().add(back) && !back.isEmpty()) {
            player.drop(back, false);
        }
    }

    /**
     * Drops every permit when the server stops, for {@code RiftRuns#forgetAll}'s reason: the state is
     * static and a singleplayer client starts a new server per world.
     */
    private static void forgetAll(ServerStoppingEvent event) {
        PERMITS.forget();
        operatorCommandTick = Long.MIN_VALUE;
    }

    /** One sanctioned crossing: who may make it, where to, and when it was asked for. */
    record Permit(@Nullable ResourceKey<Level> target, long issued) {
    }

    /**
     * The permits outstanding. Pure, so the one rule that matters &mdash; a permit is good for one
     * crossing, to one place, on one tick &mdash; can be asserted without a server behind it.
     */
    static final class Permits {

        private final Map<UUID, Permit> issued = new HashMap<>();

        /** Grants one, and drops any that were never spent. A null target means anywhere. */
        void grant(UUID holder, @Nullable ResourceKey<Level> target, long now) {
            issued.values().removeIf(permit -> stale(permit, now));
            issued.put(holder, new Permit(target, now));
        }

        /** Spends one, and answers whether there was a good one to spend. */
        boolean spend(UUID holder, ResourceKey<Level> target, long now) {
            Permit permit = issued.remove(holder);
            return permit != null && !stale(permit, now)
                    && (permit.target() == null || permit.target().equals(target));
        }

        /** Out of date, or from before a clock that went backwards, which a new world is. */
        private static boolean stale(Permit permit, long now) {
            return now - permit.issued() > PERMIT_TICKS || now < permit.issued();
        }

        int outstanding() {
            return issued.size();
        }

        void forget() {
            issued.clear();
        }
    }
}
