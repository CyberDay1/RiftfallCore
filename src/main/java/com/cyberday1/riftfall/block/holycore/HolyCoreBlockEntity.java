package com.cyberday1.riftfall.block.holycore;

import com.cyberday1.riftfall.home.ShatteredHomeLevels;
import com.cyberday1.riftfall.home.ShatteredHomeProgress;
import com.cyberday1.riftfall.home.ShatteredHomeTiles;
import com.cyberday1.riftfall.home.ShatteredHomeLayout;
import com.cyberday1.riftfall.home.ShatteredHomeExpansion;
import com.cyberday1.riftfall.init.RiftfallBlockEntities;
import com.cyberday1.riftfall.init.RiftfallItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** One server controller for all nine pedestal cells. The client only animates synced state. */
public class HolyCoreBlockEntity extends BlockEntity {
    public static final int MAX_PHASE = ShatteredHomeProgress.MAX_PHASE;
    public static final int RELICS_PER_PHASE = ShatteredHomeProgress.RELICS_PER_PHASE;
    public static final int REPAIR_TICKS = 50;
    private int relicsInserted;
    private boolean initialized;
    private int partMask;
    private double fromPhase;
    private long changedAt = Long.MIN_VALUE;
    private HolyCoreStructure.Check condition = new HolyCoreStructure.Check(
            HolyCoreStructure.Problem.UNLOADED, BlockPos.ZERO, 0);

    public HolyCoreBlockEntity(BlockPos pos, BlockState state) { super(RiftfallBlockEntities.HOLY_CORE.get(), pos, state); }
    public int relicsInserted() { return relicsInserted; }
    public int phase() { return ShatteredHomeProgress.phase(relicsInserted); }
    public int partMask() { return partMask; }
    public boolean formed() { return getBlockState().getValue(HolyCoreBlock.FORMED); }
    public double visualPhase(double now) {
        if (changedAt == Long.MIN_VALUE) return phase();
        double t = Math.clamp((now - changedAt) / REPAIR_TICKS, 0.0, 1.0);
        t = t * t * (3.0 - 2.0 * t);
        return fromPhase + (phase() - fromPhase) * t;
    }
    public static void tick(Level level, BlockPos pos, BlockState state, HolyCoreBlockEntity core) {
        if (!(level instanceof ServerLevel server)) return;
        if (!core.initialized) {
            if (!HolyCoreStructure.initialize(server, pos)) return;
            core.initialized = true;
            core.setChanged();
            core.refresh(server);
        } else if (level.getGameTime() % 20 == 0) core.refresh(server);
    }
    public HolyCoreStructure.Check refresh(ServerLevel server) {
        if (ShatteredHomeLevels.isShatteredHome(server.dimension())) {
            ShatteredHomeProgress progress = ShatteredHomeProgress.of(server);
            if (progress.shards() != relicsInserted) setCount(progress.shards(), server.getGameTime(), false);
            progress.applyBorder(server);
        }
        condition = HolyCoreStructure.check(server, worldPosition);
        boolean changed = partMask != condition.parts();
        partMask = condition.parts();
        BlockState state = getBlockState();
        BlockState next = state.setValue(HolyCoreBlock.PHASE, phase()).setValue(HolyCoreBlock.FORMED, condition.complete());
        if (next != state) server.setBlock(worldPosition, next, Block.UPDATE_ALL);
        if (changed) sync();
        return condition;
    }
    /** Refusals never spend items; all updates happen on the server. */
    public boolean offer(ServerLevel server, Player player, ItemStack stack) {
        if (!stack.is(RiftfallItems.SHATTERED_WORLD_SHARD.get())) return false;
        if (!ShatteredHomeLevels.isShatteredHome(server.dimension())) {
            notice(player, "outside_home"); return false;
        }
        if (!refresh(server).complete()) { describe(player); return false; }
        ShatteredHomeProgress progress = ShatteredHomeProgress.of(server);
        int oldPhase = phase();
        if (!progress.canInsertShard()) { notice(player, "limit"); return false; }
        if (!ShatteredHomeLayout.supports(progress.layoutVersion())) {
            notice(player, "layout_unsupported");
            return false;
        }
        int targetTiles = 1 + (progress.shards() + 1) / ShatteredHomeProgress.SHARDS_PER_TILE;
        boolean tileUnlocked = progress.unlockedTiles().size() < targetTiles;
        ShatteredHomeTiles.GridPos landed = null;
        if (tileUnlocked) {
            var planned = ShatteredHomeExpansion.next(progress.layoutVersion(),progress.unlockedTiles(),
                    ShatteredHomeExpansion.homeSeed(server.dimension().location()));
            if (planned.isEmpty()) {
                notice(player, "section_unavailable");
                return false;
            }
            var next = planned.get().position();
            if (!ShatteredHomeTiles.placeTile(server, next, planned.get().template())) {
                notice(player, "section_failed");
                return false;
            }
            progress.recordPlacedTile(next);
            landed = next;
        }
        if (!progress.insertShard()) { notice(player, "limit"); return false; }
        // The shard's world goes into the feeder's atlas; its component goes with the consumed shard.
        var origin = stack.get(com.cyberday1.riftfall.init.RiftfallDataComponents.SHARD_ORIGIN.get());
        if (origin != null) {
            var record = com.cyberday1.riftfall.home.npc.depth.ResidentProgress.of(server.getServer());
            record.player(player.getUUID()).fed(origin);
            record.setDirty();
        }
        stack.consume(1, player);
        setCount(progress.shards(), server.getGameTime(), true);
        refresh(server);
        if (tileUnlocked) {
            server.playSound(null, worldPosition, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.BLOCKS, 1.0F, 0.7F);
            server.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, worldPosition.getX() + .5,
                    worldPosition.getY() + 1.5, worldPosition.getZ() + .5, 30, .8, .5, .8, .05);
        } else {
            server.playSound(null, worldPosition, oldPhase != phase() ? SoundEvents.UI_TOAST_CHALLENGE_COMPLETE
                    : SoundEvents.AMETHYST_BLOCK_HIT, SoundSource.BLOCKS, 0.8F, 0.9F);
            server.sendParticles(ParticleTypes.END_ROD, worldPosition.getX() + .5,
                    worldPosition.getY() + 1.0, worldPosition.getZ() + .5, 12, .6, .3, .6, .025);
        }
        if (landed != null) com.cyberday1.riftfall.lore.CoreTileBeats.placed(server, player, progress.layoutVersion(),
                landed, progress.unlockedTiles().size());
        com.cyberday1.riftfall.lore.CoreHandInLines.say(server, player, phase() + 1, relicsInserted, tileUnlocked);
        com.cyberday1.riftfall.story.StoryBridge.offered(server, player, progress.unlockedTiles().size(), progress.shards());
        return true;
    }
    public void inspectOrRepair(ServerLevel server, Player player) {
        if (HolyCoreStructure.repair(server, worldPosition)) {
            server.playSound(null, worldPosition, SoundEvents.STONE_PLACE, SoundSource.BLOCKS, .8F, .9F);
        }
        refresh(server);
        describe(player);
        com.cyberday1.riftfall.story.StoryBridge.coreTouched(server, player);
    }
    private void setCount(int count, long now, boolean animate) {
        double previous = visualPhase(now);
        int oldPhase = phase();
        relicsInserted = count;
        if (phase() != oldPhase) {
            fromPhase = animate ? previous : phase();
            changedAt = animate ? now : Long.MIN_VALUE;
        }
        sync();
    }
    private void describe(Player player) {
        if (!condition.complete()) {
            BlockPos pos = condition.position();
            player.displayClientMessage(Component.translatable("message.riftfall.core." +
                    condition.problem().name().toLowerCase(java.util.Locale.ROOT), pos.getX(), pos.getY(), pos.getZ()), true);
        } else {
            player.displayClientMessage(Component.translatable("message.riftfall.core.progress", phase() + 1,
                    relicsInserted, ShatteredHomeProgress.of((ServerLevel) level).unlockedTiles().size()), true);
        }
    }
    private static void notice(Player player, String key) {
        player.displayClientMessage(Component.translatable("message.riftfall.core." + key), true);
    }
    private void sync() {
        setChanged();
        if (level instanceof ServerLevel) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }
    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("relics_inserted", relicsInserted);
        tag.putBoolean("initialized", initialized);
        tag.putInt("parts", partMask);
        tag.putDouble("from_phase", fromPhase);
        tag.putLong("changed_at", changedAt);
    }
    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        relicsInserted = Math.max(0, tag.getInt("relics_inserted"));
        initialized = tag.getBoolean("initialized");
        partMask = tag.getInt("parts") & HolyCoreStructure.ALL_PARTS;
        fromPhase = Math.clamp(tag.getDouble("from_phase"), 0.0, MAX_PHASE);
        changedAt = tag.contains("changed_at") ? tag.getLong("changed_at") : Long.MIN_VALUE;
    }
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) { return saveWithoutMetadata(registries); }
    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
}
