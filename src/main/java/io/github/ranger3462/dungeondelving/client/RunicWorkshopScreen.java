package io.github.ranger3462.dungeondelving.client;

import io.github.ranger3462.dungeondelving.DungeonDelving;
import io.github.ranger3462.dungeondelving.workshop.RunicWorkshop;
import io.github.ranger3462.dungeondelving.workshop.RunicWorkshopMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.ItemCombinerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/** The Runic Workshop's screen. Uses the smithing table's layout and background as a placeholder. */
public class RunicWorkshopScreen extends ItemCombinerScreen<RunicWorkshopMenu> {
    private static final ResourceLocation BACKGROUND = ResourceLocation.withDefaultNamespace("textures/gui/container/smithing.png");

    public RunicWorkshopScreen(RunicWorkshopMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, BACKGROUND);
        titleLabelX = 44;
        titleLabelY = 15;
    }

    @Override
    protected void renderErrorIcon(GuiGraphics graphics, int x, int y) {}

    @EventBusSubscriber(modid = DungeonDelving.MODID, value = Dist.CLIENT)
    public static final class Registration {
        private Registration() {}

        @SubscribeEvent
        public static void onRegisterScreens(RegisterMenuScreensEvent event) {
            event.register(RunicWorkshop.MENU.get(), RunicWorkshopScreen::new);
        }
    }
}
