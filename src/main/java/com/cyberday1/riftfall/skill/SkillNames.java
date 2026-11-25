package com.cyberday1.riftfall.skill;

import com.cyberday1.riftfall.RiftfallCore;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * The one name rule for a skill node, everywhere it is shown: the node's own authored name, else the authored
 * spell name of its ability, else the id made readable. Abilities borrowed from another mod's spell are not
 * named by it (several share one).
 */
public final class SkillNames {
    private SkillNames() { }

    /** By node id; the loaded table supplies the ability where there is one, else the Riftfall convention. */
    public static MutableComponent of(String id) {
        if (id == null || id.isEmpty()) return Component.empty();
        String path = id.substring(id.indexOf(':') + 1);
        SkillDef def = SkillDefs.get(path);
        if (def != null) return of(path, def.cast().engine(), def.cast().ability());
        return named(nodeKey(path), path);
    }

    /** As the screen knows it: the engine's lower-case name and the ability id from the node's facts. */
    public static MutableComponent of(String id, String engine, String ability) {
        if (id == null || id.isEmpty()) return Component.empty();
        ResourceLocation parsed = ability == null || ability.isEmpty() ? null : ResourceLocation.tryParse(ability);
        if (parsed == null) return of(id);
        SkillNode.Engine kind = engine != null && engine.equalsIgnoreCase(SkillNode.Engine.IRONS_SPELLBOOKS.name())
                ? SkillNode.Engine.IRONS_SPELLBOOKS : SkillNode.Engine.SPELL_ENGINE;
        return of(id, kind, parsed);
    }

    public static MutableComponent of(String id, SkillNode.Engine engine, ResourceLocation ability) {
        String path = id.substring(id.indexOf(':') + 1);
        String abilityKey = key(engine, ability);
        String nodeKey = nodeKey(path);
        if (nodeKey.equals(abilityKey)) return named(abilityKey, path);
        // The fallback is a template: a missing node key renders the ability's name in its place.
        return Component.translatableWithFallback(nodeKey, "%s", named(abilityKey, path));
    }

    /** The node's own name key, tried before its ability's. */
    public static String nodeKey(String path) {
        return "spell." + RiftfallCore.MODID + "." + path + ".name";
    }

    /** The authored name's lang key, or null when Riftfall has none for this ability. */
    public static @Nullable String key(SkillNode.Engine engine, ResourceLocation ability) {
        if (engine == SkillNode.Engine.IRONS_SPELLBOOKS) {
            return "spell." + ability.getNamespace() + "." + ability.getPath();
        }
        if (!ability.getNamespace().equals(RiftfallCore.MODID)) return null;
        return "spell." + RiftfallCore.MODID + "." + ability.getPath() + ".name";
    }

    public static String readable(String id) {
        return SkillCommand.readable(id);
    }

    private static MutableComponent named(@Nullable String key, String id) {
        String fallback = readable(id);
        return key == null ? Component.literal(fallback) : Component.translatableWithFallback(key, fallback);
    }
}
