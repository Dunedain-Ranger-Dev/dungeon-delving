package io.github.ranger3462.dungeondelving.client;

import io.github.ranger3462.dungeondelving.DungeonDelving;
import io.github.ranger3462.dungeondelving.forging.Crafts;
import io.github.ranger3462.dungeondelving.forging.CrucibleMenu;
import io.github.ranger3462.dungeondelving.forging.StarredItem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/** A crucible's screen, drawn as a furnace: the flame is what's left of the salt, the arrow the ingot's progress (DD-91). */
public class CrucibleScreen extends AbstractContainerScreen<CrucibleMenu> {
    private static final ResourceLocation BACKGROUND = ResourceLocation.withDefaultNamespace("textures/gui/container/furnace.png");
    private static final ResourceLocation FLAME = ResourceLocation.withDefaultNamespace("container/furnace/lit_progress");
    private static final ResourceLocation ARROW = ResourceLocation.withDefaultNamespace("container/furnace/burn_progress");

    public CrucibleScreen(CrucibleMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int left = leftPos;
        int top = topPos;
        graphics.blit(BACKGROUND, left, top, 0, 0, imageWidth, imageHeight);
        if (menu.saltLeft() > 0) {
            int flame = Mth.ceil(menu.saltLeft() * 13.0f) + 1;
            graphics.blitSprite(FLAME, 14, 14, 0, 14 - flame, left + 56, top + 36 + 14 - flame, 14, flame);
        }
        int arrow = Mth.ceil(menu.progress() * 24.0f);
        graphics.blitSprite(ARROW, 24, 16, 0, 0, left + 79, top + 34, arrow, 16);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        super.renderLabels(graphics, mouseX, mouseY);
        if (menu.saltStars() > 0) {
            // The salt's ★ under the flame: what the gold comes out at.
            graphics.drawString(font, StarredItem.starsText(menu.saltStars()), 76, 56, 0xFFFFFF, true);
        }
    }

    @EventBusSubscriber(modid = DungeonDelving.MODID, value = Dist.CLIENT)
    public static final class Registration {
        private Registration() {}

        @SubscribeEvent
        public static void onRegisterScreens(RegisterMenuScreensEvent event) {
            event.register(Crafts.CRUCIBLE_MENU.get(), CrucibleScreen::new);
        }
    }
}
