package com.cyberday1.riftfall.block.barrel;

import com.cyberday1.riftfall.RiftfallCore;
import com.cyberday1.riftfall.init.RiftfallAttributes;
import com.cyberday1.riftfall.init.RiftfallBlockEntities;
import com.cyberday1.riftfall.tool.BarrelLootBonuses;
import com.cyberday1.riftfall.tool.ToolUpgrades;
import com.cyberday1.riftfall.tool.ToolStat;
import com.cyberday1.riftfall.home.HomeProtection;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ContainerOpenersCounter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * One barrel's contents, which is a different chest for every player who opens it.
 *
 * <h2>Why not one inventory</h2>
 * A rift is run by a party, and a shared chest in a dungeon built for four is a race: the first
 * player to the barrel takes the run's reward and the other three watched. Every player gets their
 * own draw from the same table, made the moment they first open it, and what one of them takes is
 * invisible to the rest.
 *
 * <p>
 * Nobody's share is rolled until they ask for it. A rift is hundreds of barrels and a party of four
 * will open a handful of them, so rolling at generation time would be a few thousand loot draws
 * bought to throw almost all of them away.
 *
 * <h2>Looted is per player, and the lid is not</h2>
 * {@link #opened} is who has been in this barrel, and it is the whole set rather than a count,
 * because every client has to be able to ask it about <em>themselves</em>. It rides the update tag
 * so it is on every client that can see the block, and the art each client draws is picked from it
 * at bake time through {@link #LOOTED}. The lid is the other thing entirely: it is
 * {@link RiftBarrelBlock#OPEN}, a real blockstate, shared, and it opens for whoever is standing in
 * the barrel exactly the way a vanilla one does.
 */
public class RiftBarrelBlockEntity extends BlockEntity {

    /**
     * Whether the barrel is spent <em>for the client asking</em>. Read by the baked model, written
     * only by {@link #refreshLooted()}, which never runs on a server.
     */
    public static final ModelProperty<Boolean> LOOTED = new ModelProperty<>();

    /**
     * ⭐ Nine rows, which is the smallest size the clamp in {@link BarrelDraws} cannot reach.
     *
     * <p>
     * The base roll tops out at 8 draws (24 before the 2026-10-11 cut) and {@code riftfall:scavenging}
     * multiplies that by {@code 1 + quantity}, so a player at the designed 200% ceiling asks for at
     * most 24. Nine rows was sized for the old ceiling of 72; it stays, because shrinking a saved
     * container is not save-safe, and a clamped draw is a reward the player is never told about.
     *
     * <p>
     * Merging stacks would have fitted the same loot into 54, and was rejected: one draw to one slot
     * is what makes the barrel show the roll it actually got.
     */
    private static final int SIZE = RiftBarrelMenu.SIZE;

    private static final String SHARES_KEY = "Shares";
    private static final String OWNER_KEY = "Owner";
    private static final String ITEMS_KEY = "Items";
    private static final String OPENED_KEY = "Opened";

    /** How often the lid rechecks itself while somebody has it open, in ticks. */
    private static final int LID_RECHECK_TICKS = 5;

    private static final Component NAME =
            Component.translatable("container." + RiftfallCore.MODID + ".rift_barrel");

    /** One player's share, keyed by their id. Server-side only; never sent to a client. */
    private final Map<UUID, Share> shares = new HashMap<>();

    /** Probe reads: shares filled and draws taken across every barrel since boot. */
    private static final AtomicLong SHARES_FILLED = new AtomicLong();
    private static final AtomicLong DRAWS_TAKEN = new AtomicLong();

    public static long sharesFilled() {
        return SHARES_FILLED.get();
    }

    public static long drawsTaken() {
        return DRAWS_TAKEN.get();
    }

    /** Everyone who has ever opened this barrel. Sent to every client, so each can find itself. */
    private final Set<UUID> opened = new HashSet<>();

    /** The last answer {@link #LOOTED} gave on this client. Meaningless on a server. */
    private boolean lootedHere;

    private final ContainerOpenersCounter openersCounter = new ContainerOpenersCounter() {

        @Override
        protected void onOpen(Level level, BlockPos pos, BlockState state) {
            playSound(state, SoundEvents.BARREL_OPEN);
            setLid(state, true);
        }

        @Override
        protected void onClose(Level level, BlockPos pos, BlockState state) {
            playSound(state, SoundEvents.BARREL_CLOSE);
            setLid(state, false);
        }

        @Override
        protected void openerCountChanged(Level level, BlockPos pos, BlockState state, int was, int now) {
        }

        /**
         * Vanilla compares the menu's container against the block entity. Ours cannot: the container
         * is one player's {@link Share}, so the question is which barrel that share belongs to.
         */
        @Override
        protected boolean isOwnContainer(Player player) {
            return player.containerMenu instanceof RiftBarrelMenu menu
                    && menu.barrel() instanceof Share share
                    && share.barrel() == RiftBarrelBlockEntity.this;
        }
    };

    public RiftBarrelBlockEntity(BlockPos pos, BlockState state) {
        super(RiftfallBlockEntities.RIFT_BARREL.get(), pos, state);
    }

    // -----------------------------------------------------------------------------------------
    // Opening
    // -----------------------------------------------------------------------------------------

    /**
     * Shows one player their share, rolling it if this is the first time they have looked.
     *
     * <p>
     * The looted mark is set on opening rather than on taking anything, which is the honest reading
     * of what the art says: the barrel has been gone through. A player who opens one and takes
     * nothing has still seen what was in it, and can come back for it.
     */
    public void open(Player player) {
        if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer)) {
            return;
        }
        Share share = shares.computeIfAbsent(player.getUUID(), owner -> fill(server, player));
        player.openMenu(new SimpleMenuProvider(
                (containerId, inventory, opener) -> new RiftBarrelMenu(containerId, inventory, share),
                NAME));
        if (opened.add(player.getUUID())) {
            com.cyberday1.riftfall.rift.RiftRecaps.barrelLooted((ServerPlayer) player, share);
            setChanged();
            server.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
        }
    }

    /**
     * Draws one player's share.
     *
     * <p>
     * The seed is the player's id against this barrel's position, so a share is the same share
     * whenever it is first asked for, and two players at one barrel get two different draws. One
     * stream serves the whole share &mdash; the slot count, the shuffle and every draw &mdash; so
     * each draw advances it and no two of them come out alike.
     *
     * <p>
     * {@link BarrelDraws} decides how many draws there are and the table describes what one draw may
     * yield, which is the swap {@code LootTable#fill} cannot do: {@code fill} reads its count from
     * the pool's {@code rolls}, the same number for every barrel reading the table, and
     * {@code riftfall:scavenging} has to move it per player. Each stack lands in an empty slot of
     * its own and nothing is merged, so the barrel shows the roll it actually got.
     *
     * <h2>One table, and every kind of content is an entry in it</h2>
     * Gear and the rift's crystal are {@code riftfall:gear} and {@code riftfall:theme_crystal},
     * weighted entries in the tier's own pool rather than anything decided here. That keeps every
     * draw inside {@code LootTable#getRandomItems}, which is where Apotheosis' affix conversion and
     * the tier&rarr;rarity ladder attach: a stack put in from out here would carry none of them.
     */
    private Share fill(ServerLevel server, Player player) {
        return BarrelLootBonuses.roll(player.getMainHandItem(), () -> fillWithBonuses(server, player));
    }

    private Share fillWithBonuses(ServerLevel server, Player player) {
        Share share = new Share();
        LootTable tierTable = server.getServer().reloadableRegistries().getLootTable(tier().lootTable());
        LootParams params = new LootParams.Builder(server)
                .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(worldPosition))
                .withParameter(LootContextParams.THIS_ENTITY, player)
                .withLuck(player.getLuck())
                .create(LootContextParamSets.CHEST);

        RandomSource random =
                RandomSource.create(player.getUUID().hashCode() * 31L + worldPosition.asLong());
        List<Integer> free = shuffledEmptySlots(share, random);
        int draws = BarrelDraws.roll(
                random, player.getAttributeValue(RiftfallAttributes.SCAVENGING)
                        + ToolUpgrades.bonus(player.getMainHandItem(), ToolStat.QUANTITY) / 100,
                BarrelDraws.scale(), free.size());
        SHARES_FILLED.incrementAndGet();
        DRAWS_TAKEN.addAndGet(draws);

        // Shards: one roll per share, not per draw.
        LootTable shardTable = server.getServer().reloadableRegistries().getLootTable(tier().shardTable());
        for (ItemStack stack : shardTable.getRandomItems(params, random)) {
            if (!stack.isEmpty() && !free.isEmpty()) {
                share.setItem(free.remove(free.size() - 1), stack);
            }
        }

        for (int draw = 0; draw < draws && !free.isEmpty(); draw++) {
            for (ItemStack stack : tierTable.getRandomItems(params, random)) {
                if (free.isEmpty()) {
                    return share;
                }
                if (!stack.isEmpty()) {
                    share.setItem(free.remove(free.size() - 1), stack);
                }
            }
        }
        return share;
    }

    public record Collection(int moved, boolean remaining, boolean fresh) {}

    /** Never removes the block or drops items. Unaccepted items remain in this player's saved share. */
    public Collection collect(ServerPlayer player) {
        if (!(level instanceof ServerLevel server) || player.level() != level || player.isSpectator()
                || HomeProtection.denied(level, player)) return new Collection(0, true, false);
        Share share = shares.computeIfAbsent(player.getUUID(), owner -> fill(server, player));
        int moved = collectInto(share, player);
        boolean fresh = opened.add(player.getUUID());
        if (fresh) com.cyberday1.riftfall.rift.RiftRecaps.barrelLooted(player, share);
        share.setChanged();
        player.getInventory().setChanged();
        player.containerMenu.broadcastChanges();
        if (fresh) server.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
        return new Collection(moved, !share.isEmpty(), fresh);
    }

    /** Uses insertion's remainder, including creative mode; Inventory.add would discard it in creative. */
    static int collectInto(Container share, ServerPlayer player) {
        var handler = new net.neoforged.neoforge.items.wrapper.PlayerMainInvWrapper(player.getInventory());
        int moved = 0;
        for (int slot = 0; slot < share.getContainerSize(); slot++) {
            var original = share.getItem(slot);
            if (original.isEmpty()) continue;
            var remainder = com.cyberday1.riftfall.compat.BackpackUpgrades.pickup(player, original);
            remainder = net.neoforged.neoforge.items.ItemHandlerHelper.insertItemStacked(handler, remainder, false);
            moved += original.getCount() - remainder.getCount();
            share.setItem(slot, remainder);
        }
        return moved;
    }

    public boolean hasRemainingFor(UUID player) {
        var share = shares.get(player);
        return share == null || !share.isEmpty();
    }

    /**
     * Probe use: a copy of {@code player}'s share, rolled the way {@link #open} rolls it if they have none, without a
     * menu. Copies, so a probe reading two shares cannot move items between them.
     */
    public List<ItemStack> shareOf(Player player) {
        if (!(level instanceof ServerLevel server)) {
            return List.of();
        }
        Share share = shares.computeIfAbsent(player.getUUID(), owner -> fill(server, player));
        List<ItemStack> copy = new ArrayList<>(share.getContainerSize());
        for (int slot = 0; slot < share.getContainerSize(); slot++) {
            copy.add(share.getItem(slot).copy());
        }
        return copy;
    }

    /** Where the draws may land, in the order they will be taken. */
    private static List<Integer> shuffledEmptySlots(Share share, RandomSource random) {
        List<Integer> free = new ArrayList<>(share.getContainerSize());
        for (int slot = 0; slot < share.getContainerSize(); slot++) {
            if (share.getItem(slot).isEmpty()) {
                free.add(slot);
            }
        }
        Util.shuffle(free, random);
        return free;
    }

    private BarrelTier tier() {
        return getBlockState().getBlock() instanceof RiftBarrelBlock barrel ? barrel.tier() : BarrelTier.SHABBY;
    }

    // -----------------------------------------------------------------------------------------
    // The lid
    // -----------------------------------------------------------------------------------------

    /** Vanilla's recheck, scheduled by {@link RiftBarrelBlock#tick} while anybody has this open. */
    public void recheckOpen() {
        if (!remove) {
            openersCounter.recheckOpeners(level, worldPosition, getBlockState());
        }
    }

    private void setLid(BlockState state, boolean open) {
        if (level == null) {
            return;
        }
        level.setBlock(worldPosition, state.setValue(RiftBarrelBlock.OPEN, open), Block.UPDATE_ALL);
        if (open) {
            level.scheduleTick(worldPosition, state.getBlock(), LID_RECHECK_TICKS);
        }
    }

    private void playSound(BlockState state, SoundEvent sound) {
        if (level == null) {
            return;
        }
        Vec3 at = Vec3.atCenterOf(worldPosition).add(Vec3.atLowerCornerOf(
                state.getValue(RiftBarrelBlock.FACING).getNormal()).scale(0.5D));
        level.playSound(null, at.x, at.y, at.z, sound, SoundSource.BLOCKS,
                0.5F, level.random.nextFloat() * 0.1F + 0.9F);
    }

    // -----------------------------------------------------------------------------------------
    // The looted art
    // -----------------------------------------------------------------------------------------

    @Override
    public ModelData getModelData() {
        return ModelData.builder().with(LOOTED, lootedHere).build();
    }

    @Override
    public void onLoad() {
        super.onLoad();
        refreshLooted();
    }

    /**
     * Asks whether <em>this</em> client's player has emptied this barrel, and re-bakes the chunk if
     * the answer has changed. Guarded at every call site rather than inside, so a dedicated server
     * never resolves the client class it reaches for.
     */
    private void refreshLooted() {
        if (level == null || !level.isClientSide) {
            return;
        }
        boolean now = com.cyberday1.riftfall.client.LocalPlayerLoot.hasLooted(opened);
        if (now == lootedHere) {
            return;
        }
        lootedHere = now;
        requestModelDataUpdate();
        com.cyberday1.riftfall.client.LocalPlayerLoot.rebake(worldPosition);
    }

    // -----------------------------------------------------------------------------------------
    // Persistence
    // -----------------------------------------------------------------------------------------

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ListTag saved = new ListTag();
        shares.forEach((owner, share) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID(OWNER_KEY, owner);
            entry.put(ITEMS_KEY, share.createTag(registries));
            saved.add(entry);
        });
        tag.put(SHARES_KEY, saved);
        tag.put(OPENED_KEY, writeOpened());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        shares.clear();
        ListTag saved = tag.getList(SHARES_KEY, Tag.TAG_COMPOUND);
        for (int index = 0; index < saved.size(); index++) {
            CompoundTag entry = saved.getCompound(index);
            Share share = new Share();
            share.fromTag(entry.getList(ITEMS_KEY, Tag.TAG_COMPOUND), registries);
            shares.put(entry.getUUID(OWNER_KEY), share);
        }
        opened.clear();
        ListTag marks = tag.getList(OPENED_KEY, Tag.TAG_INT_ARRAY);
        for (Tag mark : marks) {
            opened.add(NbtUtils.loadUUID(mark));
        }
        refreshLooted();
    }

    private ListTag writeOpened() {
        ListTag marks = new ListTag();
        opened.forEach(owner -> marks.add(NbtUtils.createUUID(owner)));
        return marks;
    }

    /**
     * Everything a client is told: who has looted this, and nothing about what is in it. One
     * player's items are never on the wire except in their own open screen.
     */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.put(OPENED_KEY, writeOpened());
        return tag;
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /**
     * One player's chest inside one barrel.
     *
     * <p>
     * Inner rather than free-standing: {@link ContainerOpenersCounter#isOwnContainer} has to be able
     * to get from an open menu back to the barrel it belongs to, and {@link #barrel()} is that.
     */
    private final class Share extends SimpleContainer {

        private Share() {
            super(SIZE);
        }

        private RiftBarrelBlockEntity barrel() {
            return RiftBarrelBlockEntity.this;
        }

        @Override
        public void setChanged() {
            super.setChanged();
            RiftBarrelBlockEntity.this.setChanged();
        }

        @Override
        public boolean stillValid(Player player) {
            return Container.stillValidBlockEntity(RiftBarrelBlockEntity.this, player);
        }

        @Override
        public void startOpen(Player player) {
            if (!remove && !player.isSpectator()) {
                openersCounter.incrementOpeners(player, level, worldPosition, getBlockState());
            }
        }

        @Override
        public void stopOpen(Player player) {
            if (!remove && !player.isSpectator()) {
                openersCounter.decrementOpeners(player, level, worldPosition, getBlockState());
            }
        }
    }
}
