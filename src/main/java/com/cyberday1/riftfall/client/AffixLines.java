package com.cyberday1.riftfall.client;

import java.util.ArrayList;
import java.util.List;

import dev.shadowsoffire.apotheosis.affix.AffixHelper;
import dev.shadowsoffire.apotheosis.affix.AffixInstance;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.neoforged.neoforge.common.util.AttributeTooltipContext;
import org.jetbrains.annotations.Nullable;

import com.cyberday1.riftfall.RiftfallCore;
import com.cyberday1.riftfall.gear.AffixTiers;

/**
 * Builds one tooltip line per designed affix: its name, then the stat it is actually granting.
 *
 * <p>
 * Split from {@link AffixTooltip} for the reason {@code RiftRarity} is split from
 * {@code RiftLootRarity} &mdash; this is the only client class that names an Apotheosis type, and
 * keeping it behind a guarded call means a pack without Apotheosis never loads it.
 */
final class AffixLines {

    private static final String LINE_KEY = "tooltip." + RiftfallCore.MODID + ".affix_line";

    /** Apotheosis' own bullet, so our lines sit in the same column as the ones it draws itself. */
    private static final String DOT_KEY = "text.apotheosis.dot_prefix";

    private AffixLines() {
    }

    static List<Component> build(ItemStack stack, AttributeTooltipContext context) {
        if (!AffixHelper.hasAffixes(stack)) {
            return List.of();
        }
        ItemAttributeModifiers resolved = stack.getAttributeModifiers();
        List<ItemAttributeModifiers> worn = worn(stack, resolved, context.player());
        List<Component> lines = new ArrayList<>();
        AffixHelper.streamAffixes(stack).forEach(instance -> {
            Component line = line(instance, resolved, worn, context);
            if (line != null) {
                lines.add(line);
            }
        });
        return lines;
    }

    /** The curio ids {@link #build} puts on affix lines, for {@code AffixTooltip}'s skip. */
    static List<ResourceLocation> wornSkips(ItemStack stack, @Nullable Player player) {
        if (!AffixHelper.hasAffixes(stack)) {
            return List.of();
        }
        ItemAttributeModifiers resolved = stack.getAttributeModifiers();
        List<ItemAttributeModifiers> worn = worn(stack, resolved, player);
        if (worn.isEmpty()) {
            return List.of();
        }
        return AffixTooltip.wornSkips(resolved, worn, ids(stack));
    }

    /**
     * Curios' per-slot modifiers, read only when a designed affix minted nothing on the component.
     * A spellbook or trinket carries its affix stats there (Apothic's Curios bridge), not on the stack.
     */
    private static List<ItemAttributeModifiers> worn(ItemStack stack,
            ItemAttributeModifiers resolved, @Nullable Player player) {
        if (!AffixTooltip.curiosLoaded()) {
            return List.of();
        }
        for (ResourceLocation id : ids(stack)) {
            if (AffixTiers.isAffixModifier(id) && AffixTooltip.matching(resolved, id).isEmpty()) {
                return AffixCurios.worn(stack, player);
            }
        }
        return List.of();
    }

    private static List<ResourceLocation> ids(ItemStack stack) {
        List<ResourceLocation> ids = new ArrayList<>();
        AffixHelper.streamAffixes(stack).forEach(instance -> {
            if (instance.isValid()) {
                ids.add(instance.makeUniqueId());
            }
        });
        return ids;
    }

    /**
     * {@code getName(true)} is the prefix half of the pair &mdash; "Barbed", not "of the Broadhead".
     * Apotheosis passes {@code true} for the word that goes in front of an item's name, which is the
     * one that reads as a label.
     *
     * <p>
     * A name with no modifier behind it is still worth a line: it says an affix is present when the
     * stat it granted has been merged away or removed by something downstream. The one
     * {@code multi_attr} affix is two modifiers the player is meant to read as one stat, so its
     * values share a line rather than repeating the name.
     */
    private static Component line(AffixInstance instance, ItemAttributeModifiers resolved,
            List<ItemAttributeModifiers> worn, AttributeTooltipContext context) {
        if (!instance.isValid()) {
            return null;
        }
        ResourceLocation id = instance.makeUniqueId();
        if (!AffixTiers.isAffixModifier(id)) {
            return null;
        }
        Component name = instance.getName(true).copy().withStyle(ChatFormatting.GRAY);
        List<ItemAttributeModifiers.Entry> entries = AffixTooltip.matching(resolved, id);
        if (entries.isEmpty()) {
            entries = AffixTooltip.wornMatching(resolved, worn, id);
        }
        if (entries.isEmpty()) {
            return dot(name);
        }
        MutableComponent values = null;
        for (ItemAttributeModifiers.Entry entry : entries) {
            Component value = entry.attribute().value().toComponent(entry.modifier(), context.flag());
            values = values == null
                    ? value.copy()
                    : values.append(CommonComponents.SPACE).append(value);
        }
        return dot(Component.translatable(LINE_KEY, name, values));
    }

    private static Component dot(Component line) {
        return Component.translatable(DOT_KEY, line);
    }
}
