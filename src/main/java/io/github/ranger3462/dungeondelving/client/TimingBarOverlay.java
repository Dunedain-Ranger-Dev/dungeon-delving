package io.github.ranger3462.dungeondelving.client;

import io.github.ranger3462.dungeondelving.DungeonDelving;
import io.github.ranger3462.dungeondelving.forging.StrikePayload;
import io.github.ranger3462.dungeondelving.forging.TimingBar;
import io.github.ranger3462.dungeondelving.forging.TimingBarPayload;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Draws the crafting minigames ({@link TimingBar}) above the hotbar, and turns right-clicks into strikes while one is up.
 * Each is timed on the client from the moment it arrives, so lag doesn't shift it; every strike sends how long it had
 * been running ({@link StrikePayload}).
 * <ul>
 * <li>Smithing: a bar with a green sweet spot and gold middle, and a marker sweeping back and forth.</li>
 * <li>Weaving: a needle whose eye bobs up and down, and a thread tip bobbing beside it at its own pace.</li>
 * <li>Scraping: a hide with dark patches, and a scraper making one pass across it.</li>
 * </ul>
 */
@EventBusSubscriber(modid = DungeonDelving.MODID, value = Dist.CLIENT)
public final class TimingBarOverlay {
    private static final int WIDTH = 160;
    private static final int HEIGHT = 8;
    /** Weaving: the height of the needle's track. */
    private static final int TRACK = 48;
    private static TimingBarPayload bar = TimingBarPayload.HIDE;
    /** The client time (in ticks, with the partial tick) this bar arrived: it runs from then. */
    private static double barArrived;
    /** Set once a smithing or weaving bar has been struck: it holds still until the next arrives. */
    private static float struckAt = -1.0f;
    /** Scraping: how many patches this client has struck (or let pass) in the current pass. */
    private static int scraped;

    private TimingBarOverlay() {}

    /** Shows or hides a minigame. Called on the client when the server sends it. A new bar starts running now. */
    public static void handle(TimingBarPayload payload) {
        boolean sameBar = payload.needed() > 0 && payload.bar() == bar.bar() && payload.kind() == bar.kind();
        bar = payload;
        if (!sameBar) {
            barArrived = clientTicks();
            struckAt = -1.0f;
            scraped = 0;
        }
        scraped = Math.max(scraped, payload.done());
    }

    /** The client's game time in ticks, between ticks too. */
    private static double clientTicks() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.level == null ? 0.0 : minecraft.level.getGameTime() + minecraft.getTimer().getGameTimeDeltaPartialTick(false);
    }

    /** How long this bar has been running. */
    private static float elapsed() {
        return (float) (clientTicks() - barArrived);
    }

    private static boolean active() {
        Minecraft minecraft = Minecraft.getInstance();
        return bar.needed() > 0 && minecraft.level != null && minecraft.player != null;
    }

    /** Whether the use button was down at the end of the last tick: a click only counts if it wasn't. */
    private static boolean useWasDown;

    /**
     * While a minigame is up, right-clicks strike instead of using the item, and only a fresh click counts: holding the
     * button down (which normally repeats the use every few ticks) doesn't strike again.
     */
    @SubscribeEvent
    public static void onUse(InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isUseItem() || !active()) {
            return;
        }
        event.setCanceled(true);
        event.setSwingHand(false);
        if (useWasDown) {
            return;
        }
        useWasDown = true; // One strike per press, even if this fires again before the tick ends.
        if (bar.barKind() == TimingBar.Kind.SCRAPE) {
            if (scraped < bar.needed()) {
                strike(scraped++);
            }
        } else if (struckAt < 0.0f) {
            struckAt = bar.barKind() == TimingBar.Kind.WEAVE ? TimingBar.thread(elapsed(), bar.value(0), bar.value(1))
                    : TimingBar.marker(elapsed(), bar.period());
            strike(0);
        }
    }

    private static void strike(int index) {
        PacketDistributor.sendToServer(new StrikePayload(bar.bar(), index, elapsed()));
        Minecraft.getInstance().player.swing(InteractionHand.MAIN_HAND);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        useWasDown = Minecraft.getInstance().options.keyUse.isDown();
        // A patch the scraper has gone past without a click is a miss: tell the server, so the pass moves on.
        if (active() && bar.barKind() == TimingBar.Kind.SCRAPE && scraped < bar.needed()
                && TimingBar.pass(elapsed(), bar.period()) > bar.value(scraped * 2 + 1)) {
            PacketDistributor.sendToServer(new StrikePayload(bar.bar(), scraped++, elapsed()));
        }
    }

    private static void render(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!active() || minecraft.options.hideGui) {
            return;
        }
        int centerX = graphics.guiWidth() / 2;
        int top = graphics.guiHeight() - 72;
        int labelY = switch (bar.barKind()) {
            case WEAVE -> renderNeedle(graphics, centerX, top);
            case SCRAPE -> renderScrape(graphics, centerX, top);
            default -> renderBar(graphics, centerX, top);
        };
        Component label = Component.translatable("gui.dungeondelving.timing." + bar.barKind().name().toLowerCase());
        graphics.drawCenteredString(minecraft.font, label, centerX, labelY, 0xFFFFFF);
        // A pip per strike needed (not for scraping, where each patch is its own pip).
        if (bar.barKind() != TimingBar.Kind.SCRAPE) {
            for (int i = 0; i < bar.needed(); i++) {
                int x = centerX + (i - bar.needed() / 2) * 8 - 2;
                graphics.fill(x, top + HEIGHT + 4, x + 5, top + HEIGHT + 9, i < bar.done() ? 0xFFFFC040 : 0xFF555555);
            }
        }
    }

    /** Smithing: the sweeping bar. Returns where the label goes. */
    private static int renderBar(GuiGraphics graphics, int centerX, int top) {
        int left = centerX - WIDTH / 2;
        float low = bar.value(0);
        float high = bar.value(1);
        float marker = struckAt >= 0.0f ? struckAt : TimingBar.marker(elapsed(), bar.period());
        graphics.fill(left - 1, top - 1, left + WIDTH + 1, top + HEIGHT + 1, 0xFF000000);
        graphics.fill(left, top, left + WIDTH, top + HEIGHT, 0xFF3A2A20);
        graphics.fill(left + Math.round(low * WIDTH), top, left + Math.round(high * WIDTH), top + HEIGHT, 0xFF4CC24C);
        int goldLeft = left + Math.round(TimingBar.goldLow(low, high) * WIDTH);
        int goldRight = left + Math.round(TimingBar.goldHigh(low, high) * WIDTH);
        graphics.fill(goldLeft, top, Math.max(goldRight, goldLeft + 1), top + HEIGHT, 0xFFFFD23C);
        int markerX = left + Math.round(marker * WIDTH);
        graphics.fill(markerX - 1, top - 3, markerX + 1, top + HEIGHT + 3, 0xFFFFFFFF);
        return top - 12;
    }

    /** Weaving: the needle with its bobbing eye, and the thread tip bobbing beside it. Returns where the label goes. */
    private static int renderNeedle(GuiGraphics graphics, int centerX, int top) {
        float ticks = elapsed();
        float gap = bar.value(2);
        float eye = TimingBar.eye(ticks, bar.period());
        float thread = struckAt >= 0.0f ? struckAt : TimingBar.thread(ticks, bar.value(0), bar.value(1));
        int trackTop = top - TRACK + HEIGHT;
        // The needle: a steel shaft running the height of the track, with its eye cut out where the eye is right now.
        int needleX = centerX + 6;
        int eyeHalf = Math.max(2, Math.round(gap * TRACK / 2));
        int eyeY = trackTop + Math.round((1.0f - eye) * TRACK);
        graphics.fill(needleX - 1, trackTop - 1, needleX + 5, trackTop + TRACK + 1, 0xFF000000);
        graphics.fill(needleX, trackTop, needleX + 4, trackTop + TRACK, 0xFFB8C0CC);
        graphics.fill(needleX, eyeY - eyeHalf, needleX + 4, eyeY + eyeHalf, 0xFF2A2A2A);
        int goldHalf = Math.max(1, Math.round(gap * TimingBar.GOLD_SHARE * TRACK / 2));
        graphics.fill(needleX + 1, eyeY - goldHalf, needleX + 3, eyeY + goldHalf, 0xFFFFD23C);
        // The thread: a line coming in from the left, its tip at the thread's height.
        int threadY = trackTop + Math.round((1.0f - thread) * TRACK);
        graphics.fill(centerX - 40, threadY - 1, needleX - 2, threadY + 1, 0xFFF0E6D2);
        graphics.fill(needleX - 3, threadY - 2, needleX - 1, threadY + 2, 0xFFFFFFFF);
        return trackTop - 12;
    }

    /** Scraping: the hide with its patches, and the scraper's one pass. Returns where the label goes. */
    private static int renderScrape(GuiGraphics graphics, int centerX, int top) {
        int left = centerX - WIDTH / 2;
        int hideHeight = HEIGHT + 6;
        graphics.fill(left - 1, top - 1, left + WIDTH + 1, top + hideHeight + 1, 0xFF000000);
        graphics.fill(left, top, left + WIDTH, top + hideHeight, 0xFFB98A5A);
        for (int i = 0; i < bar.needed(); i++) {
            float low = bar.value(i * 2);
            float high = bar.value(i * 2 + 1);
            // Patches already scraped (or passed) fade; the rest are dark, with a gold middle to aim for.
            int color = i < scraped ? 0xFFD8B48A : 0xFF4A2E1A;
            graphics.fill(left + Math.round(low * WIDTH), top + 2, left + Math.round(high * WIDTH), top + hideHeight - 2, color);
            if (i >= scraped) {
                int goldLeft = left + Math.round(TimingBar.goldLow(low, high) * WIDTH);
                int goldRight = left + Math.round(TimingBar.goldHigh(low, high) * WIDTH);
                graphics.fill(goldLeft, top + 4, Math.max(goldRight, goldLeft + 1), top + hideHeight - 4, 0xFFFFD23C);
            }
        }
        int markerX = left + Math.round(TimingBar.pass(elapsed(), bar.period()) * WIDTH);
        graphics.fill(markerX - 1, top - 3, markerX + 1, top + hideHeight + 3, 0xFFE8E8E8);
        return top - 12;
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
