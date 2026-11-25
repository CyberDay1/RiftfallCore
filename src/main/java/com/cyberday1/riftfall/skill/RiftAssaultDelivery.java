package com.cyberday1.riftfall.skill;

import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.internals.SpellExecution;
import net.spell_engine.internals.impact.SpellImpacts;
import net.spell_engine.internals.target.EntityRelations;
import net.spell_engine.internals.target.SpellTarget;
import net.spell_engine.utils.WorldScheduler;
import java.util.*;

/** Optional Spell Engine bridge: custom delivery launches now and resolves impacts at contact/arrival. */
public final class RiftAssaultDelivery {
    private RiftAssaultDelivery() { }

    public static boolean start(String mode, Level world, Holder<Spell> spell, LivingEntity caster,
                                List<SpellExecution.DeliveryTarget> targets,
                                SpellExecution.ImpactContext context, Vec3 location) {
        if (!(world instanceof ServerLevel level) || SkillTravel.active(caster)) return false;
        String id = spell.unwrapKey().orElseThrow().location().getPath();
        TravelCast.Ticket ticket = TravelCast.begin(caster.getUUID(), id);
        if (ticket == null) return false;
        Run run = new Run(mode, level, spell, caster, context, ticket, targets);
        boolean launched = run.launch(location);
        if (!launched) TravelCast.finish(ticket, false);
        return launched;
    }

    private static final class Run {
        final String mode;
        final ServerLevel level;
        final Holder<Spell> spell;
        final LivingEntity caster;
        final SpellExecution.ImpactContext context;
        final TravelCast.Ticket ticket;
        final List<SpellExecution.DeliveryTarget> targets;
        final Set<UUID> struck = new HashSet<>();
        final Deque<LivingEntity> chain = new ArrayDeque<>();
        final boolean leap;
        int hops;

        Run(String mode, ServerLevel level, Holder<Spell> spell, LivingEntity caster,
            SpellExecution.ImpactContext context, TravelCast.Ticket ticket, List<SpellExecution.DeliveryTarget> targets) {
            this.mode = mode; this.level = level; this.spell = spell; this.caster = caster;
            this.context = context; this.ticket = ticket; this.targets = List.copyOf(targets);
            leap = mode.equals("leap") || mode.equals("chain_leap") || mode.equals("rampage");
        }

        boolean alive() { return caster.isAlive() && !caster.isRemoved() && caster.level() == level; }
        boolean eligible(LivingEntity target) { return target.isAlive() && !target.isRemoved() && target.level() == level; }
        boolean enemy(LivingEntity target) {
            return target != caster && eligible(target) && target.isAttackable()
                    && EntityRelations.actionAllowed(SpellTarget.FocusMode.AREA, SpellTarget.Intent.HARMFUL, caster, target);
        }
        boolean contact(LivingEntity target) {
            return eligible(target) && caster.getBoundingBox().inflate(1.25, .5, 1.25).intersects(target.getBoundingBox())
                    && caster.hasLineOfSight(target);
        }
        boolean launch(Vec3 location) {
            LivingEntity first = targets.stream().map(SpellExecution.DeliveryTarget::entity)
                    .filter(e -> e instanceof LivingEntity && e != caster).map(e -> (LivingEntity)e).findFirst().orElse(null);
            if (mode.equals("line")) return line();
            if (first == null) {
                if (spell.value().target.aim.required) return false;
                Vec3 look = caster.getLookAngle();
                Vec3 flat = new Vec3(look.x, 0, look.z).normalize();
                Vec3 end = location != null ? location : caster.position().add(flat.scale(spell.value().range * caster.getScale()));
                // Untargeted travel follows feet height unless the aimed point has solid support below it.
                var ground = net.spell_engine.utils.TargetHelper.findSolidBlockBelow(caster, end, level, -4F);
                end = ground != null ? ground : new Vec3(end.x, caster.getY(), end.z);
                return SkillTravel.startTo(caster, end, spell.value().range * caster.getScale(), leap, this::alive, () -> { },
                        arrived -> {
                            if (arrived) TravelCast.impact(ticket, () -> RiftSpellEngine.landingAt(caster, spell, context));
                            TravelCast.finish(ticket, arrived);
                        });
            }
            if (mode.startsWith("chain") && spell.value().area_impact != null) {
                double radius = spell.value().area_impact.radius;
                level.getEntitiesOfClass(LivingEntity.class, first.getBoundingBox().inflate(radius),
                        e -> enemy(e) && e != first && e.distanceToSqr(first) <= radius * radius)
                        .stream().sorted(Comparator.comparingDouble(first::distanceToSqr)).limit(7).forEach(chain::add);
            }
            return leg(first, spell.value().range * caster.getScale());
        }

        boolean leg(LivingEntity target, double range) {
            if (!enemy(target) || !caster.hasLineOfSight(target)) return false;
            Vec3 end = AssaultGeometry.approach(caster.position(), target.position(), caster.getBbWidth(), target.getBbWidth());
            if (end.distanceTo(caster.position()) > range + .25) return false;
            return SkillTravel.startTo(caster, end, range, leap, () -> alive() && eligible(target), () -> { }, arrived -> {
                if (!arrived || !contact(target)) { TravelCast.finish(ticket, false); return; }
                hit(target, context, !mode.startsWith("chain") && !mode.equals("rampage"));
                hops++;
                if (mode.equals("rampage")) {
                    // The next leap waits out the hop, so the player can look at the enemy it should go to.
                    Rampage.afterStrike(hops, (ticks, task) -> ((WorldScheduler) level).schedule(ticks, task),
                            () -> { if (!alive() || !rampageOn()) TravelCast.finish(ticket, true); },
                            () -> TravelCast.finish(ticket, true));
                    return;
                }
                LivingEntity next = null;
                while (!chain.isEmpty() && next == null) {
                    LivingEntity candidate = chain.removeFirst();
                    if (enemy(candidate) && !struck.contains(candidate.getUUID())) next = candidate;
                }
                if (next != null && leg(next, spell.value().area_impact.radius * 2)) return;
                TravelCast.finish(ticket, true);
            });
        }

        /** The unstruck enemy in reach nearest the crosshair now, leapt on; false when there is none. */
        boolean rampageOn() {
            List<LivingEntity> near = level.getEntitiesOfClass(LivingEntity.class, caster.getBoundingBox().inflate(Rampage.RADIUS),
                    e -> enemy(e) && !struck.contains(e.getUUID()) && caster.distanceToSqr(e) <= Rampage.RADIUS * Rampage.RADIUS
                            && caster.hasLineOfSight(e));
            int index = Rampage.aimed(caster.getEyePosition(), caster.getLookAngle(), near.stream().map(Entity::getEyePosition).toList());
            return index >= 0 && leg(near.get(index), Rampage.RADIUS);
        }

        void hit(LivingEntity target, SpellExecution.ImpactContext hitContext, boolean area) {
            if (!struck.add(target.getUUID())) return;
            TravelCast.impact(ticket, () -> SpellImpacts.performImpacts(level, caster, target, target, spell,
                    spell.value().impacts, hitContext.position(target.position().add(0, target.getBbHeight() * .5, 0)), area, null));
        }

        boolean line() {
            Vec3 look = caster.getLookAngle();
            Vec3 end = caster.position().add(new Vec3(look.x, 0, look.z).normalize().scale(spell.value().range * caster.getScale()));
            return SkillTravel.startTo(caster, end, spell.value().range * caster.getScale(), false, this::alive,
                    () -> {
                        for (var selected : targets) if (selected.entity() instanceof LivingEntity target
                                && target != caster && contact(target)) hit(target, selected.context(), false);
                    }, arrived -> {
                        if (alive() && arrived) {
                            if (targets.stream().anyMatch(t -> t.entity() == caster)) hit(caster, context, false);
                            TravelCast.finish(ticket, true);
                        } else TravelCast.finish(ticket, false);
                    });
        }
    }
}
