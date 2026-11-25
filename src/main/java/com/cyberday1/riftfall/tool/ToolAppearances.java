package com.cyberday1.riftfall.tool;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Cosmetic source items only: their stats, durability and powers are never copied. */
public final class ToolAppearances {
    public record Skin(ResourceLocation item, String name, boolean requiresDiscovery) {
        public Skin(ResourceLocation item, String name) { this(item, name, false); }
        /** {@link #name} is the English; players see this key. */
        public String key() { return ToolLang.SKIN + item.getNamespace() + "." + item.getPath(); }
        public Component displayName() { return Component.translatable(key()); }
    }
    public static final List<Skin> ALL = List.of(
            skin("diamethysts:pickaxe_iron", "Diamethyst Iron Pickaxe"),
            skin("diamethysts:pickaxe_gold", "Diamethyst Golden Pickaxe"),
            skin("diamethysts:pickaxe_quartz", "Diamethyst Quartz Pickaxe"),
            skin("diamethysts:pickaxe_netherite", "Diamethyst Netherite Pickaxe"),
            skin("ae2:fluix_pickaxe", "Fluix Pickaxe"),
            skin("aether:gravitite_pickaxe", "Gravitite Pickaxe"),
            skin("deep_aether:skyjade_pickaxe", "Skyjade Pickaxe"),
            skin("deep_aether:stratus_pickaxe", "Stratus Pickaxe"),
            skin("eternal_starlight:deepsilver_pickaxe", "Deepsilver Pickaxe"),
            skin("eternal_starlight:glacite_pickaxe", "Glacite Pickaxe"),
            skin("eternal_starlight:unrealium_pickaxe", "Unrealium Pickaxe"),
            skin("eternal_starlight:gravity_pickaxe", "Gravity Pickaxe"),
            skin("eternal_starlight:starfire_pickaxe", "Starfire Pickaxe"),
            skin("eternal_starlight:flowglaze_pickaxe", "Flowglaze Pickaxe"),
            skin("voidscape:voidic_crystal_pickaxe", "Voidic Crystal Pickaxe"),
            skin("voidscape:astral_pickaxe", "Astral Pickaxe"),
            skin("aquaculture:neptunium_pickaxe", "Neptunium Pickaxe"),
            skin("alexsmobs:ghostly_pickaxe", "Ghostly Pickaxe"),
            skin("dragon_gear:dragon_scale_pickaxe", "Dragon Scale Pickaxe"),
            skin("deeperdarker:warden_pickaxe", "Warden Pickaxe"),
            skin("legendary_monsters:enderitium_pickaxe", "Enderitium Pickaxe"),
            skin("crystalcraft_unlimited_java:prism_pickaxe", "Prism Pickaxe"),
            skin("crystalcraft_unlimited_java:umbranova_pickaxe", "Umbranova Pickaxe"),
            skin("crystalcraft_unlimited_java:eclipse_diamond_pickaxe", "Eclipse Diamond Pickaxe"),
            skin("alexsmobs:dimensional_carver", "Dimensional Carver"),
            skin("alexsmobs:shattered_dimensional_carver", "Shattered Dimensional Carver"),
            skin("cataclysm:void_forge", "Void Forge"),
            skin("cataclysm:infernal_forge", "Infernal Forge"),
            skin("arsenal:unique_hammer_1", "Hammer of Destiny"),
            skin("arsenal:unique_hammer_sw", "Hammer of Sanctification"),
            discoveredSkin("simplyswords:runic_greathammer", "Runic Greathammer"),
            discoveredSkin("simplyswords:soulkeeper", "Soulkeeper"),
            discoveredSkin("simplyswords:hearthflame", "Hearthflame"),
            discoveredSkin("simplyswords:mjolnir", "Mjolnir"));
    public static final Skin DEFAULT = ALL.getFirst();
    private static final Map<String, Skin> BY_ID = byId();
    private ToolAppearances() {}
    private static Skin skin(String item, String name) { return new Skin(ResourceLocation.parse(item), name); }
    private static Skin discoveredSkin(String item, String name) { return new Skin(ResourceLocation.parse(item), name, true); }
    /** Asked for every stack a player carries, every tick. */
    public static Skin find(String id) { return BY_ID.get(id); }
    private static Map<String, Skin> byId() {
        // A HashMap, because a null id finds nothing rather than throwing.
        var skins = new HashMap<String, Skin>();
        for (Skin skin : ALL) skins.putIfAbsent(skin.item().toString(), skin);
        return skins;
    }
    public static boolean available(Skin skin) {
        return skin != null && (skin.equals(DEFAULT) || BuiltInRegistries.ITEM.containsKey(skin.item()));
    }
    public static List<Skin> available() { return ALL.stream().filter(ToolAppearances::available).toList(); }
}
