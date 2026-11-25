package com.cyberday1.riftfall.client;

import com.cyberday1.riftfall.RiftfallCore;
import com.cyberday1.riftfall.gear.GearPool;
import com.cyberday1.riftfall.gear.GunGrade;
import com.cyberday1.riftfall.gear.UniqueWeapons;
import com.cyberday1.riftfall.init.RiftfallDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderTooltipEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

import java.util.List;
import java.util.Optional;

/** Presentation only: read existing rarity and keep the native descriptions, attributes and sockets. */
@EventBusSubscriber(modid = RiftfallCore.MODID, value = Dist.CLIENT)
public final class EquipmentTooltip {
    private EquipmentTooltip() { }

    public record Grade(int color, Component name) { }

    /** Resolve on every hover so old items and datapack rarity changes need no migration. */
    public static Grade grade(ItemStack stack) {
        if (stack.isEmpty()) return null;
        if (ModList.get().isLoaded("apotheosis")) {
            Grade affix = AffixGrade.read(stack);
            if (affix != null) return affix;
        }
        GunGrade gun = GunGrade.read(stack);
        if (gun != null) return new Grade(gun.colour(), Component.translatableWithFallback(
                GunGrade.rarityKey(gun.rarity()), GunGrade.rarityName(gun.rarity())));
        if (GunGrade.isGun(stack) || GearPool.classify(stack.getItem()) != null) {
            return new Grade(0x808080, Component.translatable("tooltip.riftfall.no_affix_rarity"));
        }
        return null;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void describe(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        Grade grade = grade(stack);
        if (grade == null) return;
        // GunGradeTooltip already provides its tier/rarity and bullet-damage lines.
        decorate(event.getToolTip(), grade, stack.getOrDefault(RiftfallDataComponents.RIFT_TIER.get(), 0),
                UniqueWeapons.isUnique(stack.getItem()), GunGrade.read(stack) == null);
    }

    public static void decorate(List<Component> lines, Grade grade, int tier, boolean unique, boolean addGrade) {
        if (lines.isEmpty()) return;
        lines.set(0, recolor(lines.getFirst(), grade.color()));
        int index = 1;
        if (addGrade) {
            Component label = tier > 0
                    ? Component.translatable("tooltip.riftfall.equipment_grade", grade.name(), tier)
                    : grade.name();
            lines.add(index++, label.copy().withStyle(Style.EMPTY.withColor(grade.color())));
        }
        if (unique) lines.add(index, Component.translatable("tooltip.riftfall.unique_weapon")
                .withStyle(ChatFormatting.GRAY));
    }

    /** Root-only styling leaves explicit donor colors on child components. Recolor every rendered run. */
    static MutableComponent recolor(Component name, int color) {
        MutableComponent result = Component.empty().withStyle(Style.EMPTY.withColor(color));
        name.visit((style, text) -> {
            result.append(Component.literal(ChatFormatting.stripFormatting(text)).withStyle(style.withColor(color)));
            return Optional.empty();
        }, Style.EMPTY);
        return result;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void border(RenderTooltipEvent.Color event) {
        Grade grade = grade(event.getItemStack());
        if (grade == null) return;
        event.setBorderStart(0xFF000000 | grade.color());
        event.setBorderEnd(0xFF000000 | grade.color());
    }

    /** Optional dependency stays behind the mod-loaded check. */
    private static final class AffixGrade {
        static Grade read(ItemStack stack) {
            var rarity = dev.shadowsoffire.apotheosis.affix.AffixHelper.getRarity(stack);
            return rarity.isBound() ? new Grade(rarity.get().color().getValue(), rarity.get().toComponent()) : null;
        }
    }
}
