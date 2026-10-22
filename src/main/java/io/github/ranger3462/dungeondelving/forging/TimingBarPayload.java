package io.github.ranger3462.dungeondelving.forging;

import io.github.ranger3462.dungeondelving.DungeonDelving;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Tells the client to show (or, with {@code needed == 0}, hide) a timing bar.
 *
 * @param startTick the server game time the marker started sweeping
 * @param low the sweet spot's start, from 0 to 1
 * @param high the sweet spot's end
 * @param done strikes made so far
 * @param needed strikes needed in all
 */
public record TimingBarPayload(int kind, long startTick, int period, float low, float high, int done, int needed) implements CustomPacketPayload {
    public static final Type<TimingBarPayload> TYPE = new Type<>(DungeonDelving.id("timing_bar"));
    public static final StreamCodec<ByteBuf, TimingBarPayload> STREAM_CODEC = StreamCodec.of((buffer, payload) -> {
        ByteBufCodecs.VAR_INT.encode(buffer, payload.kind());
        ByteBufCodecs.VAR_LONG.encode(buffer, payload.startTick());
        ByteBufCodecs.VAR_INT.encode(buffer, payload.period());
        ByteBufCodecs.FLOAT.encode(buffer, payload.low());
        ByteBufCodecs.FLOAT.encode(buffer, payload.high());
        ByteBufCodecs.VAR_INT.encode(buffer, payload.done());
        ByteBufCodecs.VAR_INT.encode(buffer, payload.needed());
    }, buffer -> new TimingBarPayload(ByteBufCodecs.VAR_INT.decode(buffer), ByteBufCodecs.VAR_LONG.decode(buffer), ByteBufCodecs.VAR_INT.decode(buffer),
            ByteBufCodecs.FLOAT.decode(buffer), ByteBufCodecs.FLOAT.decode(buffer), ByteBufCodecs.VAR_INT.decode(buffer),
            ByteBufCodecs.VAR_INT.decode(buffer)));

    public static final TimingBarPayload HIDE = new TimingBarPayload(0, 0, 1, 0, 0, 0, 0);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
