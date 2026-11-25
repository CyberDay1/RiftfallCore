package com.cyberday1.riftfall.block.breaker;

import com.cyberday1.riftfall.RiftfallCore;
import com.cyberday1.riftfall.init.RiftfallBlocks;
import com.cyberday1.riftfall.rift.RiftKeyItem;
import com.cyberday1.riftfall.rift.RiftPortalBlock;
import com.cyberday1.riftfall.rift.RiftTearBlock;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * The device that breaks a way in: an obsidian dais with an amethyst shard cluster in each corner and
 * a keyhole socket in the middle. A key goes in, turns over 1.5 s, and a tear opens above it.
 *
 * <p>
 * The cycle is {@link WorldBreakerPhase}: idle, charging (the key turning), open (a tear standing on
 * it), then a 30 s cooldown whose first second is the broken shards flying home. Every timed step is
 * a scheduled tick, so the cycle carries on across a restart; the open phase lasts exactly as long as
 * the tear above it.
 *
 * <p>
 * Only the entry tear stands on one of these. The tears a boss drops and the ones an anchor opens are
 * placed wherever the player happens to be, so nothing can be built under them in advance.
 */
public class WorldBreakerBlock extends Block implements EntityBlock {

    public static final MapCodec<WorldBreakerBlock> CODEC = simpleCodec(WorldBreakerBlock::new);

    public static final EnumProperty<WorldBreakerPhase> PHASE = EnumProperty.create("phase", WorldBreakerPhase.class);

    /** The socket's face, in texture pixels: the highest a player stands on it. */
    public static final double THICKNESS = 5.0;

    /**
     * Where the tear's picture stands, in pixels above the breaker's own floor.
     *
     * <p>
     * ⭐ Its bottom edge, not its usual foot: the breathing cracks of {@code rift_tear_frame} reach the
     * picture's very edge in 29 of its 192 frames, so the edge itself has to clear the floating shard
     * ring, whose highest corner over its whole loop is 12.62 px. At 18 px the smallest gap over the
     * full 1920-tick cycle is 5.4 px (measured in the approved prototype).
     */
    public static final double RIP_FLOOR = 18.0;

    /**
     * How far the rip comes DOWN from its column's floor on a breaker: negative, so it is raised.
     * The column's floor is the top of the breaker's block, 16 px over the breaker's own floor.
     */
    public static final float RIP_DROP = (float) ((16.0 - RIP_FLOOR) / 16.0);

    private static final String NO_ROOM = "message." + RiftfallCore.MODID + ".breaker_no_room";
    private static final String ALREADY_OPEN = "message." + RiftfallCore.MODID + ".breaker_already_open";
    private static final String COOLING = "message." + RiftfallCore.MODID + ".breaker_cooling";

    /** What a player stands on and walks into: the dais and the socket. The shards are left out. */
    private static final VoxelShape COLLISION = Shapes.or(
            Block.box(0.0, 0.0, 0.0, 16.0, 4.0, 16.0),
            Block.box(5.0, 4.0, 5.0, 11.0, THICKNESS, 11.0));

    /**
     * What the cursor outlines: the collision plus the four shard clusters.
     *
     * <p>
     * The shards are kept out of collision on purpose: a centred player's box reaches to 3.2 px from
     * the edge and a shard to 3.75, so a diagonal approach would snag on a corner it cannot see.
     */
    private static final VoxelShape OUTLINE = Shapes.or(COLLISION,
            Block.box(0.25, 4.0, 0.25, 3.75, 15.0, 3.75),
            Block.box(12.25, 4.0, 0.25, 15.75, 15.0, 3.75),
            Block.box(0.25, 4.0, 12.25, 3.75, 15.0, 15.75),
            Block.box(12.25, 4.0, 12.25, 15.75, 15.0, 15.75));

    public WorldBreakerBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(PHASE, WorldBreakerPhase.IDLE));
    }

    /**
     * A key goes in and starts turning, and the tear opens when it has turned.
     *
     * <p>
     * Every refusal comes before anything is taken. The room is checked here as well as when the
     * charge runs out, so a player is never charged for a breaker with a ceiling on it.
     */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(stack.getItem() instanceof RiftKeyItem)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (level instanceof ServerLevel server) {
            switch (state.getValue(PHASE).answerKey()) {
                case ALREADY_OPEN -> {
                    RiftPortalBlock.notice(player, ALREADY_OPEN);
                    return ItemInteractionResult.FAIL;
                }
                case COOLING -> {
                    RiftPortalBlock.notice(player, COOLING);
                    return ItemInteractionResult.FAIL;
                }
                case TAKE -> {
                    if (!RiftTearBlock.roomFor(server, pos.above())) {
                        RiftPortalBlock.notice(player, NO_ROOM);
                        return ItemInteractionResult.FAIL;
                    }
                    Direction.Axis axis = player.getDirection().getClockWise().getAxis();
                    if (!charge(server, pos, state, stack.copyWithCount(1), axis)) {
                        return ItemInteractionResult.FAIL;
                    }
                    stack.consume(1, player);
                    com.cyberday1.riftfall.story.StoryBridge.keyReady(player, "seated");
                }
            }
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    /**
     * Seats a key and starts the windup. The axis is taken across the player's line of sight,
     * the same rule {@code /rift tear} uses, so the rip's face is turned towards whoever lit it.
     *
     * @return false if the store could not be made, in which case nothing was changed
     */
    public static boolean charge(ServerLevel level, BlockPos pos, BlockState state, ItemStack key,
                                 Direction.Axis axis) {
        level.setBlock(pos, state.setValue(PHASE, WorldBreakerPhase.CHARGING), Block.UPDATE_ALL);
        if (!(level.getBlockEntity(pos) instanceof WorldBreakerBlockEntity store)) {
            level.setBlock(pos, state, Block.UPDATE_ALL);
            return false;
        }
        store.charge(key, axis, level.getGameTime());
        RiftTearBlock.prepare(level, pos, store.tier());
        level.scheduleTick(pos, state.getBlock(), WorldBreakerMotion.RIP_START);
        level.playSound(null, pos, SoundEvents.VAULT_INSERT_ITEM, SoundSource.BLOCKS, 1.0F, 0.8F);
        return true;
    }

    /**
     * Each timed phase runs out here: the charge opens the tear, and the cooldown steps down to idle.
     */
    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        WorldBreakerPhase phase = state.getValue(PHASE);
        if (phase == WorldBreakerPhase.CHARGING) {
            if (level.getBlockEntity(pos) instanceof WorldBreakerBlockEntity store
                    && store.chargedAt() != WorldBreakerBlockEntity.UNSET) {
                long elapsed = Math.max(0, level.getGameTime() - store.chargedAt());
                if (elapsed < WorldBreakerPhase.CHARGING.ticks()) {
                    int next = elapsed < WorldBreakerMotion.RIP_START ? WorldBreakerMotion.RIP_START
                            : elapsed < WorldBreakerMotion.RIP_WIDEN ? WorldBreakerMotion.RIP_WIDEN
                            : WorldBreakerPhase.CHARGING.ticks();
                    if (elapsed == WorldBreakerMotion.RIP_START || elapsed == WorldBreakerMotion.RIP_WIDEN) {
                        level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS,
                                0.8F, elapsed == WorldBreakerMotion.RIP_START ? 0.6F : 1.1F);
                    }
                    level.scheduleTick(pos, this, (int) (next - elapsed));
                    return;
                }
            }
            fire(state, level, pos);
            return;
        }
        if (phase.ticks() == 0) {
            return;
        }
        WorldBreakerPhase next = phase.afterTimer();
        // Leaving HOMING drops the charge store: isValidBlockState says no for every phase after it.
        level.setBlock(pos, state.setValue(PHASE, next), Block.UPDATE_ALL);
        if (next.ticks() > 0) {
            level.scheduleTick(pos, this, next.ticks());
        }
    }

    /**
     * The key has turned: open the tear, at the key's own tier. If the room filled while the key was
     * turning, the key comes back out and the breaker goes idle; nothing was charged for.
     */
    private void fire(BlockState state, ServerLevel level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof WorldBreakerBlockEntity store)) {
            level.setBlock(pos, state.setValue(PHASE, WorldBreakerPhase.IDLE), Block.UPDATE_ALL);
            return;
        }
        if (RiftTearBlock.light(level, pos, store.axis(), store.tier())) {
            // light() put the tear above; updateShape has already turned this to OPEN.
            if (level.getBlockEntity(pos) instanceof WorldBreakerBlockEntity open) {
                open.opened(level.getGameTime());
            }
            level.playSound(null, pos, SoundEvents.VAULT_ACTIVATE, SoundSource.BLOCKS, 1.0F, 0.7F);
            return;
        }
        ItemStack key = store.takeKey();
        RiftPortalBlock.forgetDoor(level, pos);
        level.setBlock(pos, state.setValue(PHASE, WorldBreakerPhase.IDLE), Block.UPDATE_ALL);
        Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, key);
        for (ServerPlayer near : level.getEntitiesOfClass(ServerPlayer.class, new AABB(pos).inflate(8.0))) {
            RiftPortalBlock.notice(near, NO_ROOM);
        }
    }

    /**
     * A broken breaker takes its tear and its bookkeeping with it, and a key still turning in it comes
     * back out: nothing was opened with it.
     *
     * <p>
     * Guarded on the replacement so that a phase change is not read as the block going. ⚠ A key whose
     * tear is already open is spent, as it always was: the run it bought may be under way.
     */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState replacement,
                            boolean movedByPiston) {
        if (!replacement.is(this) && level instanceof ServerLevel server) {
            ItemStack key = keyBackOnBreak(state, level.getBlockEntity(pos));
            if (!key.isEmpty()) {
                Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, key);
            }
            RiftTearBlock.broken(server, pos);
        }
        super.onRemove(state, level, pos, replacement, movedByPiston);
    }

    /**
     * The key a breaker hands back when it is broken in {@code state}: the turning one, mid-charge,
     * and nothing otherwise (an opened key is spent). Taken out of the store, so it cannot come back
     * twice.
     */
    public static ItemStack keyBackOnBreak(BlockState state, @Nullable BlockEntity entity) {
        if (state.getValue(PHASE) != WorldBreakerPhase.CHARGING || !(entity instanceof WorldBreakerBlockEntity store)) {
            return ItemStack.EMPTY;
        }
        return store.takeKey();
    }

    /**
     * The phase follows whatever is standing on the breaker: a tear arriving opens it, and a tear
     * going, by any route at all, starts the cooldown.
     *
     * <p>
     * Only {@link Direction#UP} is worth reading; the tear is directly above and the other five
     * neighbours cannot tell us anything about it.
     */
    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbour,
                                     LevelAccessor level, BlockPos pos, BlockPos neighbourPos) {
        if (direction != Direction.UP) {
            return state;
        }
        WorldBreakerPhase was = state.getValue(PHASE);
        WorldBreakerPhase now = was.withTearAbove(neighbour.is(RiftfallBlocks.RIFT_TEAR.get()));
        if (now == was) {
            return state;
        }
        if (now == WorldBreakerPhase.HOMING && level instanceof ServerLevel server) {
            server.scheduleTick(pos, this, WorldBreakerPhase.HOMING.ticks());
            if (server.getBlockEntity(pos) instanceof WorldBreakerBlockEntity store) {
                store.cooling(server.getGameTime());
            }
        }
        return state.setValue(PHASE, now);
    }

    /** Sparks rise out of the keyhole while the key turns. Client only. */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (state.getValue(PHASE) != WorldBreakerPhase.CHARGING) {
            return;
        }
        for (int i = 0; i < 2; i++) {
            level.addParticle(ParticleTypes.REVERSE_PORTAL,
                    pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.25,
                    pos.getY() + THICKNESS / 16.0 + 0.05,
                    pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.25,
                    0.0, 0.03 + random.nextDouble() * 0.04, 0.0);
        }
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(PHASE).holdsKey() ? new WorldBreakerBlockEntity(pos, state) : null;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return OUTLINE;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
                                           CollisionContext context) {
        return COLLISION;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(PHASE);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }
}
