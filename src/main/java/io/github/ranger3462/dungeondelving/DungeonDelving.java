package io.github.ranger3462.dungeondelving;

import net.neoforged.fml.event.config.ModConfigEvent;
import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import io.github.ranger3462.dungeondelving.dungeon.CuriosLoot;
import io.github.ranger3462.dungeondelving.integration.apotheosis.ApotheosisDungeons;
import io.github.ranger3462.dungeondelving.integration.professions.ProfessionsLink;
import io.github.ranger3462.dungeondelving.registry.ModRegistries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

// The value here should match the modId entry in META-INF/neoforge.mods.toml
@Mod(DungeonDelving.MODID)
public class DungeonDelving {
    public static final String MODID = "dungeondelving";
    public static final Logger LOGGER = LogUtils.getLogger();

    // The first code that runs when the mod loads. NeoForge passes in the mod event bus and container.
    public DungeonDelving(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(this::commonSetup);
        ModRegistries.register(modEventBus);
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
        modEventBus.addListener(ModConfigEvent.Loading.class, Config::onLoad);
        modEventBus.addListener(ModConfigEvent.Reloading.class, Config::onLoad);
        if (ModList.get().isLoaded("curios")) {
            CuriosLoot.register();
        }
        if (ModList.get().isLoaded("apotheosis")) {
            ApotheosisDungeons.register();
        }
        if (ModList.get().isLoaded("ddprofessions")) {
            ProfessionsLink.register();
        }
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("Dungeon Delving loaded");
    }

    /** Builds a resource location in this mod's namespace, e.g. {@code dungeondelving:dungeon/start}. */
    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }
}
