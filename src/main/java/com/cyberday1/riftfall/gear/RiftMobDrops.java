package com.cyberday1.riftfall.gear;

import com.cyberday1.riftfall.RiftfallCore;
import com.cyberday1.riftfall.rift.RiftLevels;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import java.util.function.Supplier;

/** Normal mob death loot is not a rift reward. XP and Riftfall's separate boss rolls still apply. */
@EventBusSubscriber(modid = RiftfallCore.MODID)
public final class RiftMobDrops {
    private RiftMobDrops() {}
    private static final ThreadLocal<Entity> SOURCE = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> REWARD = ThreadLocal.withInitial(() -> false);

    /** Track the actual executing entity, never infer ownership from proximity to a corpse. */
    public static <T> T during(Entity source, Supplier<T> work) {
        Entity before = SOURCE.get();
        SOURCE.set(source);
        try { return work.get(); }
        finally {
            if (before == null) SOURCE.remove(); else SOURCE.set(before);
        }
    }

    /** A tick {@link #during} would change nothing for: this entity suppresses no drop and no outer source needs masking. */
    public static boolean untracked(Entity source) {
        return !(source instanceof LivingEntity living && suppress(living)) && SOURCE.get() == null;
    }

    /** Only Riftfall's authored boss reward spawns bypass the normal mob-drop rule. */
    public static <T> T reward(Supplier<T> work) {
        boolean before = REWARD.get();
        REWARD.set(true);
        try { return work.get(); }
        finally { if (before) REWARD.set(true); else REWARD.remove(); }
    }

    public static boolean suppressSpawn(Entity source) {
        return !REWARD.get() && source instanceof LivingEntity living && suppress(living);
    }

    /** MCreator and other mods construct ItemEntities directly in damage/death/tick procedures. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void directDrops(EntityJoinLevelEvent event) {
        if (!(event.getEntity() instanceof ItemEntity) || event.loadedFromDisk()) return;
        Entity source = SOURCE.get();
        if (source != null) {
            if (source.level() == event.getLevel() && suppressSpawn(source)) event.setCanceled(true);
            return;
        }
        if (!REWARD.get() && event.getLevel() instanceof ServerLevel level && RiftLevels.isRiftWorld(level)
                && mobOverrideOnStack()) event.setCanceled(true);
    }

    /** The living-entity methods a subclass overrides to drop items from: MixinLivingEntity wraps only LivingEntity's own. */
    private static final java.util.Set<String> MOB_HOOKS = java.util.Set.of("hurt", "actuallyHurt", "die",
            "dropAllDeathLoot", "dropCustomDeathLoot", "dropFromLootTable", "dropEquipment");
    private static final int MOB_HOOK_DEPTH = 64;
    private static final StackWalker WALKER = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);

    /**
     * Whether the innermost living entity's damage or death method on this thread is a mob's (not a player's or an armor
     * stand's). A subclass's {@code hurt} that spawns before {@code super.hurt} (primitive_creatures' diloger: gunpowder),
     * or a {@code die} that spawns after {@code super.die} (its tfc: clay), runs outside the wrapped LivingEntity body,
     * so {@link #SOURCE} is unset.
     */
    static boolean mobOverrideOnStack() {
        return WALKER.walk(frames -> frames.limit(MOB_HOOK_DEPTH)
                .filter(f -> MOB_HOOKS.contains(f.getMethodName()) && LivingEntity.class.isAssignableFrom(f.getDeclaringClass()))
                .findFirst()
                .map(f -> net.minecraft.world.entity.Mob.class.isAssignableFrom(f.getDeclaringClass()))
                .orElse(false));
    }

    public static boolean suppress(LivingEntity entity) {
        return !(entity instanceof Player) && entity.level() instanceof ServerLevel level
                && RiftLevels.isRiftWorld(level);
    }

    /** Also covers mods which roll a death table and insert ItemEntities directly, bypassing LivingDropsEvent. */
    public static void strip(ObjectArrayList<ItemStack> loot, LootContext context) {
        if (AbilityDropBan.isDeathLoot(context) && RiftLevels.isRiftWorld(context.getLevel())) loot.clear();
    }

    /** Equipment, custom drops and event-added drops join the captured list after the table roll. */
    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void drops(LivingDropsEvent event) {
        if (suppress(event.getEntity())) {
            event.getDrops().clear();
            event.setCanceled(true);
        }
    }
}
