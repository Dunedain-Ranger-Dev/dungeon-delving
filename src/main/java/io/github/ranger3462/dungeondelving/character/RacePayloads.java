package io.github.ranger3462.dungeondelving.character;

import io.github.ranger3462.dungeondelving.DungeonDelving;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** The race screen's and racial ability's packets (DD-82). */
public final class RacePayloads {
    private RacePayloads() {}

    /** Server to client: open the race screen (on first join, or after an Elixir of Rebirth). */
    public record Open() implements CustomPacketPayload {
        public static final Type<Open> TYPE = new Type<>(DungeonDelving.id("open_race_screen"));
        public static final StreamCodec<ByteBuf, Open> STREAM_CODEC = StreamCodec.unit(new Open());

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Client to server: the player picked this race. Only accepted while they have none. */
    public record Choose(String race) implements CustomPacketPayload {
        public static final Type<Choose> TYPE = new Type<>(DungeonDelving.id("choose_race"));
        public static final StreamCodec<ByteBuf, Choose> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.STRING_UTF8, Choose::race, Choose::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        static void handle(Choose payload, IPayloadContext context) {
            if (context.player() instanceof ServerPlayer player && Characters.data(player).raceOf().isEmpty()) {
                Race.byId(payload.race()).ifPresent(race -> {
                    Characters.setRace(player, race);
                    CharacterItems.giveStarterTome(player);
                });
            }
        }
    }

    /** Client to server: the racial ability key was pressed. */
    public record UseAbility() implements CustomPacketPayload {
        public static final Type<UseAbility> TYPE = new Type<>(DungeonDelving.id("racial_ability"));
        public static final StreamCodec<ByteBuf, UseAbility> STREAM_CODEC = StreamCodec.unit(new UseAbility());

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        static void handle(UseAbility payload, IPayloadContext context) {
            if (context.player() instanceof ServerPlayer player) {
                RaceTraits.useAbility(player);
            }
        }
    }
}
