package io.github.ranger3462.dungeondelving.character;

import io.github.ranger3462.dungeondelving.DungeonDelving;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** A mob's level ({@link MobLevels}), sent to a client when the mob comes into view, for its nameplate. */
public record MobLevelPayload(int entityId, int level) implements CustomPacketPayload {
    public static final Type<MobLevelPayload> TYPE = new Type<>(DungeonDelving.id("mob_level"));
    public static final StreamCodec<ByteBuf, MobLevelPayload> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, MobLevelPayload::entityId,
            ByteBufCodecs.VAR_INT, MobLevelPayload::level, MobLevelPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
