package com.cyberday1.riftfall.skill;

import com.cyberday1.riftfall.RiftfallCore;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.api.spell.event.SpellHandlers;
import net.spell_engine.internals.SpellExecution;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Supplier;

/**
 * Blade Rain's delivery: a Spell Engine {@code CUSTOM} delivery, {@code riftfall:rain/blade}, that opens a
 * {@link BladeRainEntity} storm at the aimed point. The cast is priced, ranked and gated as every Riftfall cast is;
 * the storm then delivers the spell's impacts one blade at a time. Loaded only when Spell Engine is present.
 */
public final class BladeRains {

    private static final DeferredRegister<EntityType<?>> TYPES = DeferredRegister.create(Registries.ENTITY_TYPE, "riftfall");
    public static final Supplier<EntityType<BladeRainEntity>> BLADE_RAIN = TYPES.register("blade_rain",
            () -> EntityType.Builder.<BladeRainEntity>of(BladeRainEntity::new, MobCategory.MISC)
                    .sized(0.5F, 0.5F).noSave().noSummon().fireImmune()
                    .clientTrackingRange(6).updateInterval(20).build("riftfall:blade_rain"));

    private BladeRains() {
    }

    public static void register(IEventBus bus) {
        TYPES.register(bus);
    }

    static void registerDelivery() {
        SpellHandlers.registerCustomDelivery(RiftfallCore.id(BladeRainPlan.HANDLER), BladeRains::start);
    }

    /** Opens the storm where the engine aimed: its point, else the aimed entity, else straight ahead at range. */
    static boolean start(Level world, Holder<Spell> spell, LivingEntity caster,
                         List<SpellExecution.DeliveryTarget> targets, SpellExecution.ImpactContext context,
                         @Nullable Vec3 location) {
        if (world.isClientSide() || spell.value().area_impact == null) return false;
        Vec3 at = centre(spell.value(), caster, targets, location);
        BladeRainEntity storm = BLADE_RAIN.get().create(world);
        if (storm == null) return false;
        storm.moveTo(at.x, at.y, at.z, 0.0F, 0.0F);
        Spell.AreaImpact area = spell.value().area_impact;
        float radius = context.power() == null ? area.radius : area.combinedRadius(context.power().baseValue());
        storm.arm(caster, spell, context, radius);
        return world.addFreshEntity(storm);
    }

    private static Vec3 centre(Spell spell, LivingEntity caster, List<SpellExecution.DeliveryTarget> targets,
                               @Nullable Vec3 location) {
        if (location != null) return location;
        for (SpellExecution.DeliveryTarget target : targets) {
            Entity entity = target.entity();
            if (entity != null && entity != caster) return entity.position();
        }
        Vec3 look = caster.getLookAngle();
        return caster.position().add(new Vec3(look.x, 0.0, look.z).normalize().scale(spell.range * caster.getScale()));
    }
}
