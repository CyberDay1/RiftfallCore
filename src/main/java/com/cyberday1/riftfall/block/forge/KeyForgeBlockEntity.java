package com.cyberday1.riftfall.block.forge;

import com.cyberday1.riftfall.RiftfallCore;
import com.cyberday1.riftfall.init.RiftfallBlockEntities;
import com.cyberday1.riftfall.init.RiftfallItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * What a {@link KeyForgeBlock} is holding: the rift shards a player has loaded into it.
 *
 * <p>
 * One slot, and no tier. The tier is not a property of the bench &mdash; a forge is not "set to
 * tier 9" the way a comparator is set to a mode. It is a property of the one key being made, so it
 * lives on {@link KeyForgeMenu} for as long as a player has the screen open and is forgotten when
 * they close it. That also keeps it off the wire: menu data slots synchronise as {@code short}s,
 * which would have put a silent ceiling at 32767 on a number the design says has no ceiling.
 *
 * <p>
 * The shards do have to persist. They are the player's items, sitting in a block in the world, and
 * the expectation a furnace sets is that they are still there tomorrow.
 */
public class KeyForgeBlockEntity extends BaseContainerBlockEntity {

    /** The one slot: rift shards waiting to be spent. */
    public static final int SHARD_SLOT = 0;

    private static final int SIZE = 1;

    private NonNullList<ItemStack> items = NonNullList.withSize(SIZE, ItemStack.EMPTY);

    public KeyForgeBlockEntity(BlockPos pos, BlockState state) {
        super(RiftfallBlockEntities.KEY_FORGE.get(), pos, state);
    }

    @Override
    protected Component getDefaultName() {
        return Component.translatable("container." + RiftfallCore.MODID + ".key_forge");
    }

    @Override
    protected NonNullList<ItemStack> getItems() {
        return items;
    }

    @Override
    protected void setItems(NonNullList<ItemStack> replacement) {
        this.items = replacement;
    }

    @Override
    public int getContainerSize() {
        return SIZE;
    }

    /**
     * Shards only.
     *
     * <p>
     * Hoppers and droppers go through this as well as the slot in the screen does, so it is the one
     * place that has to be right: without it a pipe could pack the forge with anything and the
     * player would open it to find a slot they cannot use and cannot easily empty.
     */
    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return slot == SHARD_SLOT && stack.is(RiftfallItems.RIFT_SHARD.get());
    }

    /**
     * Writes the shards.
     *
     * <p>
     * {@link BaseContainerBlockEntity} does not do this. It saves the lock and the custom name and
     * stops there &mdash; every vanilla container writes its own contents, so a container block
     * entity that inherits and adds nothing loads back empty, quietly, having eaten whatever was in
     * it. That is a real loss of a player's items rather than a cosmetic one.
     */
    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, items, registries);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, items, registries);
    }

    @Override
    protected AbstractContainerMenu createMenu(int containerId, Inventory inventory) {
        return new KeyForgeMenu(containerId, inventory, this);
    }
}
