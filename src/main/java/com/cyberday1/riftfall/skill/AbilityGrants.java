package com.cyberday1.riftfall.skill;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * What a player got from Riftfall: the ability of every active they own, the ultimate only while it
 * sits in an open slot 8, and the spell of every spell passive they own. Every third-party ability
 * gate asks this and refuses the rest. Names no mod type, so the Iron's and item gates can share it.
 */
public final class AbilityGrants {

    /** One player's grants, by the engine that runs them. Empty allows nothing. */
    public record Grants(Map<SkillNode.Engine, Set<ResourceLocation>> byEngine) {

        public static final Grants NONE = new Grants(Map.of());

        public Grants {
            Map<SkillNode.Engine, Set<ResourceLocation>> copy = new EnumMap<>(SkillNode.Engine.class);
            byEngine.forEach((engine, ids) -> copy.put(engine, Set.copyOf(ids)));
            byEngine = Map.copyOf(copy);
        }

        public boolean allows(SkillNode.Engine engine, @Nullable ResourceLocation ability) {
            Set<ResourceLocation> ids = byEngine.get(engine);
            return ability != null && ids != null && ids.contains(ability);
        }
    }

    /**
     * The grants each owner's stored offer was built with. An engine that only rebuilds on its own
     * triggers asks this every tick, so a buy, an unslot or a table reload reaches the offer at once.
     */
    public static final class Offered {

        private final Map<Object, Grants> built = Collections.synchronizedMap(new WeakHashMap<>());

        /** Whether the owner's offer was built with a grant set other than {@code now}, or never built. */
        public boolean stale(Object owner, Grants now) {
            Grants was = built.get(owner);
            return was == null || !was.equals(now);
        }

        public void built(Object owner, Grants with) {
            built.put(owner, with);
        }
    }

    private record Cached(SkillBook book, SkillTable table, Grants grants) {
    }

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final Map<UUID, Cached> CACHE = new ConcurrentHashMap<>();

    private static volatile boolean warned;

    private AbilityGrants() {
    }

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(AbilityGrants::setup);
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent event) -> {
            CACHE.remove(event.getEntity().getUUID());
            PaidHits.MELEE.forget(event.getEntity().getUUID());
        });
    }

    /** {@code enqueueWork}: Spell Engine's {@code Event#register} is a bare {@code ArrayList#add}. */
    private static void setup(FMLCommonSetupEvent event) {
        if (SkillCasts.engineAvailable(SkillNode.Engine.SPELL_ENGINE)) {
            event.enqueueWork(() -> RiftSpellGate.register());
        } else if (ModList.get().isLoaded("spell_engine")) {
            LOGGER.error("Spell Engine is loaded at a version Riftfall does not support: the ability gate is OFF "
                    + "and every Spell Engine ability is usable by every player");
        }
        if (SkillCasts.engineAvailable(SkillNode.Engine.IRONS_SPELLBOOKS)) {
            RiftIronsCast.register();
        }
    }

    /** This player's grants, rebuilt only when their book or the table is a new instance. */
    public static Grants of(ServerPlayer player) {
        return cached(player.getUUID(), SkillService.bookOf(player), SkillDefs.table());
    }

    static Grants cached(UUID who, SkillBook book, SkillTable table) {
        Cached hit = CACHE.get(who);
        if (hit != null && hit.book() == book && hit.table() == table) {
            return hit.grants();
        }
        Grants grants = of(book, table::get, table::passive);
        CACHE.put(who, new Cached(book, table, grants));
        return grants;
    }

    /** {@code defs} answers the live castable for an id, or null; a passive or a removed node grants nothing. */
    public static Grants of(SkillBook book, Function<String, SkillDef> defs) {
        return of(book, defs, id -> null);
    }

    /** As above, plus the Spell Engine spell of every owned spell passive {@code passives} answers. */
    public static Grants of(SkillBook book, Function<String, SkillDef> defs, Function<String, PassiveDef> passives) {
        Map<SkillNode.Engine, Set<ResourceLocation>> granted = new EnumMap<>(SkillNode.Engine.class);
        for (String id : book.ranks().keySet()) {
            SkillDef def = granting(book, defs, id);
            if (def != null) {
                granted.computeIfAbsent(def.cast().engine(), engine -> new HashSet<>()).add(def.cast().ability());
            }
        }
        for (ResourceLocation spell : PassiveSpells.of(book, passives).keySet()) {
            granted.computeIfAbsent(SkillNode.Engine.SPELL_ENGINE, engine -> new HashSet<>()).add(spell);
        }
        return granted.isEmpty() ? Grants.NONE : new Grants(granted);
    }

    /**
     * The node that pays when the engine, not {@code castSlot}, starts a use of {@code ability}: one that
     * grants it, the lowest equipped slot first, else the first id. Null when nothing grants it.
     */
    public static @Nullable SkillDef payer(SkillBook book, Function<String, SkillDef> defs, SkillNode.Engine engine,
                                           @Nullable ResourceLocation ability) {
        if (ability == null) {
            return null;
        }
        for (int slot = 0; slot < SkillBook.SLOTS; slot++) {
            SkillDef def = binding(book, defs, book.slot(slot), engine, ability);
            if (def != null) {
                return def;
            }
        }
        return book.ranks().keySet().stream().sorted()
                .map(id -> binding(book, defs, id, engine, ability))
                .filter(def -> def != null)
                .findFirst().orElse(null);
    }

    private static @Nullable SkillDef binding(SkillBook book, Function<String, SkillDef> defs, @Nullable String id,
                                              SkillNode.Engine engine, ResourceLocation ability) {
        SkillDef def = id == null ? null : granting(book, defs, id);
        return def != null && def.cast().engine() == engine && def.cast().ability().equals(ability) ? def : null;
    }

    /** The owned castable {@code id} if it grants its ability right now; an ultimate only from an open slot 8. */
    private static @Nullable SkillDef granting(SkillBook book, Function<String, SkillDef> defs, String id) {
        SkillDef def = defs.apply(id);
        if (def == null || book.rank(id) < 1) {
            return null;
        }
        if (def.kind() == SkillBook.Kind.ULTIMATE
                && !(book.ultimateOpen() && id.equals(book.slot(SkillBook.ULTIMATE_SLOT)))) {
            return null;
        }
        return def;
    }

    /** Whether to refuse a use. Only a server player is judged; a lookup that throws refuses. */
    public static boolean refuses(boolean serverPlayer, Supplier<Grants> grants, SkillNode.Engine engine,
                                  @Nullable ResourceLocation ability) {
        if (!serverPlayer) {
            return false;
        }
        return !lookup(grants).allows(engine, ability);
    }

    /** What a server player's offer may keep; a lookup that throws keeps nothing. */
    public static Predicate<ResourceLocation> keeps(Supplier<Grants> grants, SkillNode.Engine engine) {
        Grants found = lookup(grants);
        return ability -> found.allows(engine, ability);
    }

    /** The grants, or none if the lookup throws or answers null. */
    public static Grants lookup(Supplier<Grants> grants) {
        try {
            Grants found = grants.get();
            return found != null ? found : Grants.NONE;
        } catch (RuntimeException failure) {
            if (!warned) {
                warned = true;
                LOGGER.error("Ability grants lookup failed; refusing every third-party ability", failure);
            }
            return Grants.NONE;
        }
    }
}
