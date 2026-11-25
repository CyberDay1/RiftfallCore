package com.cyberday1.riftfall.boss;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import com.cyberday1.riftfall.RiftfallCore;
import com.cyberday1.riftfall.skill.SkillService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;

/** Entry-tier variants must not inherit their native endgame weapons (including +69 attack). */
final class BeginnerBosses {
    private static final ResourceLocation HEALTH = RiftfallCore.id("beginner_boss_health");
    private BeginnerBosses() { }

    /** Entry variants give ordinary arrows room to compete with starter melee rotations. */
    static float arrowMultiplier(Mob mob, int tier, DamageSource source) {
        return prepared(mob, tier)
                && source.getDirectEntity() instanceof AbstractArrow && source.getEntity() instanceof Player
                ? 2.5F : 1;
    }

    /**
     * Keep fast starter melee rotations near the same encounter length as the slower classes.
     * This applies to the owner's attacks and skills, only against prepared entry bosses;
     * equipment upgrades still increase damage proportionally. No per-hit cap or timed immunity.
     */
    static float classMultiplier(Mob mob, int tier, DamageSource source) {
        if (!prepared(mob, tier) || !(source.getEntity() instanceof ServerPlayer player)) return 1;
        String name = SkillService.characterClass(player);
        if (name == null) return 1;
        return switch (name) {
            case "sword_immortal" -> 0.45F;
            case "reaper", "paladin" -> 0.60F;
            case "monk", "shifter" -> 0.65F;
            case "tactician" -> 0.80F;
            default -> 1;
        };
    }

    private static boolean prepared(Mob mob, int tier) {
        var health = mob.getAttribute(Attributes.MAX_HEALTH);
        return tier >= 1 && tier <= 2 && mob.getType().is(RiftBosses.BEGINNER_ROSTER)
                && health != null && health.hasModifier(HEALTH);
    }

    static void prepare(Mob mob, int tier) {
        if (tier > 2 || !mob.getType().is(RiftBosses.BEGINNER_ROSTER)) return;
        // A plain stone sword keeps the melee encounter while removing native weapon procs/bonuses.
        mob.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_SWORD));
        mob.setDropChance(EquipmentSlot.MAINHAND, 0);
        // Sustained starter encounters: 600 HP on Null Servant, 825 on Safey.
        // Cap the larger native pool so low-throughput classes do not face a three-minute grind.
        var health = mob.getAttribute(Attributes.MAX_HEALTH);
        if (health != null) health.addOrReplacePermanentModifier(new AttributeModifier(
                HEALTH, Math.min(825, health.getBaseValue()*12)-health.getBaseValue(), AttributeModifier.Operation.ADD_VALUE));
        if (mob instanceof PathfinderMob walking) {
            mob.goalSelector.removeAllGoals(goal -> goal instanceof MeleeAttackGoal);
            mob.goalSelector.addGoal(1, new BeginnerMeleeGoal(walking));
        }
    }
}
