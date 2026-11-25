package com.cyberday1.riftfall.client;

import com.cyberday1.riftfall.RiftfallCore;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.BuiltInPackSource;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddPackFindersEvent;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Riftfall Lite: a built-in client resource pack, off unless chosen, that swaps the sky for half-size images at
 * the same paths. The Lite export turns it on in options.txt.
 */
@EventBusSubscriber(modid = RiftfallCore.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class RiftfallLitePack {
    static final String ID = "riftfall_lite";
    static final String FOLDER = "resourcepacks/riftfall_lite";
    static final PackLocationInfo INFO = new PackLocationInfo(ID, Component.literal("Riftfall Lite"), PackSource.BUILT_IN, Optional.empty());
    /** Not required, so the pack screen leaves it off until a player or options.txt turns it on. */
    static final PackSelectionConfig SELECTION = new PackSelectionConfig(false, Pack.Position.TOP, false);

    private RiftfallLitePack() {}

    @SubscribeEvent
    static void addPack(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.CLIENT_RESOURCES) return;
        var folder = ModList.get().getModFileById(RiftfallCore.MODID).getFile().findResource(FOLDER);
        var pack = pack(folder);
        if (pack != null) event.addRepositorySource(packs -> packs.accept(pack));
    }

    /** Null when pack.mcmeta is missing or unreadable. */
    static Pack pack(Path folder) {
        return Pack.readMetaAndCreate(INFO, BuiltInPackSource.fromName(info -> new PathPackResources(info, folder)),
                PackType.CLIENT_RESOURCES, SELECTION);
    }
}
