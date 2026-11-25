package com.cyberday1.riftfall.block.forge;

import com.cyberday1.riftfall.RiftfallCore;
import com.cyberday1.riftfall.init.RiftfallBlocks;
import com.cyberday1.riftfall.init.RiftfallDataComponents;
import com.cyberday1.riftfall.init.RiftfallItems;
import com.cyberday1.riftfall.init.RiftfallMenus;
import com.cyberday1.riftfall.progression.RiftTier;
import com.cyberday1.riftfall.progression.RiftXp;
import com.cyberday1.riftfall.rift.RiftKeyItem;
import com.cyberday1.riftfall.stat.RiftStats;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

/**
 * The key forge's slots and the arithmetic behind them.
 *
 * <p>
 * Two slots: rift shards go in, a rift key comes out. The key is a <em>preview</em> in the vanilla
 * sense &mdash; it appears the moment the shards on hand cover the tier that is dialled, and the
 * shards are only spent when somebody actually picks it up. Nothing is consumed by looking.
 *
 * <h2>Where the tier comes from</h2>
 * The player types it, and it arrives over vanilla's container-button channel: the screen's text box
 * calls {@code clickMenuButton}, whose payload is a plain {@code VarInt}, so the whole integer range
 * gets across without this mod defining a packet of its own.
 *
 * <p>
 * That channel is client-controlled, so {@link #clickMenuButton} is a trust boundary rather than a
 * setter: a number from it is only accepted if it is one a key could legally carry. It is not
 * clamped to a maximum, because the design does not have one &mdash; an unaffordable tier simply
 * produces no preview, which is a truthful answer rather than a silently-lowered one.
 *
 * <p>
 * The tier lives here rather than on the block entity, so it belongs to the player at the screen and
 * not to the bench. Two players at the same forge dial their own numbers, and closing the screen
 * forgets it.
 *
 * <p>
 * A tier above the player's level tier forges nothing, and the screen says why.
 */
public class KeyForgeMenu extends AbstractContainerMenu {

    /**
     * The lowest tier a key can carry. Not a policy choice made here: the {@code rift_tier}
     * component is persisted through {@code ExtraCodecs.POSITIVE_INT}, which drops anything that is
     * not positive, so a tier-zero key would come back off disk as a tier-1 key.
     */
    public static final int MIN_TIER = RiftKeyItem.DEFAULT_TIER;

    /** The refusal's lang key: "Tier %s needs level %s". */
    public static final String LEVEL_KEY = "container." + RiftfallCore.MODID + ".key_forge.level";

    private static final int SHARD_SLOT = 0;
    private static final int RESULT_SLOT = 1;
    private static final int INVENTORY_START = 2;
    private static final int INVENTORY_END = INVENTORY_START + 36;

    private final Container forge;
    private final ResultContainer result = new ResultContainer();
    private final ContainerLevelAccess access;

    private final Player player;

    /** Re-read on the server at every preview, and synced so the screen can explain a refusal. */
    private final DataSlot level = DataSlot.standalone();

    private int tier = MIN_TIER;

    /** Client-side: the menu is rebuilt from the server's slot contents, so the container is a stand-in. */
    public KeyForgeMenu(int containerId, Inventory inventory) {
        this(containerId, inventory, new SimpleContainer(1), ContainerLevelAccess.NULL);
    }

    public KeyForgeMenu(int containerId, Inventory inventory, KeyForgeBlockEntity forge) {
        this(containerId, inventory, forge,
                ContainerLevelAccess.create(forge.getLevel(), forge.getBlockPos()));
    }

    private KeyForgeMenu(int containerId, Inventory inventory, Container forge, ContainerLevelAccess access) {
        super(RiftfallMenus.KEY_FORGE.get(), containerId);
        this.forge = forge;
        this.access = access;
        this.player = inventory.player;
        level.set(RiftXp.FIRST_LEVEL);
        addDataSlot(level);

        addSlot(new Slot(forge, KeyForgeBlockEntity.SHARD_SLOT, 56, 35) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.is(RiftfallItems.RIFT_SHARD.get());
            }
        });
        addSlot(new ForgedKeySlot(result, 0, 116, 35));

        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(inventory, column + row * 9 + 9, 8 + column * 18, 84 + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column, 8 + column * 18, 142));
        }
    }

    /**
     * How many shards a key of this tier costs: one per tier.
     *
     * <p>
     * The only place to change it. Shards are rift-only loot ({@code loot/RiftShards}); a tier-1 key
     * can also be crafted, so the forge is the one way to any tier above it.
     *
     * <p>
     * A stack of 64 in one slot caps the price at tier 64, but the level gate binds first: level
     * {@code RiftXp.LEVEL_CAP} (200) is tier 21. ⚠ Tier 22 keys exist and are unforgeable under it.
     */
    public static int cost(int tier) {
        return tier;
    }

    /**
     * The key a given tier, shard count and player level buys, or nothing. A tier above
     * {@link RiftTier#of} the level is refused, never lowered.
     *
     * <p>
     * Pure, and separate from the slots, because this is the arithmetic that fails silently: a key
     * whose tier component never got set is not a broken item, it is a legal tier-1 key, so a
     * mistake here produces an ordinary-looking key and an ordinary-looking tier-1 dungeon rather
     * than an error anybody would report.
     */
    public static ItemStack forge(int tier, int shardsOnHand, int level) {
        if (tier < MIN_TIER || tier > RiftTier.of(level) || shardsOnHand < cost(tier)) {
            return ItemStack.EMPTY;
        }
        ItemStack key = new ItemStack(RiftfallItems.RIFT_KEY.get());
        key.set(RiftfallDataComponents.RIFT_TIER.get(), tier);
        return key;
    }

    /** Why a tier forges nothing at this level, or empty when the level allows it. */
    public static Optional<Component> refusal(int tier, int level) {
        if (tier < MIN_TIER || tier <= RiftTier.of(level)) {
            return Optional.empty();
        }
        return Optional.of(Component.translatable(LEVEL_KEY, tier, RiftTier.first(tier)));
    }

    /** The player's level as this menu last saw it; on the client, as the server last sent it. */
    public int level() {
        return Math.max(RiftXp.FIRST_LEVEL, level.get());
    }

    /**
     * Takes the tier the player typed.
     *
     * <p>
     * Returns false — and changes nothing — for a number no key could carry, which is what stops a
     * hand-written packet from putting a tier-zero or negative key into the world through the one
     * hole in the screen.
     */
    @Override
    public boolean clickMenuButton(Player player, int button) {
        if (button < MIN_TIER) {
            return false;
        }
        tier = button;
        updateResult();
        return true;
    }

    @Override
    public void slotsChanged(Container container) {
        updateResult();
        super.slotsChanged(container);
    }

    /**
     * Puts the key the current shards and tier would buy into the output slot, or clears it.
     *
     * <p>
     * No {@code broadcastChanges} here: both callers are already followed by one — vanilla's button
     * handler on the typed-tier path, and the menu's own slot bookkeeping on the shards path.
     */
    private void updateResult() {
        if (player instanceof ServerPlayer server) {
            level.set(levelOf(server));
        }
        result.setItem(0, forge(tier, forge.getItem(KeyForgeBlockEntity.SHARD_SLOT).getCount(), level()));
    }

    /** Live from the XP store, never from a key's {@code rift_tier}, which describes a dungeon. */
    private static int levelOf(ServerPlayer player) {
        return RiftXp.levelFor(RiftStats.of(player.server).xpFor(player.getUUID()), RiftXp.LEVEL_CAP);
    }

    @Override
    public boolean stillValid(Player player) {
        return AbstractContainerMenu.stillValid(access, player, RiftfallBlocks.KEY_FORGE.get());
    }

    /**
     * Shift-click routing.
     *
     * <p>
     * The output slot moves outward only, and through {@code moveItemStackTo} in reverse so a
     * forged key lands in the hotbar first the way a furnace's output does. Shards clicked in the
     * player's inventory go to the forge; anything else there just crosses between the main
     * inventory and the hotbar.
     */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack inSlot = slot.getItem();
        ItemStack original = inSlot.copy();

        if (index == RESULT_SLOT) {
            if (!moveItemStackTo(inSlot, INVENTORY_START, INVENTORY_END, true)) {
                return ItemStack.EMPTY;
            }
            slot.onQuickCraft(inSlot, original);
        } else if (index == SHARD_SLOT) {
            if (!moveItemStackTo(inSlot, INVENTORY_START, INVENTORY_END, true)) {
                return ItemStack.EMPTY;
            }
        } else if (inSlot.is(RiftfallItems.RIFT_SHARD.get())) {
            if (!moveItemStackTo(inSlot, SHARD_SLOT, SHARD_SLOT + 1, false)) {
                return ItemStack.EMPTY;
            }
        } else if (index < INVENTORY_START + 27) {
            if (!moveItemStackTo(inSlot, INVENTORY_START + 27, INVENTORY_END, false)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(inSlot, INVENTORY_START, INVENTORY_START + 27, false)) {
            return ItemStack.EMPTY;
        }

        if (inSlot.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        if (inSlot.getCount() == original.getCount()) {
            return ItemStack.EMPTY;
        }
        slot.onTake(player, inSlot);
        return original;
    }

    /**
     * The output slot: it hands over a key and bills the shards for it.
     *
     * <p>
     * The charge happens in {@code onTake} rather than when the preview is drawn, so the only way to
     * spend shards is to take the key. The tier is re-read at that moment rather than trusted from
     * when the preview was built, because the shards in the slot can have changed underneath it —
     * a hopper can pull them out while the screen is open.
     */
    private final class ForgedKeySlot extends Slot {

        private ForgedKeySlot(Container container, int slot, int x, int y) {
            super(container, slot, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }

        @Override
        public void onTake(Player player, ItemStack taken) {
            forge.removeItem(KeyForgeBlockEntity.SHARD_SLOT, cost(tier));
            updateResult();
            super.onTake(player, taken);
            com.cyberday1.riftfall.story.StoryBridge.keyReady(player, "forged");
        }
    }
}
