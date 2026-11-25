package com.cyberday1.riftfall.network;

import com.cyberday1.riftfall.RiftfallCore;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * An empty hand used on the World Core's orb with nothing solid behind it, which sends no vanilla packet. It carries
 * only the client's aim, as vanilla's use packet does; the server finds the Core itself (WorldCoreAim#touched).
 */
public record CoreTouchPayload(float yRot, float xRot) implements CustomPacketPayload {

    public static final Type<CoreTouchPayload> TYPE = new Type<>(RiftfallCore.id("core_touch"));

    public static final StreamCodec<ByteBuf, CoreTouchPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.FLOAT, CoreTouchPayload::yRot,
            ByteBufCodecs.FLOAT, CoreTouchPayload::xRot,
            CoreTouchPayload::new);

    @Override
    public Type<CoreTouchPayload> type() {
        return TYPE;
    }
}
