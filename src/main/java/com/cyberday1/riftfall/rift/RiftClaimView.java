package com.cyberday1.riftfall.rift;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * What the recap screen says about held gear: what came back, how many stacks are still held, and
 * whether the last try found no room. Built from {@link RiftClaims.HandOver}s on the server.
 */
public record RiftClaimView(List<ItemStack> back, int left, boolean noRoom) {

    /** The wire carries at most this many stacks; the screen names only the first few anyway. */
    static final int WIRE_CAP = 64;

    public static final RiftClaimView NONE = new RiftClaimView(List.of(), 0, false);

    public RiftClaimView {
        back = List.copyOf(back);
    }

    public static RiftClaimView of(RiftClaims.HandOver handed) {
        if (handed.status() == RiftClaims.Status.NOTHING_HELD) {
            return NONE;
        }
        return new RiftClaimView(handed.back(), handed.left(), handed.back().isEmpty() && handed.left() > 0);
    }

    /** Whether the screen has anything to say. */
    public boolean shown() {
        return !back.isEmpty() || left > 0;
    }

    /** A later try added to this one: what came back adds up, what is left is the newer count. */
    public RiftClaimView then(RiftClaimView next) {
        List<ItemStack> all = new ArrayList<>(back);
        all.addAll(next.back);
        return new RiftClaimView(all, next.left, next.noRoom);
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, RiftClaimView> STREAM_CODEC =
            StreamCodec.of(RiftClaimView::write, RiftClaimView::read);

    private static void write(RegistryFriendlyByteBuf buf, RiftClaimView view) {
        List<ItemStack> sent = view.back.size() > WIRE_CAP ? view.back.subList(0, WIRE_CAP) : view.back;
        ItemStack.OPTIONAL_LIST_STREAM_CODEC.encode(buf, sent);
        ByteBufCodecs.VAR_INT.encode(buf, view.left);
        buf.writeBoolean(view.noRoom);
    }

    private static RiftClaimView read(RegistryFriendlyByteBuf buf) {
        List<ItemStack> back = ItemStack.OPTIONAL_LIST_STREAM_CODEC.decode(buf).stream()
                .filter(stack -> !stack.isEmpty()).limit(WIRE_CAP).toList();
        return new RiftClaimView(back, ByteBufCodecs.VAR_INT.decode(buf), buf.readBoolean());
    }
}
