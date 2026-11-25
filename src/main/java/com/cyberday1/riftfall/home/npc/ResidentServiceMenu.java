package com.cyberday1.riftfall.home.npc;

import com.cyberday1.riftfall.block.marker.NpcRole;
import com.mojang.logging.LogUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;

/**
 * A resident service with real input slots. The server quotes on every sync; the confirm id is
 * {@link #CONFIRM} + a revision that moves whenever an input, the selection or the quote's terms change, so a
 * stale or replayed confirm does nothing. Access is rechecked every tick and click; closing returns the inputs.
 * Services supply a pure {@link #quote} and a {@link #commit} that runs only for the quote the player saw.
 */
public abstract class ResidentServiceMenu<Q extends ResidentServiceMenu.Quote> extends AbstractContainerMenu {
    public static final int SELECTIONS = 50, BACK = 50, CONFIRM = 100;

    /** {@link #terms} must be value-comparable: equal terms mean the same deal. */
    public interface Quote {
        boolean ready();
        Object terms();
    }
    public record Committed(boolean success, Component message) {}
    /** Input slot positions, then the top-left of the player's 3x9 grid and the hotbar row. */
    public record Layout(int[][] inputs, int inventoryX, int inventoryY, int hotbarY) {}

    public final SimpleContainer inputs;
    protected final Inventory inventory;
    protected final UUID resident;
    private final NpcRole role;
    private final int inputCount;
    private final DataSlot selection = DataSlot.standalone();
    private final DataSlot revision = DataSlot.standalone();
    private Q shown;
    private int shownSelection = -1;
    private final ItemStack[] shownInputs, shownCopies;

    protected ResidentServiceMenu(MenuType<?> type, int id, Inventory inventory, UUID resident, NpcRole role, Layout layout) {
        super(type, id);
        this.inventory = inventory;
        this.resident = resident;
        this.role = role;
        this.inputCount = layout.inputs().length;
        this.inputs = new SimpleContainer(inputCount);
        this.shownInputs = new ItemStack[inputCount];
        this.shownCopies = new ItemStack[inputCount];
        addDataSlot(selection);
        addDataSlot(revision);
        for (int i = 0; i < inputCount; i++) {
            final int input = i;
            addSlot(new Slot(inputs, i, layout.inputs()[i][0], layout.inputs()[i][1]) {
                @Override public boolean mayPlace(ItemStack stack) { return accepts(input, stack); }
                @Override public int getMaxStackSize() { return maxStack(input); }
            });
        }
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++)
            addSlot(new Slot(inventory, col + row * 9 + 9, layout.inventoryX() + col * 18, layout.inventoryY() + row * 18));
        for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col, layout.inventoryX() + col * 18, layout.hotbarY()));
    }

    /** The page a landing tile opens on; call from the constructor only. */
    protected void startAt(int page) { selection.set(page); }

    protected abstract boolean accepts(int input, ItemStack stack);
    protected int maxStack(int input) { return net.minecraft.world.item.Item.ABSOLUTE_MAX_STACK_SIZE; }
    /** Pure: reads slots and inventory only. Runs on both sides; the client's copy is display only. */
    protected abstract Q quote(int selection);
    protected abstract boolean selectable(int selection);
    /** Only called with the current, ready quote the player confirmed. Verify space, consume, produce. */
    protected abstract Committed commit(ServerPlayer player, Q quote);

    public NpcRole role() { return role; }
    public int selection() { return selection.get(); }
    public Q preview() { return quote(selection.get()); }
    public int confirmButton() { return CONFIRM + revision.get(); }
    public int inputCount() { return inputCount; }

    @Override public void broadcastChanges() {
        if (inventory.player instanceof ServerPlayer) refresh();
        super.broadcastChanges();
    }

    private void refresh() {
        var now = quote(selection.get());
        boolean changed = shown == null || shownSelection != selection.get() || !now.terms().equals(shown.terms());
        for (int i = 0; i < inputCount && !changed; i++) {
            var stack = inputs.getItem(i);
            changed = stack != shownInputs[i] || !ItemStack.matches(stack, shownCopies[i]);
        }
        if (!changed) return;
        shown = now;
        shownSelection = selection.get();
        for (int i = 0; i < inputCount; i++) { shownInputs[i] = inputs.getItem(i); shownCopies[i] = shownInputs[i].copy(); }
        revision.set(revision.get() % 30000 + 1);
    }

    @Override public boolean clickMenuButton(Player player, int button) {
        if (!(player instanceof ServerPlayer server) || player.containerMenu != this || !stillValid(player)) return false;
        if (button == BACK) { ResidentMenus.openLanding(server, resident); return true; }
        if (button >= 0 && button < SELECTIONS) {
            if (!selectable(button)) return false;
            selection.set(button);
            broadcastChanges();
            return true;
        }
        refresh(); // Anything changed since the synced quote has already moved the confirm id.
        if (button != confirmButton() || shown == null || !shown.ready()) return false;
        var quote = shown;
        var result = commit(server, quote);
        shown = null; // One confirm per quote, whatever the outcome.
        broadcastChanges();
        if (result.success()) {
            LogUtils.getLogger().info("Resident service: {} {} for {}: {}", role.id(),
                    getClass().getSimpleName(), server.getUUID(), quote.terms());
            // XP is a side effect of a finished commit; it must never undo or block one.
            try { com.cyberday1.riftfall.home.npc.depth.ResidentDepth.service(server, role); }
            catch (RuntimeException failed) { LogUtils.getLogger().error("Resident XP failed for {}", role.id(), failed); }
        }
        if (result.message() != null) player.displayClientMessage(result.message(), true);
        return result.success();
    }

    @Override public boolean stillValid(Player player) { return ResidentMenus.canUse(player, resident, role); }

    @Override public void removed(Player player) {
        super.removed(player);
        if (!player.level().isClientSide) clearContainer(player, inputs);
    }

    @Override public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        var slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        var stack = slot.getItem();
        var original = stack.copy();
        int hotbar = inputCount + 27, end = inputCount + 36;
        if (index < inputCount) {
            if (!moveItemStackTo(stack, inputCount, end, true)) return ItemStack.EMPTY;
        } else {
            boolean wanted = false, moved = false;
            for (int i = 0; i < inputCount && !moved; i++) {
                if (!accepts(i, stack)) continue;
                wanted = true;
                moved = moveItemStackTo(stack, i, i + 1, false);
            }
            if (wanted && !moved) return ItemStack.EMPTY;
            if (!wanted && (index < hotbar ? !moveItemStackTo(stack, hotbar, end, false)
                    : !moveItemStackTo(stack, inputCount, hotbar, false))) return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY); else slot.setChanged();
        slot.onTake(player, stack);
        return original;
    }
}
