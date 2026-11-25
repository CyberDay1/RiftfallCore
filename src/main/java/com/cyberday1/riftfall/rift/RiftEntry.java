package com.cyberday1.riftfall.rift;

import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Where a player was standing at the moment they stepped into a rift, and which way they were
 * facing.
 *
 * <p>
 * Taken from the player rather than derived from the frame afterwards. The frame is a fine landmark
 * but it is not a safe place to look one up from: by the time anyone needs this the frame has usually
 * been broken, and a party lights frames in mineshafts and on cliff edges as readily as on flat
 * ground. A spot the player was standing on is known to hold a player, whatever is around it.
 *
 * <p>
 * "Doorstep" is generous about where that spot is. This is read from inside
 * {@code getPortalDestination}, so it is the block the player is standing in, which is a block of the
 * portal itself &mdash; they have been stood in it for the whole transition time by then. Coming back
 * to it therefore means landing inside the frame, which is harmless and stays harmless:
 * {@code Entity#setAsInsidePortal} will not start a teleport while a portal cooldown is running and
 * refreshes that cooldown every tick the player is still in the pane, so somebody who bails out into
 * a frame their party is still using is not sent anywhere. They walk out of it.
 *
 * <p>
 * This is also the hook the rest of the entry data will hang from. A run keeps one of these per
 * entrant and {@link RiftReturns} keeps one per player currently inside a rift, so the inventory
 * snapshot that a later slice wants captured on the way in has a record already being written at
 * exactly the right moment.
 *
 * @param origin the level the player left, which is not necessarily the overworld: any level can
 *               hold a copper frame
 */
public record RiftEntry(ResourceKey<Level> origin, Vec3 doorstep, float yaw) {

    private static final String ORIGIN = "origin";
    private static final String X = "x";
    private static final String Y = "y";
    private static final String Z = "z";
    private static final String YAW = "yaw";

    public static RiftEntry of(ServerPlayer player) {
        return new RiftEntry(player.level().dimension(), player.position(), player.getYRot());
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString(ORIGIN, origin.location().toString());
        tag.putDouble(X, doorstep.x());
        tag.putDouble(Y, doorstep.y());
        tag.putDouble(Z, doorstep.z());
        tag.putFloat(YAW, yaw);
        return tag;
    }

    /**
     * Reads one back, or null if the level it names is not a legal id any more.
     *
     * <p>
     * Null rather than a fallback to the overworld: the point of the record is to put somebody back
     * where they came from, and an entry that has lost track of where that was has nothing to say.
     * The caller drops it and lets the player stay where the game put them, which is at least a
     * place the game chose.
     */
    @Nullable
    public static RiftEntry load(CompoundTag tag) {
        ResourceLocation origin = ResourceLocation.tryParse(tag.getString(ORIGIN));
        if (origin == null) {
            return null;
        }
        return new RiftEntry(ResourceKey.create(Registries.DIMENSION, origin),
                new Vec3(tag.getDouble(X), tag.getDouble(Y), tag.getDouble(Z)),
                tag.getFloat(YAW));
    }
}
