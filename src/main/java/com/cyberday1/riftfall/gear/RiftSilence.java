package com.cyberday1.riftfall.gear;

import com.cyberday1.riftfall.RiftfallCore;
import com.cyberday1.riftfall.init.RiftfallTags;
import com.cyberday1.riftfall.rift.RiftLevels;
import com.cyberday1.riftfall.worldgen.level.RiftDimensions;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.concurrent.atomic.AtomicLong;

/**
 * An item in {@code riftfall:rift_silenced} does nothing inside a rift: no use, no tick, no ability. Outside it works,
 * except that the Magic Mirror may not carry anyone into a rift. The stack mixin and the Relics hooks ask this.
 */
public final class RiftSilence {

    public static final String SILENCED = "message." + RiftfallCore.MODID + ".relic_rift_silenced";
    /** RiftTeleports' own line, so a refused Mirror reads the same as a refused crossing. */
    public static final String BOUNDARY = "message." + RiftfallCore.MODID + ".teleport_denied_boundary";

    /** Teleports to the holder's respawn point, which can be in another dimension. */
    static final ResourceLocation MAGIC_MIRROR = ResourceLocation.fromNamespaceAndPath("relics", "magic_mirror");

    private static final AtomicLong USES_REFUSED = new AtomicLong();
    private static final AtomicLong TICKS_REFUSED = new AtomicLong();
    private static final AtomicLong ABILITIES_REFUSED = new AtomicLong();
    private static final AtomicLong ENTITIES_REMOVED = new AtomicLong();
    private static final AtomicLong CROSSINGS_REFUSED = new AtomicLong();

    private RiftSilence() {
    }

    public static boolean silenced(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.is(RiftfallTags.RIFT_SILENCED);
    }

    /**
     * Whether a level plays as a rift. The server asks the generator ({@link RiftLevels#isRiftWorld}); the client
     * cannot see it, so it asks the dimension type every rift is built on. The Blazing Flask moves the player on
     * the client, so the client has to answer too.
     */
    public static boolean inRift(Level level) {
        if (level == null) return false;
        if (level instanceof ServerLevel server) return RiftLevels.isRiftWorld(server);
        return level.dimensionTypeRegistration().is(RiftDimensions.RIFT_TYPE);
    }

    /** A silenced item held in a rift. */
    public static boolean refused(ItemStack stack, Level level) {
        return silenced(stack) && inRift(level);
    }

    /** A silenced item held by an entity standing in a rift. */
    public static boolean refused(ItemStack stack, Entity holder) {
        return holder != null && refused(stack, holder.level());
    }

    /**
     * The Mirror used outside a rift whose respawn point is in one. RiftTeleports would refuse the crossing too,
     * but only after the Mirror had spent its cooldown and an anchor charge, and it does not cover the shipped
     * {@code riftfall:rift} or a datapack rift, which are not instances.
     */
    public static boolean intoRift(ItemStack stack, Player player) {
        if (!(player instanceof ServerPlayer server) || !silenced(stack)) return false;
        if (!MAGIC_MIRROR.equals(stack.getItemHolder().unwrapKey().map(k -> k.location()).orElse(null))) return false;
        ServerLevel home = server.server.getLevel(server.getRespawnDimension());
        return home != null && RiftLevels.isRiftWorld(home);
    }

    /** The action-bar line, server side only so a refusal is said once. */
    public static void tell(Player player, String key) {
        if (player != null && !player.level().isClientSide()) {
            player.displayClientMessage(Component.translatable(key), true);
        }
    }

    public static void refusedUse() {
        USES_REFUSED.incrementAndGet();
    }

    public static void refusedTick() {
        TICKS_REFUSED.incrementAndGet();
    }

    public static void refusedAbility() {
        ABILITIES_REFUSED.incrementAndGet();
    }

    public static void removedEntity() {
        ENTITIES_REMOVED.incrementAndGet();
    }

    public static void refusedCrossing() {
        CROSSINGS_REFUSED.incrementAndGet();
    }

    public static long usesRefused() {
        return USES_REFUSED.get();
    }

    public static long ticksRefused() {
        return TICKS_REFUSED.get();
    }

    public static long abilitiesRefused() {
        return ABILITIES_REFUSED.get();
    }

    public static long entitiesRemoved() {
        return ENTITIES_REMOVED.get();
    }

    public static long crossingsRefused() {
        return CROSSINGS_REFUSED.get();
    }
}
