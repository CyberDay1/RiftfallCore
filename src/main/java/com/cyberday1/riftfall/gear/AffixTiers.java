package com.cyberday1.riftfall.gear;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.ItemAttributeModifierEvent;

import com.cyberday1.riftfall.init.RiftfallDataComponents;

/**
 * Scales the designed pool's ×T affixes by the rift tier the item rolled at.
 *
 * <p>
 * Affix data has no tier axis &mdash; values are keyed by rarity and resolved fresh on every read
 * of the stack &mdash; so the tier ride the affix cannot express is applied here, on the same event
 * that materialises affix modifiers. The ×T decision is carried in the affix id itself: ids under
 * {@code riftfall:tiered/} scale, ids under {@code riftfall:flat/} never do. {@code LOW} runs after
 * apothic_attributes has added the affix modifiers at {@code HIGH} and before {@link WeaponFloor}
 * takes the last word at {@code LOWEST}.
 *
 * <p>
 * Idempotent by construction: the event is rebuilt from the component on every fire, so the factor
 * applies once per read and never compounds.
 */
public final class AffixTiers {

    static final String TIERED_PREFIX = "riftfall:tiered/";

    static final String FLAT_PREFIX = "riftfall:flat/";

    private AffixTiers() {
    }

    public static void register() {
        // A lambda rather than a method reference: the testable overload makes the reference ambiguous.
        NeoForge.EVENT_BUS.addListener(EventPriority.LOW,
                (ItemAttributeModifierEvent event) -> scale(event));
    }

    /**
     * The design's tier ride: {@code 1 + 0.06(T-1)}, so T1 is exactly the rolled value.
     *
     * <p>
     * Public so the probe can report the factor it measured against rather than deriving one of its
     * own. A harness that recomputes the ride from its own copy of the constant agrees with this
     * class by luck; one that asks this class agrees by construction, and the rig then checks the
     * answer against {@code 1 + 0.06(T-1)} written out longhand in Python. That is the comparison
     * worth making &mdash; two independent spellings of the design, not one spelling twice.
     */
    public static double tierFactor(int tier) {
        return 1.0D + 0.06D * (tier - 1);
    }

    /** Whether an id was minted from one of the designed pool's affixes. Read by the floors. */
    public static boolean isAffixModifier(ResourceLocation id) {
        String name = id.toString();
        return name.startsWith(TIERED_PREFIX) || name.startsWith(FLAT_PREFIX);
    }

    public static boolean isTiered(ResourceLocation id) {
        return id.toString().startsWith(TIERED_PREFIX);
    }

    static void scale(ItemAttributeModifierEvent event) {
        Integer tier = event.getItemStack().get(RiftfallDataComponents.RIFT_TIER.get());
        if (tier != null) {
            scale(event, tier);
        }
    }

    /** Split from the component read so a test can hand it a tier without a mod registry. */
    static void scale(ItemAttributeModifierEvent event, int tier) {
        if (tier <= 1) {
            return;
        }
        double factor = tierFactor(tier);
        // Collected first: replaceModifier edits the list the view is reading.
        List<ItemAttributeModifiers.Entry> tiered = new ArrayList<>();
        for (ItemAttributeModifiers.Entry entry : event.getModifiers()) {
            if (isTiered(entry.modifier().id())) {
                tiered.add(entry);
            }
        }
        for (ItemAttributeModifiers.Entry entry : tiered) {
            AttributeModifier modifier = entry.modifier();
            event.replaceModifier(entry.attribute(),
                    new AttributeModifier(modifier.id(), modifier.amount() * factor, modifier.operation()),
                    entry.slot());
        }
    }
}
