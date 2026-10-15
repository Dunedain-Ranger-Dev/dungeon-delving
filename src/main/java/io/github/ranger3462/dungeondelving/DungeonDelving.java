package io.github.ranger3462.dungeondelving;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

// The value here should match the modId entry in META-INF/neoforge.mods.toml
@Mod(DungeonDelving.MODID)
public class DungeonDelving {
    public static final String MODID = "dungeondelving";
    public static final Logger LOGGER = LogUtils.getLogger();

    // The first code that runs when the mod loads. NeoForge passes in the mod event bus and container.
    public DungeonDelving(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(this::commonSetup);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("Dungeon Delving loaded");
    }

    /** Builds a resource location in this mod's namespace, e.g. {@code dungeondelving:dungeon/start}. */
    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }
}
