package com.cyberday1.riftfall.client;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.GatherSkippedAttributeTooltipsEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import org.jetbrains.annotations.Nullable;

import com.cyberday1.riftfall.RiftfallCore;
import com.cyberday1.riftfall.gear.RelicSignatureProcs;
import com.cyberday1.riftfall.gear.RelicSignatureStats;
import com.cyberday1.riftfall.gear.RelicSignatures;
import com.cyberday1.riftfall.gear.RelicStats;

/**
 * A relic's fixed lines under a "Relic" header, right below its name, and the same ids skipped from Curios'
 * "When worn" block so each number shows once. Values come off Curios' own modifier event, so a tier-stamped
 * relic shows its ridden health. Common code only: the rig probe calls {@link #lines} on a server.
 */
@EventBusSubscriber(modid = RiftfallCore.MODID, value = Dist.CLIENT)
public final class RelicTooltip {

    public static final String HEADER = "tooltip." + RiftfallCore.MODID + ".relic";
    public static final String SIGNATURE = "tooltip." + RiftfallCore.MODID + ".relic_signature.";

    private RelicTooltip() {
    }

    /** {@code LOWEST}, after the affix lines went in at 1, so the relic block sits above them. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void describe(ItemTooltipEvent event) {
        List<Component> lines = new ArrayList<>(lines(event.getItemStack(), event.getEntity(), event.getFlags()));
        if (lines.isEmpty()) return;
        lines.addAll(signatureLines(event.getItemStack(), event.getEntity()));
        List<Component> tooltip = event.getToolTip();
        tooltip.addAll(Math.min(1, tooltip.size()), lines);
    }

    @SubscribeEvent
    static void skipFixed(GatherSkippedAttributeTooltipsEvent event) {
        skips(event.getStack(), event.getContext().player()).forEach(event::skipId);
    }

    /** The header and one line per fixed stat, read off the first slot Curios would print. */
    public static List<Component> lines(ItemStack stack, @Nullable Player player, TooltipFlag flag) {
        List<RelicStats.Line> fixed = RelicStats.lines(stack);
        if (fixed.isEmpty()) return List.of();
        List<Component> out = new ArrayList<>();
        out.add(Component.translatable(HEADER).withStyle(ChatFormatting.GOLD));
        List<ItemAttributeModifiers.Entry> worn = worn(stack, player);
        if (!worn.isEmpty()) {
            worn.forEach(e -> out.add(line(e.attribute().value().toComponent(e.modifier(), flag))));
            return out;
        }
        ResourceLocation item = BuiltInRegistries.ITEM.getKey(stack.getItem());
        for (int n = 0; n < fixed.size(); n++) {
            RelicStats.Line l = fixed.get(n);
            int index = n;
            BuiltInRegistries.ATTRIBUTE.getHolder(l.attribute()).ifPresent(attribute -> out.add(line(
                    attribute.value().toComponent(new AttributeModifier(RelicStats.id(item, index, l.tiered(),
                            "curio", 0), l.amount(), l.operation()), flag))));
        }
        return out;
    }

    /**
     * A live signature's name and effect under the fixed lines; a trade-off's red cost; "Dormant" when the viewer
     * wears it past the combat cap. Signatures still unbuilt show nothing.
     */
    public static List<Component> signatureLines(ItemStack stack, @Nullable Player viewer) {
        if (stack.isEmpty()) return List.of();
        RelicSignatures.Signature signature = RelicSignatures.of(BuiltInRegistries.ITEM.getKey(stack.getItem()));
        if (signature == null || !signature.live()) return List.of();
        String key = SIGNATURE + signature.key();
        List<Component> out = new ArrayList<>();
        out.add(line(Component.translatable(key).withStyle(ChatFormatting.AQUA)));
        out.add(indent(Component.translatable(effectKey(signature), args(signature, true)), ChatFormatting.GRAY));
        if (signature.kind() == RelicSignatures.Kind.TRADE_OFF) {
            out.add(indent(Component.translatable(SIGNATURE + "cost",
                    Component.translatable(key + ".cost", args(signature, false))), ChatFormatting.RED));
        }
        if (signature == RelicSignatures.Signature.NIGHTSTALKER && viewer != null) {
            out.add(indent(Component.translatable(key + ".light",
                    RelicSignatureStats.lightAt(viewer.level(), viewer.blockPosition())), ChatFormatting.DARK_GRAY));
        }
        if (viewer != null && RelicSignatures.active(RelicSignatures.worn(viewer)).dormant().contains(signature)) {
            out.add(indent(Component.translatable(SIGNATURE + "dormant"), ChatFormatting.DARK_GRAY));
        }
        return out;
    }

    /** Ground Pound's line follows its damage switch, so the knockback-only fallback stays a one-line edit. */
    public static String effectKey(RelicSignatures.Signature signature) {
        String key = SIGNATURE + signature.key();
        return signature == RelicSignatures.Signature.GROUND_POUND && !RelicSignatureProcs.POUND_DAMAGES
                ? key + ".effect_knockback" : key + ".effect";
    }

    /** The numbers a line names that live in code rather than in its text. */
    private static Object[] args(RelicSignatures.Signature signature, boolean effect) {
        if (signature == RelicSignatures.Signature.NIGHTSTALKER) {
            return new Object[] {effect ? RelicSignatureStats.NIGHT_DARK : RelicSignatureStats.NIGHT_BRIGHT};
        }
        return new Object[0];
    }

    private static Component indent(Component text, ChatFormatting style) {
        return Component.literal("  ").append(text).withStyle(style);
    }

    /** Every fixed id Curios' block would print, in every slot it lists. */
    public static List<ResourceLocation> skips(ItemStack stack, @Nullable Player player) {
        if (RelicStats.lines(stack).isEmpty() || !AffixTooltip.curiosLoaded()) return List.of();
        List<ResourceLocation> ids = new ArrayList<>();
        for (ItemAttributeModifiers slot : AffixCurios.worn(stack, player)) {
            for (ItemAttributeModifiers.Entry entry : slot.modifiers()) {
                if (RelicStats.isFixed(entry.modifier().id())) ids.add(entry.modifier().id());
            }
        }
        return ids;
    }

    private static List<ItemAttributeModifiers.Entry> worn(ItemStack stack, @Nullable Player player) {
        if (!AffixTooltip.curiosLoaded()) return List.of();
        for (ItemAttributeModifiers slot : AffixCurios.worn(stack, player)) {
            List<ItemAttributeModifiers.Entry> fixed = slot.modifiers().stream()
                    .filter(e -> RelicStats.isFixed(e.modifier().id())).toList();
            if (!fixed.isEmpty()) return fixed;
        }
        return List.of();
    }

    private static Component line(Component stat) {
        return Component.literal(" ").append(stat);
    }
}
