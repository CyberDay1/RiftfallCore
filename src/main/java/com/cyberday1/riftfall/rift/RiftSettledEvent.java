package com.cyberday1.riftfall.rift;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.Event;

import java.util.List;

/** Fired once per run when it settles, after XP and tolls, with one report per entrant. */
public final class RiftSettledEvent extends Event {
    private final MinecraftServer server;
    private final ResourceLocation rift;
    private final List<RiftRunReport> reports;

    public RiftSettledEvent(MinecraftServer server, ResourceLocation rift, List<RiftRunReport> reports) {
        this.server = server;
        this.rift = rift;
        this.reports = List.copyOf(reports);
    }

    public MinecraftServer server() { return server; }
    public ResourceLocation rift() { return rift; }
    public List<RiftRunReport> reports() { return reports; }
}
