package io.github.ranger3462.dungeondelving.character;

import java.util.HashMap;
import java.util.Map;

import io.github.ranger3462.dungeondelving.DungeonDelving;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** The player's character level, unspent ability points, points per score and race ("" if none yet), sent to their client. */
public record CharacterSyncPayload(int level, int available, Map<String, Integer> allocated, String race) implements CustomPacketPayload {
    public static final Type<CharacterSyncPayload> TYPE = new Type<>(DungeonDelving.id("character_sync"));
    public static final StreamCodec<ByteBuf, CharacterSyncPayload> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, CharacterSyncPayload::level,
            ByteBufCodecs.VAR_INT, CharacterSyncPayload::available,
            ByteBufCodecs.map(HashMap::new, ByteBufCodecs.STRING_UTF8, ByteBufCodecs.VAR_INT), CharacterSyncPayload::allocated,
            ByteBufCodecs.STRING_UTF8, CharacterSyncPayload::race, CharacterSyncPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
