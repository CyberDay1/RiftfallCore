package com.cyberday1.riftfall.mob;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.damagesource.DamageTypes;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import java.util.Map;

/** Per-hit budgets are applied before armour/guard, never as a post-mitigation damage floor. */
public final class SporeCombat {
    private static final String TIER = "riftfall:spore_tier";
    private static final String SCALE = "riftfall:spore_projectile_scale";
    private static final String CAP = "riftfall:spore_projectile_cap";
    private static final ResourceLocation DAMAGE = ResourceLocation.parse("riftfall:spore_melee_balance");
    private static final Map<String, Double> MELEE = Map.of("inf_human", 4.0, "inf_villager", 4.0,
            "leaper", 6.0, "stalker", 7.0, "slasher", 7.0, "brute", 10.0, "spitter", 0.0);

    private SporeCombat() {}

    public static boolean managed(Entity entity) {
        if (!(entity instanceof Mob) || entity.getType() == null) return false;
        var id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        return id != null && id.getNamespace().equals("spore") && MELEE.containsKey(id.getPath())
                && SporeEncounters.restricted(entity);
    }

    public static double factor(int tier) { return 1.0 + MobScaling.DAMAGE_PER_TIER * Math.max(0, tier - 1); }

    @SuppressWarnings("unchecked")
    public static void scale(Mob mob, int tier) {
        if (!managed(mob)) return;
        mob.getPersistentData().putInt(TIER, Math.max(1, tier));
        var attack = mob.getAttribute(Attributes.ATTACK_DAMAGE);
        double target = MELEE.get(BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).getPath());
        if (attack == null || target == 0) return; // Spitters only have ranged attacks.
        double nativeBase = DefaultAttributes.getSupplier((net.minecraft.world.entity.EntityType<? extends
                net.minecraft.world.entity.LivingEntity>) mob.getType()).getBaseValue(Attributes.ATTACK_DAMAGE);
        if (nativeBase > 0) attack.addOrReplacePermanentModifier(new AttributeModifier(DAMAGE,
                target / nativeBase - 1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    }

    public static boolean managedProjectile(Entity entity) {
        return entity instanceof Projectile projectile && (entity.getPersistentData().contains(CAP)
                || managed(projectile.getOwner()));
    }

    public static void prepareProjectile(Projectile projectile) {
        if (!managed(projectile.getOwner()) || projectile.getPersistentData().contains(CAP)) return;
        var owner = projectile.getOwner();
        String mob = BuiltInRegistries.ENTITY_TYPE.getKey(owner.getType()).getPath();
        String shot = BuiltInRegistries.ENTITY_TYPE.getKey(projectile.getType()).getPath();
        double cap = mob.equals("brute") ? 8 : shot.equals("acid_ball") ? 6 : shot.equals("thrown_tumor") ? 5 : 4;
        projectile.getPersistentData().putDouble(CAP, cap);
        projectile.getPersistentData().putDouble(SCALE, factor(owner.getPersistentData().getInt(TIER)));
    }

    public static void register() { NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, SporeCombat::damage); }

    private static void damage(LivingIncomingDamageEvent event) {
        Entity direct = event.getSource().getDirectEntity();
        if (direct instanceof Projectile projectile) {
            prepareProjectile(projectile);
            if (!projectile.getPersistentData().contains(CAP)) return;
            double scale = projectile.getPersistentData().getDouble(SCALE);
            double cap = projectile.getPersistentData().getDouble(CAP);
            event.setAmount((float) (Math.min(event.getAmount(), cap) * scale));
        } else if (managed(event.getSource().getEntity())) {
            Entity mob = event.getSource().getEntity();
            String id = BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).getPath();
            // Screw variants keep a bounded ramp; thorn retaliation cannot become a second full hit.
            double scale = factor(mob.getPersistentData().getInt(TIER));
            if (event.getSource().is(DamageTypes.THORNS)) {
                event.setAmount((float) (Math.min(event.getAmount(), 2) * scale));
            } else {
                double cap = id.equals("slasher") ? 10 : MELEE.get(id);
                event.setAmount((float) Math.min(event.getAmount(), cap * scale));
            }
        }
    }
}
