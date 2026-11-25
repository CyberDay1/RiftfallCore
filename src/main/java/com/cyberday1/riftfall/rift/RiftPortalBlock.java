package com.cyberday1.riftfall.rift;

import com.cyberday1.riftfall.RiftfallCore;
import com.cyberday1.riftfall.init.RiftfallParticles;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Portal;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * The lit surface inside a copper frame.
 *
 * <p>
 * Modelled on {@code NetherPortalBlock}, because the portal is meant to be nether-like: a
 * four-pixel pane standing on one horizontal axis, unbreakable, unpushable, dimly lit, and gone the
 * moment its frame is.
 *
 * <p>
 * The <em>surface</em> is no longer, because three portals that are the same block cannot be told
 * apart. {@link #KIND} says which of the three this one is and {@link #INTENSITY} how hard the rift
 * behind it is; {@link #animateTick} is where they are spent.
 *
 * <p>
 * One of vanilla's behaviours is deliberately absent: there is no random tick, because that is where
 * a nether portal spawns zombified piglins and a rift has no such tenant.
 *
 * <h2>Where it leads</h2>
 * To the dungeon its frame was lit onto. A nether portal has a fixed other side and only has to find
 * it; a rift portal <em>makes</em> its other side, and lighting the frame is what triggers that, so
 * by the time a body reaches the pane the level is usually built and its way in already loaded
 * &mdash; see {@link RiftWarmup}. What the first entrant makes is a {@link RiftRun}, not the level:
 * the run holds the frame to the dungeon for as long as the dungeon lasts, so a party following one
 * player through arrives in the same place rather than in four different ones, and it holds the
 * roster that stops any of them coming back. The same block standing inside a rift is the way home,
 * and reads that roster for where home was.
 *
 * <p>
 * A frame is good for exactly one run. When that run ends the frame is put out from under it, so
 * everything below is written on the understanding that a lit frame is either standing open onto a
 * live run or has never been walked into at all. The third case &mdash; lit, spent, and with no run
 * left, which only a restart can produce &mdash; is caught here and put right by putting the frame
 * out.
 */
public class RiftPortalBlock extends Block implements Portal {

    /** The top rung of {@link #INTENSITY}. */
    public static final int MAX_INTENSITY = 3;

    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;

    /** Which of the three portals this is. Set at lighting time and never changed after. */
    public static final EnumProperty<RiftPortalKind> KIND = EnumProperty.create("kind", RiftPortalKind.class);

    /**
     * How violent the surface is, on four rungs.
     *
     * <p>
     * A stand-in for the tier, which cannot be read where it is needed: the tier lives in
     * {@link RiftPortals}, a server-side {@code SavedData} keyed on the frame's corner, and
     * {@link #animateTick} runs on a client that has none of it. Baking a bucketed copy into the
     * blockstate is enough to drive the ambience and costs neither a packet nor a block entity.
     * Four rungs because that is what {@link #intensityFor} can distinguish without the state count
     * running away &mdash; 2 axes x 3 kinds x 4 rungs is 24 states.
     */
    public static final IntegerProperty INTENSITY = IntegerProperty.create("intensity", 0, MAX_INTENSITY);

    private static final String LOCKED_OUT = "message." + RiftfallCore.MODID + ".rift_locked_out";
    private static final String EXPIRED = "message." + RiftfallCore.MODID + ".rift_expired";
    private static final String ROSTER_LOCKED = "message." + RiftfallCore.MODID + ".rift_roster_locked";

    /** How often the lockout notice may be repeated at somebody standing in a portal, in ticks. */
    private static final int NOTICE_INTERVAL = 20;

    /** One in this many ticks carries the ambient cue at rung zero. Shortened as the rungs climb. */
    private static final int AMBIENCE_ODDS = 100;
    private static final int AMBIENCE_PER_RUNG = 15;

    /** Motes per tick at rung zero, and how many more each rung adds. */
    private static final int MOTES = 3;
    private static final int MOTES_PER_RUNG = 2;

    /**
     * How far off the face an entrance's motes are born, in blocks: a fixed clearance plus a spread.
     * The spread widens per rung, and the trip in always takes the same time, so a higher rung is
     * what makes the pull look faster as well as heavier.
     */
    private static final double REACH = 0.5;
    private static final double REACH_SPREAD = 1.2;
    private static final double REACH_PER_RUNG = 0.3;

    /** How fast a way home throws its embers off the pane, and how much each rung adds. */
    private static final double THROW_SPEED = 0.06;
    private static final double THROW_PER_RUNG = 0.03;

    /**
     * Sideways wander, so neither flow is a set of parallel lines. Two numbers because the flows read
     * their velocity differently: blocks over the whole trip in, blocks per tick out.
     */
    private static final double PULL_WANDER = 0.3;
    private static final double DRIFT = 0.03;

    /** Half the pane's thickness. Both flows start or end on its face, not in the middle of it. */
    private static final double FACE = 0.25;

    /**
     * What counts as going through the pane rather than standing in it: blocks moved along the pane's
     * normal in one tick. Measured on the normal alone, so sliding along the face is not a crossing.
     * Walking is about 0.13 and sprinting about 0.28, so this catches both and not a player who is
     * merely waiting out the transition.
     */
    private static final double WAKE_SPEED = 0.08;

    /**
     * The wake itself, per pane per tick. Small because an entity of any height overlaps several panes
     * and every one of them is asked.
     */
    private static final int WAKE_MOTES = 2;
    private static final double WAKE_SPREAD = 0.6;
    private static final double WAKE_WANDER = 0.04;
    private static final double WAKE_DRAG = 0.05;

    /**
     * The one-off across the whole opening, at the two moments a frame changes hands: lit, and spent.
     * Per block of opening, because a 21x21 rift should read bigger than a 2x3 one, but capped, since
     * the difference between those two is a factor of 73.
     */
    private static final int FLARE_PER_BLOCK = 6;
    private static final int FLARE_MAX = 120;
    private static final double FLARE_SPEED = 0.12;

    /**
     * What the two flares sound like. Lighting is pitched off the rung, so a heavier key opens a
     * deeper hole; spending sits below all four of them, because it is the rarer event and wants to be
     * told apart from the one that happened minutes earlier at the same frame.
     */
    public static final float LIGHT_PITCH = 1.1F;
    public static final float LIGHT_PER_RUNG = 0.05F;
    static final float SPEND_PITCH = 0.7F;

    private static final VoxelShape X_SHAPE = Block.box(0.0, 0.0, 6.0, 16.0, 16.0, 10.0);
    private static final VoxelShape Z_SHAPE = Block.box(6.0, 0.0, 0.0, 10.0, 16.0, 16.0);

    public RiftPortalBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(AXIS, Direction.Axis.X)
                .setValue(KIND, RiftPortalKind.ENTRANCE)
                .setValue(INTENSITY, 0));
    }

    /**
     * The rung a tier sits on: one per doubling.
     *
     * <p>
     * The tier ladder has no agreed top &mdash; the forge lets a player dial any number they can pay
     * for &mdash; so a linear mapping would either waste every rung below the ceiling or pin
     * everything above it to the same one. Doubling spends the four rungs over tiers 1, 2, 4 and 8
     * and up, which is where the difference between two rifts is actually felt.
     */
    public static int intensityFor(int tier) {
        return Mth.clamp(Mth.log2(Math.max(1, tier)), 0, MAX_INTENSITY);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(AXIS) == Direction.Axis.Z ? Z_SHAPE : X_SHAPE;
    }

    /**
     * Pops the block when its frame stops being one.
     *
     * <p>
     * Only changes that could be the frame are looked at: the jambs, which lie along the portal's
     * own axis, and the sill and lid above and below. A change on the face of the pane is somebody
     * building in front of the portal, and walling a portal in has never switched one off.
     */
    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbour,
                                     LevelAccessor level, BlockPos pos, BlockPos neighbourPos) {
        Direction.Axis axis = state.getValue(AXIS);
        boolean throughPane = direction.getAxis().isHorizontal() && direction.getAxis() != axis;
        if (throughPane || neighbour.is(this)) {
            return super.updateShape(state, direction, neighbour, level, pos, neighbourPos);
        }
        return RiftPortalShape.find(level, pos, axis).filter(RiftPortalShape::isComplete).isPresent()
                ? super.updateShape(state, direction, neighbour, level, pos, neighbourPos)
                : Blocks.AIR.defaultBlockState();
    }

    /**
     * Forgets the frame when its portal goes.
     *
     * <p>
     * Only the bottom-left block matters, because that is the corner a frame is keyed on, and it is
     * always one of the blocks that pops. Checking every block against the map instead would be the
     * same work with more chances to drop the wrong entry.
     *
     * <p>
     * Both the persistent record and any run standing open onto this frame are told, because both
     * ways of losing a frame come through here: a run putting its own frame out at the end, and a
     * player breaking the copper while the party is still inside. A run whose frame goes mid-dungeon
     * carries on with nowhere left to put out.
     *
     * <p>
     * A frame nobody ever walked into has no run but may well have a dungeon, so that is told too.
     */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState replacement, boolean movedByPiston) {
        if (!replacement.is(this) && level instanceof ServerLevel server) {
            forget(server, pos);
        }
        super.onRemove(state, level, pos, replacement, movedByPiston);
    }

    /**
     * Everything a vanishing pane has to unsay, split out so that a portal with no frame can say
     * nothing. A tear is keyed on nothing and recorded nowhere, so all three of these calls would be
     * asking three maps about a position none of them has heard of.
     */
    protected void forget(ServerLevel level, BlockPos pos) {
        forgetDoor(level, pos);
    }

    /**
     * The same three, for a door that is not a pane. A World Breaker is keyed on its own position
     * exactly as a frame is on its corner, so breaking one has exactly as much to unsay.
     */
    public static void forgetDoor(ServerLevel level, BlockPos pos) {
        RiftPortals.of(level).forget(pos);
        RiftRuns.frameBroken(level, pos);
        RiftWarmup.abandon(level, pos);
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (entity.canUsePortal(false)) {
            entity.setAsInsidePortal(this, pos);
        }
        if (level.isClientSide) {
            wake(state, level, pos, entity);
        }
    }

    /**
     * The mark something leaves in the pane on its way through.
     *
     * <p>
     * Client side and unsent: this is asked every tick of every entity overlapping every pane, which
     * is far too often to spend a packet on, and the client already has the one thing it needs.
     *
     * <p>
     * Embers rather than motes whatever the kind. The two flows say what the portal is <em>for</em>,
     * and a wake is not about that &mdash; it is the surface being disturbed, and it should look the
     * same going into an entrance as coming out of a way home.
     */
    private static void wake(BlockState state, Level level, BlockPos pos, Entity entity) {
        boolean faceAlongZ = state.getValue(AXIS) == Direction.Axis.X;
        // Movement along the pane's normal only. Walking the length of a wide portal's face is not
        // going through it and should not leave anything behind.
        double crossed = faceAlongZ ? entity.getZ() - entity.zOld : entity.getX() - entity.xOld;
        if (Math.abs(crossed) < WAKE_SPEED) {
            return;
        }

        RandomSource random = level.getRandom();
        double along = faceAlongZ ? entity.getX() : entity.getZ();
        double plane = (faceAlongZ ? pos.getZ() : pos.getX()) + 0.5;
        // Dragged the way the entity was going, so the wake trails rather than bursting symmetrically.
        double drag = Math.signum(crossed) * WAKE_DRAG;

        for (int mote = 0; mote < WAKE_MOTES; mote++) {
            double side = (random.nextDouble() - 0.5) * WAKE_SPREAD;
            double wander = (random.nextDouble() - 0.5) * WAKE_WANDER;
            level.addParticle(RiftfallParticles.RIFT_EMBER.get(),
                    faceAlongZ ? along + side : plane,
                    pos.getY() + random.nextDouble(),
                    faceAlongZ ? plane : along + side,
                    faceAlongZ ? wander : drag,
                    (random.nextDouble() - 0.5) * WAKE_WANDER,
                    faceAlongZ ? drag : wander);
        }
    }

    /**
     * What the whole opening does at the two moments it changes hands: lit by a key, and spent by the
     * first player through it.
     *
     * <p>
     * One broadcast for the opening rather than one per pane, because {@code sendParticles} already
     * spreads a count over a volume and a 21x21 rift would otherwise be 441 packets.
     *
     * <p>
     * Embers again, and for a plainer reason than the wake: {@code RiftMoteParticle} reads its velocity
     * as a whole journey to a destination, and the vanilla particle packet hands every particle a
     * random one. Motes sent this way would arrive nowhere.
     */
    static void flare(ServerLevel level, RiftPortalShape shape, float pitch) {
        flare(level, shape.area(), shape.axis(), pitch);
    }

    /**
     * The same, for an opening that has no frame to measure. A tear knows the blocks it fills and the
     * axis it spans, which is everything the flare ever read off a shape.
     */
    static void flare(ServerLevel level, BoundingBox area, Direction.Axis axis, float pitch) {
        Vec3i centre = area.getCenter();
        boolean faceAlongZ = axis == Direction.Axis.X;
        int panes = area.getXSpan() * area.getYSpan() * area.getZSpan();

        level.sendParticles(RiftfallParticles.RIFT_EMBER.get(),
                centre.getX() + 0.5, centre.getY() + 0.5, centre.getZ() + 0.5,
                Math.min(FLARE_MAX, FLARE_PER_BLOCK * panes),
                // The spread is a gaussian width, so a quarter of the span puts most of it inside the
                // opening rather than out in the copper.
                faceAlongZ ? area.getXSpan() / 4.0 : FACE,
                area.getYSpan() / 4.0,
                faceAlongZ ? FACE : area.getZSpan() / 4.0,
                FLARE_SPEED);
        // The respawn anchor's, not the nether portal's. Same reason as the ambience below, and from
        // the same family so the two cues sound like one object.
        level.playSound(null, new BlockPos(centre), SoundEvents.RESPAWN_ANCHOR_SET_SPAWN, SoundSource.BLOCKS,
                0.6F, pitch);
    }

    /**
     * None. Touching a rift portal takes you, on the tick you touch it.
     *
     * <p>
     * This did read the nether portal's delay game rule, on the grounds that the design was
     * nether-like and a server that has tuned how long players stand in a portal means it for
     * portals generally. What that missed is that the Nether's delay is only bearable because it
     * comes with the Nether's screen-warp: {@link #getLocalTransition} here is {@code NONE}, so the
     * default 80 ticks were four seconds of standing in a lit frame with no cue that anything had
     * begun. The delay read as the portal not working.
     *
     * <p>
     * Zero rather than one because {@code PortalProcessor} compares {@code portalTime++} against
     * this, so zero is the only value that fires on the first tick inside. A rift is entered
     * deliberately, with a key, through a frame you built: there is no accidental entry for a grace
     * period to protect against, which is the delay's whole job in the Nether.
     */
    @Override
    public int getPortalTransitionTime(ServerLevel level, Entity entity) {
        return 0;
    }

    /**
     * The rift on the other side, starting a run if the frame has not been walked into yet.
     *
     * <p>
     * Returning null leaves the entity standing in the portal, which is the right failure for a frame
     * that is merely not ready: it keeps its particles and its sound, and walking out and back in
     * tries again. It is also the answer for the two refusals below, where trying again will not help,
     * which is why those say so out loud rather than leaving a player stood in a portal wondering.
     *
     * <p>
     * Asked once a tick for as long as somebody stands in a portal past its transition time, not once
     * per entry: {@code PortalProcessor} keeps returning true from {@code processPortalTeleportation}
     * after the timer is up. That is why the refusal notice is rationed off the game time rather than
     * sent whenever it is true.
     */
    @Nullable
    @Override
    public DimensionTransition getPortalDestination(ServerLevel level, Entity entity, BlockPos pos) {
        // Inside a rift the same block is the way back out, not a door to a second one: the frame
        // raised beside the arrival point when the run began, leading to the doorstep the player who
        // walks into it left from. Anything the roster does not know gets nothing and stays put.
        // Bailing is not an ending -- whoever is left carries the run on, and if the bailer was the
        // last one holding it the ordinary run-end rule closes the rift on this very tick.
        if (RiftLevels.isRift(level.dimension())) {
            return entity instanceof ServerPlayer player ? RiftRuns.leaving(player) : null;
        }
        Optional<RiftPortalShape> found = RiftPortalShape.find(level, pos, level.getBlockState(pos).getValue(AXIS))
                .filter(RiftPortalShape::isComplete);
        if (found.isEmpty()) {
            return null;
        }
        RiftPortalShape shape = found.get();
        return enter(level, entity, shape.bottomLeft(), shape::douse, () -> flare(level, shape, SPEND_PITCH));
    }

    /**
     * Walking into a door that leads out of this world, from the corner it is known by.
     *
     * <p>
     * ⭐ Every service this touches &mdash; {@link RiftPortals}, {@link RiftRuns}, {@link RiftWarmup}
     * &mdash; is keyed on a bare {@link BlockPos} and has never known what stands at it. That is what
     * lets a World Breaker be a door on the same terms as a copper frame: it hands over its own
     * position and the two gestures a frame would have made.
     *
     * @param corner what the run is filed under, which must be the same position that lit it
     * @param expire puts the door out, for a frame lit before a restart and now leading nowhere
     * @param spent  says out loud that the first player through has just made the dungeon
     */
    @Nullable
    static DimensionTransition enter(ServerLevel level, Entity entity, BlockPos corner,
                                     Runnable expire, Runnable spent) {
        RiftRun run = RiftRuns.at(level, corner);
        if (run == null) {
            RiftPortals portals = RiftPortals.of(level);
            if (portals.isSpent(corner)) {
                // Lit, spent, and with no run behind it. The only way to be all three is to have been
                // lit before a restart with no run still going at the stop: a run still going comes
                // back with its dungeon (G9). A frame never spent is rebuilt from its saved seed below,
                // but this one cannot be -- the seed reproduces the dungeon, not what was killed and looted in
                // it, so rebuilding would sell one key two runs. Put it out here, where its chunk is
                // loaded and there is somebody to tell, rather than at boot where neither is true.
                expire.run();
                notice(entity, EXPIRED);
                return null;
            }
            if (!(entity instanceof ServerPlayer)) {
                // Only a player may spend a frame. Anything else that can use a portal -- a wandering
                // mob, a pet, a minecart -- would otherwise start the run it is not able to hold open:
                // holders are players, so the dungeon would stand empty through the grace window and
                // then end, putting the frame out and taking the key that lit it with it. A cow would
                // cost a tier-five key. It may still follow a party through a run already under way,
                // which is the branch below.
                return null;
            }
            ServerLevel rift = RiftWarmup.claim(level, corner, portals.tier(corner),
                    portals.seed(corner, level.getRandom()), portals.attunement(corner));
            if (rift == null) {
                // Vanilla installs its 300-tick portal cooldown before asking for a destination.
                // No journey happened: keep contact retryable on the next tick as preparation finishes.
                entity.setPortalCooldown(0);
                if (level.getGameTime() % NOTICE_INTERVAL == 0)
                    notice(entity, "message.riftfall.rift_forming");
                return null;
            }
            run = RiftRuns.begin(level, corner, rift);
            portals.spend(corner);
            // Deeper than the pitch a key lights at, and for the people left standing outside rather
            // than for the player it fires at: whoever triggered it is gone on this same tick.
            spent.run();
        } else if (entity instanceof ServerPlayer player && run.hasEntered(player.getUUID())) {
            if (level.getGameTime() % NOTICE_INTERVAL == 0) {
                notice(entity, LOCKED_OUT);
            }
            return null;
        } else if (entity instanceof ServerPlayer && run.locked()) {
            // The roster closed when somebody on it went deeper than the room they arrived in. Only
            // players are refused: a pet that follows a party through was never on a roster and the
            // ruling is about who is on a party.
            if (level.getGameTime() % NOTICE_INTERVAL == 0) {
                notice(entity, ROSTER_LOCKED);
            }
            return null;
        }

        ServerLevel rift = level.getServer().getLevel(run.rift());
        if (rift == null) {
            return null;
        }
        if (entity instanceof ServerPlayer player) {
            RiftRuns.entering(run, player);
        }

        // Ours, so it takes out a permit: handlePortal calls changeDimension on the line after it
        // asks for this, which is what makes one tick enough.
        RiftTeleports.permit(entity, rift);

        // The ticket is not decoration. Nothing else is holding the arrival chunk open — a rift has no
        // spawn chunks and, until somebody is standing in it, no player to load one — so an entity that
        // is not itself a chunk loader arrives in an unloaded section and stops existing as far as the
        // level is concerned. Vanilla pairs the two on every portal it has for the same reason.
        return new DimensionTransition(rift, RiftLevels.arrival(rift), Vec3.ZERO,
                entity.getYRot(), entity.getXRot(),
                DimensionTransition.PLAY_PORTAL_SOUND.then(DimensionTransition.PLACE_PORTAL_TICKET));
    }

    /**
     * Says why the portal did nothing, on the action bar, and only to players.
     *
     * <p>
     * The action bar rather than chat because it is a caption on something the player is looking at
     * and not worth a line of history, and grey because it is the game explaining itself rather than
     * anybody speaking.
     *
     * <p>
     * Shared with {@link RiftKeyItem} and the World Breaker rather than copied: a refused key, a
     * refused entry and a refused plate are the same kind of message and want to keep looking like
     * each other. Null-safe on purpose, because {@code UseOnContext} hands out a nullable player.
     */
    public static void notice(@Nullable Entity entity, String key) {
        if (entity instanceof ServerPlayer player) {
            player.displayClientMessage(Component.translatable(key).withStyle(ChatFormatting.GRAY), true);
        }
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return switch (rotation) {
            case CLOCKWISE_90, COUNTERCLOCKWISE_90 -> state.setValue(AXIS,
                    state.getValue(AXIS) == Direction.Axis.Z ? Direction.Axis.X : Direction.Axis.Z);
            default -> state;
        };
    }

    /**
     * The surface, as the client sees it: the ambient cue, and one of two opposed flows of motes.
     *
     * <p>
     * The direction is the whole of what tells the three portals apart from across a room. An
     * entrance takes: motes are born off the face and travel into the pane, because what is behind it
     * is somewhere you have not been. The two ways home give: embers leave the pane and burn down
     * outside it. {@link #INTENSITY} then drives all three of density, speed and how often the pane
     * is heard, so a tier-8 rift is louder and busier than a tier-1 one without being a different
     * thing.
     */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        int intensity = state.getValue(INTENSITY);
        if (random.nextInt(AMBIENCE_ODDS - intensity * AMBIENCE_PER_RUNG) == 0) {
            // Not PORTAL_AMBIENT. That is the nether portal's own hum, and a rift that sounds exactly
            // like a nether portal is a rift the ear files as one. The respawn anchor's is a low tone
            // most players have never stood next to long enough to name.
            level.playLocalSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                    SoundEvents.RESPAWN_ANCHOR_AMBIENT, SoundSource.BLOCKS, 0.5F,
                    random.nextFloat() * 0.4F + 0.8F, false);
        }

        boolean inward = state.getValue(KIND).inward();
        ParticleOptions particle = inward
                ? RiftfallParticles.RIFT_MOTE.get()
                : RiftfallParticles.RIFT_EMBER.get();
        // The pane stands in the plane of its axis, so what "off the face" means is the other one.
        boolean faceAlongZ = state.getValue(AXIS) == Direction.Axis.X;
        double thrown = THROW_SPEED + THROW_PER_RUNG * intensity;

        double spread = REACH_SPREAD * (1.0 + REACH_PER_RUNG * intensity);
        double wanders = inward ? PULL_WANDER : DRIFT;

        for (int mote = 0; mote < MOTES + MOTES_PER_RUNG * intensity; mote++) {
            int side = random.nextInt(2) * 2 - 1;
            double across = across(random);
            double reach = REACH + spread * random.nextDouble();

            // Inward: born out at reach, and handed the trip back to the face as its velocity, which
            // is what RiftMoteParticle interpolates. Outward: born on the face and given a speed.
            double off = side * (inward ? reach : FACE);
            double drive = inward ? -side * (reach - FACE) : side * thrown * (0.5 + random.nextDouble());

            double wander = (random.nextDouble() - 0.5) * wanders;
            double x = pos.getX() + (faceAlongZ ? across : 0.5 + off);
            double z = pos.getZ() + (faceAlongZ ? 0.5 + off : across);
            level.addParticle(particle, x, pos.getY() + random.nextDouble(), z,
                    faceAlongZ ? wander : drive,
                    (random.nextDouble() - 0.5) * wanders,
                    faceAlongZ ? drive : wander);
        }
    }

    /**
     * Where along the face a mote is born, from 0 at one edge of the block to 1 at the other.
     *
     * <p>
     * The whole width, because a pane fills its block and a party should see the surface working
     * right up to the copper. A tear does not fill its block and narrows this to the rip.
     */
    protected double across(RandomSource random) {
        return random.nextDouble();
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AXIS, KIND, INTENSITY);
    }
}
