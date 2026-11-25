package com.cyberday1.riftfall.skill;

import com.cyberday1.riftfall.RiftfallCore;
import com.cyberday1.riftfall.init.RiftfallAttributes;
import com.google.gson.Gson;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.api.spell.event.SpellHandlers;
import net.spell_engine.api.spell.registry.SpellRegistry;
import net.spell_engine.api.spell.summon.AttributeScaling;
import net.spell_engine.fx.ReleaseFx;
import net.spell_engine.Platform;
import net.spell_engine.utils.AnimationHelper;
import net.spell_engine.internals.casting.SpellCast;
import net.spell_engine.internals.casting.SpellCaster;
import net.spell_engine.internals.SpellExecution;
import net.spell_engine.internals.delivery.SpellDelivery;
import net.spell_engine.internals.target.SpellIntents;
import net.spell_engine.internals.target.SpellTarget;
import net.spell_power.api.SpellPower;
import net.spell_power.api.SpellSchool;
import net.spell_power.api.SpellSchools;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * The cast half of the two classes here that name a Spell Engine type ({@code RiftSpellGate} is the
 * other), reached only from behind {@code SkillCasts}' version check. Written against Spell Engine 1.10.
 *
 * <p>{@link #cast} is the engine's own non-player cast ({@code MobCastController#performSpell})
 * rebuilt for any caster, so the context is ours and the riders go on {@code chargeModifier}
 * directly: the one per-cast slot that travels into projectiles and clouds.
 */
public final class RiftSpellEngine {

    private static int delivered;
    private static long releaseAnimations;
    private static final Gson MODIFIER_COPY = new Gson();
    private record FreeImpactScope(LivingEntity caster, Holder<Spell> spell, boolean self) { }
    private static final ThreadLocal<FreeImpactScope> FREE_IMPACTS = new ThreadLocal<>();

    private RiftSpellEngine() {
    }

    /** Same school, weapon bridge and transient rank scope as casting, without delivering an effect. */
    static double guidePower(ServerPlayer player, SkillNode node, int rank) {
        Spell spell = SpellRegistry.from(player.serverLevel()).get(node.ability());
        if (spell == null || spell.school == null) return Double.NaN;
        return SkillCasts.atRankedPower(player, node, rank,
                () -> SpellPower.getSpellPower(spell.school, player).nonCriticalValue());
    }

    /** Keep optional callback signatures in this guarded bridge, outside the movement runtime. */
    static void registerTravel() {
        SpellHandlers.registerCustomImpact(RiftfallCore.id("assault_contact"),
                (spell, power, caster, target, context) -> new SpellHandlers.ImpactResult(target != null && target != caster, false));
        for (String mode : new String[]{"charge", "leap", "line", "chain", "chain_leap", "rampage"})
            SpellHandlers.registerCustomDelivery(RiftfallCore.id("assault/" + mode),
                    (world, spell, caster, targets, context, location) ->
                            RiftAssaultDelivery.start(mode, world, spell, caster, targets, context, location));
        for (String mode : new String[]{"dash", "leap", "grapple"}) {
            for (int distance : new int[]{4, 6, 8, 10, 12, 16}) {
                SpellHandlers.registerCustomImpact(RiftfallCore.id("travel/" + mode + "/" + distance),
                        (spell, power, caster, target, context) ->
                                new SpellHandlers.ImpactResult(SkillTravel.start(caster, mode, distance), false));
            }
        }
        SkillTravel.register();
    }

    /**
     * Adds the class schools, {@link HolySchool}, {@link CommandSchool}, {@link DivineSchool}, {@link SummoningSchool},
     * {@link ShadowSchool} and {@link ArcanaSchool}, each raised by {@code riftfall:spell_damage},
     * to Spell Power once, before any
     * ability file names them. MELEE, as physical_melee is: a MAGIC school would also take Spell Power's
     * generic multiplier on its flat power.
     */
    static void registerClassSchools() {
        register(HolySchool.ID, HolySchool.COLOR, HolySchool.DAMAGE_TYPE, HolySchool::power);
        register(CommandSchool.ID, CommandSchool.COLOR, CommandSchool.DAMAGE_TYPE, CommandSchool::power);
        register(DivineSchool.ID, DivineSchool.COLOR, DivineSchool.DAMAGE_TYPE, DivineSchool::power);
        register(SummoningSchool.ID, SummoningSchool.COLOR, SummoningSchool.DAMAGE_TYPE, SummoningSchool::power);
        register(ShadowSchool.ID, ShadowSchool.COLOR, ShadowSchool.DAMAGE_TYPE, ShadowSchool::power);
        register(ArcanaSchool.ID, ArcanaSchool.COLOR, ArcanaSchool.DAMAGE_TYPE, ArcanaSchool::power);
    }

    private static void register(ResourceLocation id, int color, ResourceLocation damageType,
                                 java.util.function.ToDoubleFunction<LivingEntity> power) {
        if (SpellSchools.getSchool(id.toString()) != null) {
            return;
        }
        SpellSchool school = new SpellSchool(SpellSchool.Archetype.MELEE, id, color,
                ResourceKey.create(Registries.DAMAGE_TYPE, damageType), Attributes.ATTACK_DAMAGE);
        school.addSource(SpellSchool.Trait.POWER, SpellSchool.Apply.ADD,
                query -> withSpellDamage(power.applyAsDouble(query.entity()), spellDamage(query.entity())));
        SpellSchools.register(school);
    }

    /** A class school's power raised by {@code riftfall:spell_damage}, a fraction (Arcanist +10% spell damage). */
    static double withSpellDamage(double power, double spellDamage) {
        return power * (1.0 + Math.max(0.0, spellDamage));
    }

    private static double spellDamage(LivingEntity entity) {
        AttributeInstance instance = entity.getAttribute(RiftfallAttributes.SPELL_DAMAGE);
        return instance == null ? 0.0 : instance.getValue();
    }

    /** Refuses an id the pack lacks and, separately, an ability with nothing to deliver. */
    static SkillCasts.Invocation cast(ServerLevel level, LivingEntity caster, SkillNode node,
                                      SkillCasts.Riders riders) {
        Optional<Holder.Reference<Spell>> found = SpellRegistry.from(level).getHolder(node.ability());
        SkillCasts.Invocation gate = SkillCasts.Invocation.gate(found.isPresent(),
                found.isPresent() && found.get().value().active != null);
        if (gate != SkillCasts.Invocation.CAST) {
            return gate;
        }
        Holder<Spell> spell = found.get();
        Spell value = spell.value();
        if (movementBusy(caster, value)) return SkillCasts.Invocation.NOT_CASTABLE;
        if (node.ability().equals(RiftfallCore.id("gunner_grapple_roll")) && !SkillTravel.canGrapple(caster, 6))
            return SkillCasts.Invocation.NO_TARGET;
        if (hasSelfEffect(caster, node, value)) {
            releaseFx(level, caster, spell);
            return SkillCasts.Invocation.CAST;
        }
        SpellTarget.SearchResult selected = targets(caster, spell, value);
        if (missingTarget(value, selected)) return SkillCasts.Invocation.NO_TARGET;
        if (isEmptySwing(value, selected)) {
            releaseFx(level, caster, spell);
            return SkillCasts.Invocation.CAST;
        }
        SpellExecution.ImpactContext context = new SpellExecution.ImpactContext()
                .power(SpellPower.getSpellPower(value.school, caster))
                .target(SpellIntents.focusMode(value));
        if (!caster.isAttackable() && caster instanceof OwnableEntity owned && owned.getOwner() != null) {
            context = context.effectiveCaster(owned.getOwner());
        }
        Spell.Modifier modifier = modifierFor(value, SpellEngineRiders.of(riders, node.powerMultiplier()));
        if (modifier != null) {
            context = context.chargeModifier(modifier);
            delivered++;
        }
        SpellExecution.ImpactContext delivered = context;
        if (isFreeAssault(value) && selected.entities().isEmpty()) {
            freeAssault(caster, spell, selected, delivered);
            releaseFx(level, caster, spell);
            return SkillCasts.Invocation.CAST;
        }
        // A harmful effect this cast removes is the caster's cleanse.
        boolean[] cast = {false};
        Reactions.cleansing(caster, () -> cast[0] = SkillCasts.delivered(completed -> SpellDelivery.resolveAndDeliver(level, caster, spell,
                selected, delivered, done -> {
                    completed.accept(done.success());
                    if (done.success()) {
                        releaseFx(level, caster, spell);
                    }
                })));
        if (!cast[0]) {
            return SkillCasts.Invocation.NOT_CASTABLE;
        }
        // A melee ability's hit comes back on the client's packet; this paid cast covers it.
        RiftSpellGate.paidMelee(caster, spell);
        PhantomSignatures.cast(level, caster, node.ability(), selected.entities(), selected.location());
        return SkillCasts.Invocation.CAST;
    }

    /**
     * A counter's strike: {@code spell} delivered at {@code target} alone, at the caster's power
     * in the spell's school and the node's power multiplier, outside any price (the window's cast paid it).
     */
    static void strike(ServerLevel level, LivingEntity caster, SkillNode node, Holder<Spell> spell, Entity target) {
        strike(level, caster, node, spell, target, SkillCasts.Riders.NONE, 1.0);
    }

    /**
     * As above with the riders and an extra power {@code factor}: a stored value's release or pulse on one target its
     * anchor chose.
     */
    static void strike(ServerLevel level, LivingEntity caster, SkillNode node, Holder<Spell> spell, Entity target,
                       SkillCasts.Riders riders, double factor) {
        Spell value = spell.value();
        SpellExecution.ImpactContext context = new SpellExecution.ImpactContext()
                .power(SpellPower.getSpellPower(value.school, caster))
                .target(SpellIntents.focusMode(value));
        Spell.Modifier modifier = modifierFor(value, SpellEngineRiders.of(riders, node.powerMultiplier() * factor));
        if (modifier != null) {
            context = context.chargeModifier(modifier);
        }
        SpellDelivery.resolveAndDeliver(level, caster, spell, SpellTarget.SearchResult.of(target), context, done -> {
            if (done.success()) {
                releaseFx(level, caster, spell);
            }
        });
    }

    /**
     * A stored value's release from the caster: {@code spell} delivered by its own targeting,
     * from where the caster stands now, at the node's power times {@code factor}, outside any price (the opening cast
     * paid it).
     */
    static void release(ServerLevel level, LivingEntity caster, SkillNode node, Holder<Spell> spell,
                        SkillCasts.Riders riders, double factor) {
        Spell value = spell.value();
        SpellExecution.ImpactContext context = new SpellExecution.ImpactContext()
                .power(SpellPower.getSpellPower(value.school, caster))
                .target(SpellIntents.focusMode(value));
        Spell.Modifier modifier = modifierFor(value, SpellEngineRiders.of(riders, node.powerMultiplier() * factor));
        if (modifier != null) {
            context = context.chargeModifier(modifier);
        }
        SpellDelivery.resolveAndDeliver(level, caster, spell, targets(caster, spell, value), context, done -> {
            if (done.success()) {
                releaseFx(level, caster, spell);
            }
        });
    }

    static boolean hasSelfEffect(LivingEntity caster, SkillNode node, Spell spell) {
        return SkillCasts.hasSelfEffect(caster, node, spell.deliver.type == Spell.Delivery.Type.DIRECT
                && spell.target.type == Spell.Target.Type.CASTER
                && spell.impacts.isEmpty());
    }

    /** Optional direct melee casts still spend their cost and animate when the weapon misses. */
    public static boolean isEmptySwing(Spell spell, SpellTarget.SearchResult targets) {
        return spell.active != null && spell.range > 0 && !isFreeAssault(spell)
                && spell.target.type == Spell.Target.Type.AIM && !spell.target.aim.required
                && !spell.target.aim.use_caster_as_fallback
                && spell.deliver.type == Spell.Delivery.Type.DIRECT && spell.area_impact == null
                && targets.entities().isEmpty();
    }

    public static boolean missingTarget(Spell spell, SpellTarget.SearchResult targets) {
        return spell.target.type == Spell.Target.Type.AIM && spell.target.aim.required
                && targets.entities().isEmpty();
    }

    public static boolean movementBusy(LivingEntity caster, Spell spell) {
        return spell.deliver.type == Spell.Delivery.Type.CUSTOM && spell.deliver.custom != null
                && spell.deliver.custom.handler.startsWith("riftfall:assault/")
                && (SkillTravel.active(caster) || TravelCast.active(caster.getUUID()));
    }

    public static boolean isFreeAssault(Spell spell) {
        return spell.target.type == Spell.Target.Type.AIM && !spell.target.aim.required
                && spell.deliver.type == Spell.Delivery.Type.DIRECT
                && spell.impacts.stream().anyMatch(RiftSpellEngine::behindTarget);
    }

    private static boolean behindTarget(Spell.Impact impact) {
        return impact.action.type == Spell.Impact.Action.Type.TELEPORT
                && impact.action.teleport.mode.name().equals("BEHIND_TARGET");
    }

    /** No victim: travel once toward the aim point, then apply the authored landing area without retargeting. */
    public static void freeAssault(LivingEntity caster, Holder<Spell> entry, SpellTarget.SearchResult targets,
                                   SpellExecution.ImpactContext context) {
        Spell spell = entry.value();
        var world = caster.level();
        var start = caster.position();
        var requested = targets.location() != null ? targets.location()
                : start.add(caster.getLookAngle().scale(spell.range));
        var ground = net.spell_engine.utils.TargetHelper.findSolidBlockBelow(caster, requested, world, -4F);
        if (ground != null) requested = ground;
        var destination = AimTravel.destination(start, requested, spell.range * caster.getScale(), point -> {
            var box = caster.getBoundingBox().move(point.subtract(start));
            return world.hasChunkAt(net.minecraft.core.BlockPos.containing(point))
                    && world.getWorldBorder().isWithinBounds(box) && world.noCollision(caster, box);
        });
        caster.teleportTo(destination.x, destination.y, destination.z);
        caster.fallDistance = 0;
        landingAt(caster, entry, context);
    }

    /** Untargeted physical landings retain self buffs and resolve their area at the actual arrival point. */
    public static void landingAt(LivingEntity caster, Holder<Spell> entry, SpellExecution.ImpactContext context) {
        Spell spell = entry.value();
        var world = caster.level();
        if (spell.impacts.stream().anyMatch(impact -> impact.action.apply_to_caster)) {
            freeImpacts(caster, entry, true, () ->
                    net.spell_engine.internals.impact.SpellImpacts.performImpacts(world, caster, caster, caster,
                            entry, spell.impacts, context, false, null));
        }
        if (spell.area_impact != null) {
            var impacts = spell.impacts.stream().filter(impact -> !behindTarget(impact)).toList();
            freeImpacts(caster, entry, false, () -> net.spell_engine.internals.impact.SpellImpacts.lookupAndPerformAreaImpact(spell.area_impact,
                    entry, caster, caster, caster, impacts,
                    context.position(caster.position().add(0, .1, 0)), false));
        }
    }

    private static void freeImpacts(LivingEntity caster, Holder<Spell> spell, boolean self, Runnable run) {
        var previous = FREE_IMPACTS.get();
        FREE_IMPACTS.set(new FreeImpactScope(caster, spell, self));
        try { run.run(); }
        finally {
            if (previous == null) FREE_IMPACTS.remove(); else FREE_IMPACTS.set(previous);
        }
    }

    /** Engine 1.10 rebuilds impacts from the registry, ignoring the supplied list; filter at execution. */
    public static boolean allowsFreeImpact(LivingEntity caster, Holder<Spell> spell, Spell.Impact impact) {
        var scope = FREE_IMPACTS.get();
        if (scope == null || scope.caster() != caster || !scope.spell().equals(spell)) return true;
        return scope.self() ? impact.action.apply_to_caster
                : !behindTarget(impact) && !impact.action.apply_to_caster;
    }

    /** Slot casts bypass SpellExecution's player completion callback, including its animation packet. */
    private static void releaseFx(ServerLevel level, LivingEntity caster, Holder<Spell> spell) {
        ReleaseFx.send(level, caster, spell, 1.0F);
        if (caster instanceof ServerPlayer player && spell.value().release.animation != null) {
            AnimationHelper.sendAnimation(player, Platform.tracking(player), SpellCast.Animation.RELEASE,
                    spell.value().release.animation, ((SpellCaster.Player) player).getCastingSpeed());
            releaseAnimations++;
        }
    }

    /** Probe observation: calls to the engine's release-animation broadcaster, not client playback acknowledgements. */
    public static long releaseAnimations() {
        return releaseAnimations;
    }

    /** The engine's search, nearest first and cut to the ability's cap. */
    private static SpellTarget.SearchResult targets(LivingEntity caster, Holder<Spell> spell, Spell value) {
        SpellTarget.SearchResult found = SpellTarget.findTargets(caster, spell, SpellTarget.SearchResult.empty(), true);
        if (value.target.cap <= 0) {
            return found;
        }
        List<Entity> capped = found.entities().stream()
                .sorted(Comparator.comparingDouble(e -> e.distanceToSqr(caster.position())))
                .limit(value.target.cap).toList();
        return new SpellTarget.SearchResult(capped, found.location());
    }

    /** How many casts carried a non-neutral modifier. Counts what was handed over, not what landed. */
    static int delivered() {
        return delivered;
    }

    /** Rebuild power while the shared cast scope carries the rank and weapon; retain native targeting and charge. */
    static SpellExecution.ImpactContext nativeContext(LivingEntity caster, Spell spell, SkillNode node,
                                                       SkillCasts.Riders riders, SpellExecution.ImpactContext context) {
        Spell.Modifier extra = modifierFor(spell, SpellEngineRiders.of(riders, node.powerMultiplier()));
        return context.power(SpellPower.getSpellPower(spell.school, caster))
                .chargeModifier(merge(context.chargeModifier(), extra));
    }

    /** Copy the native charge modifier before adding our riders: registry-owned modifiers must never be mutated. */
    static Spell.Modifier merge(Spell.Modifier nativeModifier, Spell.Modifier extra) {
        if (extra == null) return nativeModifier;
        if (nativeModifier == null) return extra;
        Spell.Modifier merged = MODIFIER_COPY.fromJson(MODIFIER_COPY.toJsonTree(nativeModifier), Spell.Modifier.class);
        merged.effect_duration_add += extra.effect_duration_add;
        merged.spawn_duration_add += extra.spawn_duration_add;
        merged.summon_behaviour.lifespan.active_seconds_add += extra.summon_behaviour.lifespan.active_seconds_add;
        if (extra.power_modifier != null) {
            if (merged.power_modifier == null) merged.power_modifier = new Spell.Impact.Modifier();
            merged.power_modifier.power_multiplier += extra.power_modifier.power_multiplier;
        }
        if (extra.summon_attribute_scaling != null) {
            if (merged.summon_attribute_scaling == null) merged.summon_attribute_scaling = new AttributeScaling();
            merged.summon_attribute_scaling.entries.addAll(extra.summon_attribute_scaling.entries);
        }
        return merged;
    }

    /** Null when the riders change nothing, so a neutral cast is byte-for-byte the engine's own. */
    static Spell.Modifier modifierFor(Spell spell, SpellEngineRiders riders) {
        if (riders.neutral()) {
            return null;
        }
        Spell.Modifier modifier = new Spell.Modifier();
        modifier.effect_duration_add = riders.effectDurationAdd();
        modifier.spawn_duration_add = riders.spawnDurationAdd();
        modifier.summon_behaviour.lifespan.active_seconds_add = riders.activeSecondsAdd();
        if (riders.powerMultiplierAdd() != 0.0F) {
            // No impact filter: the whole ability is rescaled, heals and power-scaled effects included.
            modifier.power_modifier = new Spell.Impact.Modifier();
            modifier.power_modifier.power_multiplier = riders.powerMultiplierAdd();
        }
        if (riders.ownerCoefficientScale() > 0.0) {
            modifier.summon_attribute_scaling = scaledOwnerTerms(spell, riders.ownerCoefficientScale());
        }
        return modifier;
    }

    /**
     * The ability's own owner coefficients, times the scale, base dropped. The engine concatenates
     * a modifier's entries onto the action's and sums every term, so this adds exactly that share.
     */
    private static AttributeScaling scaledOwnerTerms(Spell spell, double scale) {
        AttributeScaling scaled = new AttributeScaling();
        if (spell.impacts == null) {
            return scaled;
        }
        for (Spell.Impact impact : spell.impacts) {
            if (impact.action == null || impact.action.type != Spell.Impact.Action.Type.SUMMON
                    || impact.action.summon == null || impact.action.summon.attribute_scaling == null) {
                continue;
            }
            for (AttributeScaling.Entry entry : impact.action.summon.attribute_scaling.entries) {
                AttributeScaling.Entry extra = new AttributeScaling.Entry();
                extra.attribute_id = entry.attribute_id;
                for (AttributeScaling.Entry.OwnerModifier term : entry.modifiers) {
                    extra.modifiers.add(new AttributeScaling.Entry.OwnerModifier(
                            term.attribute_id, term.operation, 0.0, term.coefficient * scale));
                }
                scaled.entries.add(extra);
            }
        }
        return scaled;
    }

    /** The top-level school and each DAMAGE impact's, resolved as the engine resolves them. Empty when absent. */
    static Optional<SkillCasts.AbilitySchool> school(ServerLevel level, ResourceLocation ability) {
        return SpellRegistry.from(level).getHolder(ability)
                .map(Holder::value)
                .filter(spell -> spell.school != null)
                .map(spell -> new SkillCasts.AbilitySchool(String.valueOf(spell.school.id),
                        String.valueOf(spell.school.damageType.location()), damageSchools(spell)));
    }

    private static List<String> damageSchools(Spell spell) {
        List<String> schools = new ArrayList<>();
        if (spell.impacts == null) {
            return schools;
        }
        for (Spell.Impact impact : spell.impacts) {
            if (impact.action != null && impact.action.type == Spell.Impact.Action.Type.DAMAGE) {
                SpellSchool school = SkillCasts.AbilitySchool.impactSchool(spell.school, impact.school);
                schools.add(String.valueOf(school.id));
            }
        }
        return schools;
    }

    /** The ability's target and delivery types, as {@code AIM/PROJECTILE}. Empty when absent. */
    static Optional<String> shape(ServerLevel level, ResourceLocation ability) {
        return SpellRegistry.from(level).getHolder(ability).map(Holder::value)
                .map(spell -> spell.target.type + "/" + spell.deliver.type);
    }

    /** How many abilities the datapack registry holds on this level. */
    static int count(ServerLevel level) {
        return SpellRegistry.from(level).size();
    }

    /** The first few ability ids in registry order. */
    static List<String> sample(ServerLevel level, int limit) {
        return SpellRegistry.from(level).keySet().stream().limit(limit)
                .map(ResourceLocation::toString).sorted().toList();
    }
}
