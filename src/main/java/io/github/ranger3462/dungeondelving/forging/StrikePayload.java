package io.github.ranger3462.dungeondelving.forging;

import io.github.ranger3462.dungeondelving.DungeonDelving;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * A strike in a minigame, sent by the client on a fresh right-click (holding the button doesn't repeat it), or for
 * scraping, when the scraper passes a patch without one. It says which bar it's for, which strike it is ({@code index},
 * so a scraping pass can't count a patch twice), and how long (in ticks, on the client's own clock) the bar had been
 * running. Timing it on the client keeps lag out of it: what the player saw is what gets judged. The server checks the
 * timing is believable for the player's ping ({@link ForgeActions#checkedElapsed}).
 */
public record StrikePayload(int bar, int index, float elapsed) implements CustomPacketPayload {
    public static final Type<StrikePayload> TYPE = new Type<>(DungeonDelving.id("strike"));
    public static final StreamCodec<ByteBuf, StrikePayload> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, StrikePayload::bar,
            ByteBufCodecs.VAR_INT, StrikePayload::index, ByteBufCodecs.FLOAT, StrikePayload::elapsed, StrikePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    static void handle(StrikePayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            ForgeActions.onStrike(player, payload.bar(), payload.index(), payload.elapsed());
        }
    }
}
