package com.cyberday1.riftfall.worldgen.space;

import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Consumer;

/**
 * A room occupying an axis-aligned box of chunk sections.
 *
 * <p>
 * <strong>Every coordinate on this record is in chunk sections, not blocks.</strong> A
 * {@code size} of {@code (3,3,3)} is a 48&times;48&times;48 block room. The layout never sees a
 * block coordinate; converting back happens at placement time, in a later pass.
 *
 * <p>
 * The record is immutable so the layout can mint one canonical room per orientation and then
 * {@link #at(Vec3i)} it into as many candidate positions as it likes without copying socket lists.
 *
 * @param size      extent in sections, strictly positive on each axis
 * @param origin    the minimum corner, in sections
 * @param sockets   connection points, positions section-local to this room
 * @param template  the authored {@code .nbt} this came from; null for synthetic test rooms
 * @param transform which of the eight orientations {@code sockets} and {@code size} already reflect
 */
public record RiftSpace(Vec3i size, Vec3i origin, List<RoomSocket> sockets,
                        @Nullable ResourceLocation template, TripleMirror transform) {

    public RiftSpace {
        if (size.getX() <= 0 || size.getY() <= 0 || size.getZ() <= 0) {
            throw new IllegalArgumentException("Room size must be positive on every axis, got " + size);
        }
        sockets = List.copyOf(sockets);
        for (RoomSocket socket : sockets) {
            Vec3i p = socket.position();
            if (p.getX() < 0 || p.getY() < 0 || p.getZ() < 0
                    || p.getX() >= size.getX() || p.getY() >= size.getY() || p.getZ() >= size.getZ()) {
                throw new IllegalArgumentException("Socket " + socket + " lies outside a room of size " + size);
            }
        }
    }

    public static RiftSpace of(Vec3i size, List<RoomSocket> sockets) {
        return new RiftSpace(size, Vec3i.ZERO, sockets, null, TripleMirror.IDENTITY);
    }

    /** The same room moved so its minimum corner sits at {@code newOrigin}. */
    public RiftSpace at(Vec3i newOrigin) {
        return newOrigin.equals(origin) ? this : new RiftSpace(size, newOrigin, sockets, template, transform);
    }

    public RiftSpace offset(Vec3i delta) {
        return at(origin.offset(delta));
    }

    public boolean containsSection(Vec3i section) {
        int dx = section.getX() - origin.getX();
        int dy = section.getY() - origin.getY();
        int dz = section.getZ() - origin.getZ();
        return dx >= 0 && dy >= 0 && dz >= 0
                && dx < size.getX() && dy < size.getY() && dz < size.getZ();
    }

    public void forEachSection(Consumer<Vec3i> action) {
        for (int x = 0; x < size.getX(); x++) {
            for (int y = 0; y < size.getY(); y++) {
                for (int z = 0; z < size.getZ(); z++) {
                    action.accept(new Vec3i(origin.getX() + x, origin.getY() + y, origin.getZ() + z));
                }
            }
        }
    }

    public Vec3i socketSection(RoomSocket socket) {
        return socket.section(origin);
    }

    public Vec3i connectingSection(RoomSocket socket) {
        return socket.connectingSection(origin);
    }
}
