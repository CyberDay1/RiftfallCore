package com.cyberday1.riftfall.rift;

import com.cyberday1.riftfall.RiftfallCore;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A defeat that has not been charged yet, because the player it belongs to was offline when their run
 * wiped.
 *
 * <p>
 * An offline player's inventory is a file rather than a container, so {@link RiftRuns} cannot take
 * anything at the moment the run ends. This is the note it leaves instead: the toll is charged at their
 * next login, by the path a death runs.
 *
 * <p>
 * It carries the two things that path needs and cannot ask for later, both of which die with the run
 * that is ending: which rift the marks on their gear name, and what they walked in carrying. Lives on
 * the overworld's data storage for the reason {@link RiftClaims} does, and has to outlive the same
 * three things: the level, the process, and the owner being away for both.
 */
public final class RiftTolls extends SavedData {

    private static final String FILE = RiftfallCore.MODID + "_tolls";
    private static final String TOLLS = "tolls";
    private static final String PLAYER = "player";
    private static final String RIFT = "rift";
    private static final String KIT = "kit";

    /**
     * One unpaid defeat: the rift that took it, and the kit that went into that rift.
     *
     * @param rift what {@link RiftLoot#carriedInto} matches the marks against
     * @param kit  the allowance {@link RiftLoot#split} counts stackables against
     */
    public record Toll(ResourceLocation rift, List<ItemStack> kit) {

        public Toll {
            kit = Collections.unmodifiableList(kit);
        }
    }

    private final Map<UUID, Toll> owed = new HashMap<>();

    // Package-private, like RiftReturns', so the round trip can be tested without a server.
    RiftTolls() {
    }

    public static RiftTolls of(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(RiftTolls::new, RiftTolls::load), FILE);
    }

    static RiftTolls load(CompoundTag tag, HolderLookup.Provider registries) {
        RiftTolls tolls = new RiftTolls();
        ListTag list = tag.getList(TOLLS, Tag.TAG_COMPOUND);
        for (int index = 0; index < list.size(); index++) {
            CompoundTag record = list.getCompound(index);
            ResourceLocation rift = ResourceLocation.tryParse(record.getString(RIFT));
            if (rift == null) {
                // The rift is what the marks are matched against, so a toll that has lost it cannot be
                // charged fairly: every non-stackable would read as loot. Dropped rather than charged.
                continue;
            }
            ListTag items = record.getList(KIT, Tag.TAG_COMPOUND);
            List<ItemStack> kit = new ArrayList<>(items.size());
            for (int slot = 0; slot < items.size(); slot++) {
                // A stack that will not parse is dropped, exactly as RiftClaims drops one: the item is
                // gone from the kit either way, and throwing would take everybody else's toll with it.
                ItemStack.parse(registries, items.getCompound(slot)).ifPresent(kit::add);
            }
            tolls.owed.put(record.getUUID(PLAYER), new Toll(rift, kit));
        }
        return tolls;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        owed.forEach((player, toll) -> {
            ListTag items = new ListTag();
            for (ItemStack stack : toll.kit()) {
                items.add(stack.save(registries));
            }
            CompoundTag record = new CompoundTag();
            record.putUUID(PLAYER, player);
            record.putString(RIFT, toll.rift().toString());
            record.put(KIT, items);
            list.add(record);
        });
        tag.put(TOLLS, list);
        return tag;
    }

    /**
     * Books a defeat against a player who was not there to pay for it.
     *
     * <p>
     * An existing toll wins, on the argument {@link RiftReturns#owe} makes: a player cannot be inside
     * two rifts, so a second one means the first has not been charged yet and the older kit is the one
     * that describes what they are actually carrying.
     *
     * <p>
     * The stacks are copied, and empty ones dropped: {@link ItemStack#save} refuses to write an empty,
     * which would not fail until the next autosave and would then take the whole file.
     */
    public void owe(UUID player, ResourceLocation rift, List<ItemStack> kit) {
        List<ItemStack> real = kit.stream().filter(stack -> !stack.isEmpty()).map(ItemStack::copy).toList();
        if (owed.putIfAbsent(player, new Toll(rift, real)) == null) {
            setDirty();
        }
    }

    /** What this player owes, or null for everybody who does not owe anything. */
    @Nullable
    public Toll owed(UUID player) {
        return owed.get(player);
    }

    /** Strikes a toll off, whether it was charged or written off. */
    public void paid(UUID player) {
        if (owed.remove(player) != null) {
            setDirty();
        }
    }

    public boolean isEmpty() {
        return owed.isEmpty();
    }
}
