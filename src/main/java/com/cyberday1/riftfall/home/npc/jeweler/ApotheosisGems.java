package com.cyberday1.riftfall.home.npc.jeweler;

import dev.shadowsoffire.apotheosis.Apoth;
import dev.shadowsoffire.apotheosis.socket.SocketHelper;
import dev.shadowsoffire.apotheosis.socket.SocketedGems;
import dev.shadowsoffire.apotheosis.socket.gem.GemInstance;
import dev.shadowsoffire.apotheosis.socket.gem.GemItem;
import dev.shadowsoffire.apotheosis.socket.gem.Purity;
import dev.shadowsoffire.apotheosis.socket.gem.UnsocketedGem;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** The only Jeweler class that names Apotheosis types. Hooks checked against 8.7.0 (compile) and 8.9.0 (pack). */
final class ApotheosisGems implements JewelerGems {
    static JewelerGems create() { return new ApotheosisGems(); }

    private ApotheosisGems() {}

    @Override public int purity(ItemStack gem) {
        if (gem.isEmpty() || !gem.is(Apoth.Items.GEM) || !UnsocketedGem.of(gem).isValid()) return -1;
        return GemItem.getPurity(gem).ordinal();
    }

    @Override public int sockets(ItemStack item) { return item.isEmpty() ? 0 : SocketHelper.getSockets(item); }

    @Override public List<ItemStack> gems(ItemStack item) {
        var out = new ArrayList<ItemStack>();
        if (item.isEmpty()) return out;
        for (GemInstance gem : SocketHelper.getGems(item)) out.add(gem.gemStack().isEmpty() ? ItemStack.EMPTY : gem.gemStack().copy());
        while (out.size() < sockets(item)) out.add(ItemStack.EMPTY);
        return out;
    }

    @Override public boolean canSet(ItemStack item, ItemStack gem) {
        return !item.isEmpty() && !gem.isEmpty() && JewelerAuthority.call(() -> SocketHelper.canSocketGemInItem(item, gem));
    }

    @Override public ItemStack set(ItemStack item, ItemStack gem) {
        return JewelerAuthority.call(() -> SocketHelper.socketGemInItem(item, gem));
    }

    @Override public ItemStack unset(ItemStack item, int index) {
        var gems = new ArrayList<>(SocketHelper.getGems(item));
        if (index < 0 || index >= gems.size() || gems.get(index).gemStack().isEmpty()) return ItemStack.EMPTY;
        gems.set(index, GemInstance.EMPTY);
        var copy = item.copy();
        SocketHelper.setGems(copy, new SocketedGems(gems));
        return copy;
    }

    @Override public ItemStack withPurity(ItemStack gem, int purity) {
        var copy = gem.copy();
        GemItem.setPurity(copy, Purity.values()[purity]);
        return copy;
    }

    @Override public Set<DataComponentType<?>> socketComponents() { return Set.of(Apoth.Components.SOCKETED_GEMS); }
    @Override public Set<DataComponentType<?>> purityComponents() { return Set.of(Apoth.Components.PURITY); }
    @Override public int rarity(ItemStack item) {
        if (item.isEmpty()) return -1;
        // A gun's rarity is its grade's; Apotheosis gives it none.
        var grade = com.cyberday1.riftfall.gear.GunGrade.read(item);
        if (grade != null) return grade.rarity();
        var holder = dev.shadowsoffire.apotheosis.affix.AffixHelper.getRarity(item);
        if (holder == null || !holder.isBound()) return -1;
        return java.util.Arrays.asList(com.cyberday1.riftfall.gear.RarityLadder.RARITIES).indexOf(holder.getId().toString());
    }

    @Override public boolean socketable(ItemStack item) {
        if (item.isEmpty()) return false;
        // An ungraded gun (creative, or from before grades) is no rift gear, so no sockets.
        if (com.cyberday1.riftfall.gear.GunGrade.isGun(item) && com.cyberday1.riftfall.gear.GunGrade.read(item) == null) return false;
        var category = dev.shadowsoffire.apotheosis.loot.LootCategory.forItem(item);
        return !category.isNone() && dev.shadowsoffire.apotheosis.socket.gem.GemRegistry.INSTANCE.getValues().stream()
                .anyMatch(gem -> gem.getBonus(category) != null);
    }

    @Override public boolean fits(ItemStack item, int index) {
        var gems = SocketHelper.getGems(item);
        return index >= 0 && index < gems.size() && gems.get(index).isValid();
    }

    @Override public ItemStack withSockets(ItemStack item, int sockets) {
        var copy = item.copy();
        SocketHelper.setSockets(copy, sockets);
        return copy;
    }

    @Override public Set<DataComponentType<?>> socketCountComponents() { return Set.of(Apoth.Components.SOCKETS); }

    @Override public Component purityName(int purity) {
        return purity >= 0 && purity < Purity.values().length ? Purity.values()[purity].toComponent() : Component.empty();
    }
}
