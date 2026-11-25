package com.cyberday1.riftfall.init;

import com.cyberday1.riftfall.RiftfallCore;
import com.cyberday1.riftfall.block.barrel.RiftBarrelMenu;
import com.cyberday1.riftfall.block.forge.KeyForgeMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Menu registry for Riftfall Core. */
public final class RiftfallMenus {

    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, RiftfallCore.MODID);

    /**
     * The key forge screen.
     *
     * <p>
     * Built from the two-argument constructor, not the extended one that carries extra data at open
     * time: the client rebuilds the forge's contents from the slots the server sends anyway, and the
     * tier is typed on the client rather than handed to it.
     */
    public static final DeferredHolder<MenuType<?>, MenuType<KeyForgeMenu>> KEY_FORGE =
            MENUS.register("key_forge", () -> new MenuType<>(KeyForgeMenu::new, FeatureFlags.DEFAULT_FLAGS));

    /**
     * A loot barrel's nine rows, which is one more than {@code ChestMenu} will build.
     *
     * <p>
     * Same two-argument form as the forge, and for the same reason: the client is sent the slot
     * contents, so it needs nothing at open time beyond the size, which is fixed.
     */
    public static final DeferredHolder<MenuType<?>, MenuType<RiftBarrelMenu>> RIFT_BARREL =
            MENUS.register("rift_barrel",
                    () -> new MenuType<>(RiftBarrelMenu::new, FeatureFlags.DEFAULT_FLAGS));

    private RiftfallMenus() {
    }

    public static final DeferredHolder<MenuType<?>, MenuType<com.cyberday1.riftfall.tool.BlacksmithMenu>> BLACKSMITH =
            MENUS.register("blacksmith", () -> new MenuType<>(com.cyberday1.riftfall.tool.BlacksmithMenu::new, FeatureFlags.DEFAULT_FLAGS));

    public static final DeferredHolder<MenuType<?>, MenuType<com.cyberday1.riftfall.home.npc.jeweler.JewelerMenu>> JEWELER =
            MENUS.register("jeweler", () -> new MenuType<>(com.cyberday1.riftfall.home.npc.jeweler.JewelerMenu::new, FeatureFlags.DEFAULT_FLAGS));

    public static final DeferredHolder<MenuType<?>, MenuType<com.cyberday1.riftfall.home.npc.cartographer.CartographerMenu>> CARTOGRAPHER =
            MENUS.register("cartographer", () -> new MenuType<>(com.cyberday1.riftfall.home.npc.cartographer.CartographerMenu::new, FeatureFlags.DEFAULT_FLAGS));
    public static final DeferredHolder<MenuType<?>, MenuType<com.cyberday1.riftfall.home.npc.quarry.QuarryMenu>> QUARRY =
            MENUS.register("quarry", () -> new MenuType<>(com.cyberday1.riftfall.home.npc.quarry.QuarryMenu::new, FeatureFlags.DEFAULT_FLAGS));

    public static final DeferredHolder<MenuType<?>, MenuType<com.cyberday1.riftfall.home.npc.smith.SmithMenu>> SMITH =
            MENUS.register("smith", () -> new MenuType<>(com.cyberday1.riftfall.home.npc.smith.SmithMenu::new, FeatureFlags.DEFAULT_FLAGS));

    public static final DeferredHolder<MenuType<?>, MenuType<com.cyberday1.riftfall.home.npc.smith.BulkScrapMenu>> SMITH_BULK_SCRAP =
            MENUS.register("smith_bulk_scrap", () -> new MenuType<>(com.cyberday1.riftfall.home.npc.smith.BulkScrapMenu::new, FeatureFlags.DEFAULT_FLAGS));

    public static final DeferredHolder<MenuType<?>, MenuType<com.cyberday1.riftfall.home.npc.smith.ToolWorkMenu>> SMITH_TOOLS =
            MENUS.register("smith_tools", () -> new MenuType<>(com.cyberday1.riftfall.home.npc.smith.ToolWorkMenu::new, FeatureFlags.DEFAULT_FLAGS));

    public static void register(IEventBus modEventBus) {
        MENUS.register(modEventBus);
    }

    /** Every home resident's landing; the role travels in a data slot and the title is a lang key. */
    public static final DeferredHolder<MenuType<?>, MenuType<com.cyberday1.riftfall.home.npc.ResidentLandingMenu>> RESIDENT_LANDING =
            MENUS.register("resident_landing", () -> new MenuType<>(com.cyberday1.riftfall.home.npc.ResidentLandingMenu::new, FeatureFlags.DEFAULT_FLAGS));
    public static final DeferredHolder<MenuType<?>, MenuType<com.cyberday1.riftfall.tool.BlacksmithAppearanceMenu>> BLACKSMITH_APPEARANCE =
            MENUS.register("blacksmith_appearance", () -> new MenuType<>(com.cyberday1.riftfall.tool.BlacksmithAppearanceMenu::new, FeatureFlags.DEFAULT_FLAGS));
}
