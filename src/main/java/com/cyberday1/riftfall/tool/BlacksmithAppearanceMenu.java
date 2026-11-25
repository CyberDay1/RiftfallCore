package com.cyberday1.riftfall.tool;

import com.cyberday1.riftfall.init.RiftfallMenus;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import java.util.List;
import java.util.UUID;

/** Explicit target slot, server discovery flags, and a revision-bound free cosmetic swap. */
public final class BlacksmithAppearanceMenu extends AbstractContainerMenu {
    public static final int BACK = 1, PREVIOUS = 2, NEXT = 3, SELECT = 100, APPLY = 1000, PER_PAGE = 6;
    public final SimpleContainer input = new SimpleContainer(1);
    private final Inventory inventory;
    private final UUID smith;
    private final DataSlot page = DataSlot.standalone(), selected = DataSlot.standalone(), revision = DataSlot.standalone();
    private final DataSlot receipt = DataSlot.standalone();
    private final DataSlot[] access = new DataSlot[ToolAppearances.ALL.size()];
    private ItemStack snapshot = ItemStack.EMPTY;
    private ItemStack original = ItemStack.EMPTY;
    private int lastSelected = -1;

    public BlacksmithAppearanceMenu(int id, Inventory inventory) { this(id, inventory, null); }
    public BlacksmithAppearanceMenu(int id, Inventory inventory, UUID smith) {
        super(RiftfallMenus.BLACKSMITH_APPEARANCE.get(), id);
        this.inventory = inventory; this.smith = smith;
        addDataSlot(page); addDataSlot(selected); addDataSlot(revision); addDataSlot(receipt);
        for (int i = 0; i < access.length; i++) { access[i] = DataSlot.standalone(); addDataSlot(access[i]); }
        addSlot(new Slot(input, 0, 30, 30) {
            @Override public boolean mayPlace(ItemStack stack) { return LootingToolItem.isTool(stack); }
            @Override public int getMaxStackSize() { return 1; }
        });
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++)
            addSlot(new Slot(inventory, col + row * 9 + 9, 74 + col * 18, 154 + row * 18));
        for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col, 74 + col * 18, 212));
    }
    public int page() { return page.get(); }
    public int pageCount() { return Math.max(1, (ToolAppearances.available().size() + PER_PAGE - 1) / PER_PAGE); }
    public List<ToolAppearances.Skin> pageSkins() {
        return ToolAppearances.available().stream().skip((long) page() * PER_PAGE).limit(PER_PAGE).toList();
    }
    public ToolAppearances.Skin selected() {
        return ToolAppearances.ALL.get(Math.clamp(selected.get(), 0, ToolAppearances.ALL.size() - 1));
    }
    public boolean unlocked(ToolAppearances.Skin skin) {
        int index = ToolAppearances.ALL.indexOf(skin);
        return index >= 0 && access[index].get() == 2;
    }
    public boolean canApply() {
        var target = input.getItem(0);
        return LootingToolItem.isTool(target) && target.getCount() == 1 && pageSkins().contains(selected())
                && unlocked(selected()) && !selected().equals(LootingToolItem.appearance(target));
    }
    public boolean applied() { return receipt.get() == 1; }
    public int applyButton() { return APPLY + revision.get(); }
    private void refresh(ServerPlayer player) {
        ToolAppearanceUnlocks.scan(player);
        boolean changed = lastSelected != selected.get() || original != input.getItem(0)
                || !ItemStack.matches(snapshot, input.getItem(0));
        for (int i = 0; i < access.length; i++) {
            var skin = ToolAppearances.ALL.get(i);
            int value = !ToolAppearances.available(skin) ? 0 : ToolAppearanceUnlocks.unlocked(player, skin) ? 2 : 1;
            changed |= value != access[i].get(); access[i].set(value);
        }
        if (changed) {
            original = input.getItem(0); snapshot = original.copy(); lastSelected = selected.get();
            revision.set(revision.get() % 30000 + 1); receipt.set(0);
        }
    }
    @Override public void broadcastChanges() {
        if (inventory.player instanceof ServerPlayer server) refresh(server);
        super.broadcastChanges();
    }
    @Override public boolean clickMenuButton(Player player, int button) {
        if (!(player instanceof ServerPlayer server) || player.containerMenu != this || !stillValid(player)) return false;
        if (button == BACK) { BlacksmithMenus.open(server, smith, 0); return true; }
        if (button == PREVIOUS || button == NEXT) {
            int next = page() + (button == NEXT ? 1 : -1);
            if (next < 0 || next >= pageCount()) return false;
            page.set(next); selected.set(ToolAppearances.ALL.indexOf(pageSkins().getFirst()));
            broadcastChanges(); return true;
        }
        if (button >= SELECT && button < SELECT + ToolAppearances.ALL.size()) {
            var skin = ToolAppearances.ALL.get(button - SELECT);
            if (!pageSkins().contains(skin)) return false;
            selected.set(button - SELECT); broadcastChanges(); return true;
        }
        refresh(server); // Reject a quote whose target, selection or discovery state changed.
        if (button != applyButton() || !canApply()) return false;
        if (!LootingToolItem.applyAppearance(input.getItem(0), selected().item().toString())) return false;
        input.setChanged(); refresh(server); receipt.set(1); broadcastChanges();
        return true;
    }
    @Override public boolean stillValid(Player player) { return BlacksmithMenus.canUse(player, smith); }
    @Override public void removed(Player player) {
        super.removed(player);
        if (!player.level().isClientSide) clearContainer(player, input);
    }
    @Override public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size() || !slots.get(index).hasItem()) return ItemStack.EMPTY;
        var slot = slots.get(index); var stack = slot.getItem(); var before = stack.copy();
        if (index == 0) { if (!moveItemStackTo(stack, 1, 37, true)) return ItemStack.EMPTY; }
        else if (LootingToolItem.isTool(stack)) { if (!moveItemStackTo(stack, 0, 1, false)) return ItemStack.EMPTY; }
        else if (index < 28 ? !moveItemStackTo(stack, 28, 37, false) : !moveItemStackTo(stack, 1, 28, false)) return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY); else slot.setChanged();
        slot.onTake(player, stack); return before;
    }
}
