package io.github.ranger3462.dungeondelving.client;

import io.github.ranger3462.dungeondelving.DungeonDelving;
import io.github.ranger3462.dungeondelving.forging.TimingBar;
import io.github.ranger3462.dungeondelving.forging.TimingBarPayload;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/**
 * Draws the forging timing bar above the hotbar: the marker sweeping across, the green sweet spot, and a pip for each
 * strike. The server sends the bar's numbers ({@link TimingBarPayload}); the marker is worked out from the game time
 * the same way the server does, so it lines up with what the server checks.
 */
public final class TimingBarOverlay {
    private static final int WIDTH = 160;
    private static final int HEIGHT = 8;
    private static TimingBarPayload bar = TimingBarPayload.HIDE;

    private TimingBarOverlay() {}

    /** Shows or hides the bar. Called on the client when the server sends it. */
    public static void handle(TimingBarPayload payload) {
        bar = payload;
    }

    private static void render(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft minecraft = Minecraft.getInstance();
        if (bar.needed() <= 0 || minecraft.level == null || minecraft.options.hideGui) {
            return;
        }
        int left = (graphics.guiWidth() - WIDTH) / 2;
        int top = graphics.guiHeight() - 72;
        double ticks = minecraft.level.getGameTime() + delta.getGameTimeDeltaPartialTick(false) - bar.startTick();
        float marker = TimingBar.marker(ticks, bar.period());

        graphics.fill(left - 1, top - 1, left + WIDTH + 1, top + HEIGHT + 1, 0xFF000000);
        graphics.fill(left, top, left + WIDTH, top + HEIGHT, 0xFF3A2A20);
        graphics.fill(left + Math.round(bar.low() * WIDTH), top, left + Math.round(bar.high() * WIDTH), top + HEIGHT, 0xFF4CC24C);
        int markerX = left + Math.round(marker * WIDTH);
        graphics.fill(markerX - 1, top - 3, markerX + 1, top + HEIGHT + 3, 0xFFFFFFFF);

        for (int i = 0; i < bar.needed(); i++) {
            int x = left + WIDTH / 2 + (i - bar.needed() / 2) * 8 - 2;
            graphics.fill(x, top + HEIGHT + 4, x + 5, top + HEIGHT + 9, i < bar.done() ? 0xFFFFC040 : 0xFF555555);
        }
        TimingBar.Kind kind = TimingBar.Kind.values()[Math.min(bar.kind(), TimingBar.Kind.values().length - 1)];
        Component label = Component.translatable("gui.dungeondelving.timing." + kind.name().toLowerCase());
        graphics.drawCenteredString(minecraft.font, label, graphics.guiWidth() / 2, top - 12, 0xFFFFFF);
    }

    @EventBusSubscriber(modid = DungeonDelving.MODID, value = Dist.CLIENT)
    public static final class Registration {
        private Registration() {}

        @SubscribeEvent
        public static void onRegisterLayers(RegisterGuiLayersEvent event) {
            event.registerAbove(VanillaGuiLayers.HOTBAR, DungeonDelving.id("timing_bar"), TimingBarOverlay::render);
        }
    }
}
