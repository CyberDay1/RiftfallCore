package com.cyberday1.riftfall.compat;

import com.cyberday1.riftfall.gear.GunGrade;
import com.cyberday1.riftfall.gear.RiftArms;
import com.cyberday1.riftfall.init.RiftfallDataComponents;
import com.cyberday1.riftfall.init.RiftfallEffects;
import com.cyberday1.riftfall.skill.ChargeRules;
import com.cyberday1.riftfall.skill.ChargeSync;
import com.cyberday1.riftfall.skill.ChargeView;
import com.cyberday1.riftfall.skill.GunnerGuns;
import com.cyberday1.riftfall.skill.ManaCombat;
import com.cyberday1.riftfall.skill.RangedCharge;
import com.mojang.logging.LogUtils;
import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.api.GunProperties;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.event.common.AttachmentPropertyEvent;
import com.tacz.guns.api.event.common.EntityHurtByGunEvent;
import com.tacz.guns.api.event.common.GunFireEvent;
import com.tacz.guns.api.event.common.GunShootEvent;
import com.tacz.guns.api.item.IAmmo;
import com.tacz.guns.api.item.IAmmoBox;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.builder.AmmoItemBuilder;
import com.tacz.guns.api.item.gun.AbstractGunItem;
import com.tacz.guns.api.modifier.ParameterizedCache;
import com.tacz.guns.api.modifier.ParameterizedCachePair;
import com.tacz.guns.entity.EntityKineticBullet;
import com.tacz.guns.entity.shooter.ShooterDataHolder;
import com.tacz.guns.resource.index.CommonGunIndex;
import com.tacz.guns.resource.modifier.AttachmentCacheProperty;
import com.tacz.guns.resource.modifier.AttachmentPropertyManager;
import com.tacz.guns.resource.pojo.data.gun.BulletData;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import com.tacz.guns.resource.pojo.data.gun.InaccuracyType;
import com.tacz.guns.util.AttachmentDataUtils;
import it.unimi.dsi.fastutil.Pair;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The only class outside the TaCZ probe that names a TaCZ type, reached only behind {@link GunnerGuns#loaded()}.
 * Bullet damage rides {@code EntityHurtByGunEvent.Pre}; fire rate, spread, recoil and aim time ride TaCZ's per-shooter
 * property cache ({@code AttachmentPropertyEvent}, both sides, refreshed when a buff or the gun's grade changes); reload
 * speed moves the reload's start back each server tick. The numbers are {@link GunnerGuns}'s and the gun's
 * {@link GunGrade.Stats}. A player's gun needs no ammo ({@code MixinTaczAmmoCheck}): each round fired debits
 * {@link RangedCharge} instead, and a round Riftfall takes from the shooter's inventory boosts that round's bullets.
 */
public final class TaczGuns {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ResourceLocation RANGED_DAMAGE = ResourceLocation.fromNamespaceAndPath("ranged_weapon", "damage");
    private static final ResourceLocation DRAW_SPEED =
            ResourceLocation.fromNamespaceAndPath("apothic_attributes", "draw_speed");

    private static final AtomicLong HITS_SCALED = new AtomicLong();
    private static final AtomicLong BULLETS_GRADED = new AtomicLong();
    /** A bullet's whole gun multiplier (global x band x grade), written when it enters the level. */
    public static final String FACTOR_KEY = "riftfall:gun_factor";
    private static final AtomicLong CACHES_EDITED = new AtomicLong();
    private static final AtomicLong RELOAD_TICKS = new AtomicLong();
    private static final AtomicLong REFILLS = new AtomicLong();
    private static final Map<UUID, Integer> SIGNATURES = new ConcurrentHashMap<>();
    private static final Map<UUID, Double> CARRY = new ConcurrentHashMap<>();
    private static final Map<UUID, Double> BOLT_CARRY = new ConcurrentHashMap<>();
    private static final AtomicLong BOLT_TICKS = new AtomicLong();
    private static final AtomicLong ROUNDS_BOOSTED = new AtomicLong();
    /** Each shooter's round in flight: opened by its fire event, paid by its first bullet. */
    private static final Map<UUID, Round> ROUNDS = new ConcurrentHashMap<>();
    /** TaCZ's recoil pair has only a private constructor; null if it ever goes, and recoil is then left alone. */
    private static final @Nullable Constructor<?> RECOIL_PAIR = recoilPairConstructor();
    private static volatile boolean registered;

    private TaczGuns() {
    }

    /** The listeners. Idempotent. */
    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        NeoForge.EVENT_BUS.addListener(TaczGuns::onHurt);
        NeoForge.EVENT_BUS.addListener(TaczGuns::onProperties);
        NeoForge.EVENT_BUS.addListener(TaczGuns::onTick);
        NeoForge.EVENT_BUS.addListener(TaczGuns::onJoin);
        NeoForge.EVENT_BUS.addListener(TaczGuns::onBlast);
        NeoForge.EVENT_BUS.addListener(TaczGuns::onShoot);
        NeoForge.EVENT_BUS.addListener(TaczGuns::onFire);
        GunGrade.setShotReader(TaczGuns::gradedShot);
        LOGGER.info("Gunner TaCZ hooks registered: bullet damage, gun grades, fire rate, spread, reload speed, "
                + "magazine refill, Charge");
    }

    public static boolean registered() {
        return registered;
    }

    // ---- reads ----

    /** One trigger pull of the gun {@code entity} holds (bullet damage x pellets), or 0 with no gun in hand. */
    public static double shotDamage(LivingEntity entity) {
        return gunData(entity.getMainHandItem()).map(TaczGuns::shotDamage).orElse(0.0);
    }

    /**
     * TaCZ's damage for one pull at close range: the first step of the gun's distance falloff when it has one
     * (an M16A1 lists 8 and hits 8.5 inside 45 blocks), else its listed damage. A shotgun's damage is the whole shot:
     * TaCZ splits it across the pellets ({@code applyShotgunDamageSpread}), so the pellet count does not multiply it.
     */
    public static double shotDamage(GunData data) {
        BulletData bullet = data.getBulletData();
        if (bullet == null) {
            return 0.0;
        }
        var extra = bullet.getExtraDamage();
        var falloff = extra == null ? null : extra.getDamageAdjust();
        return falloff == null || falloff.isEmpty() ? bullet.getDamageAmount() : falloff.getFirst().getDamage();
    }

    /** The whole bullet multiplier a gun stack carries (global x band x grade); 1.0 for anything not a gun. */
    public static double gunFactor(ItemStack stack) {
        IGun gun = IGun.getIGunOrNull(stack);
        return gun == null ? 1.0 : RiftArms.gunFactor(gun.getGunId(stack).toString(), GunGrade.of(stack));
    }

    /** One pull of {@code stack} as it lands in Riftfall: TaCZ's damage times {@link #gunFactor}; 0 if no gun. */
    public static double gradedShot(ItemStack stack) {
        return gunData(stack).map(data -> shotDamage(data) * gunFactor(stack)).orElse(0.0);
    }

    /**
     * The multiplier for a bullet: the one written on it when it entered the level, else the attacker's held gun if
     * it is the gun that fired, else that gun at its baseline grade.
     */
    static double bulletFactor(@Nullable Entity bullet, @Nullable LivingEntity attacker, @Nullable ResourceLocation gunId) {
        if (bullet != null && bullet.getPersistentData().contains(FACTOR_KEY)) {
            return bullet.getPersistentData().getDouble(FACTOR_KEY);
        }
        if (attacker != null) {
            ItemStack held = attacker.getMainHandItem();
            IGun gun = IGun.getIGunOrNull(held);
            if (gun != null && (gunId == null || gunId.equals(gun.getGunId(held)))) {
                return gunFactor(held);
            }
        }
        return gunId == null ? 1.0 : RiftArms.gunFactor(gunId.toString(), GunGrade.BASELINE);
    }

    public static Optional<GunData> gunData(ItemStack stack) {
        IGun gun = IGun.getIGunOrNull(stack);
        if (gun == null) {
            return Optional.empty();
        }
        return TimelessAPI.getCommonGunIndex(gun.getGunId(stack)).map(CommonGunIndex::getGunData);
    }

    /** The bullet multiplier for {@code shooter}: ranged damage bonus and damage buffs, the gun counted as a weapon. */
    public static double damageMultiplier(LivingEntity shooter) {
        return GunnerGuns.damageMultiplier(bonus(shooter, RANGED_DAMAGE), buffRatio(shooter));
    }

    /** {@code shooter}'s fire-rate bonus (Fast Hands), 0 without it. */
    public static double fireRate(LivingEntity shooter) {
        return bonus(shooter, com.cyberday1.riftfall.init.RiftfallAttributes.FIRE_RATE.getId());
    }

    /** How fast {@code shooter} reloads against TaCZ's clock: draw speed and Hastened, times the held gun's grade. */
    public static double reloadRate(LivingEntity shooter) {
        return GunnerGuns.reloadRate(bonus(shooter, DRAW_SPEED), levels(shooter, RiftfallEffects.HASTENED))
                * GunGrade.of(shooter.getMainHandItem()).stats().reloadRate();
    }

    /**
     * What one trigger cycle of {@code stack} costs the Charge pool: its close-range shot at its tier and common
     * rarity, so a rarer gun's premium stays real damage. Null for anything that is not a gun TaCZ knows.
     */
    public static @Nullable ChargeRules.Shot chargeShot(ItemStack stack) {
        IGun gun = IGun.getIGunOrNull(stack);
        Optional<GunData> data = gunData(stack);
        if (gun == null || data.isEmpty()) {
            return null;
        }
        int tier = GunGrade.of(stack).tier();
        double common = shotDamage(data.get())
                * RiftArms.gunFactor(gun.getGunId(stack).toString(), new GunGrade(tier, 0, 0.0F));
        return ChargeRules.shot(common, tier, gun.getRPM(stack) / 60.0);
    }

    /** Value over base of one of {@code entity}'s attributes, by id; 0 without it. */
    static double bonus(LivingEntity entity, ResourceLocation id) {
        AttributeInstance instance = BuiltInRegistries.ATTRIBUTE.getHolder(id)
                .map(entity::getAttribute).orElse(null);
        return instance == null ? 0.0 : instance.getValue() - instance.getBaseValue();
    }

    /** Attack damage with the gun as a weapon, all over the same without any status effect's modifiers. */
    static double buffRatio(LivingEntity shooter) {
        AttributeInstance attack = shooter.getAttribute(Attributes.ATTACK_DAMAGE);
        if (attack == null) {
            return 1.0;
        }
        Set<ResourceLocation> fromEffects = new HashSet<>();
        for (MobEffectInstance effect : shooter.getActiveEffects()) {
            effect.getEffect().value().createModifiers(effect.getAmplifier(), (attribute, modifier) -> {
                if (attribute.is(Attributes.ATTACK_DAMAGE)) {
                    fromEffects.add(modifier.id());
                }
            });
        }
        if (fromEffects.isEmpty()) {
            return 1.0;
        }
        double base = attack.getBaseValue() + (attack.getModifier(GunnerGuns.CAST_GUN_ID) == null
                ? GunnerGuns.castBonus(shotDamage(shooter)) : 0.0);
        return GunnerGuns.buffRatio(base, attack.getModifiers(), fromEffects::contains);
    }

    /** The levels (amplifier + 1) of {@code effect} on {@code entity}; 0 without it. */
    static int levels(LivingEntity entity, Holder<MobEffect> effect) {
        MobEffectInstance instance = entity.getEffect(effect);
        return instance == null ? 0 : instance.getAmplifier() + 1;
    }

    // ---- bullets ----

    private static void onHurt(EntityHurtByGunEvent.Pre event) {
        LivingEntity attacker = event.getAttacker();
        if (attacker == null || event.getLogicalSide().isClient()) {
            return;
        }
        double multiplier = damageMultiplier(attacker) * bulletFactor(event.getBullet(), attacker, event.getGunId());
        if (multiplier != 1.0) {
            event.setBaseAmount((float) (event.getBaseAmount() * multiplier));
            HITS_SCALED.incrementAndGet();
        }
    }

    /** Writes the firing gun's multiplier on a bullet as it enters the level, so a weapon swap mid-flight is moot. */
    private static void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof EntityKineticBullet bullet)) {
            return;
        }
        LivingEntity shooter = bullet.getOwner() instanceof LivingEntity living ? living : null;
        double factor = bulletFactor(null, shooter, bullet.getGunId());
        if (shooter instanceof ServerPlayer player && RangedCharge.applies(player)) {
            factor *= ChargeRules.ammoBonus(paid(player, bullet.getGunId()), true);
        }
        bullet.getPersistentData().putDouble(FACTOR_KEY, factor);
        BULLETS_GRADED.incrementAndGet();
    }

    // ---- Charge: the gate, the debit and the crafted-round bonus ----

    /** One trigger cycle: however many pellets it throws, it is paid for once and boosted by at most one round. */
    private static final class Round {
        final long tick;
        boolean paid;
        boolean boosted;

        Round(long tick) {
            this.tick = tick;
        }
    }

    /** A trigger pull, both sides: the client asks its synced copy (so no recoil or sound), the server the pool. */
    private static void onShoot(GunShootEvent event) {
        if (!(event.getShooter() instanceof Player player) || !RangedCharge.applies(player)) {
            return;
        }
        if (event.getLogicalSide().isClient()) {
            if (player.isLocalPlayer() && ChargeView.locked()) {
                event.setCanceled(true);
            }
            return;
        }
        ChargeRules.Shot shot = chargeShot(event.getGunItemStack());
        if (shot != null && player instanceof ServerPlayer server
                && !RangedCharge.gate(server, shot, ManaCombat.now(server.server))) {
            event.setCanceled(true);
            ChargeSync.tell(server);
        }
    }

    /** Each round of a pull or a burst opens a new {@link Round}; its bullets follow in the same call. */
    private static void onFire(GunFireEvent event) {
        if (event.getLogicalSide().isServer() && event.getShooter() instanceof ServerPlayer player) {
            ROUNDS.put(player.getUUID(), new Round(ManaCombat.now(player.server)));
        }
    }

    /**
     * Pays for the round {@code shooter} is firing, once: the pool is debited when its first bullet really enters
     * the level, and one real round of the gun's calibre is taken if the shooter carries one. Answers whether the
     * round was boosted.
     */
    private static boolean paid(ServerPlayer shooter, ResourceLocation gunId) {
        UUID id = shooter.getUUID();
        long now = ManaCombat.now(shooter.server);
        Round round = ROUNDS.get(id);
        if (round == null || round.tick != now) {
            round = new Round(now);
            ROUNDS.put(id, round);
        }
        if (!round.paid) {
            round.paid = true;
            ItemStack held = shooter.getMainHandItem();
            IGun gun = IGun.getIGunOrNull(held);
            if (gun != null && gunId.equals(gun.getGunId(held))) {
                ChargeRules.Shot shot = chargeShot(held);
                if (shot != null) {
                    RangedCharge.debit(shooter, shot.cost());
                }
                round.boosted = takeRound(shooter, held);
                if (round.boosted) {
                    ROUNDS_BOOSTED.incrementAndGet();
                }
            }
            ChargeSync.tell(shooter);
        }
        return round.boosted;
    }

    /**
     * Takes one real round for {@code gun} from {@code shooter}: an ammo item, else one out of an ammo box. A
     * creative box holds nothing real and a gun's dummy ammo is not an item, so neither pays. True only when a
     * round was removed.
     */
    static boolean takeRound(LivingEntity shooter, ItemStack gun) {
        IItemHandler items = shooter.getCapability(Capabilities.ItemHandler.ENTITY, null);
        if (items == null) {
            return false;
        }
        for (int slot = 0; slot < items.getSlots(); slot++) {
            ItemStack stack = items.getStackInSlot(slot);
            if (stack.getItem() instanceof IAmmo ammo && ammo.isAmmoOfGun(gun, stack)) {
                if (items.extractItem(slot, 1, false).getCount() == 1) {
                    return true;
                }
            } else if (stack.getItem() instanceof IAmmoBox box && !box.isCreative(stack)
                    && !box.isAllTypeCreative(stack) && box.isAmmoBoxOfGun(gun, stack)) {
                int held = box.getAmmoCount(stack);
                if (held > 0) {
                    box.setAmmoCount(stack, held - 1);
                    if (held == 1) {
                        box.setAmmoId(stack, DefaultAssets.EMPTY_AMMO_ID);
                    }
                    return true;
                }
            }
        }
        return false;
    }

    /** How many real rounds for {@code gun} the shooter carries: ammo items and ammo boxes, creative boxes aside. */
    static int rounds(LivingEntity shooter, ItemStack gun) {
        IItemHandler items = shooter.getCapability(Capabilities.ItemHandler.ENTITY, null);
        int count = 0;
        for (int slot = 0; items != null && slot < items.getSlots(); slot++) {
            ItemStack stack = items.getStackInSlot(slot);
            if (stack.getItem() instanceof IAmmo ammo && ammo.isAmmoOfGun(gun, stack)) {
                count += stack.getCount();
            } else if (stack.getItem() instanceof IAmmoBox box && !box.isCreative(stack)
                    && !box.isAllTypeCreative(stack) && box.isAmmoBoxOfGun(gun, stack)) {
                count += Math.max(0, box.getAmmoCount(stack));
            }
        }
        return count;
    }

    /** Takes {@code count} real rounds for {@code gun}, all or none. A Gunner cast's price for its bonus. */
    public static boolean takeRounds(LivingEntity shooter, ItemStack gun, int count) {
        if (count <= 0 || rounds(shooter, gun) < count) {
            return false;
        }
        for (int taken = 0; taken < count; taken++) {
            takeRound(shooter, gun);
        }
        return true;
    }

    /** Hands back rounds a cast took and then was not kept, as loose ammo of {@code gun}'s calibre. */
    public static void giveRounds(Player player, ItemStack gun, int count) {
        gunData(gun).ifPresent(data -> {
            ItemStack ammo = AmmoItemBuilder.create().setId(data.getAmmoId()).setCount(count).build();
            if (!ammo.isEmpty()) {
                player.getInventory().placeItemBackInInventory(ammo);
            }
        });
    }

    /**
     * What an extended-magazine change does to a player's gun in place of TaCZ handing the magazine back as ammo
     * items: the magazine is emptied and nothing is returned, because nothing real was ever loaded into it.
     */
    public static void emptyMagazine(ItemStack stack) {
        IGun gun = IGun.getIGunOrNull(stack);
        if (gun != null && !gun.useInventoryAmmo(stack) && gun.getCurrentAmmoCount(stack) > 0) {
            gun.setCurrentAmmoCount(stack, 0);
        }
    }

    /** A launcher's or an explosive round's blast takes the same multiplier as the bullet that set it off. */
    private static void onBlast(LivingIncomingDamageEvent event) {
        DamageSource source = event.getSource();
        if (source.is(DamageTypeTags.IS_EXPLOSION) && source.getDirectEntity() instanceof EntityKineticBullet bullet
                && bullet.getPersistentData().contains(FACTOR_KEY)) {
            event.setAmount((float) (event.getAmount() * bullet.getPersistentData().getDouble(FACTOR_KEY)));
        }
    }

    // ---- fire rate, spread, recoil and aim time: TaCZ's per-shooter cache ----

    /**
     * Hastened and Keen from the shooter, the rarity's handling from the gun stack's grade. TaCZ posts this on both
     * sides, so the server's fire-rate and spread checks and the client's recoil and aim feel read the same numbers.
     */
    private static void onProperties(AttachmentPropertyEvent event) {
        LivingEntity shooter = event.getShooter();
        if (shooter == null) {
            return;
        }
        int haste = levels(shooter, RiftfallEffects.HASTENED);
        int keen = levels(shooter, RiftfallEffects.KEEN);
        double fireRate = fireRate(shooter);
        GunGrade.Stats stats = GunGrade.of(event.getGunItem()).stats();
        if (haste == 0 && keen == 0 && fireRate <= 0.0 && stats.none()) {
            return;
        }
        AttachmentCacheProperty cache = event.getCacheProperty();
        if (haste > 0 || fireRate > 0.0 || stats.fireRate() > 0.0F) {
            Integer rpm = cache.getCache(GunProperties.ROUNDS_PER_MINUTE);
            if (rpm != null) {
                cache.setCache(GunProperties.ROUNDS_PER_MINUTE,
                        GunnerGuns.rpm(rpm, haste, stats.rpmFactor(), fireRate));
            }
        }
        if (keen > 0 || stats.spread() > 0.0F) {
            Map<InaccuracyType, Float> spread = cache.getCache(GunProperties.INACCURACY);
            if (spread != null) {
                Map<InaccuracyType, Float> tighter = new HashMap<>();
                spread.forEach((type, value) -> tighter.put(type,
                        GunnerGuns.spread(value, keen, stats.spreadFactor())));
                cache.setCache(GunProperties.INACCURACY, tighter);
            }
        }
        if (stats.ads() > 0.0F) {
            Float ads = cache.getCache(GunProperties.ADS_TIME);
            if (ads != null) {
                cache.setCache(GunProperties.ADS_TIME, (float) (ads * stats.adsFactor()));
            }
        }
        if (stats.recoil() > 0.0F) {
            ParameterizedCachePair<Float, Float> recoil = cache.getCache(GunProperties.RECOIL);
            if (recoil != null) {
                cache.setCache(GunProperties.RECOIL, scaledRecoil(recoil, stats.recoilFactor()));
            }
        }
        CACHES_EDITED.incrementAndGet();
    }

    /** {@code recoil} with both axes times {@code factor}, attachments kept; unchanged if TaCZ's pair can't be built. */
    @SuppressWarnings("unchecked")
    static ParameterizedCachePair<Float, Float> scaledRecoil(ParameterizedCachePair<Float, Float> recoil, double factor) {
        if (RECOIL_PAIR == null) {
            return recoil;
        }
        try {
            return (ParameterizedCachePair<Float, Float>) RECOIL_PAIR.newInstance(Pair.of(
                    new ScaledCache<>(recoil.left(), factor), new ScaledCache<>(recoil.right(), factor)));
        } catch (ReflectiveOperationException | RuntimeException e) {
            return recoil;
        }
    }

    private static @Nullable Constructor<?> recoilPairConstructor() {
        try {
            Constructor<?> constructor = ParameterizedCachePair.class.getDeclaredConstructor(Pair.class);
            constructor.setAccessible(true);
            return constructor;
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOGGER.warn("TaCZ's recoil cache can't be rebuilt ({}); gun grades leave recoil alone", e.toString());
            return null;
        }
    }

    /** One recoil axis: TaCZ's own evaluation (attachments and scripts included), times the grade's factor. */
    private static final class ScaledCache<T> extends ParameterizedCache<T> {
        private final ParameterizedCache<T> inner;
        private final double factor;

        ScaledCache(ParameterizedCache<T> inner, double factor) {
            super(List.of(), inner.getDefaultValue());
            this.inner = inner;
            this.factor = factor;
        }

        @Override
        public double eval(double input) {
            return inner.eval(input) * factor;
        }

        @Override
        public double eval(double input, double extraAddend, double extraPercent, double extraMultiplier) {
            return inner.eval(input, extraAddend, extraPercent, extraMultiplier) * factor;
        }
    }

    /** Rebuilds {@code shooter}'s TaCZ cache for the held gun, so a buff's start or end shows at once. */
    public static void refreshCache(LivingEntity shooter) {
        ItemStack held = shooter.getMainHandItem();
        if (IGun.getIGunOrNull(held) != null) {
            AttachmentPropertyManager.postChangeEvent(shooter, held);
        }
    }

    // ---- per tick: the cache's buff signature, and reload speed ----

    private static void onTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        boolean client = player.level().isClientSide();
        if (client && !player.isLocalPlayer()) {
            return;
        }
        ItemStack held = player.getMainHandItem();
        IGun gun = IGun.getIGunOrNull(held);
        UUID key = client ? new UUID(0L, player.getId()) : player.getUUID();
        if (gun == null) {
            SIGNATURES.remove(key);
            CARRY.remove(key);
            BOLT_CARRY.remove(key);
            return;
        }
        int signature = Objects.hash(levels(player, RiftfallEffects.HASTENED), levels(player, RiftfallEffects.KEEN),
                GunGrade.of(held).stats(), fireRate(player));
        Integer last = SIGNATURES.put(key, signature);
        if (last != null && last != signature) {
            refreshCache(player);
        }
        if (!client) {
            hastenReload(player);
            hastenBolt(player);
            refreshShown(held);
        }
    }

    /**
     * A bolt-action or pump gun cycles on TaCZ's bolt clock, not its rpm, so Fast Hands moves a running bolt's start
     * back by this tick's share, as {@link #hastenReload} does for a reload. Server side; the client reads the synced
     * bolting flag. Answers the shift.
     */
    public static long hastenBolt(LivingEntity shooter) {
        ShooterDataHolder data = IGunOperator.fromLivingEntity(shooter).getDataHolder();
        UUID key = shooter.getUUID();
        double rate = GunnerGuns.cycleRate(fireRate(shooter));
        if (data == null || !data.isBolting || data.boltTimestamp < 0L || rate <= 1.0) {
            BOLT_CARRY.remove(key);
            return 0L;
        }
        double owed = GunnerGuns.reloadShiftMillis(rate) + BOLT_CARRY.getOrDefault(key, 0.0);
        long shift = (long) Math.floor(owed);
        BOLT_CARRY.put(key, owed - shift);
        if (shift > 0L) {
            data.boltTimestamp -= shift;
            BOLT_TICKS.incrementAndGet();
        }
        return shift;
    }

    /** Moves a running reload's start back by this tick's share of {@code shooter}'s reload rate. Answers the shift. */
    public static long hastenReload(LivingEntity shooter) {
        ShooterDataHolder data = IGunOperator.fromLivingEntity(shooter).getDataHolder();
        UUID key = shooter.getUUID();
        if (data == null || !data.reloadStateType.isReloading() || data.reloadTimestamp < 0L) {
            CARRY.remove(key);
            return 0L;
        }
        double owed = GunnerGuns.reloadShiftMillis(reloadRate(shooter)) + CARRY.getOrDefault(key, 0.0);
        long shift = (long) Math.floor(owed);
        CARRY.put(key, owed - shift);
        if (shift > 0L) {
            data.reloadTimestamp -= shift;
            RELOAD_TICKS.incrementAndGet();
        }
        return shift;
    }

    /**
     * Keeps a held gun's tooltip damage and handling bonuses in step with the datapack's current numbers. A changed
     * bonus changes the tick signature, which rebuilds TaCZ's cache on both sides.
     */
    public static void refreshShown(ItemStack held) {
        GunGrade grade = GunGrade.read(held);
        if (grade == null) {
            // A gun from before grades (or creative) fires at the baseline, so it says so.
            GunGrade.stamp(held, GunGrade.BASELINE.tier(), GunGrade.BASELINE.rarity());
            return;
        }
        float shown = (float) (Math.round(gradedShot(held) * 10.0) / 10.0);
        GunGrade.Stats stats = RiftArms.grading().stats(grade.rarity());
        if (shown != grade.shown() || !stats.equals(grade.stats())) {
            held.set(RiftfallDataComponents.GUN_GRADE.get(), grade.withShown(shown).withStats(stats));
        }
    }

    // ---- Tactical / Quick Reload ----

    /**
     * Fills the magazine of the gun {@code shooter} holds, ending any reload in progress: free for a player, whose
     * guns need no ammo, and from its own ammo (or the gun's dummy ammo) for anything else. Answers the rounds loaded.
     */
    public static int refill(LivingEntity shooter) {
        ItemStack held = shooter.getMainHandItem();
        if (!(held.getItem() instanceof AbstractGunItem gun) || gun.useInventoryAmmo(held)) {
            return 0;
        }
        Optional<GunData> data = gunData(held);
        if (data.isEmpty()) {
            return 0;
        }
        IGunOperator operator = IGunOperator.fromLivingEntity(shooter);
        int max = AttachmentDataUtils.getAmmoCountWithAttachment(held, data.get());
        int needed = max - gun.getCurrentAmmoCount(held);
        if (needed <= 0) {
            return 0;
        }
        int got;
        if (!operator.needCheckAmmo()) {
            got = needed;
        } else if (gun.useDummyAmmo(held)) {
            got = gun.findAndExtractDummyAmmo(held, needed);
        } else {
            IItemHandler items = shooter.getCapability(Capabilities.ItemHandler.ENTITY, null);
            got = items == null ? 0 : gun.findAndExtractInventoryAmmo(items, held, needed);
        }
        if (got <= 0) {
            return 0;
        }
        operator.cancelReload();
        ShooterDataHolder holder = operator.getDataHolder();
        if (holder != null && holder.reloadStateType.isReloading()) {
            holder.reloadTimestamp = -1L;
            holder.reloadStateType = com.tacz.guns.api.entity.ReloadState.StateType.NOT_RELOADING;
        }
        gun.setCurrentAmmoCount(held, gun.getCurrentAmmoCount(held) + got);
        REFILLS.incrementAndGet();
        return got;
    }

    // ---- the probe's reads ----

    public static long hitsScaled() {
        return HITS_SCALED.get();
    }

    public static long bulletsGraded() {
        return BULLETS_GRADED.get();
    }

    public static long cachesEdited() {
        return CACHES_EDITED.get();
    }

    public static long boltTicks() {
        return BOLT_TICKS.get();
    }

    public static long reloadTicks() {
        return RELOAD_TICKS.get();
    }

    public static long refills() {
        return REFILLS.get();
    }

    public static long roundsBoosted() {
        return ROUNDS_BOOSTED.get();
    }

    /** The attribute TaCZ guns' ranged bonus is read from, for the probe. */
    public static @Nullable Holder<Attribute> rangedDamage() {
        return BuiltInRegistries.ATTRIBUTE.getHolder(RANGED_DAMAGE).<Holder<Attribute>>map(h -> h).orElse(null);
    }

    public static @Nullable Holder<Attribute> drawSpeed() {
        return BuiltInRegistries.ATTRIBUTE.getHolder(DRAW_SPEED).<Holder<Attribute>>map(h -> h).orElse(null);
    }
}
