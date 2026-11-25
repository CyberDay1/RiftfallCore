package com.cyberday1.riftfall.client;

import com.cyberday1.riftfall.RiftfallCore;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

/** Light rising from points exported by the floor-fracture texture generator. */
public final class WorldBreakerGroundLight implements ResourceManagerReloadListener {
    public static final WorldBreakerGroundLight INSTANCE = new WorldBreakerGroundLight();
    private volatile List<Anchor> anchors = List.of();

    public record Anchor(double x, double z, double phase) {}
    public record Glow(float height, float halfWidth, float alpha) {}

    public List<Anchor> anchors() {
        return anchors;
    }

    /** Same outward 48-tick wave as the floor, with local flickers and a smooth loop. */
    public static Glow glow(Anchor anchor, double ticks) {
        double x = anchor.x() - 0.5;
        double z = anchor.z() - 0.5;
        double time = (ticks % 48.0 + 48.0) % 48.0 / 48.0;
        double wave = 0.5 + 0.5 * Math.cos(Math.PI * 2.0 * (time - Math.hypot(x, z) * 0.45)
                + 0.3 * Math.sin(Math.atan2(z, x) * 3.0));
        double flicker = 0.5 + 0.5 * Math.sin(Math.PI * 2.0 * (time * 3.0 + anchor.phase()));
        return new Glow((float) (0.17 + 0.25 * wave + 0.07 * flicker),
                (float) (0.08 + 0.035 * wave), (float) (0.30 + 0.30 * wave + 0.08 * flicker));
    }

    /** Keep the whole glow inside its supported floor cell, even at a hole's edge. */
    public static double clippedHalfWidth(Anchor a, double rightX, double rightZ, double desired) {
        double fx = a.x() - Math.floor(a.x());
        double fz = a.z() - Math.floor(a.z());
        double xLimit = Math.abs(rightX) < 1e-6 ? desired : Math.min(fx, 1.0 - fx) / Math.abs(rightX);
        double zLimit = Math.abs(rightZ) < 1e-6 ? desired : Math.min(fz, 1.0 - fz) / Math.abs(rightZ);
        return Math.max(0.0, Math.min(desired, Math.min(xLimit, zLimit)) - 0.001);
    }

    @Override
    public void onResourceManagerReload(ResourceManager resources) {
        List<Anchor> next = List.of();
        var resource = resources.getResource(RiftfallCore.id("world_breaker_ground_light.json"));
        if (resource.isPresent()) {
            try (Reader reader = resource.get().openAsReader()) {
                next = parse(reader);
            } catch (IOException | RuntimeException error) {
                LogUtils.getLogger().warn("Could not load World Breaker floor light; retaining floor cracks only", error);
            }
        }
        anchors = next;
    }

    static List<Anchor> parse(Reader reader) {
        var data = JsonParser.parseReader(reader).getAsJsonObject();
        if (data.get("cells").getAsInt() != 5) throw new IllegalArgumentException("Expected five floor cells");
        var entries = data.getAsJsonArray("anchors");
        if (entries.size() > 96) throw new IllegalArgumentException("Too many floor lights");
        List<Anchor> result = new ArrayList<>();
        for (var entry : entries) {
            var point = entry.getAsJsonObject();
            var a = new Anchor(point.get("x").getAsDouble(), point.get("z").getAsDouble(),
                    point.get("phase").getAsDouble());
            if (!Double.isFinite(a.x()) || !Double.isFinite(a.z()) || !Double.isFinite(a.phase())
                    || a.x() <= -2 || a.x() >= 3 || a.z() <= -2 || a.z() >= 3
                    || (Math.floor(a.x()) == 0 && Math.floor(a.z()) == 0)) {
                throw new IllegalArgumentException("Floor light outside usable decal cells");
            }
            result.add(a);
        }
        return List.copyOf(result);
    }
}
