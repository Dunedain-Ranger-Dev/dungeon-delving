package io.github.ranger3462.dungeondelving.forging;

import io.github.ranger3462.dungeondelving.DungeonDelving;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * A strike on the timing bar, sent by the client on a fresh right-click (holding the button doesn't repeat it), with
 * where the client saw the marker. The server checks that against where the marker could have been
 * ({@link ForgeActions#checkedMarker}).
 */
public record StrikePayload(float marker) implements CustomPacketPayload {
    public static final Type<StrikePayload> TYPE = new Type<>(DungeonDelving.id("strike"));
    public static final StreamCodec<ByteBuf, StrikePayload> STREAM_CODEC = ByteBufCodecs.FLOAT.map(StrikePayload::new, StrikePayload::marker);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    static void handle(StrikePayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            ForgeActions.onStrike(player, payload.marker());
        }
    }
}
