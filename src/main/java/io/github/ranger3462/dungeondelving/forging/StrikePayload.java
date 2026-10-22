package io.github.ranger3462.dungeondelving.forging;

import io.github.ranger3462.dungeondelving.DungeonDelving;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * A strike on the timing bar, sent by the client on a fresh right-click (holding the button doesn't repeat it). It says
 * which bar it's for, and how long (in ticks, on the client's own clock) that bar had been sweeping when the player
 * clicked. Timing the bar on the client keeps lag out of it: what the player saw is what gets judged. The server checks
 * the timing is believable for the player's ping ({@link ForgeActions#checkedElapsed}).
 */
public record StrikePayload(int bar, float elapsed) implements CustomPacketPayload {
    public static final Type<StrikePayload> TYPE = new Type<>(DungeonDelving.id("strike"));
    public static final StreamCodec<ByteBuf, StrikePayload> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, StrikePayload::bar,
            ByteBufCodecs.FLOAT, StrikePayload::elapsed, StrikePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    static void handle(StrikePayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            ForgeActions.onStrike(player, payload.bar(), payload.elapsed());
        }
    }
}
