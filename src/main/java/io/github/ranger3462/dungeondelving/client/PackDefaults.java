package io.github.ranger3462.dungeondelving.client;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import io.github.ranger3462.dungeondelving.DungeonDelving;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.loading.FMLPaths;

/**
 * Video settings the pack starts with, set once on a player's first launch (a marker in the config folder remembers it),
 * so they can still change them after: VSync off.
 */
@EventBusSubscriber(modid = DungeonDelving.MODID, value = Dist.CLIENT)
public final class PackDefaults {
    private static final String MARKER = DungeonDelving.MODID + "-defaults-applied.txt";

    private PackDefaults() {}

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        Path marker = FMLPaths.CONFIGDIR.get().resolve(MARKER);
        if (Files.exists(marker)) {
            return;
        }
        event.enqueueWork(() -> {
            Minecraft minecraft = Minecraft.getInstance();
            minecraft.options.enableVsync().set(false);
            minecraft.options.save();
            try {
                Files.writeString(marker, "Dungeon Delving set its default video settings (VSync off) on first launch. Delete this file to set them again.\n");
            } catch (IOException e) {
                DungeonDelving.LOGGER.warn("Couldn't save {}: defaults will be set again next launch", marker, e);
            }
        });
    }
}
