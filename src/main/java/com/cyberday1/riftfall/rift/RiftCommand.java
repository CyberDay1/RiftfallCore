package com.cyberday1.riftfall.rift;

import com.cyberday1.riftfall.RiftfallCore;
import com.cyberday1.riftfall.block.marker.MarkerKind;
import com.cyberday1.riftfall.block.marker.RiftMobSpawnerBlockEntity;
import com.cyberday1.riftfall.block.marker.RiftfallMarkers;
import com.cyberday1.riftfall.boss.RiftBossArena;
import com.cyberday1.riftfall.boss.RiftBosses;
import com.cyberday1.riftfall.gallery.GalleryCommand;
import com.cyberday1.riftfall.init.RiftfallBlocks;
import com.cyberday1.riftfall.init.RiftfallDataComponents;
import com.cyberday1.riftfall.init.RiftfallItems;
import com.cyberday1.riftfall.skill.RespecCommand;
import com.cyberday1.riftfall.skill.SkillCommand;
import com.cyberday1.riftfall.worldgen.layout.RiftPlan;
import com.cyberday1.riftfall.worldgen.level.RiftChunkGenerator;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;

/**
 * {@code /riftfall} — a way in before there is a way in.
 *
 * <p>
 * The portal is a later slice, and this exists so the machinery underneath it can be driven by hand
 * while it is being built: {@code rift open|close|list} reaches the level manager directly, and
 * {@code key} hands out the item the portal will eventually want lit. It is not scaffolding to be
 * thrown away afterwards. An operator who needs to look at a rift, or to shut one that a broken
 * objective has stranded a party in, will want exactly these verbs.
 *
 * <p>
 * {@code piece} is a different job under the same root: it stamps one authored piece somewhere it
 * can be looked at, rather than making or unmaking a whole dungeon. It lives in
 * {@link GalleryCommand} because it is the larger half of the tree by some way, and because none of
 * it has anything to do with the rift level lifecycle this class exists to drive.
 *
 * <p>
 * {@code boss} is the one verb here that really is scaffolding, and it is marked as such on the
 * method: it exists only until something in the dungeon spawns a boss on its own. {@code rift
 * spawners} looks like a sibling of it and is not: it fires spawners that are already there, and it
 * is here for good because it is the only way to see what one does without a player next to it. So is
 * {@code rift arena}, which only moves the caller to a room the dungeon already built.
 *
 * <p>
 * {@code rift tear} is the same kind of verb again: it stands an inert tear in front of the caller
 * so the artwork can be judged without killing a boss first, and {@code rift tear clear} takes them
 * away again, which is not optional because a tear cannot be mined.
 *
 * <p>
 * {@code claim} is one of the three verbs here an ordinary player may run. It is the fallback for
 * gear a rift death left held: that gear comes back on its own at the end-of-rift recap
 * ({@link RiftAutoClaims}).
 *
 * <p>
 * {@code party} is another, and unlike {@code claim} it is player-facing for good rather than for
 * the time being. It lives in {@link PartyCommand} because it is a whole subtree of its own, and
 * because a party is something players arrange between themselves rather than anything to do with
 * making or unmaking a rift.
 *
 * <p>
 * {@code skill} is the third: spending points, the loadout and casting, in {@link SkillCommand}.
 *
 * <h2>The permission level is per verb, not on the root</h2>
 * Everything but {@code claim}, {@code party} and {@code skill} makes or destroys a world, or hands out an item
 * that will, and stays at level 2. The gate is therefore hung on each of those subtrees rather than
 * on {@code /riftfall} itself, because a {@code requires} on the root is inherited by every child
 * and would put a player-facing verb behind operator rights. The cost is that {@code /riftfall} is
 * now visible to everybody, showing them the three verbs they can use.
 */
public final class RiftCommand {

    private static final String NOTHING_HELD = "message." + RiftfallCore.MODID + ".claim_nothing";
    private static final String IN_A_RIFT = "message." + RiftfallCore.MODID + ".claim_in_rift";
    private static final String RECOVERED = "message." + RiftfallCore.MODID + ".claim_recovered";
    private static final String PARTLY_RECOVERED = "message." + RiftfallCore.MODID + ".claim_partial";

    /** Everything that makes or unmakes a world. */
    private static final Predicate<CommandSourceStack> OPERATOR = source -> source.hasPermission(2);

    /**
     * The largest radius {@code rift spawners} will sweep, in blocks.
     *
     * <p>
     * A cube rather than a sphere, so the ceiling is what stops the verb reading nine million block
     * states on a typo. Sixty-four is a couple of rooms in every direction, which is as much as
     * anyone wants firing at once anyway.
     */
    private static final int SPAWNER_REACH = 64;

    /** How far in front of the caller {@code rift tear} puts one, and the furthest it may be asked to. */
    private static final int TEAR_DISTANCE = 3;
    private static final int TEAR_REACH = 32;

    private RiftCommand() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(RiftCommand::onRegister);
    }

    private static void onRegister(RegisterCommandsEvent event) {
        register(event.getDispatcher(), event.getBuildContext());
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext build) {
        LiteralArgumentBuilder<CommandSourceStack> rift = Commands.literal("rift")
                .then(Commands.literal("open")
                        .executes(context -> open(context.getSource(), context.getSource().getLevel().random.nextLong(),
                                RiftKeyItem.DEFAULT_TIER))
                        .then(Commands.argument("seed", LongArgumentType.longArg())
                                .executes(context -> open(context.getSource(),
                                        LongArgumentType.getLong(context, "seed"), RiftKeyItem.DEFAULT_TIER))
                                .then(Commands.argument("tier", IntegerArgumentType.integer(RiftKeyItem.DEFAULT_TIER))
                                        .executes(context -> open(context.getSource(),
                                                LongArgumentType.getLong(context, "seed"),
                                                IntegerArgumentType.getInteger(context, "tier"))))))
                .then(Commands.literal("close")
                        .executes(context -> close(context.getSource())))
                .then(Commands.literal("list")
                        .executes(context -> list(context.getSource())))
                .then(Commands.literal("where")
                        .executes(context -> where(context.getSource())))
                .then(Commands.literal("arena")
                        .executes(context -> arena(context.getSource())))
                .then(Commands.literal("caption")
                        .executes(context -> caption(context.getSource())))
                .then(Commands.literal("tear")
                        .executes(context -> tear(context.getSource(), TEAR_DISTANCE))
                        .then(Commands.literal("clear")
                                .executes(context -> tearClear(context.getSource())))
                        .then(Commands.argument("distance", IntegerArgumentType.integer(1, TEAR_REACH))
                                .executes(context -> tear(context.getSource(),
                                        IntegerArgumentType.getInteger(context, "distance")))))
                .then(Commands.literal("spawners")
                        .then(Commands.argument("radius", IntegerArgumentType.integer(1, SPAWNER_REACH))
                                .executes(context -> spawners(context.getSource(),
                                        IntegerArgumentType.getInteger(context, "radius")))));

        LiteralArgumentBuilder<CommandSourceStack> key = Commands.literal("key")
                .then(Commands.argument("tier", IntegerArgumentType.integer(1))
                        .executes(context -> key(context.getSource(),
                                IntegerArgumentType.getInteger(context, "tier"))));

        LiteralArgumentBuilder<CommandSourceStack> boss = Commands.literal("boss")
                .then(Commands.argument("entity", ResourceArgument.resource(build, Registries.ENTITY_TYPE))
                        .suggests(RiftCommand::suggestBosses)
                        .executes(context -> boss(context.getSource(),
                                ResourceArgument.getSummonableEntityType(context, "entity"))));

        LiteralArgumentBuilder<CommandSourceStack> claim = Commands.literal("claim")
                .executes(context -> claim(context.getSource()));

        // The piece gallery hangs here rather than registering a second `/riftfall` root of its own.
        // Brigadier would merge the two, but the merge keeps the first node and drops the second
        // one's `requires`, which would make the permission level a function of listener order.
        dispatcher.register(Commands.literal("riftfall")
                .then(rift.requires(OPERATOR))
                .then(key.requires(OPERATOR))
                .then(boss.requires(OPERATOR))
                .then(GalleryCommand.subtree().requires(OPERATOR))
                .then(claim)
                .then(com.cyberday1.riftfall.progression.XpCommand.subtree())
                .then(PartyCommand.subtree())
                .then(RiftRecaps.command())
                .then(SkillCommand.subtree())
                .then(com.cyberday1.riftfall.skill.SkillShowcase.subtree())
                .then(com.cyberday1.riftfall.tool.PickCommand.subtree())
                .then(RespecCommand.respec())
                .then(com.cyberday1.riftfall.skill.StatCommand.subtree())
                .then(RespecCommand.classReset())
                .then(RespecCommand.kit()));
    }

    /**
     * Spawns an entity at the caller and runs it through {@link RiftBosses#take}.
     *
     * <p>
     * A development affordance, and the only one in this class that expects to be thrown away. Nothing
     * populates a rift with bosses yet &mdash; the boss room is a separate unbuilt slice &mdash; so
     * without this verb the threat subsystem has no way of being exercised at all, and code that has
     * never been run once is not plumbing, it is a guess. When the boss room exists and calls
     * {@code take} for itself, this stops being the only caller and becomes redundant.
     *
     * <p>
     * The tier is the rift's own rather than an argument, because a boss's tier is a fact about the
     * dungeon it is standing in, and letting an operator type a different one would make the one thing
     * this verb is for testing &mdash; that tier reaches the boss &mdash; untestable.
     *
     * <p>
     * {@code EventHooks.finalizeMobSpawn} runs, and runs before {@link RiftBosses#take}, for the
     * reason {@code RiftMobSpawnerBlockEntity} sets out at length: skipping it is what leaves a
     * pillager holding no crossbow, and the answer to "whatever runs last wins" is to run last
     * rather than to leave the mob half built. {@link com.cyberday1.riftfall.boss.BossScaling}
     * does not mind going second &mdash; it writes named permanent modifiers, which equipment
     * cannot collide with. This matches how the boss room will eventually spawn one.
     */
    /**
     * Offers {@link RiftBosses#ROSTER} and nothing else.
     *
     * <p>
     * Replaces {@code SuggestionProviders.SUMMONABLE_ENTITIES}, which offered every summonable type
     * in the game &mdash; upwards of a thousand in this pack, which is not a menu, and it buried the
     * three dozen entries that are actually bosses.
     *
     * <p>
     * Read off the source's registries rather than {@code BuiltInRegistries}, because tag membership
     * is a datapack fact: the built-in registry knows the types but not which of them a pack has put
     * on the roster. An unbound tag suggests nothing rather than falling back to everything &mdash;
     * an empty menu is a legible failure, and silently reverting to a thousand entries is not.
     *
     * <p>
     * The tooltip is the entity's own display name, which is doing real work here: {@code cataclysm:
     * the_harbinger} is readable but {@code too_many_weapons:safey_is_a_sin} is not, and the roster
     * is other people's mods all the way down.
     */
    private static CompletableFuture<Suggestions> suggestBosses(
            CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        List<EntityType<?>> roster = context.getSource().registryAccess()
                .registryOrThrow(Registries.ENTITY_TYPE)
                .getTag(RiftBosses.ROSTER)
                .map(members -> members.stream().map(Holder::value).toList())
                .orElse(List.of());
        return SharedSuggestionProvider.suggestResource(roster, builder, EntityType::getKey,
                type -> Component.translatable(type.getDescriptionId()));
    }

    private static int boss(CommandSourceStack source, Holder.Reference<EntityType<?>> type)
            throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel level = source.getLevel();
        if (!RiftLevels.isRift(level.dimension())) {
            source.sendFailure(Component.literal("Not in a rift: " + level.dimension().location()));
            return 0;
        }
        // Refused rather than merely unsuggested. Tab completion is a convenience and typing past
        // it is easy, and a mob scaled to a tier without a boss's health is not a boss that needs
        // tuning -- it is a wrong answer to "how does the boss fight read", which is the only
        // question this verb is asked.
        if (!RiftBosses.isBoss(type)) {
            source.sendFailure(Component.literal(type.key().location()
                    + " is not on the rift boss roster (#" + RiftBosses.ROSTER.location() + ")."));
            return 0;
        }
        if (!(type.value().create(level) instanceof Mob mob)) {
            source.sendFailure(Component.literal(type.key().location() + " is not a mob, so it has nothing to aggro with."));
            return 0;
        }

        int tier = RiftLevels.tier(level);
        mob.moveTo(player.getX(), player.getY(), player.getZ(), player.getYRot(), 0.0F);
        EventHooks.finalizeMobSpawn(mob, level, level.getCurrentDifficultyAt(mob.blockPosition()),
                MobSpawnType.COMMAND, null);
        RiftBosses.take(mob, tier);
        level.addFreshEntity(mob);
        source.sendSuccess(() -> Component.literal("Spawned " + type.key().location() + " as a tier " + tier
                + " rift boss"), true);
        return 1;
    }

    /**
     * Hands back what a rift took off the caller when they died in it. The fallback now: the gear
     * comes back on its own at the recap and whenever there is room ({@link RiftAutoClaims}), and all
     * of them run {@link RiftClaims#handOver(ServerPlayer)}.
     *
     * <p>
     * Having nothing on file is reported before the refusal, so a player in a rift with no claim is
     * not told their things are waiting outside.
     */
    private static int claim(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        RiftClaims.HandOver result = RiftClaims.handOver(player);
        switch (result.status()) {
            case NOTHING_HELD -> {
                source.sendFailure(Component.translatable(NOTHING_HELD));
                return 0;
            }
            case IN_A_RIFT -> {
                source.sendFailure(Component.translatable(IN_A_RIFT));
                return 0;
            }
            default -> {
            }
        }
        int recovered = result.recovered();
        if (result.left() > 0) {
            source.sendSuccess(() -> Component.translatable(PARTLY_RECOVERED, recovered, result.left()), false);
        } else {
            source.sendSuccess(() -> Component.translatable(RECOVERED, recovered), false);
        }
        return recovered;
    }

    /**
     * Puts a rift key of the given tier in the caller's hands.
     *
     * <p>
     * The forge that is supposed to make these is the last slice of this feature, and the portal
     * that consumes them is the next one. Without this there is no way to hold a key while the
     * portal is being built, and testing the portal would mean waiting on a whole block entity, menu
     * and recipe first.
     */
    private static int key(CommandSourceStack source, int tier) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ItemStack stack = new ItemStack(RiftfallItems.RIFT_KEY.get());
        stack.set(RiftfallDataComponents.RIFT_TIER.get(), tier);
        // Drops at the player's feet rather than vanishing if the inventory is full, the way /give does.
        player.getInventory().placeItemBackInInventory(stack);
        source.sendSuccess(() -> Component.literal("Gave " + player.getName().getString()
                + " a tier " + tier + " rift key"), true);
        return 1;
    }

    /**
     * Opens a rift and steps into it.
     *
     * <p>
     * Arrival is {@link RiftLevels#arrival}, the same place a portal drops a player, so that the
     * operator shortcut and the real way in cannot disagree about where a rift begins. ⚠ No way out
     * is opened: that is {@link RiftRuns#begin}'s, and this verb makes a rift nobody is running.
     *
     * <p>
     * Both arguments default, and the tier only became one of them when the tier started deciding how
     * big the dungeon is. Before that it was a fact about the key and the portal was the only thing
     * entitled to read it; now it is also a fact about the terrain, and the terrain is what this verb
     * exists to look at. A rift is generated once, so the only way to see a tier 12 layout is to ask
     * for one when it is made &mdash; forging a key to get there would put a shard count between an
     * operator and the thing they are trying to look at.
     */
    private static int open(CommandSourceStack source, long seed, int tier) {
        ServerPlayer player = source.getPlayer();
        ServerLevel rift = RiftLevels.create(source.getServer(), seed, tier);
        // Asked for before the player is checked, because asking is what plans the layout and writes
        // the line describing it to the log. From the console there is nobody to send anywhere, and a
        // rift that is made and never planned is a folder rather than a dungeon -- which makes the
        // one way of measuring a layout without a client the one way this verb did not work.
        Vec3 entry = RiftLevels.arrival(rift);
        if (player != null) {
            if (!enter(player, rift, entry)) {
                RiftLevels.close(rift);
                source.sendFailure(Component.literal("Travel to the new rift was prevented by the server."));
                return 0;
            }
        }
        source.sendSuccess(() -> Component.literal("Opened " + rift.dimension().location()
                + " (seed " + seed + ", tier " + tier + ")"), true);
        return 1;
    }

    /** Debug entry needs the same persistent way back as a portal entry. */
    static boolean enter(ServerPlayer player, ServerLevel rift, Vec3 position) {
        RiftEntry origin = RiftEntry.of(player);
        RiftTeleports.permit(player, rift);
        if (player.changeDimension(new DimensionTransition(rift, position, Vec3.ZERO,
                player.getYRot(), player.getXRot(), DimensionTransition.DO_NOTHING)) == null) return false;
        RiftReturns.of(player.getServer()).owe(player.getUUID(), origin);
        return true;
    }

    /** Closes the rift the command was run in, which sends everyone in it home first. */
    private static int close(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        if (!RiftLevels.isRift(level.dimension())) {
            source.sendFailure(Component.literal("Not in a rift: " + level.dimension().location()));
            return 0;
        }
        RiftLevels.close(level);
        source.sendSuccess(() -> Component.literal("Closing " + level.dimension().location()), true);
        return 1;
    }

    /**
     * Names the room the caller is standing in.
     *
     * <p>
     * The whole id and the bounds, rather than the short name the action bar shows, because this is
     * the verb you run when you have found something wrong and want to go and fix it: the id is what
     * {@code /riftfall piece place} takes, and the bounds are how you tell which of a room's eight
     * orientations you are looking at.
     *
     * <p>
     * Answers about the source's position rather than a player's, so {@code execute in ... positioned
     * ... run riftfall rift where} works from the console. Asking a rift what is at a coordinate is a
     * reasonable thing to want without standing there, and it is the only way this verb can be tested
     * at all without a client.
     */
    private static int where(CommandSourceStack source) {
        RiftPlan.PlacedRoom room = RoomCaption.roomAt(source.getLevel(), BlockPos.containing(source.getPosition()));
        if (room == null) {
            source.sendFailure(Component.literal("No authored room here."));
            return 0;
        }
        BoundingBox bounds = room.bounds();
        source.sendSuccess(() -> Component.literal(room.template() + "\n  depth " + room.depth()
                + ", mirror " + room.mirror()
                + "\n  " + bounds.minX() + " " + bounds.minY() + " " + bounds.minZ()
                + " to " + bounds.maxX() + " " + bounds.maxY() + " " + bounds.maxZ()), false);
        return 1;
    }

    /**
     * Puts the caller on the boss room's entry pad.
     *
     * <p>
     * A rift is large enough that walking to the boss room costs more than whatever is being tested
     * once you get there. Aimed at {@code boss_entry} rather than the arena pad, for the reason
     * {@link RiftBossArena} gives: the pad is the one block a party must not land on.
     *
     * <p>
     * ⚠ The lookup consumes the room's markers and generates its chunks on this thread. That is a
     * one-off, and it is what walking in would have triggered anyway &mdash; but it does mean the gate
     * is armed from here on, so this verb is not a way to go and look at an unshut doorway.
     */
    private static int arena(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel level = source.getLevel();
        if (!RiftLevels.isRift(level.dimension())) {
            source.sendFailure(Component.literal("Not in a rift: " + level.dimension().location()));
            return 0;
        }
        BlockPos entry = RiftBossArena.entry(level).orElse(null);
        if (entry == null) {
            source.sendFailure(Component.literal(
                    "No boss entry in this rift: either it has no boss room, or that room marks no boss_entry."));
            return 0;
        }
        player.teleportTo(level, entry.getX() + 0.5, entry.getY(), entry.getZ() + 0.5,
                player.getYRot(), player.getXRot());
        source.sendSuccess(() -> Component.literal("Moved to the boss entry at "
                + entry.getX() + " " + entry.getY() + " " + entry.getZ()), false);
        return 1;
    }

    /**
     * Stands an inert tear in front of the caller.
     *
     * <p>
     * The tear is the one part of the rift exit that is a look rather than a mechanism, and a look is
     * settled by standing in front of it in a lit room and changing one number at a time. Waiting for
     * a boss to die each round would put a dungeon between every pass, so this puts one down on
     * demand, anywhere, in any light.
     *
     * <p>
     * Inert, in the {@link RiftTearBlock#LIVE} sense, and that is the whole point rather than a
     * caution: a live tear inside a rift would throw the operator out of the dungeon the moment they
     * walked up to the thing they came to look at.
     *
     * <p>
     * Faced at the caller, and standing on the block they are standing on, so it is at eye level from
     * where the command was typed. The intensity is the rift's own where there is one, because how
     * bright a tear is at tier 8 is one of the things being looked at, and zero outside &mdash; a
     * creative flat is the other place this gets used and there is no tier there to read.
     */
    private static int tear(CommandSourceStack source, int distance) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel level = source.getLevel();
        Direction facing = player.getDirection();
        BlockPos base = player.blockPosition().relative(facing, distance);
        // The rip spans the axis across the line of sight, so the face it draws on is turned towards
        // the caller rather than edge-on to them.
        Direction.Axis axis = facing.getClockWise().getAxis();
        int tier = RiftLevels.isRift(level.dimension()) ? RiftLevels.tier(level) : 0;

        if (!RiftTearBlock.open(level, base, axis, RiftPortalKind.BAIL,
                RiftPortalBlock.intensityFor(tier), false)) {
            source.sendFailure(Component.literal("No room for a tear at " + base.getX() + " " + base.getY()
                    + " " + base.getZ() + ": it needs " + TearSegment.HEIGHT + " clear blocks."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Tear at " + base.getX() + " " + base.getY() + " "
                + base.getZ() + " (axis " + axis.getSerializedName() + ", intensity "
                + RiftPortalBlock.intensityFor(tier) + ", inert)"), false);
        return 1;
    }

    /**
     * Takes away every inert tear near the caller.
     *
     * <p>
     * Needed rather than tidy: a tear has no loot table and negative strength, so once one is down
     * there is no way to get rid of it by hand short of {@code /setblock} on each of its three
     * blocks, and a session spent iterating on the artwork leaves a row of them.
     *
     * <p>
     * Inert ones only. A live tear is a party's way out of a dungeon, and an operator who mistypes a
     * radius while standing in a rift should not be able to take it away from them.
     */
    private static int tearClear(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        BlockPos centre = BlockPos.containing(source.getPosition());
        List<BlockPos> found = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(centre.offset(-TEAR_REACH, -TEAR_REACH, -TEAR_REACH),
                centre.offset(TEAR_REACH, TEAR_REACH, TEAR_REACH))) {
            BlockState state = level.getBlockState(pos);
            if (state.is(RiftfallBlocks.RIFT_TEAR.get()) && !state.getValue(RiftTearBlock.LIVE)) {
                found.add(pos.immutable());
            }
        }
        found.forEach(pos -> level.removeBlock(pos, false));
        source.sendSuccess(() -> Component.literal("Removed " + found.size() + " inert tear block(s) within "
                + TEAR_REACH + " blocks"), false);
        return found.size();
    }

    /** Turns the standing room caption on the action bar on or off for the caller. */
    private static int caption(CommandSourceStack source) throws CommandSyntaxException {
        boolean on = RoomCaption.toggle(source.getPlayerOrException());
        source.sendSuccess(() -> Component.literal("Room caption " + (on ? "on" : "off")), false);
        return 1;
    }

    /**
     * Fires every rift spawner in a cube around the caller, and says how many that was.
     *
     * <p>
     * The reason this exists is that a spawner's own trigger is a player standing near it, so the one
     * thing that cannot be checked from a console is the thing a spawner does. Run from a position,
     * this does it on demand: {@code execute in <rift> positioned <x y z> run riftfall rift spawners
     * 32}. The positions are collected before any of them fires, because firing takes the block away
     * and a mob may land in the next one.
     */
    private static int spawners(CommandSourceStack source, int radius) {
        ServerLevel level = source.getLevel();
        BlockPos centre = BlockPos.containing(source.getPosition());
        List<BlockPos> found = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(centre.offset(-radius, -radius, -radius),
                centre.offset(radius, radius, radius))) {
            if (RiftfallMarkers.identify(level.getBlockState(pos)).orElse(null) == MarkerKind.RIFT_MOB_SPAWNER) {
                found.add(pos.immutable());
            }
        }
        found.forEach(pos -> RiftMobSpawnerBlockEntity.trigger(level, pos));
        source.sendSuccess(() -> Component.literal("Fired " + found.size() + " spawner(s) within "
                + radius + " blocks"), false);
        return found.size();
    }

    private static int list(CommandSourceStack source) {
        List<ServerLevel> rifts = RiftLevels.openRifts(source.getServer());
        if (rifts.isEmpty()) {
            source.sendSuccess(() -> Component.literal("No rifts are open."), false);
            return 0;
        }
        for (ServerLevel rift : rifts) {
            String filled = rift.getChunkSource().getGenerator() instanceof RiftChunkGenerator generator
                    ? ", " + generator.barrelReport()
                    : "";
            source.sendSuccess(() -> Component.literal(rift.dimension().location()
                    + " (tier " + RiftLevels.tier(rift) + ", " + rift.players().size() + " players, "
                    + RiftWarmup.held(rift) + " warm" + filled + ")"), false);
        }
        return rifts.size();
    }
}
