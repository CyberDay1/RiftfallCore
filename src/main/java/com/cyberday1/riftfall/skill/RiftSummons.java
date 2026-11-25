package com.cyberday1.riftfall.skill;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/** Loaded only when Spell Engine is present. Combat and lifetime are authored in spell data. */
public final class RiftSummons {
    private static final DeferredRegister<EntityType<?>> TYPES = DeferredRegister.create(Registries.ENTITY_TYPE, "riftfall");
    public static final Map<String, Supplier<EntityType<RiftSummon>>> ALL = new LinkedHashMap<>();
    static {
        for (String name : new String[]{"imp", "sprite", "skeleton", "ghoul", "lich", "demon_knight",
                "fire_elemental", "storm_elemental", "ancestral_spirit", "wraith", "plague_servant",
                "spirit_healer", "guardian_spirit"}) {
            boolean small = name.equals("imp") || name.equals("sprite");
            ALL.put(name, TYPES.register("summon_" + name, () -> EntityType.Builder
                    .of(RiftSummon::new, MobCategory.MISC).sized(small ? .5F : .6F, small ? .9F : 1.95F)
                    .clientTrackingRange(10).updateInterval(2).build("riftfall:summon_" + name)));
        }
    }
    private RiftSummons() { }
    public static void register(IEventBus bus) {
        TYPES.register(bus);
        bus.addListener(RiftSummons::attributes);
    }
    private static void attributes(EntityAttributeCreationEvent event) {
        for (var type : ALL.values()) {
            var attributes = net.minecraft.world.entity.Mob.createMobAttributes()
                    .add(Attributes.MAX_HEALTH, 4).add(Attributes.MOVEMENT_SPEED, .30)
                    .add(Attributes.FOLLOW_RANGE, 24).add(Attributes.ATTACK_DAMAGE, 0)
                    .add(Attributes.ATTACK_KNOCKBACK, 0).add(Attributes.KNOCKBACK_RESISTANCE, 0)
                    .add(Attributes.ARMOR, 0);
            for (String school : new String[]{"fire", "frost", "arcane", "healing", "haste"}) {
                BuiltInRegistries.ATTRIBUTE.getHolder(ResourceLocation.parse("spell_power:" + school))
                        .ifPresent(holder -> attributes.add(holder, school.equals("haste") ? 100 : 0));
            }
            event.put(type.get(), attributes.build());
        }
    }
}
