package com.cyberday1.riftfall.compat;

import com.mojang.logging.LogUtils;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.util.FakePlayer;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import tocraft.walkers.Walkers;
import tocraft.walkers.ability.AbilityRegistry;
import tocraft.walkers.api.PlayerShape;
import tocraft.walkers.api.events.ShapeEvents;
import tocraft.walkers.api.platform.ApiLevel;
import tocraft.walkers.api.platform.WalkersConfig;
import tocraft.walkers.traits.ShapeTrait;
import tocraft.walkers.traits.TraitRegistry;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The only class that names a Woodwalkers type outside the compat mixins and the probe. Woodwalkers is a visual layer
 * here: a shape changes only inside Riftfall's own call (every other swap, unlock and shape ability is vetoed), the
 * player's base health, armour, flight and mount survive the change, and the policy blanks the mob's traits and
 * abilities on the shapes forms wear. Reached only behind ShifterForms#loaded.
 */
public final class WalkersShapes {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ThreadLocal<Boolean> OURS = ThreadLocal.withInitial(() -> false);
    private static final AtomicLong WORN = new AtomicLong();
    private static final AtomicLong SHED = new AtomicLong();
    private static final AtomicLong SWAPS_VETOED = new AtomicLong();
    private static final AtomicLong UNLOCKS_VETOED = new AtomicLong();
    private static final AtomicLong ABILITIES_VETOED = new AtomicLong();
    private static volatile boolean registered;

    private WalkersShapes() {
    }

    /** The vetoes. Idempotent. */
    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        ShapeEvents.SWAP_SHAPE.register((ShapeEvents.ShapeSwapCallback) (player, shape) -> {
            if (OURS.get()) {
                return InteractionResult.PASS;
            }
            SWAPS_VETOED.incrementAndGet();
            return InteractionResult.FAIL;
        });
        ShapeEvents.UNLOCK_SHAPE.register((ShapeEvents.UnlockShapeCallback) (player, type) -> {
            UNLOCKS_VETOED.incrementAndGet();
            return InteractionResult.FAIL;
        });
        ShapeEvents.USE_SHAPE_ABILITY.register((ShapeEvents.UseShapeAbilityCallback) (player, ability) -> {
            ABILITIES_VETOED.incrementAndGet();
            return InteractionResult.FAIL;
        });
    }

    /** The model {@code player} wears, by entity type id, or null. */
    public static @Nullable ResourceLocation current(Player player) {
        LivingEntity shape = PlayerShape.getCurrentShape(player);
        return shape == null ? null : EntityType.getKey(shape.getType());
    }

    /** Whether {@code player} wears any model. Read by the body mixins on both sides. */
    public static boolean shaped(Player player) {
        return PlayerShape.getCurrentShape(player) != null;
    }

    /** Puts the {@code type} model on {@code player}. Answers whether it is worn. */
    public static boolean wear(ServerPlayer player, ResourceLocation type) {
        Entity made = BuiltInRegistries.ENTITY_TYPE.getOptional(type).map(t -> t.create(player.level())).orElse(null);
        if (!(made instanceof LivingEntity shape)) {
            if (made != null) {
                made.discard();
            }
            return false;
        }
        boolean worn = change(player, shape) && PlayerShape.getCurrentShape(player) == shape;
        if (worn) {
            WORN.incrementAndGet();
        }
        return worn;
    }

    /** Takes {@code player}'s model off. */
    public static boolean shed(ServerPlayer player) {
        boolean off = change(player, null) && PlayerShape.getCurrentShape(player) == null;
        if (off) {
            SHED.incrementAndGet();
        }
        return off;
    }

    /** One Riftfall swap: the only kind the veto lets through. The player's own body is put back after it. */
    private static boolean change(ServerPlayer player, @Nullable LivingEntity shape) {
        Body body = Body.of(player);
        OURS.set(true);
        try {
            return PlayerShape.updateShapes(player, shape);
        } catch (NullPointerException untracked) {
            // Walkers syncs to the chunk map's tracker after the swap; a probe's fake player has none.
            if (!(player instanceof FakePlayer)) {
                throw untracked;
            }
            return PlayerShape.getCurrentShape(player) == shape;
        } finally {
            OURS.set(false);
            body.restore(player);
        }
    }

    /** What Walkers may change on a swap and Riftfall owns: base stats, health, flight, the mount. */
    @SuppressWarnings("deprecation") // Walkers writes Abilities#mayfly itself, so that is what is put back.
    private record Body(double maxHealth, double armor, double toughness, float health, boolean mayfly, boolean flying,
                        float flySpeed, @Nullable Entity vehicle) {

        static Body of(ServerPlayer p) {
            return new Body(base(p, Attributes.MAX_HEALTH), base(p, Attributes.ARMOR), base(p, Attributes.ARMOR_TOUGHNESS),
                    p.getHealth(), p.getAbilities().mayfly, p.getAbilities().flying, p.getAbilities().getFlyingSpeed(),
                    p.getVehicle());
        }

        void restore(ServerPlayer p) {
            setBase(p, Attributes.MAX_HEALTH, maxHealth);
            setBase(p, Attributes.ARMOR, armor);
            setBase(p, Attributes.ARMOR_TOUGHNESS, toughness);
            if (p.getHealth() != Math.min(health, p.getMaxHealth()) && p.isAlive()) {
                p.setHealth(Math.min(health, p.getMaxHealth()));
            }
            var abilities = p.getAbilities();
            if (abilities.mayfly != mayfly || abilities.flying != flying || abilities.getFlyingSpeed() != flySpeed) {
                abilities.mayfly = mayfly;
                abilities.flying = flying;
                abilities.setFlyingSpeed(flySpeed);
                p.onUpdateAbilities();
            }
            if (vehicle != null && vehicle.isAlive() && p.getVehicle() != vehicle) {
                p.startRiding(vehicle, true);
            }
        }

        private static double base(ServerPlayer p, Holder<Attribute> attribute) {
            AttributeInstance instance = p.getAttribute(attribute);
            return instance == null ? Double.NaN : instance.getBaseValue();
        }

        private static void setBase(ServerPlayer p, Holder<Attribute> attribute, double value) {
            AttributeInstance instance = p.getAttribute(attribute);
            if (instance != null && !Double.isNaN(value) && instance.getBaseValue() != value) {
                instance.setBaseValue(value);
            }
        }
    }

    /**
     * Woodwalkers as a visual layer, enforced over whatever its config file says: no menus, unlocking or keybind
     * swaps (API level 0), no health/armour from the mob, no flight, no mob AI favours, and no traits or abilities on
     * the shapes Riftfall's forms wear.
     */
    public static void enforcePolicy(MinecraftServer server, Collection<ResourceLocation> shapes) {
        WalkersConfig c = Walkers.CONFIG;
        if (c == null) {
            return;
        }
        c.apiLevel = ApiLevel.API_ONLY.ordinal();
        ApiLevel.setApiLevel(ApiLevel.API_ONLY);
        c.scalingHealth = false;
        c.scalingAmor = false;
        c.enableFlight = false;
        c.playerCanTriggerHostiles = false;
        c.hostilesIgnoreHostileShapedPlayer = false;
        c.hostilesIgnoreNotHostileShapedPlayer = false;
        c.hostilesForgetNewShapedPlayer = false;
        c.hunterAttackAsPreyMorphedPlayer = false;
        c.ownedHunterAttackAsPreyMorphedPlayer = false;
        c.villagersRunFrom2ndShapes = false;
        c.unlockOverridesCurrentShape = false;
        List<String> abilities = new ArrayList<>(c.abilityBlacklist == null ? List.of() : c.abilityBlacklist);
        Map<String, List<String>> traits = new HashMap<>(c.traitBlacklist == null ? Map.of() : c.traitBlacklist);
        for (ResourceLocation id : shapes) {
            String key = id.toString();
            if (!abilities.contains(key)) {
                abilities.add(key);
            }
            Set<String> off = new LinkedHashSet<>(traits.getOrDefault(key, List.of()));
            off.addAll(traitIds(server, id));
            traits.put(key, List.copyOf(off));
        }
        c.abilityBlacklist = abilities;
        c.traitBlacklist = traits;
        LOGGER.info("Riftfall: Woodwalkers held to a visual layer for {} shape(s): {}", shapes.size(), shapes);
    }

    /** Every trait id Walkers gives {@code type}, read off a throwaway instance. */
    static Set<String> traitIds(MinecraftServer server, ResourceLocation type) {
        Set<String> ids = new LinkedHashSet<>();
        Entity made = BuiltInRegistries.ENTITY_TYPE.getOptional(type).map(t -> t.create(server.overworld())).orElse(null);
        if (made instanceof LivingEntity shape) {
            for (ShapeTrait<LivingEntity> trait : TraitRegistry.getAll(shape)) {
                ids.add(trait.getId().toString());
            }
        }
        if (made != null) {
            made.discard();
        }
        return ids;
    }

    /** Traits and ability Walkers would give {@code shape} now (after the policy: none). Probe reads. */
    public static Map<String, Object> grants(LivingEntity shape) {
        Map<String, Object> out = new HashMap<>();
        List<String> traits = new ArrayList<>();
        for (ShapeTrait<LivingEntity> trait : TraitRegistry.getAll(shape)) {
            traits.add(trait.getId().toString());
        }
        out.put("traits", traits);
        out.put("ability", AbilityRegistry.get(shape) == null ? "" : String.valueOf(AbilityRegistry.get(shape).getId()));
        return out;
    }

    public static long worn() {
        return WORN.get();
    }

    public static long shedCount() {
        return SHED.get();
    }

    public static long swapsVetoed() {
        return SWAPS_VETOED.get();
    }

    public static long unlocksVetoed() {
        return UNLOCKS_VETOED.get();
    }

    public static long abilitiesVetoed() {
        return ABILITIES_VETOED.get();
    }
}
