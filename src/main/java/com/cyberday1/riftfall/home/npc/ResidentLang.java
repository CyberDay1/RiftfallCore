package com.cyberday1.riftfall.home.npc;

import com.cyberday1.riftfall.RiftfallCore;
import com.cyberday1.riftfall.block.marker.NpcRole;
import com.cyberday1.riftfall.lore.ResidentStory;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Every line the native resident screens show; the lang provider reads {@link #ENGLISH}. */
public final class ResidentLang {
    private static final String RESIDENT = "resident." + RiftfallCore.MODID + ".";
    private static final String SCREEN = "screen." + RiftfallCore.MODID + ".resident.";

    public static final String PROMPT = SCREEN + "prompt";
    public static final String NO_SERVICES = SCREEN + "no_services";
    public static final String CLOSE = SCREEN + "close";
    /** A service page's confirm button while the server answers; the status line names who is working. */
    public static final String WORKING = SCREEN + "working";
    /** "Penk, Blacksmith": the person, then the role. A locale may reorder with %1$s and %2$s. */
    public static final String NAMEPLATE = RESIDENT + "nameplate";

    public static String name(NpcRole role) { return RESIDENT + role.id() + ".name"; }
    public static String title(NpcRole role) { return RESIDENT + role.id() + ".title"; }

    /** Where a resident is shown as a person (nameplate, screen header). Mechanical text keeps {@link #name}. */
    public static Component nameplate(NpcRole role) {
        return Component.translatable(NAMEPLATE, Component.translatable(ResidentStory.personKey(role)),
                Component.translatable(name(role)));
    }

    /** The role whose {@link #name} key this is, or null. */
    public static @Nullable NpcRole roleOfName(String key) {
        if (key == null || !key.startsWith(RESIDENT) || !key.endsWith(".name")) return null;
        String id = key.substring(RESIDENT.length(), key.length() - ".name".length());
        for (var role : NpcRole.values()) if (role.id().equals(id)) return role;
        return null;
    }

    public static final Map<String, String> ENGLISH;

    static {
        Map<String, String> english = new LinkedHashMap<>();
        english.put(PROMPT, "Choose a service");
        english.put(NO_SERVICES, "No services are open here yet.");
        english.put(CLOSE, "Close");
        english.put(WORKING, "Working...");
        english.put(NAMEPLATE, "%s, %s");
        for (var role : NpcRole.values()) {
            english.put(name(role), role.displayName());
            english.put(title(role), ResidentAppearance.forRole(role).title());
        }
        ENGLISH = Collections.unmodifiableMap(english);
    }

    private ResidentLang() {}
}
