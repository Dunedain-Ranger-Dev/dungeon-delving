package io.github.ranger3462.dungeondelving.character;

import io.github.ranger3462.dungeondelving.DungeonDelving;
import io.github.ranger3462.dungeondelving.registry.ModRegistries;
import io.netty.buffer.ByteBuf;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** The class screen's packets (DD-83). */
public final class ClassPayloads {
    private ClassPayloads() {}

    /** Server to client: open the class screen, saying the player's current class ("" if none). */
    public record Open(String current) implements CustomPacketPayload {
        public static final Type<Open> TYPE = new Type<>(DungeonDelving.id("open_class_screen"));
        public static final StreamCodec<ByteBuf, Open> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.STRING_UTF8, Open::current, Open::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Client to server: the player chose this class. Takes a Tome of Calling from their inventory. */
    public record Choose(String playerClass) implements CustomPacketPayload {
        public static final Type<Choose> TYPE = new Type<>(DungeonDelving.id("choose_class"));
        public static final StreamCodec<ByteBuf, Choose> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.STRING_UTF8, Choose::playerClass, Choose::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        static void handle(Choose payload, IPayloadContext context) {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            PlayerClass.byId(payload.playerClass()).filter(chosen -> Characters.data(player).classOf().orElse(null) != chosen).ifPresent(chosen -> {
                if (takeTome(player)) {
                    Characters.setClass(player, chosen);
                    player.sendSystemMessage(Component.translatable("message.dungeondelving.class_chosen", chosen.displayName()).withStyle(ChatFormatting.GOLD));
                }
            });
        }

        /** Uses up one Tome of Calling from the player's inventory (creative players keep theirs). False if they have none. */
        private static boolean takeTome(ServerPlayer player) {
            var inventory = player.getInventory();
            for (int i = 0; i < inventory.getContainerSize(); i++) {
                ItemStack stack = inventory.getItem(i);
                if (stack.is(ModRegistries.TOME_OF_CALLING.get())) {
                    stack.consume(1, player);
                    return true;
                }
            }
            return false;
        }
    }
}
