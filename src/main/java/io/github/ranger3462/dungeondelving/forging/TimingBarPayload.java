package io.github.ranger3462.dungeondelving.forging;

import java.util.List;

import io.github.ranger3462.dungeondelving.DungeonDelving;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Tells the client to show (or, with {@code needed == 0}, hide) a minigame ({@link TimingBar}). The client starts it the
 * moment a new {@code bar} arrives, on its own clock, so lag doesn't shift it (see {@link StrikePayload}).
 *
 * @param bar which bar this is: smithing and weaving get a new one each strike; scraping keeps one for its whole pass
 * @param values the minigame's layout: the sweet spot {@code [low, high]} for smithing; {@code [thread period, phase,
 *        gap]} for weaving; each patch's {@code [low, high]}, in order, for scraping
 * @param done strikes made so far
 * @param needed strikes needed in all
 */
public record TimingBarPayload(int kind, int bar, int period, List<Float> values, int done, int needed) implements CustomPacketPayload {
    public static final Type<TimingBarPayload> TYPE = new Type<>(DungeonDelving.id("timing_bar"));
    public static final StreamCodec<ByteBuf, TimingBarPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, TimingBarPayload::kind,
            ByteBufCodecs.VAR_INT, TimingBarPayload::bar,
            ByteBufCodecs.VAR_INT, TimingBarPayload::period,
            ByteBufCodecs.FLOAT.apply(ByteBufCodecs.list()), TimingBarPayload::values,
            ByteBufCodecs.VAR_INT, TimingBarPayload::done,
            ByteBufCodecs.VAR_INT, TimingBarPayload::needed,
            TimingBarPayload::new);

    public static final TimingBarPayload HIDE = new TimingBarPayload(0, 0, 1, List.of(), 0, 0);

    public TimingBar.Kind barKind() {
        return TimingBar.Kind.values()[Math.min(kind, TimingBar.Kind.values().length - 1)];
    }

    public float value(int index) {
        return index < values.size() ? values.get(index) : 0.0f;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
