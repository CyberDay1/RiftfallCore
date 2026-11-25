package com.cyberday1.riftfall.rift;

import com.cyberday1.riftfall.RiftfallCore;
import com.cyberday1.riftfall.worldgen.barrel.BarrelRules;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;

/**
 * The runs still going when the world was last saved, and what each one's level was built from.
 *
 * <p>
 * Holds nothing of its own while the server runs: {@link #save} writes whatever {@link RiftRuns}
 * holds at that moment, and {@link #records} is read once at boot. So the file can never disagree
 * with the live runs, and the final save during a stop (after every player has been disconnected,
 * which is what pauses their runs) is the one a restart reads back.
 */
public final class RiftRunStore extends SavedData {

    private static final String FILE = RiftfallCore.MODID + "_runs";
    private static final String RUNS = "runs";
    private static final String RUN = "run";
    private static final String SEED = "seed";
    private static final String TIER = "levelTier";
    private static final String BARRELS = "barrels";
    private static final String LEVEL = "level";
    private static final String ATTUNED = "attuned";

    /**
     * One run, and the three facts its level is rebuilt from. {@code level} is false for a run whose
     * level was already gone or going when it was written; it is restored without one.
     */
    public record Record(RiftRun run, boolean level, long seed, int tier, BarrelRules barrels, RiftAttunement attunement) {
        public Record(RiftRun run, boolean level, long seed, int tier, BarrelRules barrels) {
            this(run, level, seed, tier, barrels, RiftAttunement.NONE);
        }
    }

    /** What the live runs are, asked at save time. */
    private final Supplier<List<Record>> live;

    private final List<Record> loaded;

    RiftRunStore(Supplier<List<Record>> live, List<Record> loaded) {
        this.live = live;
        this.loaded = loaded;
    }

    static RiftRunStore of(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(
                () -> new RiftRunStore(RiftRuns::records, List.of()),
                (tag, registries) -> new RiftRunStore(RiftRuns::records, read(tag, registries))), FILE);
    }

    /** What the file held when the server came up. */
    List<Record> records() {
        return loaded;
    }

    static List<Record> read(CompoundTag tag, HolderLookup.Provider registries) {
        List<Record> records = new ArrayList<>();
        ListTag list = tag.getList(RUNS, Tag.TAG_COMPOUND);
        for (int index = 0; index < list.size(); index++) {
            CompoundTag entry = list.getCompound(index);
            RiftRun run = RiftRun.load(entry.getCompound(RUN), registries);
            if (run != null) {
                // Autosaves can precede logout events. Wait for surviving occupants to reconnect;
                // loggedOut filters deaths/exits, so a finished run is not brought back to life.
                run.pauseForRestart();
                records.add(new Record(run, entry.getBoolean(LEVEL), entry.getLong(SEED), entry.getInt(TIER),
                        barrels(entry.getCompound(BARRELS)), RiftAttunement.load(entry.getCompound(ATTUNED))));
            }
        }
        return records;
    }

    static CompoundTag write(CompoundTag tag, Collection<Record> records, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Record record : records) {
            CompoundTag entry = new CompoundTag();
            entry.put(RUN, record.run().save(registries));
            entry.putBoolean(LEVEL, record.level());
            entry.putLong(SEED, record.seed());
            entry.putInt(TIER, record.tier());
            entry.put(BARRELS, barrels(record.barrels()));
            if (!record.attunement().none()) entry.put(ATTUNED, record.attunement().save());
            list.add(entry);
        }
        tag.put(RUNS, list);
        return tag;
    }

    /** Always worth writing while a run is live: its clock and its kills move every tick. */
    @Override
    public boolean isDirty() {
        return super.isDirty() || RiftRuns.anyLive();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        return write(tag, live.get(), registries);
    }

    private static CompoundTag barrels(BarrelRules rules) {
        CompoundTag tag = new CompoundTag();
        tag.putDouble("chance", rules.chance());
        tag.putInt("shabby", rules.shabby());
        tag.putInt("wood", rules.wood());
        tag.putInt("iron", rules.iron());
        tag.putInt("gold", rules.gold());
        return tag;
    }

    private static BarrelRules barrels(CompoundTag tag) {
        return new BarrelRules(tag.getDouble("chance"), tag.getInt("shabby"), tag.getInt("wood"),
                tag.getInt("iron"), tag.getInt("gold"));
    }
}
