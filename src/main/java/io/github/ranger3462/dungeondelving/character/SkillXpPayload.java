package io.github.ranger3462.dungeondelving.character;

import io.github.ranger3462.dungeondelving.DungeonDelving;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** A skill just gained experience: where it now stands, for the XP popup by the hotbar. */
public record SkillXpPayload(String skill, int level, int current, int required, int gained) implements CustomPacketPayload {
    public static final Type<SkillXpPayload> TYPE = new Type<>(DungeonDelving.id("skill_xp"));
    public static final StreamCodec<ByteBuf, SkillXpPayload> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.STRING_UTF8, SkillXpPayload::skill,
            ByteBufCodecs.VAR_INT, SkillXpPayload::level, ByteBufCodecs.VAR_INT, SkillXpPayload::current, ByteBufCodecs.VAR_INT, SkillXpPayload::required,
            ByteBufCodecs.VAR_INT, SkillXpPayload::gained, SkillXpPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
