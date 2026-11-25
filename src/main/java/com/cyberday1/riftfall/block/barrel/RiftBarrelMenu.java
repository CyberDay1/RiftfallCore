package com.cyberday1.riftfall.block.barrel;

import com.cyberday1.riftfall.init.RiftfallMenus;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * A nine-row barrel shown six rows at a time.
 *
 * <p>
 * {@code ChestMenu} stops at {@code GENERIC_9x6}, so a barrel that can hand out more than 54 stacks
 * needs a menu of its own. Everything here is vanilla's arithmetic with the row count left as a
 * variable: the container grid sits at {@code 8 + col*18, 18 + row*18}, the player's inventory drops
 * by {@code (rows - 4) * 18}, and the screen is {@code 114 + rows * 18} tall.
 *
 * <h2>Why the grid scrolls</h2>
 * Nine rows drawn at once is 276 tall and {@code Window#calculateScale} only promises a
 * 320&times;240 virtual screen, so at 720p on auto GUI scale {@code topPos} went negative and the
 * panel reached into the hotbar. The barrel keeps all nine rows &mdash; that is what stops a draw
 * being silently discarded, see {@link BarrelDraws} &mdash; and shows {@link #VISIBLE_ROWS} of them,
 * which is a vanilla double chest and fits the guarantee with room to spare.
 *
 * <h2>⛔ The slots cannot move, so the contents do</h2>
 * {@code Slot}'s x, y and container index are all final, and {@code getItem} reads the index
 * directly rather than through {@link Slot#getSlotIndex()}, so there is no override that makes one
 * slot point somewhere else. The 54 visible slots are therefore fixed wells over {@link View}, a
 * container that adds {@code scrollRow * COLUMNS} to every index it is asked for. Scrolling moves
 * the view, never the slots.
 *
 * <p>
 * The offset has to exist on both sides: the client scrolls, but the server resolves the click. It
 * travels as a menu button, the same channel the loom and the stonecutter use, so no packet is
 * added. A button id outside 0..{@link #MAX_SCROLL} is refused rather than clamped &mdash; clamping
 * a bad id silently hands the sender a different slot than the one they asked for.
 */
public class RiftBarrelMenu extends AbstractContainerMenu {

    /** Nine by nine. The one factorisation of 81 that is a sensible grid. */
    public static final int ROWS = 9;

    public static final int COLUMNS = 9;

    public static final int SIZE = ROWS * COLUMNS;

    /** How many of the nine rows are on screen. Six is a vanilla double chest. */
    public static final int VISIBLE_ROWS = 6;

    public static final int VISIBLE = VISIBLE_ROWS * COLUMNS;

    /** The topmost row index the grid can be scrolled to. */
    public static final int MAX_SCROLL = ROWS - VISIBLE_ROWS;

    /** Vanilla's chest geometry, kept here because the screen has to draw the same wells. */
    public static final int LEFT = 8;
    public static final int TOP = 18;
    public static final int SLOT = 18;

    /** How far the player's inventory moves down relative to a four-row chest. */
    private static final int DROP = (VISIBLE_ROWS - 4) * SLOT;

    public static final int PLAYER_TOP = 103 + DROP;
    public static final int HOTBAR_TOP = 161 + DROP;

    /**
     * Wider than a chest's 176, because a chest has no gutter to put a bar in: its grid runs from
     * x=8 to x=168 and leaves 8 either side, which is narrower than the bar. The extra 18 is the
     * gutter, so no well moves and the commissioned texture still matches every slot coordinate.
     */
    public static final int WIDTH = 194;
    public static final int HEIGHT = 114 + VISIBLE_ROWS * SLOT;

    private final Container barrel;
    private final View view;

    /** Client side: the server sends the contents, so the container is a stand-in of the right size. */
    public RiftBarrelMenu(int containerId, Inventory inventory) {
        this(containerId, inventory, new SimpleContainer(SIZE));
    }

    public RiftBarrelMenu(int containerId, Inventory inventory, Container barrel) {
        super(RiftfallMenus.RIFT_BARREL.get(), containerId);
        checkContainerSize(barrel, SIZE);
        this.barrel = barrel;
        this.view = new View(barrel);
        barrel.startOpen(inventory.player);

        for (int row = 0; row < VISIBLE_ROWS; row++) {
            for (int column = 0; column < COLUMNS; column++) {
                addSlot(new Slot(view, column + row * COLUMNS,
                        LEFT + column * SLOT, TOP + row * SLOT));
            }
        }
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(inventory, column + row * 9 + 9,
                        LEFT + column * SLOT, PLAYER_TOP + row * SLOT));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column, LEFT + column * SLOT, HOTBAR_TOP));
        }
    }

    public Container barrel() {
        return barrel;
    }

    /** Which of the nine rows is drawn at the top of the grid. */
    public int scrollRow() {
        return view.offsetRow;
    }

    /**
     * Move the view. Returns whether it actually moved, so the screen can skip a needless packet.
     *
     * <p>
     * Out-of-range rows are clamped here because this is the local, client-driven path where a
     * mouse wheel legitimately runs past the end. The network path does not clamp.
     */
    public boolean scrollTo(int row) {
        int clamped = Math.clamp(row, 0, MAX_SCROLL);
        if (clamped == view.offsetRow) {
            return false;
        }
        view.offsetRow = clamped;
        return true;
    }

    /** The sync channel. Refuses an id it did not offer rather than clamping it into range. */
    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id < 0 || id > MAX_SCROLL) {
            return false;
        }
        view.offsetRow = id;
        broadcastChanges();
        return true;
    }

    @Override
    public boolean stillValid(Player player) {
        return barrel.stillValid(player);
    }

    /**
     * Vanilla's chest routing, except that the barrel half reaches all nine rows.
     *
     * <p>
     * ⚠ {@code moveItemStackTo} walks {@link #slots}, and only 54 of the barrel's 81 are in there,
     * so routing a player stack through it would fill the visible rows and then refuse &mdash; with
     * 27 empty slots scrolled off screen. The barrel half therefore works the backing container
     * directly. The player half is ordinary, because every player slot is always present.
     */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack inSlot = slot.getItem();
        ItemStack original = inSlot.copy();
        if (index < VISIBLE) {
            if (!moveItemStackTo(inSlot, VISIBLE, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveIntoBarrel(inSlot)) {
            return ItemStack.EMPTY;
        }
        if (inSlot.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return original;
    }

    /**
     * Vanilla's two-pass merge, over the container rather than over the slot list.
     *
     * <p>
     * Pass one tops up stacks that already match, pass two takes empty slots. Same order and same
     * stack-size rules as {@code moveItemStackTo}; the only difference is what it walks.
     */
    private boolean moveIntoBarrel(ItemStack moving) {
        boolean moved = false;
        if (moving.isStackable()) {
            for (int index = 0; index < SIZE && !moving.isEmpty(); index++) {
                ItemStack present = barrel.getItem(index);
                if (present.isEmpty() || !ItemStack.isSameItemSameComponents(present, moving)) {
                    continue;
                }
                int limit = Math.min(barrel.getMaxStackSize(), present.getMaxStackSize());
                int room = limit - present.getCount();
                if (room <= 0) {
                    continue;
                }
                int taken = Math.min(room, moving.getCount());
                present.grow(taken);
                moving.shrink(taken);
                barrel.setChanged();
                moved = true;
            }
        }
        for (int index = 0; index < SIZE && !moving.isEmpty(); index++) {
            if (!barrel.getItem(index).isEmpty()) {
                continue;
            }
            int limit = Math.min(barrel.getMaxStackSize(), moving.getMaxStackSize());
            barrel.setItem(index, moving.split(Math.min(limit, moving.getCount())));
            barrel.setChanged();
            moved = true;
        }
        return moved;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        barrel.stopOpen(player);
    }

    /**
     * The barrel's 81 slots seen 54 at a time.
     *
     * <p>
     * Every call is forwarded to the backing container with {@code offsetRow * COLUMNS} added, so a
     * fixed well shows a different stack after a scroll without anything about the well changing.
     * {@code getContainerSize} reports the window rather than the barrel, because that is the size
     * the 54 slots were built against.
     */
    private static final class View implements Container {

        private final Container backing;
        private int offsetRow;

        private View(Container backing) {
            this.backing = backing;
        }

        private int resolve(int index) {
            return index + offsetRow * COLUMNS;
        }

        @Override
        public int getContainerSize() {
            return VISIBLE;
        }

        @Override
        public boolean isEmpty() {
            for (int index = 0; index < VISIBLE; index++) {
                if (!getItem(index).isEmpty()) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public ItemStack getItem(int index) {
            return backing.getItem(resolve(index));
        }

        @Override
        public ItemStack removeItem(int index, int count) {
            return backing.removeItem(resolve(index), count);
        }

        @Override
        public ItemStack removeItemNoUpdate(int index) {
            return backing.removeItemNoUpdate(resolve(index));
        }

        @Override
        public void setItem(int index, ItemStack stack) {
            backing.setItem(resolve(index), stack);
        }

        @Override
        public int getMaxStackSize() {
            return backing.getMaxStackSize();
        }

        @Override
        public void setChanged() {
            backing.setChanged();
        }

        @Override
        public boolean stillValid(Player player) {
            return backing.stillValid(player);
        }

        @Override
        public boolean canPlaceItem(int index, ItemStack stack) {
            return backing.canPlaceItem(resolve(index), stack);
        }

        @Override
        public void clearContent() {
            backing.clearContent();
        }
    }
}
