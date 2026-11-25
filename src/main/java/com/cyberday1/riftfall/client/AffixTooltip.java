package com.cyberday1.riftfall.client;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.event.GatherSkippedAttributeTooltipsEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.AttributeTooltipContext;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import org.jetbrains.annotations.Nullable;

import com.cyberday1.riftfall.RiftfallCore;
import com.cyberday1.riftfall.gear.AffixTiers;

/**
 * Puts the designed pool's affix names on the tooltip, and takes the anonymous numbers off it.
 *
 * <p>
 * Apotheosis names effect affixes and deliberately never names attribute ones, so an all-attribute
 * pool renders as bare stat lines with nothing saying an affix produced them. The two hooks are one
 * move: the skip removes our modifiers from the vanilla attribute block, the tooltip puts each back
 * under its own name. Skipping is what keeps the number from appearing twice.
 */
@EventBusSubscriber(modid = RiftfallCore.MODID, value = Dist.CLIENT)
public final class AffixTooltip {

    /**
     * Resolved on first tooltip rather than in a static initialiser: this class is loaded for its
     * annotations during mod construction, and {@link ModList} is not answerable that early.
     */
    @Nullable
    private static Boolean apotheosis;

    @Nullable
    private static Boolean curios;

    /** Whether {@link #attach} has already run. Both hooks go on together or not at all. */
    private static boolean attached;

    private AffixTooltip() {
    }

    /**
     * Puts both hooks on the bus by hand, and says whether it had to.
     *
     * <p>
     * The annotation above is {@code Dist.CLIENT}, which is right for shipping and wrong for
     * measuring: a dedicated server registers neither hook, so a headless run of the tooltip would
     * be reading the absence of this class rather than its behaviour. The probe calls this first, so
     * what it then measures is these two methods and not a stand-in for them.
     *
     * <p>
     * Returns false on a client and on a second call, because the hooks are already on in both cases
     * and attaching twice would print every affix line twice &mdash; which is one of the things the
     * probe exists to catch, and it must not be the probe that causes it.
     */
    public static synchronized boolean attach() {
        if (attached || FMLEnvironment.dist.isClient()) {
            return false;
        }
        attached = true;
        NeoForge.EVENT_BUS.addListener(EventPriority.LOW, AffixTooltip::nameTheAffixes);
        NeoForge.EVENT_BUS.addListener(AffixTooltip::skipOurModifiers);
        return true;
    }

    /**
     * {@code LOW} so the lines land after Apotheosis' own default-priority handler has had the
     * tooltip, rather than in the middle of it.
     *
     * <p>
     * Public because the probe calls it by name. ⛔ That is not tidiness &mdash; this pack cannot
     * build a whole tooltip on a dedicated server (two different mods' listeners die on one), so a
     * headless reading has to reach this body directly rather than through a broadcast. Calling it
     * measures the shipped listener; that the listener is also ON the bus is a separate claim, and
     * {@code AffixTooltipAttachTest} is what holds it.
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void nameTheAffixes(ItemTooltipEvent event) {
        if (!apotheosisLoaded()) {
            return;
        }
        AttributeTooltipContext context =
                AttributeTooltipContext.of(event.getEntity(), event.getContext(), event.getFlags());
        List<Component> lines = AffixLines.build(event.getItemStack(), context);
        if (!lines.isEmpty()) {
            // Directly under the item's name, which is where Apotheosis puts its own affix lines.
            List<Component> tooltip = event.getToolTip();
            tooltip.addAll(Math.min(1, tooltip.size()), lines);
        }
    }

    /**
     * Not gated on Apotheosis: the ids are matched by prefix and nothing else mints them, so a pack
     * without Apotheosis has none to skip and the walk costs one empty list.
     */
    @SubscribeEvent
    static void skipOurModifiers(GatherSkippedAttributeTooltipsEvent event) {
        for (ResourceLocation id : ours(event.getStack().getAttributeModifiers())) {
            event.skipId(id);
        }
        // The curio ids AffixLines prints on the affix line, so Curios' slot block does not too.
        if (apotheosisLoaded() && curiosLoaded()) {
            for (ResourceLocation id : AffixLines.wornSkips(event.getStack(),
                    event.getContext().player())) {
                event.skipId(id);
            }
        }
    }

    /**
     * Every id on the stack that the designed pool minted.
     *
     * <p>
     * Read off the resolved modifiers rather than off the affixes, so the skip does not depend on
     * the affix component parsing and cannot leave a line behind that the named pass also draws.
     */
    static List<ResourceLocation> ours(ItemAttributeModifiers resolved) {
        List<ResourceLocation> ids = new ArrayList<>();
        for (ItemAttributeModifiers.Entry entry : resolved.modifiers()) {
            ResourceLocation id = entry.modifier().id();
            if (AffixTiers.isAffixModifier(id)) {
                ids.add(id);
            }
        }
        return ids;
    }

    /**
     * Every resolved entry an affix minted, in the order the stack carries them.
     *
     * <p>
     * ⛔ This match is the whole reason the value is read off the stack. {@code getAugmentingText}
     * recomputes from the affix's own value factory, which is the unscaled roll, so above tier 1 it
     * understates by {@link AffixTiers#tierFactor}. The resolved entries have already been through
     * {@link AffixTiers}, so they carry the numbers the player actually gets.
     *
     * <p>
     * A prefix match rather than an equals, because an affix is not always one modifier: an
     * {@code attribute} affix mints {@code makeUniqueId()} exactly, but a {@code multi_attr} one
     * mints {@code makeUniqueId("0")}, {@code ("1")} and so on. The bare id ends in the
     * {@code _modifier_<slots>_} that {@code Affix.makeUniqueId} appends, so one affix id can never
     * be a prefix of another's minted id even when one path is a prefix of the other.
     */
    static List<ItemAttributeModifiers.Entry> matching(ItemAttributeModifiers resolved,
            ResourceLocation bare) {
        String prefix = bare.toString();
        List<ItemAttributeModifiers.Entry> entries = new ArrayList<>();
        for (ItemAttributeModifiers.Entry entry : resolved.modifiers()) {
            if (entry.modifier().id().toString().startsWith(prefix)) {
                entries.add(entry);
            }
        }
        return entries;
    }

    /**
     * An affix's Curios-delivered entries: the first slot's that has any, and none at all when the
     * component minted it (the component line wins and Curios keeps its own).
     */
    static List<ItemAttributeModifiers.Entry> wornMatching(ItemAttributeModifiers resolved,
            List<ItemAttributeModifiers> worn, ResourceLocation bare) {
        if (!matching(resolved, bare).isEmpty()) {
            return List.of();
        }
        for (ItemAttributeModifiers slot : worn) {
            List<ItemAttributeModifiers.Entry> entries = matching(slot, bare);
            if (!entries.isEmpty()) {
                return entries;
            }
        }
        return List.of();
    }

    /** Every slot's curio id for the affixes {@link #wornMatching} would print. */
    static List<ResourceLocation> wornSkips(ItemAttributeModifiers resolved,
            List<ItemAttributeModifiers> worn, List<ResourceLocation> affixes) {
        List<ResourceLocation> ids = new ArrayList<>();
        for (ResourceLocation bare : affixes) {
            if (!AffixTiers.isAffixModifier(bare) || !matching(resolved, bare).isEmpty()) {
                continue;
            }
            for (ItemAttributeModifiers slot : worn) {
                for (ItemAttributeModifiers.Entry entry : matching(slot, bare)) {
                    ids.add(entry.modifier().id());
                }
            }
        }
        return ids;
    }

    static boolean curiosLoaded() {
        if (curios == null) {
            curios = ModList.get().isLoaded("curios");
        }
        return curios;
    }

    private static boolean apotheosisLoaded() {
        if (apotheosis == null) {
            apotheosis = ModList.get().isLoaded("apotheosis");
        }
        return apotheosis;
    }
}
