package io.github.ranger3462.dungeondelving.character;

import io.github.ranger3462.dungeondelving.DungeonDelving;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Sent from the stats screen: put one ability point into this score. The server checks the player has one to spend. */
public record AllocateScorePayload(String score) implements CustomPacketPayload {
    public static final Type<AllocateScorePayload> TYPE = new Type<>(DungeonDelving.id("allocate_score"));
    public static final StreamCodec<ByteBuf, AllocateScorePayload> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.STRING_UTF8,
            AllocateScorePayload::score, AllocateScorePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    static void handle(AllocateScorePayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            AbilityScore.byId(payload.score()).ifPresent(score -> Characters.allocate(player, score));
        }
    }
}
