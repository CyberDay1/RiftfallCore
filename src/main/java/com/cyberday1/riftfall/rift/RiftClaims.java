package com.cyberday1.riftfall.rift;

import com.cyberday1.riftfall.RiftfallCore;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * What a player who died in a rift is owed, waiting to be fetched.
 *
 * <p>
 * Dying in a rift used to be total loss, and not because anybody decided it should be. A run ends
 * when its rift is empty, ending it deletes the level folder, and a death drop is lying on the floor
 * of that folder; the roster that is also the lockout then stops the player walking back in to look
 * for it. So the items were destroyed by a filesystem operation, at a moment nobody chose, with no
 * rule anywhere saying that was the cost of dying. This is that rule, made explicit and made
 * survivable: the items are taken out of the level before they can drop and held here.
 *
 * <h2>Handed back outside, never inside</h2>
 * Since 2026-11-17 the gear comes back on its own: at the end-of-rift recap, and whenever its owner
 * is alive outside a rift with room for it ({@link RiftAutoClaims}). {@code /riftfall claim} is the
 * fallback. All three go through {@link #handOver(ServerPlayer)}, so they cannot drift apart. The
 * price of a death is the wear and the lost loot, not the walk back.
 *
 * <h2>Why this cannot live anywhere near a rift</h2>
 * The three things it has to survive are the level being deleted, the server being restarted, and the
 * owner being offline for both. That rules out the rift's own storage, which is the thing being
 * deleted, and it rules out anything hanging off the player entity, which is not there to be written
 * to. The overworld's data storage is the same home {@link RiftReturns} picked for the same reason,
 * and for the same second reason: this is keyed by player, and a player has one of these however many
 * rifts they have died in.
 *
 * <p>
 * There is no cap on what one player may be owed. A second death appends rather than replaces,
 * because the alternative is a record that eats items to make room for items.
 */
public final class RiftClaims extends SavedData {

    private static final String FILE = RiftfallCore.MODID + "_claims";
    private static final String CLAIMS = "claims";
    private static final String PLAYER = "player";
    private static final String ITEMS = "items";

    private final Map<UUID, List<ItemStack>> held = new HashMap<>();

    // Package-private, like RiftReturns', so the round trip can be tested without a server.
    RiftClaims() {
    }

    public static RiftClaims of(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(RiftClaims::new, RiftClaims::load), FILE);
    }

    /**
     * Whether a claim may be handed over in this level.
     *
     * <p>
     * Not inside a rift instance, which is the whole of the rule. The things being held are the
     * things a run took off you, and giving them back while you are still in a run would let a player
     * die, respawn, walk into the next rift and re-arm from the last one -- and, with a party still
     * inside, be re-armed by a death that has not yet cost anybody anything. The claim is waiting
     * outside on purpose.
     *
     * <p>
     * Scoped to {@link RiftLevels#isRift} rather than to every level our generator builds, because
     * the argument is about runs and only an instance is one. A permanent dimension somebody's
     * datapack put on the rift generator has no run, no roster and no deletion, so there is nothing
     * there for a refusal to protect.
     *
     * <p>
     * Static and taking only the level key so that the physical recovery block this command is
     * standing in for can ask exactly the same question when it exists.
     */
    public static boolean canHandOverIn(ResourceKey<Level> level) {
        return !RiftLevels.isRift(level);
    }

    static RiftClaims load(CompoundTag tag, HolderLookup.Provider registries) {
        RiftClaims claims = new RiftClaims();
        ListTag list = tag.getList(CLAIMS, Tag.TAG_COMPOUND);
        for (int index = 0; index < list.size(); index++) {
            CompoundTag record = list.getCompound(index);
            ListTag items = record.getList(ITEMS, Tag.TAG_COMPOUND);
            List<ItemStack> stacks = new ArrayList<>(items.size());
            for (int slot = 0; slot < items.size(); slot++) {
                // Dropping a stack that will not parse is the least bad answer available. It happens
                // when a mod that was holding somebody's sword has been uninstalled, and the only
                // alternatives are throwing -- which would take the whole file, and everybody else's
                // claims with it -- or an air stack that reads as a bug at the moment it is claimed.
                ItemStack.parse(registries, items.getCompound(slot)).ifPresent(stacks::add);
            }
            if (!stacks.isEmpty()) {
                claims.held.put(record.getUUID(PLAYER), stacks);
            }
        }
        return claims;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        held.forEach((owner, stacks) -> {
            ListTag items = new ListTag();
            for (ItemStack stack : stacks) {
                items.add(stack.save(registries));
            }
            CompoundTag record = new CompoundTag();
            record.putUUID(PLAYER, owner);
            record.put(ITEMS, items);
            list.add(record);
        });
        tag.put(CLAIMS, list);
        return tag;
    }

    /**
     * Takes charge of what a player died holding.
     *
     * <p>
     * The stacks are taken as they are rather than copied, because the caller has already removed
     * them from the inventory they came out of and nothing else refers to them. Empty stacks are
     * dropped on the way in: {@link ItemStack#save} refuses to write one, so an empty allowed in here
     * would not fail until the next autosave and would then take the whole file with it.
     */
    public void hold(UUID owner, List<ItemStack> stacks) {
        List<ItemStack> real = stacks.stream().filter(stack -> !stack.isEmpty()).toList();
        if (real.isEmpty()) {
            return;
        }
        held.computeIfAbsent(owner, key -> new ArrayList<>()).addAll(real);
        setDirty();
    }

    /** How many stacks are being held for this player, which is zero for most of them. */
    public int count(UUID owner) {
        return held.getOrDefault(owner, List.of()).size();
    }

    /** What is being held, for anything that wants to look without taking. */
    public List<ItemStack> heldFor(UUID owner) {
        return Collections.unmodifiableList(held.getOrDefault(owner, List.of()));
    }

    public boolean isEmpty() {
        return held.isEmpty();
    }

    /**
     * Hands a claim over, as far as it will go, and returns how many stacks are still on file.
     *
     * <p>
     * {@code into} is offered each stack in turn and answers whether it took all of it, on
     * {@code Inventory#add}'s contract: a partial acceptance shrinks the stack and returns false, so
     * the remainder goes back on file and nothing is lost or duplicated. A full inventory hands over
     * what fits and keeps the rest.
     */
    public int handBack(UUID owner, Predicate<ItemStack> into) {
        return give(owner, into).left();
    }

    /** What one hand-back moved: copies of what came back, at the count that came back, and what is left. */
    public record Given(List<ItemStack> back, int left) {
        static final Given NOTHING = new Given(List.of(), 0);
    }

    /** {@link #handBack} that also says what came back, for the recap screen and the chat line. */
    public Given give(UUID owner, Predicate<ItemStack> into) {
        List<ItemStack> stacks = held.get(owner);
        if (stacks == null) {
            return Given.NOTHING;
        }
        List<ItemStack> back = new ArrayList<>();
        List<ItemStack> left = new ArrayList<>();
        for (ItemStack stack : stacks) {
            ItemStack before = stack.copy();
            boolean all = into.test(stack);
            int moved = all ? before.getCount() : before.getCount() - stack.getCount();
            if (moved > 0) {
                back.add(before.copyWithCount(moved));
            }
            if (!all && !stack.isEmpty()) {
                left.add(stack);
            }
        }
        if (left.isEmpty()) {
            held.remove(owner);
        } else {
            held.put(owner, left);
        }
        if (!back.isEmpty()) {
            setDirty();
        }
        return new Given(List.copyOf(back), left.size());
    }

    /** Why a hand-over gave nothing, or that it ran. */
    public enum Status { NOTHING_HELD, IN_A_RIFT, HANDED }

    /**
     * One hand-over: {@code waiting} stacks were on file before it, {@code back} came back,
     * {@code left} stacks are still held.
     */
    public record HandOver(Status status, int waiting, List<ItemStack> back, int left) {
        /** Stacks now wholly off the file, which is what the command has always counted. */
        public int recovered() {
            return waiting - left;
        }
    }

    /**
     * The claim itself, shared by the command, the recap screen, its button and the automatic retry.
     * Refused inside a rift; otherwise hands over what fits and keeps the rest.
     */
    public static HandOver handOver(ServerPlayer player) {
        Inventory inventory = player.getInventory();
        HandOver result = handOver(of(player.server), player.getUUID(),
                !canHandOverIn(player.level().dimension()), stack -> place(player, inventory, stack));
        if (!result.back().isEmpty()) {
            // Pushed now, as /give does, so the items appear with the line that says they came back.
            player.containerMenu.broadcastChanges();
        }
        return result;
    }

    /** {@link #handOver(ServerPlayer)} without a server, so every branch of it is plain JUnit. */
    static HandOver handOver(RiftClaims claims, UUID owner, boolean inRift, Predicate<ItemStack> into) {
        int waiting = claims.count(owner);
        if (waiting == 0) {
            return new HandOver(Status.NOTHING_HELD, 0, List.of(), 0);
        }
        if (inRift) {
            return new HandOver(Status.IN_A_RIFT, waiting, List.of(), waiting);
        }
        Given given = claims.give(owner, into);
        return new HandOver(Status.HANDED, waiting, given.back(), given.left());
    }

    /**
     * {@code Inventory#add}, except that a creative player's add never voids a remainder: with
     * infinite materials vanilla zeroes whatever did not fit and calls it taken. A whole free slot
     * always takes a whole held stack, so asking for one first keeps the stack on file instead.
     */
    private static boolean place(ServerPlayer player, Inventory inventory, ItemStack stack) {
        if (player.hasInfiniteMaterials() && inventory.getFreeSlot() < 0) {
            return false;
        }
        return inventory.add(stack);
    }

    /**
     * Whether anything held could go into this inventory now, asked before an automatic retry so a
     * full inventory costs a slot scan rather than a hand-over. Damaged stacks need a free slot.
     */
    public static boolean roomFor(Inventory inventory, List<ItemStack> held, boolean creative) {
        if (inventory.getFreeSlot() >= 0) {
            return true;
        }
        if (creative) {
            return false;
        }
        for (ItemStack stack : held) {
            if (!stack.isDamaged() && inventory.getSlotWithRemainingSpace(stack) >= 0) {
                return true;
            }
        }
        return false;
    }
}
