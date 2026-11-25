package com.cyberday1.riftfall.home.npc;

import com.cyberday1.riftfall.block.marker.NpcRole;
import com.cyberday1.riftfall.init.RiftfallMenus;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.OptionalInt;
import java.util.UUID;

/** Every role's native landing. No slots: navigation and on-the-spot actions, all rechecked on the server. */
public final class ResidentLandingMenu extends AbstractContainerMenu {
    private final Inventory inventory;
    private final UUID resident;
    private final DataSlot role = DataSlot.standalone();   // ordinal + 1; 0 until the server says
    private final DataSlot rank = DataSlot.standalone();   // -1: no rank to show
    private final DataSlot xp = DataSlot.standalone(), best = DataSlot.standalone();
    // This visit's line, chosen once on open: trigger ordinal (-1 none), variant, argument.
    private final DataSlot trigger = DataSlot.standalone(), variant = DataSlot.standalone(), arg = DataSlot.standalone();
    private final DataSlot known = DataSlot.standalone(); // 1: this resident has recognised their note for this player
    private final DataSlot[] states = new DataSlot[ResidentLanding.MAX_ENTRIES];

    public ResidentLandingMenu(int id, Inventory inventory) { this(id, inventory, null, null); }
    public ResidentLandingMenu(int id, Inventory inventory, UUID resident, NpcRole role) {
        super(RiftfallMenus.RESIDENT_LANDING.get(), id);
        this.inventory = inventory;
        this.resident = resident;
        this.role.set(role == null ? 0 : role.ordinal() + 1);
        rank.set(-1);
        trigger.set(-1);
        addDataSlot(this.role);
        addDataSlot(rank);
        for (var slot : new DataSlot[]{xp, best, trigger, variant, arg, known}) addDataSlot(slot);
        if (role != null && inventory.player instanceof ServerPlayer server) {
            var panel = com.cyberday1.riftfall.home.npc.depth.ResidentDepth.panel(server, role);
            if (panel != null) {
                trigger.set(panel.trigger()); variant.set(panel.variant()); arg.set(panel.arg());
                known.set(panel.known() ? 1 : 0);
            }
        }
        for (int i = 0; i < states.length; i++) {
            states[i] = DataSlot.standalone();
            states[i].set(1); // Unavailable until the authoritative state arrives.
            addDataSlot(states[i]);
        }
    }

    public NpcRole role() {
        int value = role.get();
        return value <= 0 || value > NpcRole.values().length ? null : NpcRole.values()[value - 1];
    }
    public List<ResidentLanding.Entry> entries() {
        var r = role();
        return r == null ? List.of() : ResidentLanding.entries(r);
    }
    public int state(int index) { return index >= 0 && index < states.length ? states[index].get() : 1; }
    public OptionalInt rank() { return rank.get() < 0 ? OptionalInt.empty() : OptionalInt.of(rank.get()); }
    public int xp() { return xp.get(); }
    public int best() { return best.get(); }
    /** The line to show: trigger ordinal or -1, then variant and argument. */
    public int trigger() { return trigger.get(); }
    public int variant() { return variant.get(); }
    public int arg() { return arg.get(); }
    /** True once this resident's lines swap for this player. */
    public boolean known() { return known.get() == 1; }

    private void refresh(ServerPlayer player) {
        var list = entries();
        for (int i = 0; i < list.size(); i++) states[i].set(list.get(i).state().of(player, this));
        var shown = ResidentLanding.rank(player, role()).orElse(null);
        rank.set(shown == null ? -1 : shown[0]);
        xp.set(shown == null ? 0 : Math.min(Short.MAX_VALUE, shown[1]));
        best.set(shown == null ? 0 : Math.min(Short.MAX_VALUE, shown[2]));
    }

    private int boardTicks;

    @Override public void broadcastChanges() {
        if (inventory.player instanceof ServerPlayer player && role() != null) {
            refresh(player);
            // The Requests and Orders tabs: on open, then every two seconds so order clocks move.
            if (boardTicks++ % 40 == 0) com.cyberday1.riftfall.home.npc.depth.ResidentBoard.send(player, role());
        }
        super.broadcastChanges();
    }

    @Override public boolean clickMenuButton(Player player, int button) {
        if (!(player instanceof ServerPlayer server) || player.containerMenu != this || !stillValid(player)) return false;
        if (com.cyberday1.riftfall.home.npc.depth.ResidentBoard.handles(button)) {
            boolean changed = com.cyberday1.riftfall.home.npc.depth.ResidentBoard.handle(server, role(), button);
            broadcastChanges();
            return changed;
        }
        var list = entries();
        for (int i = 0; i < list.size(); i++) {
            var entry = list.get(i);
            if (entry.id() != button) continue;
            if (entry.state().of(server, this) != 0) return false;
            entry.action().run(server, resident, this);
            if (server.containerMenu == this) broadcastChanges();
            return true;
        }
        return false;
    }

    @Override public boolean stillValid(Player player) {
        if (!(player instanceof ServerPlayer)) return true;
        var r = role();
        return r != null && ResidentMenus.canUse(player, resident, r);
    }
    @Override public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }
}
