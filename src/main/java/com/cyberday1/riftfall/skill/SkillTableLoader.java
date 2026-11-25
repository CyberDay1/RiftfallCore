package com.cyberday1.riftfall.skill;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.slf4j.Logger;

import java.util.Map;

/**
 * Loads {@code data/<ns>/skill_node/*.json} on every reload and installs it as {@link SkillDefs}.
 * A refused file is logged by name and left out; the rest still load.
 */
public final class SkillTableLoader extends SimpleJsonResourceReloadListener {

    private static final Logger LOGGER = LogUtils.getLogger();

    public SkillTableLoader() {
        super(new Gson(), SkillTable.DIRECTORY);
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
        SkillTable.Loaded loaded = SkillTable.parse(files);
        loaded.refusals().forEach(refusal -> LOGGER.error("Skill table refused {}", refusal));
        // Not a refusal: the table is pure, and a passive on a missing mod's attribute writes nothing.
        loaded.table().passives().values().stream()
                .filter(passive -> passive.attribute() != null
                        && !BuiltInRegistries.ATTRIBUTE.containsKey(passive.attribute()))
                .forEach(passive -> LOGGER.error("Skill table: passive {} names attribute {}, which is not registered",
                        passive.id(), passive.attribute()));
        LOGGER.info("Skill table: {} nodes from {} files, {} refused.",
                loaded.table().size(), loaded.files(), loaded.refusals().size());
        SkillDefs.install(loaded);
    }
}
