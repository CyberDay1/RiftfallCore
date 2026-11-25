package com.cyberday1.riftfall.home.npc.smith;

import com.cyberday1.riftfall.RiftfallCore;
import com.cyberday1.riftfall.block.marker.NpcRole;
import com.cyberday1.riftfall.gear.RarityLadder;
import com.cyberday1.riftfall.home.npc.ResidentServiceMenu;
import com.cyberday1.riftfall.home.npc.depth.ResidentDepth;
import com.cyberday1.riftfall.init.RiftfallMenus;
import com.cyberday1.riftfall.tool.BlacksmithMenus;
import com.mojang.logging.LogUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.UUID;

/**
 * The Fitter's bulk scrap page: a 3x9 grid that takes only what single salvage would, and one Scrap button. The
 * server quotes the grid every sync and sends the totals; an Epic or rarer item makes Scrap take a second press.
 * The grid goes back to the player on close, and into the inventory on logout or death rather than onto the ground.
 */
@EventBusSubscriber(modid = RiftfallCore.MODID)
public class BulkScrapMenu extends ResidentServiceMenu<BulkScrapService.Quote> {
    public static final int GRID_X = 74, GRID_Y = 30, INVENTORY_Y = 136, HOTBAR_Y = 194;
    private static final Layout LAYOUT = layout();
    private static final int RESULT = 0, ITEMS = 1, STOCK = 2, CONFIRM_NEEDED = 3, ARMED = 4, MATERIALS = 5;

    /** The server's totals, as the client draws them; {@code materials} is indexed by rarity. */
    public record Shown(SmithService.Result result, int items, int stock, int[] materials, boolean needsConfirm, boolean armed) {}

    private final DataSlot[] shown = new DataSlot[MATERIALS + RarityLadder.RARITIES.length];
    private final BulkScrapService.Arm arm = new BulkScrapService.Arm();

    public BulkScrapMenu(int id, Inventory inventory) { this(id, inventory, null); }
    public BulkScrapMenu(int id, Inventory inventory, UUID smith) { this(RiftfallMenus.SMITH_BULK_SCRAP.get(), id, inventory, smith); }
    protected BulkScrapMenu(MenuType<?> type, int id, Inventory inventory, UUID smith) {
        super(type, id, inventory, smith, NpcRole.BLACKSMITH, LAYOUT);
        for (int i = 0; i < shown.length; i++) {
            shown[i] = DataSlot.standalone();
            shown[i].set(i == RESULT ? SmithService.Result.NO_ITEM.ordinal() : 0);
            addDataSlot(shown[i]);
        }
    }

    private static Layout layout() {
        int[][] grid = new int[BulkScrapService.GRID][];
        for (int i = 0; i < grid.length; i++) grid[i] = new int[]{GRID_X + (i % 9) * 18, GRID_Y + (i / 9) * 18};
        return new Layout(grid, GRID_X, INVENTORY_Y, HOTBAR_Y);
    }

    protected SmithService.Context context() { return SmithMenu.context(inventory.player); }
    protected long now() { return inventory.player.level().getGameTime(); }

    /** Why this item may not enter the grid; null when single salvage would take it. */
    public SmithService.Result refusal(ItemStack stack) { return BulkScrapService.refusal(stack, inventory, context()); }

    @Override protected boolean accepts(int input, ItemStack stack) { return refusal(stack) == null; }
    @Override protected int maxStack(int input) { return 1; }
    @Override protected BulkScrapService.Quote quote(int selection) { return BulkScrapService.quote(inputs, inventory, context()); }
    @Override protected boolean selectable(int selection) { return false; }

    @Override public boolean clickMenuButton(Player player, int button) {
        if (button != BACK) return super.clickMenuButton(player, button);
        if (!(player instanceof ServerPlayer server) || player.containerMenu != this || !stillValid(player)) return false;
        BlacksmithMenus.open(server, resident, BlacksmithMenus.SALVAGE);
        return true;
    }

    @Override protected Committed commit(ServerPlayer player, BulkScrapService.Quote quote) {
        long tick = now();
        if (quote.needsConfirm() && !arm.armed(quote.terms(), tick)) {
            arm.arm(quote.terms(), tick);
            return new Committed(false, Component.translatable(BulkScrapLang.CONFIRM_NEEDED));
        }
        arm.clear();
        var outcome = BulkScrapService.commit(inputs, inventory, quote, context());
        for (var stack : outcome.outputs()) player.getInventory().placeItemBackInInventory(stack);
        if (outcome.scrapped() == 0) return new Committed(false, Component.translatable(outcome.result().key()));
        LogUtils.getLogger().info("Fitter bulk scrap: {} scrapped {} of {}", player.getUUID(), outcome.scrapped(), quote.items());
        // The base awards one service; single salvage would have awarded one per item.
        for (int i = 1; i < outcome.scrapped(); i++) {
            try { ResidentDepth.service(player, NpcRole.BLACKSMITH); }
            catch (RuntimeException failed) { LogUtils.getLogger().error("Resident XP failed for bulk scrap", failed); break; }
        }
        return new Committed(true, Component.translatable(BulkScrapLang.SCRAPPED, count(outcome.scrapped())));
    }

    public static Component count(int items) {
        return items == 1 ? Component.translatable(BulkScrapLang.ITEM_ONE) : Component.translatable(BulkScrapLang.ITEMS, items);
    }

    @Override public void broadcastChanges() {
        if (inventory.player instanceof ServerPlayer) {
            var q = preview();
            set(RESULT, q.result().ordinal());
            set(ITEMS, q.items());
            set(STOCK, q.stock());
            set(CONFIRM_NEEDED, q.needsConfirm() ? 1 : 0);
            set(ARMED, arm.armed(q.terms(), now()) ? 1 : 0);
            for (int r = 0; r < q.materials().length; r++) set(MATERIALS + r, q.materials()[r]);
        }
        super.broadcastChanges();
    }

    private void set(int index, int value) { shown[index].set(Math.clamp(value, Short.MIN_VALUE, Short.MAX_VALUE)); }

    public Shown shown() {
        int r = shown[RESULT].get();
        var values = SmithService.Result.values();
        var result = r < 0 || r >= values.length ? SmithService.Result.NO_ITEM : values[r];
        int[] materials = new int[RarityLadder.RARITIES.length];
        for (int i = 0; i < materials.length; i++) materials[i] = shown[MATERIALS + i].get();
        return new Shown(result, shown[ITEMS].get(), shown[STOCK].get(), materials, shown[CONFIRM_NEEDED].get() == 1,
                shown[ARMED].get() == 1);
    }

    /** Moves the grid into the player's inventory, dropping what does not fit at their feet. */
    void giveBack(Player player) {
        for (int i = 0; i < inputs.getContainerSize(); i++) {
            var stack = inputs.removeItemNoUpdate(i);
            if (!stack.isEmpty()) player.getInventory().placeItemBackInInventory(stack);
        }
    }

    /** Before the player is saved: vanilla would drop the grid on the ground here. */
    @SubscribeEvent
    static void loggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity().containerMenu instanceof BulkScrapMenu menu) menu.giveBack(event.getEntity());
    }

    /** Before the death drop, so the grid follows the inventory's death rules instead of landing loose. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void died(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && player.containerMenu instanceof BulkScrapMenu menu)
            menu.giveBack(player);
    }
}
