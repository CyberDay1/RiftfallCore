package com.cyberday1.riftfall.init;

import com.cyberday1.riftfall.RiftfallCore;
import com.cyberday1.riftfall.boss.UnseatedPreviewEntity;
import com.cyberday1.riftfall.boss.UnseatedEntity;
import com.cyberday1.riftfall.boss.UnseatedTetherEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import java.util.function.Supplier;

public final class RiftfallEntities {
    private static final DeferredRegister<EntityType<?>> TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, RiftfallCore.MODID);
    public static final Supplier<EntityType<UnseatedPreviewEntity>> UNSEATED_PREVIEW =
            TYPES.register("unseated_preview", () -> EntityType.Builder
                    .<UnseatedPreviewEntity>of(UnseatedPreviewEntity::new, MobCategory.MISC)
                    .sized(10F, 16F).fireImmune().clientTrackingRange(12).updateInterval(20)
                    .build("riftfall:unseated_preview"));
    public static final Supplier<EntityType<UnseatedEntity>> UNSEATED =
            TYPES.register("unseated", () -> EntityType.Builder
                    .<UnseatedEntity>of(UnseatedEntity::new, MobCategory.MISC)
                    .sized(8F,15F).fireImmune().clientTrackingRange(12).updateInterval(1)
                    .build("riftfall:unseated"));
    // ASCENT's tethers (F7): stationary, so the slow update interval is enough.
    public static final Supplier<EntityType<UnseatedTetherEntity>> UNSEATED_TETHER =
            TYPES.register("unseated_tether", () -> EntityType.Builder
                    .<UnseatedTetherEntity>of(UnseatedTetherEntity::new, MobCategory.MISC)
                    .sized(1.5F,5F).fireImmune().clientTrackingRange(12).updateInterval(20)
                    .build("riftfall:unseated_tether"));

    private RiftfallEntities() {}
    public static void register(IEventBus bus) {
        TYPES.register(bus);
        bus.addListener((net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent event) ->
                {event.put(UNSEATED.get(),UnseatedEntity.attributes().build());
                    event.put(UNSEATED_TETHER.get(),UnseatedTetherEntity.attributes().build());});
    }
}
