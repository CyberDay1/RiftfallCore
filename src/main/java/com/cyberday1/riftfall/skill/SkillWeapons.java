package com.cyberday1.riftfall.skill;

import com.cyberday1.riftfall.RiftfallCore;
import com.cyberday1.riftfall.gear.ClassLoot;
import com.cyberday1.riftfall.gear.GearPool;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Locale;

/** Class loot families are also the accepted casting weapons; decorative names alone never qualify. */
public final class SkillWeapons {
    private SkillWeapons() { }

    public static boolean movement(SkillNode node) {
        return node.category() == SkillNode.Category.MOVEMENT || node.lever() instanceof SkillNode.Movement;
    }

    public static boolean matches(String skillClass, ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        var families = ClassLoot.spec(skillClass);
        if (families == null) return false;
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        // A usable weapon need not be eligible to roll in a rift barrel (e.g. vanilla crossbows).
        var slot = GearPool.weaponSlot(stack.getItem());
        if (slot == null) slot = GearPool.classify(stack.getItem());
        if (!ClassLoot.TACZ_GUN.equals(id) && (slot == null || !GearPool.isWeapon(slot))) return false;
        return families.matches(id, slot, tag -> {
            var key = ResourceLocation.tryParse(tag);
            return key != null && stack.is(TagKey.create(Registries.ITEM, key));
        });
    }

    static boolean canCast(LivingEntity caster, SkillNode node) {
        if (!(caster instanceof ServerPlayer) || movement(node)) return true;
        SkillDef def = SkillDefs.get(node.id());
        // Unregistered internal/test nodes have no class. All player-owned skills have a definition.
        // The operator-only dev class has no class loot, so no weapon could ever match it.
        return def == null || SkillTable.DEV_CLASS.equals(def.skillClass())
                || matches(def.skillClass(), caster.getMainHandItem());
    }

    /** The class's weapon families, each by its own lang key with the data's name as the English. */
    public static Component families(String skillClass) {
        var spec = ClassLoot.spec(skillClass);
        if (spec == null) return GuideLang.text("weapon_any");
        List<Object> names = spec.families().stream().map(family -> (Object) familyName(family.name())).toList();
        return GuideLang.joined("slash", names);
    }

    public static Component familyName(String name) {
        return Component.translatableWithFallback(familyKey(name), name);
    }

    public static String familyKey(String name) {
        return "weapon_family." + RiftfallCore.MODID + "." + name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_");
    }

    public static Component requirement(SkillDef def) {
        return movement(def.cast()) ? GuideLang.text("weapon_movement")
                : GuideLang.text("weapon_required", families(def.skillClass()));
    }
}
