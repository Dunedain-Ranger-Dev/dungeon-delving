package io.github.ranger3462.dungeondelving.client;

import io.github.ranger3462.dungeondelving.DungeonDelving;
import io.github.ranger3462.dungeondelving.character.MobLevelPayload;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Mob;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderNameTagEvent;
import net.neoforged.neoforge.common.util.TriState;

/**
 * Mob nameplates showing each mob's level ({@link io.github.ranger3462.dungeondelving.character.MobLevels}): "Lv 12
 * Zombie", coloured by how it compares to the player's character level (grey well below, green below, white about
 * even, gold above, red well above). Shown for mobs within {@link #RANGE} blocks that the player can see.
 */
@EventBusSubscriber(modid = DungeonDelving.MODID, value = Dist.CLIENT)
public final class MobNameplates {
    private static final double RANGE = 16.0;
    private static final Int2IntOpenHashMap LEVELS = new Int2IntOpenHashMap();

    private MobNameplates() {}

    public static void receive(MobLevelPayload payload) {
        LEVELS.put(payload.entityId(), payload.level());
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        LEVELS.clear();
    }

    @SubscribeEvent
    public static void onNameTag(RenderNameTagEvent event) {
        if (!(event.getEntity() instanceof Mob mob) || !LEVELS.containsKey(mob.getId())) {
            return;
        }
        var player = Minecraft.getInstance().player;
        if (player == null || player.distanceToSqr(mob) > RANGE * RANGE || !player.hasLineOfSight(mob)) {
            return;
        }
        int level = LEVELS.get(mob.getId());
        int gap = level - CharacterScreen.characterLevel();
        ChatFormatting color = gap <= -10 ? ChatFormatting.GRAY : gap <= -3 ? ChatFormatting.GREEN : gap < 3 ? ChatFormatting.WHITE
                : gap < 10 ? ChatFormatting.GOLD : ChatFormatting.RED;
        event.setContent(Component.literal("Lv " + level + " ").withStyle(color).append(event.getContent()));
        event.setCanRender(TriState.TRUE);
    }
}
