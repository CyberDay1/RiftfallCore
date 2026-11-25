package com.cyberday1.riftfall.skill;

import com.cyberday1.riftfall.compat.TaczGuns;
import com.cyberday1.riftfall.gear.GunGrade;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Crafted ammo on a Ranger's or a Gunner's cast. A cast stays free of ammo; one that finds
 * {@link ChargeRules#SKILL_ARROWS} arrows (bow held) or {@link ChargeRules#SKILL_ROUNDS} rounds of the held gun's
 * calibre uses them up and is raised by the ammo bonus. The raise stands for that skill until its next cast, so a
 * strike that lands late carries it; a cast that is not kept gets its ammo back.
 */
public final class SkillAmmo {

    enum Kind {
        ARROWS, ROUNDS
    }

    private static final Runnable NOTHING = () -> {
    };
    private static final Map<UUID, Map<String, Double>> RAISED = new ConcurrentHashMap<>();

    private SkillAmmo() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent event) ->
                RAISED.remove(event.getEntity().getUUID()));
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent event) -> RAISED.clear());
    }

    /** What a cast of a {@code skillClass} skill with this weapon in hand would use up; null for none. */
    static @Nullable Kind kind(@Nullable String skillClass, boolean bowHeld, boolean gunHeld, boolean reload) {
        if ("ranger".equals(skillClass) && bowHeld) {
            return Kind.ARROWS;
        }
        return "gunner".equals(skillClass) && gunHeld && !reload ? Kind.ROUNDS : null;
    }

    /** The multiplier {@code caster}'s casts of {@code node} carry now: the ammo bonus, or 1. */
    static double multiplier(@Nullable UUID caster, String node) {
        // A mocked caster has no UUID, and the map throws on a null key.
        Map<String, Double> mine = caster == null ? null : RAISED.get(caster);
        return mine == null ? 1.0 : mine.getOrDefault(node, 1.0);
    }

    static void raise(UUID caster, String node, double multiplier) {
        RAISED.computeIfAbsent(caster, id -> new ConcurrentHashMap<>()).put(node, multiplier);
    }

    static void lower(UUID caster, String node) {
        Map<String, Double> mine = RAISED.get(caster);
        if (mine != null) {
            mine.remove(node);
        }
    }

    static void forget() {
        RAISED.clear();
    }

    /**
     * Before a paid cast runs: the last cast's raise ends, and if the caster carries the ammo it is taken and the
     * raise stands. Answers what to run if the cast is not kept: the ammo back, the raise off.
     */
    static Runnable begin(LivingEntity caster, SkillNode node) {
        UUID id = caster.getUUID();
        if (id == null) {
            return NOTHING;
        }
        lower(id, node.id());
        if (!(caster instanceof ServerPlayer player) || !RangedCharge.applies(player)) {
            return NOTHING;
        }
        SkillDef def = SkillDefs.get(node.id());
        ItemStack held = player.getMainHandItem();
        boolean bow = held.getItem() instanceof BowItem || held.getItem() instanceof CrossbowItem;
        boolean gun = GunGrade.isGun(held) && GunnerGuns.loaded();
        Kind kind = kind(def == null ? null : def.skillClass(), bow, gun, GunnerGuns.reloads(node));
        if (kind == Kind.ARROWS && RangedArrows.take(player, ChargeRules.SKILL_ARROWS)) {
            raise(id, node.id(), ChargeRules.ammoBonus(true, false));
            return () -> {
                lower(id, node.id());
                RangedArrows.give(player, ChargeRules.SKILL_ARROWS);
            };
        }
        if (kind == Kind.ROUNDS && TaczGuns.takeRounds(player, held, ChargeRules.SKILL_ROUNDS)) {
            raise(id, node.id(), ChargeRules.ammoBonus(true, true));
            return () -> {
                lower(id, node.id());
                TaczGuns.giveRounds(player, held, ChargeRules.SKILL_ROUNDS);
            };
        }
        return NOTHING;
    }
}
