package com.cyberday1.riftfall.gear;

import com.cyberday1.riftfall.RiftfallCore;
import com.cyberday1.riftfall.init.RiftfallDataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.minecraft.world.entity.item.ItemEntity;

import java.util.List;
import java.util.Set;

/** Imported combat equipment is earned through Riftfall, never through a donor's rewards or shops. */
@EventBusSubscriber(modid = RiftfallCore.MODID)
public final class DonorProgression {
    public static final Set<String> MODS = Set.of("better_weaponry", "glixtors_random_additions",
            "more_and_more_armor4", "op_scythes", "scythes", "simple_staves", "too_many_bows");
    private static final ResourceLocation WITHERING_MINION = ResourceLocation.fromNamespaceAndPath("scythes", "withering_minion");

    private DonorProgression() { }

    public static boolean gear(ItemStack stack) {
        return !stack.isEmpty() && MODS.contains(BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace())
                && GearPool.owned(stack.getItem().builtInRegistryHolder()) != null;
    }

    public static void stripLoot(List<ItemStack> loot, boolean rift) {
        if (!rift) loot.removeIf(DonorProgression::gear);
    }

    /** Tools and optional-mod-free accessories may have neither affixes nor a stat floor. */
    public static ItemStack earned(ItemStack stack, int tier) {
        if (gear(stack)) stack.set(RiftfallDataComponents.RIFT_TIER.get(), Math.max(1, tier));
        return stack;
    }

    public static boolean unrolled(ItemStack stack) {
        if (!gear(stack) || stack.getOrDefault(RiftfallDataComponents.RIFT_TIER.get(), 0) > 0) return false;
        // Floors also identify legitimate rewards when the optional affix mod is absent.
        return stack.getOrDefault(net.minecraft.core.component.DataComponents.ATTRIBUTE_MODIFIERS,
                net.minecraft.world.item.component.ItemAttributeModifiers.EMPTY).modifiers().stream().noneMatch(entry -> {
                    String id = entry.modifier().id().toString();
                    return id.startsWith(WeaponFloor.FLOOR_PREFIX) || id.startsWith("riftfall:defence_floor/");
                });
    }

    /** Direct custom rewards bypass loot tables. Earned gear dropped by players retains its tier. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void spawned(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        // Old scythe pets can be restored at login without another weapon activation.
        if (WITHERING_MINION.equals(BuiltInRegistries.ENTITY_TYPE.getKey(event.getEntity().getType()))) {
            event.setCanceled(true);
        } else if (event.getEntity() instanceof ItemEntity item && unrolled(item.getItem())) {
            event.setCanceled(true);
        }
    }
}
