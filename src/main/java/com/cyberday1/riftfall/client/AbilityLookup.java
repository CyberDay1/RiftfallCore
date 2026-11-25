package com.cyberday1.riftfall.client;

import com.cyberday1.riftfall.skill.SpellEngineCompat;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.neoforged.fml.ModList;

import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;

/**
 * The detail page's ability facts, cached per id until the next skill view. Only Spell Engine abilities
 * resolve; an Iron's ability, a missing engine or a read that throws all answer {@link AbilityFacts#NONE}.
 */
final class AbilityLookup {

    static final String SPELL_ENGINE = "spell_engine";
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final Map<String, AbilityFacts> CACHE = new HashMap<>();
    private static Boolean engine;

    private AbilityLookup() {
    }

    static synchronized AbilityFacts of(String engineName, String ability) {
        if (!SPELL_ENGINE.equals(engineName) || ability.isEmpty() || !engineReady()) {
            return AbilityFacts.NONE;
        }
        return CACHE.computeIfAbsent(ability, AbilityLookup::read);
    }

    /** A reload can change any spell, so a new view drops every cached read. */
    static synchronized void forget() {
        CACHE.clear();
    }

    private static AbilityFacts read(String ability) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) {
            return AbilityFacts.NONE;
        }
        try {
            return SpellEngineAbilities.read(client.level, ability);
        } catch (RuntimeException | LinkageError e) {
            LOGGER.debug("Could not read ability {} for the skill book", ability, e);
            return AbilityFacts.NONE;
        }
    }

    /** Once per login: the synced spell registry is what names Riftfall's abilities. */
    static void registerTooltipTokens() {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || !engineReady()) return;
        try {
            SpellEngineAbilities.registerStageTokens(client.level);
        } catch (RuntimeException | LinkageError e) {
            LOGGER.debug("Could not register Riftfall's tooltip tokens with Spell Engine", e);
        }
    }

    private static boolean engineReady() {
        if (engine == null) {
            engine = ModList.get().getModContainerById(SPELL_ENGINE)
                    .map(mod -> SpellEngineCompat.supports(mod.getModInfo().getVersion().toString()))
                    .orElse(false);
        }
        return engine;
    }
}
