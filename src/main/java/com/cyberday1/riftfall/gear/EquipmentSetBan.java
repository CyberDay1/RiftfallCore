package com.cyberday1.riftfall.gear;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Says out loud whether modded armour still pays for being worn.
 *
 * <h2>Why this is a listener and not an assertion</h2>
 * Spell Engine's {@code minecraft:equipment_set} is a datapack registry, so the 39 sets the pack
 * ships are turned off by replacing each file with a copy whose {@code bonuses} list is empty. A
 * replacement only wins if it is read after the one it replaces, and a replacement that lost that
 * race is byte-identical on disk to one that won &mdash; counting our own files proves nothing.
 *
 * <h2>What it actually measures</h2>
 * This reads the same thing the registry will: the top of the pack stack, one JSON per id. A set
 * that still lists a bonus here is a set that will still grant it, whoever won and whyever. Six of
 * the first 36 come from built-in packs rather than from their mod's own data and so sit outside
 * mod load order entirely, which is the case this exists to catch.
 */
public final class EquipmentSetBan extends SimpleJsonResourceReloadListener {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final String DIRECTORY = "equipment_set";

    private static final String BONUSES = "bonuses";

    public EquipmentSetBan() {
        super(new Gson(), DIRECTORY);
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> sets, ResourceManager manager, ProfilerFiller profiler) {
        List<String> granting = new ArrayList<>();
        for (Map.Entry<ResourceLocation, JsonElement> set : sets.entrySet()) {
            if (grants(set.getValue())) {
                granting.add(set.getKey().toString());
            }
        }
        if (granting.isEmpty()) {
            LOGGER.info("Equipment set ban: all {} modded armour sets grant nothing for being worn.",
                    sets.size());
            return;
        }
        LOGGER.warn("Equipment set ban: {} of {} sets still grant a bonus, so their armour still pays "
                + "for being worn: {}", granting.size(), sets.size(), String.join(", ", granting));
    }

    /** A set grants something if it still lists one bonus, whatever that bonus turns out to be. */
    private static boolean grants(JsonElement set) {
        if (!(set instanceof JsonObject object) || !object.has(BONUSES)) {
            return false;
        }
        JsonElement bonuses = object.get(BONUSES);
        return bonuses.isJsonArray() && !bonuses.getAsJsonArray().isEmpty();
    }
}
