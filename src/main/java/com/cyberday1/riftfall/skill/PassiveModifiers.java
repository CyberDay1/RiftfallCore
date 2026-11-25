package com.cyberday1.riftfall.skill;

import com.cyberday1.riftfall.RiftfallCore;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeMap;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Puts a book's passives on a player as transient modifiers, one per owned passive, and puts them
 * back whenever the map is new: login, respawn, a spend, a reload. Removal is by id, never by
 * attribute, because the channels also carry gear and stats. A rank above the ceiling applies at it.
 */
public final class PassiveModifiers {

    /** K-4, taken provisionally: passives' own prefix, outside the {@code riftfall:stat/} budget. */
    public static final String PREFIX = "passive/";

    private record Applied(Holder<Attribute> attribute, ResourceLocation id) {}

    // Values contain no reference back to the attribute map, so discarded player maps can be collected.
    private static final Map<AttributeMap, List<Applied>> APPLIED = new WeakHashMap<>();

    private PassiveModifiers() {
    }

    public static ResourceLocation id(PassiveDef def) {
        return RiftfallCore.id(PREFIX + def.id());
    }

    /** Writes every owned passive the table knows, returning how many were written. */
    public static int apply(AttributeMap attributes, SkillBook book, SkillTable table) {
        List<Applied> previous = APPLIED.remove(attributes);
        if (previous != null) {
            for (Applied applied : previous) {
                AttributeInstance instance = attributes.getInstance(applied.attribute());
                if (instance != null) {
                    instance.removeModifier(applied.id());
                }
            }
        }
        List<Applied> applied = new ArrayList<>();
        APPLIED.put(attributes, applied);
        int written = 0;
        for (PassiveDef def : table.passives().values()) {
            AttributeInstance instance = instance(attributes, def);
            if (instance == null) {
                continue;
            }
            instance.removeModifier(id(def));
            SkillBook.Owned owned = book.ranks().get(def.id());
            if (owned == null || owned.kind() != SkillBook.Kind.PASSIVE) {
                continue;
            }
            int rank = Math.min(owned.rank(), def.node().maxRank());
            instance.addTransientModifier(new AttributeModifier(id(def), def.amountAt(rank),
                    AttributeModifier.Operation.ADD_VALUE));
            applied.add(new Applied(instance.getAttribute(), id(def)));
            written++;
        }
        return written;
    }

    public static int reapply(ServerPlayer player) {
        return apply(player.getAttributes(), SkillService.bookOf(player), SkillDefs.table());
    }

    /** Null for an attribute no mod registered, or one this entity does not carry. */
    static @Nullable AttributeInstance instance(AttributeMap attributes, PassiveDef def) {
        if (def.attribute() == null) {
            return null;
        }
        Holder<Attribute> attribute = BuiltInRegistries.ATTRIBUTE.getHolder(def.attribute())
                .<Holder<Attribute>>map(reference -> reference).orElse(null);
        return attribute == null ? null : attributes.getInstance(attribute);
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(PassiveModifiers::onLoggedIn);
        NeoForge.EVENT_BUS.addListener(PassiveModifiers::onRespawn);
        NeoForge.EVENT_BUS.addListener(PassiveModifiers::onDatapackSync);
    }

    private static void onLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            reapply(player);
        }
    }

    private static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            reapply(player);
        }
    }

    private static void onDatapackSync(OnDatapackSyncEvent event) {
        event.getRelevantPlayers().forEach(PassiveModifiers::reapply);
    }
}
