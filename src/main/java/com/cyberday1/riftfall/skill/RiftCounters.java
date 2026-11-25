package com.cyberday1.riftfall.skill;

import com.cyberday1.riftfall.init.RiftfallEffects;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.api.spell.event.SpellHandlers;
import net.spell_engine.api.spell.registry.SpellRegistry;
import net.spell_engine.internals.SpellExecution;
import net.spell_power.api.SpellPower;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The counter window: an active opens it, and the first hit the caster takes inside it is
 * parried and answered. The active's spell puts {@code riftfall:parrying} on its caster (the window's length and its
 * icon) and runs the {@code riftfall:counter_window} impact, which records the node and its rank.
 *
 * <p>Inside the window, a hit from a living attacker within {@link #REACH} is cancelled (the parry), the window
 * closes, and the node's counter spell, {@code riftfall:counter/<node>}, strikes the attacker at the node's rank power.
 * A dodged hit never reaches the window (the dodges cancel first), a hit from farther away passes through untouched,
 * and nothing a reaction delivers opens or spends one.
 */
public final class RiftCounters {

    public static final ResourceLocation WINDOW = ResourceLocation.fromNamespaceAndPath("riftfall", "counter_window");
    /** The counter spell of a node lives at {@code riftfall:counter/<node id>}. */
    public static final String COUNTER_DIR = "counter/";
    /** How far an attacker may stand and still be answered, in blocks. */
    public static final double REACH = 8.0;
    /** A window with no parrying effect to read its length from (never authored so): 1.5 s. */
    public static final int DEFAULT_TICKS = 30;

    private record Window(String node, int rank, long endsAt) {
    }

    private static final Map<UUID, Window> OPEN = new ConcurrentHashMap<>();
    private static final AtomicLong OPENED = new AtomicLong();
    private static final AtomicLong PARRIED = new AtomicLong();
    private static final AtomicLong COUNTERED = new AtomicLong();
    private static final AtomicLong OUT_OF_REACH = new AtomicLong();
    private static final AtomicLong LAPSED = new AtomicLong();

    private RiftCounters() {
    }

    static void register() {
        SpellHandlers.registerCustomImpact(WINDOW, RiftCounters::open);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOW, RiftCounters::onIncoming);
        NeoForge.EVENT_BUS.addListener(RiftCounters::onLogout);
    }

    /** The node whose ability is {@code ability}, if any castable names it. */
    static @Nullable SkillDef byAbility(SkillTable table, ResourceLocation ability) {
        for (String id : table.ids()) {
            SkillDef def = table.get(id);
            if (def != null && ability.equals(def.cast().ability())) {
                return def;
            }
        }
        return null;
    }

    /** The {@code riftfall:counter_window} impact: records the window, as long as the parrying effect it just put on. */
    private static SpellHandlers.ImpactResult open(Holder<Spell> spell, SpellPower.Result power, LivingEntity caster,
                                                   @Nullable Entity target, SpellExecution.ImpactContext context) {
        ResourceLocation ability = spell.unwrapKey().map(ResourceKey::location).orElse(null);
        if (!(caster instanceof ServerPlayer player) || ability == null) {
            return new SpellHandlers.ImpactResult(false, false);
        }
        SkillDef def = byAbility(SkillDefs.table(), ability);
        int rank = def == null ? 0 : SkillService.bookOf(player).rank(def.id());
        if (def == null || rank < 1) {
            return new SpellHandlers.ImpactResult(false, false);
        }
        MobEffectInstance parrying = player.getEffect(RiftfallEffects.PARRYING);
        int ticks = parrying == null ? DEFAULT_TICKS : parrying.getDuration();
        OPEN.put(player.getUUID(), new Window(def.id(), rank, player.level().getGameTime() + ticks));
        OPENED.incrementAndGet();
        return new SpellHandlers.ImpactResult(true, false);
    }

    /** The parry: inside an open window, the first hit from a living attacker in reach is cancelled and answered. */
    private static void onIncoming(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || Reactions.firing()) {
            return;
        }
        Window window = OPEN.get(player.getUUID());
        if (window == null) {
            return;
        }
        long now = player.level().getGameTime();
        if (now >= window.endsAt() || !player.hasEffect(RiftfallEffects.PARRYING)) {
            OPEN.remove(player.getUUID());
            LAPSED.incrementAndGet();
            return;
        }
        if (!(event.getSource().getEntity() instanceof LivingEntity attacker) || attacker == player || !attacker.isAlive()) {
            return;
        }
        if (attacker.distanceToSqr(player) > REACH * REACH) {
            OUT_OF_REACH.incrementAndGet();
            return;
        }
        OPEN.remove(player.getUUID());
        player.removeEffect(RiftfallEffects.PARRYING);
        event.setCanceled(true);
        PARRIED.incrementAndGet();
        counter(player, window, attacker);
    }

    /** The answer: the node's counter spell at the attacker, at the node's rank power, as a reaction (no recursion). */
    private static void counter(ServerPlayer player, Window window, LivingEntity attacker) {
        SkillDef def = SkillDefs.get(window.node());
        if (def == null || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(def.cast().ability().getNamespace(),
                COUNTER_DIR + def.id());
        Holder<Spell> spell = SpellRegistry.from(level).getHolder(id).orElse(null);
        if (spell == null) {
            return;
        }
        Reactions.deliver(() -> {
            if (SkillCasts.atRank(player, def.cast(), window.rank(),
                    () -> RiftSpellEngine.strike(level, player, def.cast(), spell, attacker))) {
                COUNTERED.incrementAndGet();
            }
        });
    }

    /** Whether a window is open for the player now. Probe read. */
    public static boolean open(ServerPlayer player) {
        Window window = OPEN.get(player.getUUID());
        return window != null && player.level().getGameTime() < window.endsAt();
    }

    /** Closes the player's window (a probe's teardown). */
    public static void close(UUID player) {
        OPEN.remove(player);
    }

    private static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        OPEN.remove(event.getEntity().getUUID());
    }

    /** Windows opened, hits parried, counters landed, hits out of reach, windows that ran out. Probe reads. */
    public static long opened() {
        return OPENED.get();
    }

    public static long parried() {
        return PARRIED.get();
    }

    public static long countered() {
        return COUNTERED.get();
    }

    public static long outOfReach() {
        return OUT_OF_REACH.get();
    }

    public static long lapsed() {
        return LAPSED.get();
    }
}
