package com.cyberday1.riftfall.gear;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import org.jetbrains.annotations.Nullable;

import com.cyberday1.riftfall.RiftfallCore;

/**
 * The 19 signature relics: which item carries which signature, its kind, and which worn signatures are active.
 * Mobility is free; at most {@link #COMBAT_CAP} combat or trade-off signatures are active, the first in
 * {@link #SLOT_ORDER}; copies count once. Worn relics come from {@link #reader} (Curios, set by
 * {@code RelicSignatureCurios}), never {@code curioTick} or Relics' {@code EntityUtils}, both silenced.
 * No signature is a spell, a cast or an {@code AbilityGrants} entry.
 */
public final class RelicSignatures {

    public enum Kind {
        MOBILITY, COMBAT, TRADE_OFF;

        /** Counts toward {@link #COMBAT_CAP}. */
        public boolean capped() {
            return this != MOBILITY;
        }
    }

    /** The ruled 19. {@code live} once the slice that builds its effect lands. */
    public enum Signature {
        DOUBLE_JUMP("double_jump", "relics:springy_boot", Kind.MOBILITY, true),
        WALL_CLING("wall_cling", "relics:phantom_boot", Kind.MOBILITY, true),
        MOMENTUM("momentum", "relics:roller_skates", Kind.MOBILITY, true),
        GROUND_POUND("ground_pound", "relics:ice_breaker", Kind.COMBAT, true),
        SPORE_BURST("spore_burst", "relics:spore_sack", Kind.COMBAT, true),
        RICOCHET("ricochet", "relics:shadow_glaive", Kind.COMBAT, true),
        LAST_RITES("last_rites", "relics:holy_locket", Kind.COMBAT, true),
        TORMENT("torment", "more_relics:greater_liandrys_torment", Kind.COMBAT, true),
        FIREBORN("fireborn", "relics:magma_walker", Kind.TRADE_OFF, true),
        BERSERKER_RAGE("berserker_rage", "relics:rage_glove", Kind.TRADE_OFF, true),
        NIGHTSTALKER("nightstalker", "relics:midnight_robe", Kind.TRADE_OFF, true),
        DEAD_WEIGHT("dead_weight", "relics:drowned_belt", Kind.TRADE_OFF, true),
        PACK_LEADER("pack_leader", "relics:hunter_belt", Kind.TRADE_OFF, true),
        GREED("greed", "relics:bastion_ring", Kind.TRADE_OFF, true),
        UNSTABLE_STEP("unstable_step", "relics:chorus_inhibitor", Kind.TRADE_OFF, true),
        SOUL_STACKS("soul_stacks", "more_relics:superior_mejais_soulstealer", Kind.TRADE_OFF, true),
        LAST_STAND("last_stand", "more_relics:greater_proc_rage", Kind.TRADE_OFF, true),
        BERSERK("berserk", "more_relics:medium_proc_rage", Kind.TRADE_OFF, true),
        FROSTBOUND("frostbound", "more_relics:greater_frozen_heart", Kind.TRADE_OFF, true);

        private final String key;
        private final ResourceLocation item;
        private final Kind kind;
        private final boolean live;

        Signature(String key, String item, Kind kind, boolean live) {
            this.key = key;
            this.item = ResourceLocation.parse(item);
            this.kind = kind;
            this.live = live;
        }

        /** {@code riftfall:relic_signature/<key>}: a name, not a spell or skill id. */
        public ResourceLocation id() {
            return RiftfallCore.id("relic_signature/" + key);
        }

        public String key() {
            return key;
        }

        public ResourceLocation item() {
            return item;
        }

        public Kind kind() {
            return kind;
        }

        /** Whether its effect is built; the tooltip names only live signatures. */
        public boolean live() {
            return live;
        }
    }

    /** One worn signature relic, where Curios has it. */
    public record Worn(String slot, int index, Signature signature) {
    }

    /** Active and dormant signatures; a signature is never both. */
    public record ActiveSet(Set<Signature> active, Set<Signature> dormant) {
        public static final ActiveSet NONE = new ActiveSet(Set.of(), Set.of());

        public boolean isActive(Signature signature) {
            return active.contains(signature);
        }
    }

    /** Ruled 2026-11-20: two combat or trade-off signatures at once. */
    public static final int COMBAT_CAP = 2;

    /** The slot order that picks the active two; {@code rings} sits with {@code ring}, any other slot last. */
    public static final List<String> SLOT_ORDER =
            List.of("spell_trinket", "necklace", "belt", "ring", "hands", "charm", "back", "feet");

    /** The shared damage bucket's ceiling (draft section 5). */
    public static final double BUCKET_CAP = 0.40;

    public static final ResourceLocation BUCKET_ID = RiftfallCore.id("relic_signature/damage");

    /** Self-damage and procs from signatures; physical, never crits, never triggers a relic. */
    public static final ResourceKey<DamageType> RELIC_DAMAGE =
            ResourceKey.create(Registries.DAMAGE_TYPE, RiftfallCore.id("relic"));

    public static final TagKey<DamageType> TRIGGERS_NO_RELIC =
            TagKey.create(Registries.DAMAGE_TYPE, RiftfallCore.id("triggers_no_relic"));

    private static final Map<ResourceLocation, Signature> BY_ITEM = byItem();

    private static final Map<UUID, ActiveSet> CACHE = new ConcurrentHashMap<>();

    /** Damage terms by signature, summed into the bucket while active. Filled by the slices that build them. */
    private static final Map<Signature, ToDoubleFunction<ServerPlayer>> TERMS =
            Collections.synchronizedMap(new EnumMap<>(Signature.class));

    private static volatile Function<Player, List<Worn>> reader = player -> List.of();

    private static volatile boolean suspended;

    private RelicSignatures() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(RelicSignatures::onLoggedIn);
        NeoForge.EVENT_BUS.addListener(RelicSignatures::onRespawn);
        NeoForge.EVENT_BUS.addListener(RelicSignatures::onLoggedOut);
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent event) -> CACHE.clear());
    }

    /** Probe only: signature lines and the bucket held off, so a fixed-line measurement sees fixed lines alone. */
    public static <T> T withoutSignatures(java.util.function.Supplier<T> measure) {
        boolean previous = suspended;
        suspended = true;
        try {
            return measure.get();
        } finally {
            suspended = previous;
        }
    }

    static boolean suspended() {
        return suspended;
    }

    /** Curios' reader; without Curios nothing is ever worn. */
    static void readWith(Function<Player, List<Worn>> worn) {
        reader = worn;
    }

    public static @Nullable Signature of(ResourceLocation item) {
        return BY_ITEM.get(item);
    }

    /** The worn signature relics, read now. Either side. */
    public static List<Worn> worn(Player player) {
        return reader.apply(player);
    }

    /** Mobility is always active when worn, so this needs no active set. Either side. */
    public static boolean wears(Player player, Signature signature) {
        for (Worn w : worn(player)) {
            if (w.signature() == signature) return true;
        }
        return false;
    }

    /** The cached active set, computed on first ask after a change. Server. */
    public static ActiveSet active(ServerPlayer player) {
        return CACHE.computeIfAbsent(player.getUUID(), id -> active(worn(player)));
    }

    /** A worn relic changed: recompute, then put the signature lines and the bucket right. Server. */
    public static void refresh(ServerPlayer player) {
        CACHE.put(player.getUUID(), active(worn(player)));
        RelicSignatureStats.apply(player);
        RelicSignatureProcs.refreshed(player);
        rebucket(player);
    }

    /** A term moved (a condition, a stack): sum the active terms again, and tell the HUD if a stack moved. Server. */
    public static void rebucket(ServerPlayer player) {
        applyBucket(player, bucket(player));
        RelicStackSync.check(player);
    }

    /** A new or rebuilt client (login, respawn): refresh, and send the stacks whether or not they moved. Server. */
    public static void rejoin(ServerPlayer player, boolean respawned) {
        if (respawned) RelicSignatureStats.reset(player);
        RelicStackSync.unsent(player);
        refresh(player);
    }

    /** Relic damage: no direct entity, so on-hit stats that read one (Apothic's life steal) never see it. */
    public static DamageSource damage(Level level, @Nullable Entity cause) {
        return new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(RELIC_DAMAGE), null, cause);
    }

    /**
     * The rule: walk {@code worn} in {@link #SLOT_ORDER} (then index); mobility is active, the first
     * {@link #COMBAT_CAP} other signatures are active, later ones dormant, and a copy of one already placed is skipped.
     */
    public static ActiveSet active(List<Worn> worn) {
        if (worn.isEmpty()) return ActiveSet.NONE;
        List<Worn> ordered = new ArrayList<>(worn);
        ordered.sort(Comparator.comparingInt((Worn w) -> rank(w.slot())).thenComparing(Worn::slot)
                .thenComparingInt(Worn::index));
        Set<Signature> active = EnumSet.noneOf(Signature.class);
        Set<Signature> dormant = EnumSet.noneOf(Signature.class);
        int capped = 0;
        for (Worn w : ordered) {
            Signature s = w.signature();
            if (active.contains(s) || dormant.contains(s)) continue;
            if (!s.kind().capped()) {
                active.add(s);
            } else if (capped < COMBAT_CAP) {
                active.add(s);
                capped++;
            } else {
                dormant.add(s);
            }
        }
        return new ActiveSet(Collections.unmodifiableSet(active), Collections.unmodifiableSet(dormant));
    }

    static int rank(String slot) {
        int at = SLOT_ORDER.indexOf("rings".equals(slot) ? "ring" : slot);
        return at < 0 ? SLOT_ORDER.size() : at;
    }

    /** Summed terms, at most {@link #BUCKET_CAP}; NaN counts as none. */
    public static double bucket(double... terms) {
        double sum = 0.0D;
        for (double t : terms) {
            if (!Double.isNaN(t)) sum += t;
        }
        return Math.min(BUCKET_CAP, sum);
    }

    /** A signature's damage term, read while it is active. For the effect slices. */
    static void damageTerm(Signature signature, ToDoubleFunction<ServerPlayer> term) {
        TERMS.put(signature, term);
    }

    private static double bucket(ServerPlayer player) {
        if (suspended) return 0.0D;
        ActiveSet set = active(player);
        List<Double> terms = new ArrayList<>();
        synchronized (TERMS) {
            TERMS.forEach((signature, term) -> {
                if (set.isActive(signature)) terms.add(term.applyAsDouble(player));
            });
        }
        return bucket(terms.stream().mapToDouble(Double::doubleValue).toArray());
    }

    /** One transient {@code add_multiplied_total} on attack damage, or none at 0. */
    public static void applyBucket(Player player, double amount) {
        AttributeInstance damage = player.getAttribute(Attributes.ATTACK_DAMAGE);
        if (damage == null) return;
        AttributeModifier now = damage.getModifier(BUCKET_ID);
        if (amount == 0.0D) {
            if (now != null) damage.removeModifier(BUCKET_ID);
            return;
        }
        if (now != null && now.amount() == amount) return;
        damage.addOrUpdateTransientModifier(new AttributeModifier(BUCKET_ID, amount,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    }

    /** Whether a hit may start a relic effect: not a relic's own damage, nor anything tagged with it. */
    public static boolean triggers(DamageSource source) {
        return !source.is(TRIGGERS_NO_RELIC) && !source.is(RELIC_DAMAGE);
    }

    private static void onLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) rejoin(player, false);
    }

    /** A respawn is a new entity: its transient lines and bucket are gone, and its conditions start over. */
    private static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) rejoin(player, true);
    }

    private static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        CACHE.remove(event.getEntity().getUUID());
    }

    private static Map<ResourceLocation, Signature> byItem() {
        Map<ResourceLocation, Signature> out = new java.util.HashMap<>();
        for (Signature s : Signature.values()) {
            if (out.put(s.item(), s) != null) throw new IllegalStateException("two signatures on " + s.item());
        }
        return Map.copyOf(out);
    }
}
