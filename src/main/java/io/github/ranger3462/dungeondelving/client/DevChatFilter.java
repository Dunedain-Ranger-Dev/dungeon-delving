package io.github.ranger3462.dungeondelving.client;

import io.github.ranger3462.dungeondelving.DungeonDelving;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent;

/** Hides FTB Library's "Hello from FTB Library!", which it only sends in dev runs, on every world join. */
@EventBusSubscriber(modid = DungeonDelving.MODID, value = Dist.CLIENT)
public final class DevChatFilter {
    private DevChatFilter() {}

    @SubscribeEvent
    public static void onChat(ClientChatReceivedEvent.System event) {
        if (!FMLEnvironment.production && event.getMessage().getString().equals("Hello from FTB Library!")) {
            event.setCanceled(true);
        }
    }
}
