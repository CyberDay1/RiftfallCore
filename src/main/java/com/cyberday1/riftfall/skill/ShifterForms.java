package com.cyberday1.riftfall.skill;

import com.cyberday1.riftfall.RiftfallConfig;
import com.cyberday1.riftfall.compat.WalkersShapes;
import com.cyberday1.riftfall.init.RiftfallEffects;
import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/**
 * The Shifter's forms as Woodwalkers models, driven only by Riftfall. While a player holds an aspect they wear its
 * model; when it ends, swaps, or they die, log out or change dimension, they are human again. The aspect's stats stay
 * the source of truth (WalkersShapes keeps the body and blanks the mob's). Names no Walkers type: without the mod, or
 * with {@code shifterShapes} off, a form keeps the size-and-dust stance.
 */
public final class ShifterForms {

    /** Why a form ended outright. */
    public enum End { LOGOUT, DIMENSION, DEATH }

    /** How often a player's model is checked against their aspect, in ticks. */
    static final int RECONCILE_TICKS = 20;

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Predicate<Object> NOBODY = player -> false;
    private static volatile Predicate<Object> probeOptIn = NOBODY;
    private static final Set<String> WARNED = new HashSet<>();
    private static Boolean loaded;

    private ShifterForms() {
    }

    /** Whether Woodwalkers is installed. */
    public static boolean loaded() {
        if (loaded == null) {
            ModList mods = ModList.get();
            loaded = mods != null && mods.isLoaded("walkers");
        }
        return loaded;
    }

    /** Wires the vetoes and the lifecycle listeners; nothing without Woodwalkers. */
    public static void register() {
        if (!loaded()) {
            return;
        }
        WalkersShapes.register();
        NeoForge.EVENT_BUS.addListener(ShifterForms::onRemoved);
        NeoForge.EVENT_BUS.addListener(ShifterForms::onExpired);
        NeoForge.EVENT_BUS.addListener(ShifterForms::onTick);
        NeoForge.EVENT_BUS.addListener(ShifterForms::onLoggedIn);
        NeoForge.EVENT_BUS.addListener(ShifterForms::onLoggedOut);
        NeoForge.EVENT_BUS.addListener(ShifterForms::onDimension);
        NeoForge.EVENT_BUS.addListener(ShifterForms::onDeath);
        NeoForge.EVENT_BUS.addListener(ShifterForms::onRespawn);
        NeoForge.EVENT_BUS.addListener(ShifterForms::onServerStarted);
        NeoForge.EVENT_BUS.addListener(ShifterForms::onDatapackSync);
    }

    // ---- what a form wears ----

    /** The first id in {@code ids} that {@code registered} accepts. */
    static Optional<ResourceLocation> pick(List<? extends String> ids, Predicate<ResourceLocation> registered) {
        for (String id : ids) {
            ResourceLocation rl = ResourceLocation.tryParse(id);
            if (rl != null && registered.test(rl)) {
                return Optional.of(rl);
            }
        }
        return Optional.empty();
    }

    /** The model {@code aspect} wears on this server, if any. */
    public static Optional<ResourceLocation> shapeFor(DeferredHolder<MobEffect, MobEffect> aspect) {
        List<? extends String> ids = aspect == RiftfallEffects.BEAR_ASPECT
                ? setting(RiftfallConfig.SHIFTER_BEAR_SHAPES, List.of("minecraft:polar_bear"))
                : aspect == RiftfallEffects.TIGER_ASPECT
                ? setting(RiftfallConfig.SHIFTER_TIGER_SHAPES, List.of("minecraft:ocelot"))
                : List.of();
        return pick(ids, BuiltInRegistries.ENTITY_TYPE::containsKey);
    }

    /** Every model a form can wear here (the Walkers policy blanks these). */
    public static List<ResourceLocation> shapes() {
        List<ResourceLocation> out = new ArrayList<>();
        for (DeferredHolder<MobEffect, MobEffect> aspect : RiftfallEffects.aspects()) {
            shapeFor(aspect).filter(id -> !out.contains(id)).ifPresent(out::add);
        }
        return out;
    }

    /** Whether forms wear models at all on this server. */
    public static boolean enabled() {
        return loaded() && setting(RiftfallConfig.SHIFTER_SHAPES, true);
    }

    /** Whether a form wearing a model also sheds its dust. */
    public static boolean dustWithModel() {
        return setting(RiftfallConfig.SHIFTER_SHAPE_DUST, false);
    }

    /** A real player, or a probe's opted-in stand-in: other mods' fake players never wear a model. */
    static boolean eligible(ServerPlayer player) {
        return enabled() && (!(player instanceof FakePlayer) || probeOptIn.test(player));
    }

    /** Whether a model would go on {@code entity} now: the dust follows this. */
    public static boolean wouldWear(LivingEntity entity) {
        return entity instanceof ServerPlayer player && eligible(player) && !riding(player);
    }

    /** Walkers dismounts a player whose model cannot ride, so a model waits until they are off. */
    static boolean riding(ServerPlayer player) {
        return player.isPassenger() || !player.getPassengers().isEmpty();
    }

    // ---- keeping the model and the aspect in step ----

    /** Puts the right model on {@code player} for the aspect they hold, or none. Answers what they wear. */
    public static @Nullable ResourceLocation reconcile(ServerPlayer player) {
        return reconcile(player, null);
    }

    /** As {@link #reconcile(ServerPlayer)}, as if {@code leaving} (an aspect about to go) were already gone. */
    public static @Nullable ResourceLocation reconcile(ServerPlayer player, @Nullable MobEffect leaving) {
        if (!loaded()) {
            return null;
        }
        DeferredHolder<MobEffect, MobEffect> aspect = held(player, leaving);
        ResourceLocation wanted = aspect == null || !eligible(player) ? null : shapeFor(aspect).orElse(null);
        ResourceLocation worn = WalkersShapes.current(player);
        if (wanted == null) {
            if (worn != null) {
                WalkersShapes.shed(player);
            }
        } else if (!wanted.equals(worn) && !riding(player)) {
            // The stand-in comes off first: Walkers sizes the body as it swaps.
            bodyScale(player, aspect, false);
            if (!WalkersShapes.wear(player, wanted)) {
                warnOnce(wanted, player);
            }
        }
        worn = WalkersShapes.current(player);
        if (aspect != null && bodyScale(player, aspect, worn == null)) {
            player.refreshDimensions();
        }
        return worn;
    }

    /** The aspect's scale stand-in on or off; answers whether it changed. */
    private static boolean bodyScale(ServerPlayer player, DeferredHolder<MobEffect, MobEffect> aspect, boolean shown) {
        MobEffectInstance instance = player.getEffect(aspect);
        return aspect.get() instanceof RiftfallEffects.AspectEffect effect && instance != null
                && effect.bodyScale(player, instance.getAmplifier(), shown);
    }

    /** Ends {@code player}'s form outright: the aspect goes, and the model with it. */
    public static void end(ServerPlayer player, End why) {
        if (!loaded()) {
            return;
        }
        for (DeferredHolder<MobEffect, MobEffect> aspect : RiftfallEffects.aspects()) {
            if (player.hasEffect(aspect)) {
                player.removeEffect(aspect);
            }
        }
        if (WalkersShapes.current(player) != null) {
            WalkersShapes.shed(player);
        }
    }

    private static @Nullable DeferredHolder<MobEffect, MobEffect> held(LivingEntity entity, @Nullable MobEffect leaving) {
        for (DeferredHolder<MobEffect, MobEffect> aspect : RiftfallEffects.aspects()) {
            if (aspect.get() != leaving && entity.hasEffect(aspect)) {
                return aspect;
            }
        }
        return null;
    }

    // ---- listeners ----

    private static void onRemoved(MobEffectEvent.Remove event) {
        if (event.getEntity() instanceof ServerPlayer player && event.getEffect().value() instanceof RiftfallEffects.AspectEffect) {
            reconcile(player, event.getEffect().value());
        }
    }

    private static void onExpired(MobEffectEvent.Expired event) {
        MobEffectInstance instance = event.getEffectInstance();
        if (event.getEntity() instanceof ServerPlayer player && instance != null
                && instance.getEffect().value() instanceof RiftfallEffects.AspectEffect) {
            reconcile(player, instance.getEffect().value());
        }
    }

    private static void onTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player && player.tickCount % RECONCILE_TICKS == 0) {
            reconcile(player);
        }
    }

    private static void onLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            reconcile(player);
        }
    }

    private static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            end(player, End.LOGOUT);
        }
    }

    private static void onDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            end(player, End.DIMENSION);
        }
    }

    private static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && !event.isCanceled()) {
            end(player, End.DEATH);
        }
    }

    /** Walkers copies the old body's model onto the respawned one; it holds no aspect, so it goes. */
    private static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            reconcile(player);
        }
    }

    private static void onServerStarted(ServerStartedEvent event) {
        WalkersShapes.enforcePolicy(event.getServer(), shapes());
    }

    private static void onDatapackSync(OnDatapackSyncEvent event) {
        if (event.getPlayer() == null) {
            WalkersShapes.enforcePolicy(event.getPlayerList().getServer(), shapes());
        }
    }

    // ---- plumbing ----

    private static <T> T setting(net.neoforged.neoforge.common.ModConfigSpec.ConfigValue<? extends T> value, T fallback) {
        try {
            return value.get();
        } catch (RuntimeException notLoaded) {
            return fallback;
        }
    }

    private static void warnOnce(ResourceLocation shape, ServerPlayer player) {
        synchronized (WARNED) {
            if (WARNED.add(shape.toString())) {
                LOGGER.warn("Riftfall: could not put the {} model on {}; the form keeps its size-and-dust stance",
                        shape, player.getGameProfile().getName());
            }
        }
    }

    /** Probe only: which fake players may wear a model. Null puts the default (none) back. */
    public static void optInForProbe(@Nullable Predicate<Object> who) {
        probeOptIn = who == null ? NOBODY : who;
    }
}
