package com.cyberday1.riftfall.block.breaker;

import com.cyberday1.riftfall.init.RiftfallBlockEntities;
import com.cyberday1.riftfall.rift.RiftKeyItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * The charge store: the key that went in, and when each moving part of the cycle started.
 *
 * <p>
 * It exists only from the key going in until the shards are home, one second into the cooldown
 * ({@link WorldBreakerPhase#holdsKey}). Idle breakers, which are most of them, have none.
 *
 * <p>
 * ⭐ The three times are game times, saved and synced, so a chunk that reloads mid-cycle draws the
 * motion where it actually is rather than replaying the lift-off from the start.
 */
public class WorldBreakerBlockEntity extends BlockEntity {

    /** No time recorded yet. */
    public static final long UNSET = Long.MIN_VALUE;

    private ItemStack key = ItemStack.EMPTY;
    private Direction.Axis axis = Direction.Axis.X;
    private long chargedAt = UNSET;
    private long openedAt = UNSET;
    private long coolingAt = UNSET;

    public WorldBreakerBlockEntity(BlockPos pos, BlockState state) {
        super(RiftfallBlockEntities.WORLD_BREAKER.get(), pos, state);
    }

    /** The key that went in, exactly as it was, one of it. Drawn seated, and dropped if the charge is broken. */
    public ItemStack key() {
        return key;
    }

    /** The tier the tear opens at: the key's own. */
    public int tier() {
        return RiftKeyItem.tier(key);
    }

    /** The tear's axis, across the view of whoever turned the key. */
    public Direction.Axis axis() {
        return axis;
    }

    public long chargedAt() {
        return chargedAt;
    }

    public long openedAt() {
        return openedAt;
    }

    public long coolingAt() {
        return coolingAt;
    }

    /** The key goes in. */
    public void charge(ItemStack one, Direction.Axis axis, long now) {
        this.key = one.copyWithCount(1);
        this.axis = axis;
        this.chargedAt = now;
        this.openedAt = UNSET;
        this.coolingAt = UNSET;
        sync();
    }

    public void opened(long now) {
        this.openedAt = now;
        sync();
    }

    public void cooling(long now) {
        this.coolingAt = now;
        sync();
    }

    /** Hands the key back out, leaving the store empty: for a charge that is broken or blocked. */
    public ItemStack takeKey() {
        ItemStack out = key;
        key = ItemStack.EMPTY;
        sync();
        return out;
    }

    private void sync() {
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    /**
     * ⭐ Only the phases that hold a key keep a store. When the breaker steps past them the chunk sees
     * this say no and drops the store itself, on both sides, so nothing has to remember to.
     */
    @Override
    public boolean isValidBlockState(BlockState state) {
        return state.getBlock() instanceof WorldBreakerBlock && state.getValue(WorldBreakerBlock.PHASE).holdsKey();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (!key.isEmpty()) {
            tag.put("key", key.save(registries));
        }
        tag.putString("axis", axis.getSerializedName());
        tag.putLong("charged_at", chargedAt);
        tag.putLong("opened_at", openedAt);
        tag.putLong("cooling_at", coolingAt);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        key = tag.contains("key") ? ItemStack.parseOptional(registries, tag.getCompound("key")) : ItemStack.EMPTY;
        Direction.Axis read = Direction.Axis.byName(tag.getString("axis"));
        axis = read == null ? Direction.Axis.X : read;
        chargedAt = tag.contains("charged_at") ? tag.getLong("charged_at") : UNSET;
        openedAt = tag.contains("opened_at") ? tag.getLong("opened_at") : UNSET;
        coolingAt = tag.contains("cooling_at") ? tag.getLong("cooling_at") : UNSET;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
