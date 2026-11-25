package com.cyberday1.riftfall.init;

import com.cyberday1.riftfall.RiftfallCore;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import javax.annotation.Nullable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Sound registry for Riftfall Core.
 *
 * <p>
 * Ambience is registered per theme, in an intro/loop pair. Both halves of every pair are registered
 * even where only the loop has a file: {@code sounds.json} is what decides whether a theme has an
 * intro, so adding one later is a resource change rather than a Java change.
 */
public final class RiftfallSounds {

    /** One per {@code rift_theme}. A theme outside this list plays {@code default}. */
    private static final List<String> AMBIENCE_THEMES =
            List.of("infernal", "void", "ender", "catacombs", "cave", "ruins", "default",
                    "celestial", "foundry", "glacial", "mycelial", "solar", "tidal");

    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(Registries.SOUND_EVENT, RiftfallCore.MODID);

    private static final Map<ResourceLocation, Ambience> AMBIENCE = registerAmbience();
    public static final Holder<SoundEvent> GUNNER_FIRE = event("skill.gunner.fire");

    private RiftfallSounds() {
    }

    public static void register(IEventBus modEventBus) {
        SOUNDS.register(modEventBus);
    }

    /**
     * The cue a rift of this theme plays, falling back to the default theme's so an unknown theme is
     * quiet rather than silent. {@code theme} being null means the fallback too.
     */
    public static Ambience ambienceFor(@Nullable ResourceLocation theme) {
        Ambience found = theme == null ? null : AMBIENCE.get(theme);
        return found != null ? found : AMBIENCE.get(RiftfallRegistries.DEFAULT_THEME.location());
    }

    private static Map<ResourceLocation, Ambience> registerAmbience() {
        Map<ResourceLocation, Ambience> map = new LinkedHashMap<>();
        for (String theme : AMBIENCE_THEMES) {
            map.put(RiftfallCore.id(theme), new Ambience(
                    event("music.ambience." + theme + ".intro"),
                    event("music.ambience." + theme)));
        }
        return Map.copyOf(map);
    }

    private static Holder<SoundEvent> event(String path) {
        ResourceLocation id = RiftfallCore.id(path);
        return SOUNDS.register(path, () -> SoundEvent.createVariableRangeEvent(id));
    }

    /**
     * A themed ambience cue. The intro is a swell the pack ships to lead into the loop, and is
     * optional: {@code ruins} and {@code default} come from a pack that cuts loops only.
     */
    public record Ambience(Holder<SoundEvent> intro, Holder<SoundEvent> loop) {
    }
}
