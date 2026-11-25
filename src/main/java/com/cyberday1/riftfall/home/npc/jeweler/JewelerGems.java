package com.cyberday1.riftfall.home.npc.jeweler;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;

import java.util.List;
import java.util.Set;

/**
 * What the Jeweler asks of the socket system. Apotheosis' types stay in {@link ApotheosisGems}, reached only
 * when the mod is loaded; without it every answer is "no", so the services refuse rather than fail.
 * Every edit returns a changed copy; the caller writes it back through {@code GearMutation}.
 */
public interface JewelerGems {
    /** Purity index of a loose, valid gem, or -1. */
    int purity(ItemStack gem);
    int sockets(ItemStack item);
    /** One entry per socket; EMPTY for an empty socket. */
    List<ItemStack> gems(ItemStack item);
    /** Apotheosis' own checks (empty socket, fitting category), asked under the Jeweler's authority. */
    boolean canSet(ItemStack item, ItemStack gem);
    /** A copy of {@code item} with the gem in its first empty socket, or EMPTY. */
    ItemStack set(ItemStack item, ItemStack gem);
    /** A copy of {@code item} with socket {@code index} emptied, or EMPTY when it holds no gem. */
    ItemStack unset(ItemStack item, int index);
    /** A copy of the gem at {@code purity}. */
    ItemStack withPurity(ItemStack gem, int purity);
    Set<DataComponentType<?>> socketComponents();
    Set<DataComponentType<?>> purityComponents();
    Component purityName(int purity);
    /** The item's affix rarity as a {@code RarityLadder} index (common 0 to mythic 4), or -1 for none. */
    default int rarity(ItemStack item) { return -1; }
    /** Whether some registered gem fits the item's loot category, so a socket there can be filled. */
    default boolean socketable(ItemStack item) { return false; }
    /** Whether the gem in socket {@code index} still has a bonus for this item; one that no longer fits is dormant. */
    default boolean fits(ItemStack item, int index) { return true; }
    /** A copy with {@code sockets} sockets, or EMPTY. */
    default ItemStack withSockets(ItemStack item, int sockets) { return ItemStack.EMPTY; }
    /** The component the socket count lives in. */
    default Set<DataComponentType<?>> socketCountComponents() { return Set.of(); }

    JewelerGems NONE = new JewelerGems() {
        @Override public int purity(ItemStack gem) { return -1; }
        @Override public int sockets(ItemStack item) { return 0; }
        @Override public List<ItemStack> gems(ItemStack item) { return List.of(); }
        @Override public boolean canSet(ItemStack item, ItemStack gem) { return false; }
        @Override public ItemStack set(ItemStack item, ItemStack gem) { return ItemStack.EMPTY; }
        @Override public ItemStack unset(ItemStack item, int index) { return ItemStack.EMPTY; }
        @Override public ItemStack withPurity(ItemStack gem, int purity) { return ItemStack.EMPTY; }
        @Override public Set<DataComponentType<?>> socketComponents() { return Set.of(); }
        @Override public Set<DataComponentType<?>> purityComponents() { return Set.of(); }
        @Override public Component purityName(int purity) { return Component.literal(String.valueOf(purity)); }
    };

    static JewelerGems current() { return Holder.GEMS; }

    final class Holder {
        private static final JewelerGems GEMS = ModList.get() != null && ModList.get().isLoaded("apotheosis")
                ? ApotheosisGems.create() : NONE;
        private Holder() {}
    }
}
