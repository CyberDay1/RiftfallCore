package com.cyberday1.riftfall.skill;

import io.redspace.ironsspellbooks.api.events.SpellCooldownAddedEvent;
import io.redspace.ironsspellbooks.api.events.SpellOnCastEvent;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.CastType;
import io.redspace.ironsspellbooks.damage.SpellDamageSource;
import io.redspace.ironsspellbooks.network.casting.OnCastStartedPacket;
import io.redspace.ironsspellbooks.network.casting.UpdateCastingStatePacket;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.atomic.AtomicLong;

/**
 * The cast half of H5: Riftfall's own cast of an {@code IRONS_SPELLBOOKS} node, reached only from
 * behind {@code SkillCasts}' presence check. Casts the way irons_apothic's zero-cast-time helper does,
 * from {@code COMMAND} with no cooldown, so Iron's charges nothing. For the length of the cast Iron's
 * spell power is the node's multiplier times its power attribute ({@link IronsPower}).
 */
public final class RiftIronsCast {

    /** Iron's casting-slot string for a cast no item started. */
    static final String SLOT = "riftfall";
    private static final ResourceLocation POWER_ID =
            ResourceLocation.fromNamespaceAndPath("riftfall", "irons_node_power");

    private static final AtomicLong CAST = new AtomicLong();
    private static final AtomicLong BUSY = new AtomicLong();
    private static final AtomicLong NO_TARGET = new AtomicLong();
    private static final AtomicLong CHANNELLED = new AtomicLong();
    private static final AtomicLong CLAMPED = new AtomicLong();
    private static final AtomicLong HOOK_REFUSED = new AtomicLong();
    private static final AtomicLong SKILL_HITS = new AtomicLong();

    private RiftIronsCast() {
    }

    /** Waives Iron's own mana and cooldown on every granted cast, item routes included. */
    static void register() {
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, SpellOnCastEvent.class, RiftIronsCast::onCast);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, SpellCooldownAddedEvent.Pre.class,
                RiftIronsCast::onCooldown);
        NeoForge.EVENT_BUS.addListener(RiftIronsCast::onHurt);
    }

    /** A granted Iron's spell's damage counts as a skill hit for the passive triggers. */
    private static void onHurt(LivingDamageEvent.Post event) {
        if (!(event.getSource() instanceof SpellDamageSource source) || !(source.getEntity() instanceof ServerPlayer player)
                || event.getNewDamage() <= 0.0F || player == event.getEntity()) {
            return;
        }
        if (RiftIronsGate.waives(true, () -> AbilityGrants.of(player), source.spell().getSpellId())) {
            SKILL_HITS.incrementAndGet();
            SkillHits.landed(player, event.getEntity());
        }
    }

    /** Granted Iron's hits handed to the passive triggers. Probe read. */
    public static long skillHits() {
        return SKILL_HITS.get();
    }

    private static void onCast(SpellOnCastEvent event) {
        if (RiftIronsGate.waivesMana(event.getEntity(), event.getSpellId())) {
            event.setManaCost(0);
        }
    }

    private static void onCooldown(SpellCooldownAddedEvent.Pre event) {
        if (RiftIronsGate.waivesCooldown(event.getEntity(), event.getSpell().getSpellId())) {
            event.setCanceled(true);
        }
    }

    /**
     * One cast at the spell's minimum level, finished at once. A channelled spell is refused (one
     * {@code castSpell} is not its damage); a spell with nothing to aim at, or a caster mid-cast, refuses
     * and is refunded.
     */
    static SkillCasts.Invocation cast(ServerLevel level, LivingEntity caster, SkillNode node, SkillCasts.Riders riders) {
        AbstractSpell spell = SpellRegistry.getSpell(node.ability());
        boolean exists = spell != SpellRegistry.none() && spell.isEnabled();
        boolean channelled = exists && spell.getCastType() == CastType.CONTINUOUS;
        SkillCasts.Invocation gate = SkillCasts.Invocation.gate(exists,
                exists && !channelled && caster instanceof ServerPlayer);
        if (gate != SkillCasts.Invocation.CAST) {
            if (channelled) {
                CHANNELLED.incrementAndGet();
            }
            return gate;
        }
        ServerPlayer player = (ServerPlayer) caster;
        MagicData data = MagicData.getPlayerMagicData(player);
        if (data.isCasting()) {
            BUSY.incrementAndGet();
            return SkillCasts.Invocation.NOT_CASTABLE;
        }
        int spellLevel = spell.getMinLevel();
        if (!spell.checkPreCastConditions(level, spellLevel, player, data)) {
            data.resetAdditionalCastData();
            NO_TARGET.incrementAndGet();
            return SkillCasts.Invocation.NOT_CASTABLE;
        }
        boolean ran = false;
        try {
            ran = atPower(player, node, () -> {
                String id = spell.getSpellId();
                data.initiateCast(spell, spellLevel, 0, CastSource.COMMAND, SLOT);
                data.setPlayerCastingItem(ItemStack.EMPTY);
                spell.onServerPreCast(level, spellLevel, player, data);
                PacketDistributor.sendToPlayer(player, new UpdateCastingStatePacket(id, spellLevel, 0, CastSource.COMMAND, SLOT));
                PacketDistributor.sendToPlayersTrackingEntityAndSelf(player,
                        new OnCastStartedPacket(player.getUUID(), id, spellLevel));
                return RiftIronsGate.castOwn(player, id,
                        () -> spell.castSpell(level, spellLevel, player, CastSource.COMMAND, false));
            });
        } finally {
            spell.onServerCastComplete(level, spellLevel, player, data, false);
        }
        if (!ran) {
            HOOK_REFUSED.incrementAndGet();
            return SkillCasts.Invocation.NOT_CASTABLE;
        }
        CAST.incrementAndGet();
        return SkillCasts.Invocation.CAST;
    }

    /** Both native and slot casts translate the ranked node power into Iron's attributes for the actual cast. */
    static <T> T atPower(ServerPlayer player, SkillNode node, java.util.function.Supplier<T> cast) {
        AbstractSpell spell = SpellRegistry.getSpell(node.ability());
        AttributeInstance spellPower = player.getAttribute(AttributeRegistry.SPELL_POWER);
        AttributeInstance school = schoolPower(player, spell);
        AttributeInstance power = attribute(player, node.powerAttribute());
        IronsPower.Factors factors = power == null || spellPower == null ? IronsPower.Factors.NONE
                : IronsPower.factors(node.powerMultiplier(), power.getValue(), spellPower.getValue(), max(spellPower),
                school == null ? 1.0 : school.getValue(), school == null ? 1.0 : max(school));
        if (factors.clamped()) CLAMPED.incrementAndGet();
        try {
            scale(spellPower, factors.spellPower());
            scale(school, factors.school());
            return cast.get();
        } finally {
            unscale(spellPower);
            unscale(school);
        }
    }

    /** The school's power attribute by Iron's naming ({@code <school>_spell_power}), if it is the one the spell reads. */
    private static @Nullable AttributeInstance schoolPower(LivingEntity caster, AbstractSpell spell) {
        ResourceLocation school = spell.getSchoolType().getId();
        AttributeInstance found = attribute(caster, ResourceLocation.fromNamespaceAndPath(school.getNamespace(),
                school.getPath() + "_spell_power"));
        return found != null && found.getValue() == spell.getSchoolType().getPowerFor(caster) ? found : null;
    }

    private static @Nullable AttributeInstance attribute(LivingEntity caster, ResourceLocation id) {
        return BuiltInRegistries.ATTRIBUTE.getHolder(id).map(caster::getAttribute).orElse(null);
    }

    private static double max(AttributeInstance instance) {
        Holder<Attribute> attribute = instance.getAttribute();
        return attribute.value() instanceof RangedAttribute ranged ? ranged.getMaxValue() : Double.MAX_VALUE;
    }

    private static void scale(@Nullable AttributeInstance instance, double factor) {
        if (instance != null && factor != 1.0) {
            instance.addOrUpdateTransientModifier(new AttributeModifier(POWER_ID, factor - 1.0,
                    AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
    }

    private static void unscale(@Nullable AttributeInstance instance) {
        if (instance != null) {
            instance.removeModifier(POWER_ID);
        }
    }

    /** Casts landed, and refusals by reason. Probe reads. */
    public static long casts() {
        return CAST.get();
    }

    public static long busy() {
        return BUSY.get();
    }

    public static long noTarget() {
        return NO_TARGET.get();
    }

    public static long channelled() {
        return CHANNELLED.get();
    }

    public static long clamped() {
        return CLAMPED.get();
    }

    public static long hookRefused() {
        return HOOK_REFUSED.get();
    }
}
