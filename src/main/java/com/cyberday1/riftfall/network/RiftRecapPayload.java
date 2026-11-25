package com.cyberday1.riftfall.network;

import com.cyberday1.riftfall.RiftfallCore;
import com.cyberday1.riftfall.rift.RiftRecap;
import com.cyberday1.riftfall.rift.RiftRecapLog;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.List;

/**
 * A player's run recaps, newest first. {@code fresh} says the first one has just been earned and
 * should be shown; otherwise the client opens the history list.
 */
public record RiftRecapPayload(List<RiftRecap> runs, boolean fresh) implements CustomPacketPayload {

    public static final Type<RiftRecapPayload> TYPE = new Type<>(RiftfallCore.id("rift_recap"));
    public static final RiftRecapPayload EMPTY = new RiftRecapPayload(List.of(), false);

    public RiftRecapPayload {
        runs = List.copyOf(runs);
    }

    public static final StreamCodec<ByteBuf, RiftRecapPayload> STREAM_CODEC =
            StreamCodec.of(RiftRecapPayload::write, RiftRecapPayload::read);

    private static void write(ByteBuf buf, RiftRecapPayload payload) {
        ByteBufCodecs.VAR_INT.encode(buf, payload.runs.size());
        payload.runs.forEach(recap -> RiftRecap.STREAM_CODEC.encode(buf, recap));
        buf.writeBoolean(payload.fresh);
    }

    private static RiftRecapPayload read(ByteBuf buf) {
        int sent = ByteBufCodecs.VAR_INT.decode(buf);
        List<RiftRecap> runs = new ArrayList<>();
        for (int index = 0; index < sent; index++) {
            RiftRecap recap = RiftRecap.STREAM_CODEC.decode(buf);
            if (index < RiftRecapLog.HISTORY_CAP) {
                runs.add(recap);
            }
        }
        return new RiftRecapPayload(runs, buf.readBoolean());
    }

    @Override
    public Type<RiftRecapPayload> type() {
        return TYPE;
    }
}
