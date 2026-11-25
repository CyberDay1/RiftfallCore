package com.cyberday1.riftfall.skill;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Supplier;

/** Opens a Phantom signature sight where a signature ultimate lands. Loaded only when Spell Engine is present. */
public final class PhantomSignatures {

    private static final DeferredRegister<EntityType<?>> TYPES = DeferredRegister.create(Registries.ENTITY_TYPE, "riftfall");
    public static final Supplier<EntityType<PhantomSignatureEntity>> SIGNATURE = TYPES.register("phantom_signature",
            () -> EntityType.Builder.<PhantomSignatureEntity>of(PhantomSignatureEntity::new, MobCategory.MISC)
                    .sized(0.5F, 0.5F).noSave().noSummon().fireImmune()
                    .clientTrackingRange(8).updateInterval(20).build("riftfall:phantom_signature"));
    private static final float RADIUS = 6.0F;

    private PhantomSignatures() {
    }

    public static void register(IEventBus bus) {
        TYPES.register(bus);
    }

    /** After a cast is kept: at the first target, else the aimed point. Nothing for any other spell. */
    static void cast(ServerLevel level, LivingEntity caster, ResourceLocation spell, List<Entity> targets,
                     @Nullable Vec3 location) {
        PhantomSignaturePlan.Kind kind = PhantomSignaturePlan.of(spell.toString());
        if (kind == null) return;
        Vec3 at = location;
        for (Entity target : targets) {
            if (target != caster) { at = target.position(); break; }
        }
        if (at == null) return;
        PhantomSignatureEntity sight = SIGNATURE.get().create(level);
        if (sight == null) return;
        sight.moveTo(at.x, at.y, at.z, 0.0F, 0.0F);
        sight.arm(kind, RADIUS);
        level.addFreshEntity(sight);
    }
}
