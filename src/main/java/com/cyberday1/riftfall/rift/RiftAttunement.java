package com.cyberday1.riftfall.rift;

import com.cyberday1.riftfall.init.RiftfallDataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * What a Cartographer-attuned key asks of its rift: one or two themes the generator rolls between, and optionally the
 * boss the arena puts on its pad. Carried key, then frame, then rift level, then the saved run, so a restart keeps it.
 */
public record RiftAttunement(List<ResourceLocation> themes, Optional<ResourceLocation> boss) {
    public static final int MAX_THEMES = 2;
    public static final RiftAttunement NONE = new RiftAttunement(List.of(), Optional.empty());

    public RiftAttunement {
        var distinct = new ArrayList<ResourceLocation>();
        for (var theme : themes) if (theme != null && !distinct.contains(theme) && distinct.size() < MAX_THEMES) distinct.add(theme);
        themes = List.copyOf(distinct);
        boss = boss == null ? Optional.empty() : boss;
    }

    public boolean none() { return themes.isEmpty() && boss.isEmpty(); }

    /** What a key carries; a plain key carries nothing. */
    public static RiftAttunement of(ItemStack key) {
        if (key.isEmpty()) return NONE;
        var themes = key.get(RiftfallDataComponents.RIFT_THEME.get());
        var boss = key.get(RiftfallDataComponents.RIFT_BOSS.get());
        return new RiftAttunement(themes == null ? List.of() : themes, Optional.ofNullable(boss));
    }

    /** Writes this onto a key, removing what it does not name. */
    public void write(ItemStack key) {
        if (themes.isEmpty()) key.remove(RiftfallDataComponents.RIFT_THEME.get());
        else key.set(RiftfallDataComponents.RIFT_THEME.get(), themes);
        if (boss.isEmpty()) key.remove(RiftfallDataComponents.RIFT_BOSS.get());
        else key.set(RiftfallDataComponents.RIFT_BOSS.get(), boss.get());
    }

    public CompoundTag save() {
        var tag = new CompoundTag();
        var list = new ListTag();
        for (var theme : themes) list.add(StringTag.valueOf(theme.toString()));
        tag.put("themes", list);
        boss.ifPresent(b -> tag.putString("boss", b.toString()));
        return tag;
    }

    public static RiftAttunement load(CompoundTag tag) {
        var themes = new ArrayList<ResourceLocation>();
        for (var entry : tag.getList("themes", Tag.TAG_STRING)) {
            var id = ResourceLocation.tryParse(entry.getAsString());
            if (id != null) themes.add(id);
        }
        var boss = tag.contains("boss") ? Optional.ofNullable(ResourceLocation.tryParse(tag.getString("boss"))) : Optional.<ResourceLocation>empty();
        return new RiftAttunement(themes, boss);
    }
}
