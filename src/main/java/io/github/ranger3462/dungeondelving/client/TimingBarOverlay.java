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
 * Draws the crafting minigames ({@link TimingBar}) above the hotbar, and turns clicks into strikes while one is up.
 * Each is timed on the client from the moment it arrives, so lag doesn't shift it; every strike sends how long it had
 * been running ({@link StrikePayload}).
 * <ul>
 * <li>Smithing: a bar with a green sweet spot and gold middle, and a marker sweeping back and forth.</li>
 * <li>Weaving: a needle whose eye bobs up and down, and a thread tip bobbing beside it at its own pace.</li>
 * <li>Cutting: a strip of leather with the pattern line and its end mark; the blade moves while the button is held.</li>
 * <li>Turning: a depth gauge filling while the button is held, with its green band.</li>
 * </ul>
 */
@EventBusSubscriber(modid = DungeonDelving.MODID, value = Dist.CLIENT)
public final class TimingBarOverlay {
    private static final int WIDTH = 160;
    private static final int HEIGHT = 8;
    /** Weaving: the height of the needle's track; turning: the gauge's. */
    private static final int TRACK = 48;
    private static TimingBarPayload bar = TimingBarPayload.HIDE;
    /** The client time (in ticks, with the partial tick) this bar arrived: it runs from then. */
    private static double barArrived;
    /** Set once a smithing, weaving, cutting or turning bar has been struck: it holds still until the next arrives. */
    private static float struckAt = -1.0f;
    /** Cutting and turning: when the button went down (bar time), or -1 while it's up. */
    private static float holdStart = -1.0f;

    private TimingBarOverlay() {}

    /** Shows or hides a minigame. Called on the client when the server sends it. A new bar starts running now. */
    public static void handle(TimingBarPayload payload) {
        boolean sameBar = payload.needed() > 0 && payload.bar() == bar.bar() && payload.kind() == bar.kind();
        bar = payload;
        if (!sameBar) {
            barArrived = clientTicks();
            struckAt = -1.0f;
            holdStart = -1.0f;
        }
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
     * While a minigame is up, clicks strike instead of using the item, and only a fresh click counts: holding the button
     * down (which normally repeats every few ticks) doesn't strike again.
     */
    @SubscribeEvent
    public static void onUse(InputEvent.InteractionKeyMappingTriggered event) {
        if (!active()) {
            return;
        }
        TimingBar.Kind kind = bar.barKind();
        if (!event.isUseItem()) {
            return;
        }
        event.setCanceled(true);
        event.setSwingHand(false);
        if (useWasDown) {
            return;
        }
        useWasDown = true; // One strike per press, even if this fires again before the tick ends.
        switch (kind) {
            case CUT, TURN -> {
                if (struckAt < 0.0f && holdStart < 0.0f) {
                    holdStart = elapsed();
                }
            }
            default -> {
                if (struckAt < 0.0f) {
                    struckAt = kind == TimingBar.Kind.WEAVE ? TimingBar.thread(elapsed(), bar.value(0), bar.value(1))
                            : TimingBar.marker(elapsed(), bar.period());
                    strike(0, elapsed(), 0);
                }
            }
        }
    }

    private static void strike(int index, float time, int button) {
        PacketDistributor.sendToServer(new StrikePayload(bar.bar(), index, time, button));
        Minecraft.getInstance().player.swing(InteractionHand.MAIN_HAND);
    }

    /** How long the button has been held in a held minigame. */
    private static float held() {
        return holdStart < 0.0f ? 0.0f : elapsed() - holdStart;
    }

    private static float heldPosition(float held) {
        return bar.barKind() == TimingBar.Kind.CUT ? TimingBar.cut(held, bar.period()) : TimingBar.turn(held, bar.period());
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        boolean useDown = minecraft.options.keyUse.isDown();
        useWasDown = useDown;
        if (!active()) {
            return;
        }
        TimingBar.Kind kind = bar.barKind();
        // Letting go of a held minigame strikes; so does holding on far past the end.
        if (kind.held() && holdStart >= 0.0f && (!useDown || held() > bar.period() * 1.3f)) {
            float held = held();
            struckAt = heldPosition(held);
            holdStart = -1.0f;
            strike(bar.done(), held, 0);
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
            case CUT -> renderCut(graphics, centerX, top);
            case TURN -> renderTurn(graphics, centerX, top);
            default -> renderBar(graphics, centerX, top);
        };
        Component label = Component.translatable("gui.dungeondelving.timing." + bar.barKind().name().toLowerCase());
        graphics.drawCenteredString(minecraft.font, label, centerX, labelY, 0xFFFFFF);
        // A pip per strike needed.
        for (int i = 0; i < bar.needed(); i++) {
            int x = centerX + (i - bar.needed() / 2) * 8 - 2;
            graphics.fill(x, top + HEIGHT + 4, x + 5, top + HEIGHT + 9, i < bar.done() ? 0xFFFFC040 : 0xFF555555);
        }
    }

    /** Smithing: the sweeping bar. Returns where the label goes. */
    private static int renderBar(GuiGraphics graphics, int centerX, int top) {
        float marker = struckAt >= 0.0f ? struckAt : TimingBar.marker(elapsed(), bar.period());
        sweetBar(graphics, centerX, top, bar.value(0), bar.value(1), marker, 0xFF3A2A20);
        return top - 12;
    }

    /** A bar with a green sweet spot, its gold middle, and a marker. */
    private static void sweetBar(GuiGraphics graphics, int centerX, int top, float low, float high, float marker, int background) {
        int left = centerX - WIDTH / 2;
        graphics.fill(left - 1, top - 1, left + WIDTH + 1, top + HEIGHT + 1, 0xFF000000);
        graphics.fill(left, top, left + WIDTH, top + HEIGHT, background);
        graphics.fill(left + Math.round(low * WIDTH), top, left + Math.round(high * WIDTH), top + HEIGHT, 0xFF4CC24C);
        int goldLeft = left + Math.round(TimingBar.goldLow(low, high) * WIDTH);
        int goldRight = left + Math.round(TimingBar.goldHigh(low, high) * WIDTH);
        graphics.fill(goldLeft, top, Math.max(goldRight, goldLeft + 1), top + HEIGHT, 0xFFFFD23C);
        int markerX = left + Math.round(marker * WIDTH);
        graphics.fill(markerX - 1, top - 3, markerX + 1, top + HEIGHT + 3, 0xFFFFFFFF);
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

    /** Cutting: the leather strip, the pattern line (dashed) and its end mark, and the blade where the hold has taken it. */
    private static int renderCut(GuiGraphics graphics, int centerX, int top) {
        int left = centerX - WIDTH / 2;
        int stripHeight = HEIGHT + 6;
        float blade = struckAt >= 0.0f ? struckAt : heldPosition(held());
        graphics.fill(left - 1, top - 1, left + WIDTH + 1, top + stripHeight + 1, 0xFF000000);
        graphics.fill(left, top, left + WIDTH, top + stripHeight, 0xFF8A5A34);
        int lineY = top + stripHeight / 2;
        for (int x = 0; x < WIDTH; x += 6) {
            graphics.fill(left + x, lineY, left + x + 3, lineY + 1, 0xFFE8D2B0);
        }
        // The cut so far: a solid line up to the blade.
        graphics.fill(left, lineY - 1, left + Math.round(blade * WIDTH), lineY + 2, 0xFF2A1A0E);
        float low = bar.value(0);
        float high = bar.value(1);
        graphics.fill(left + Math.round(low * WIDTH), top + 1, left + Math.round(high * WIDTH), top + stripHeight - 1, 0x904CC24C);
        int goldLeft = left + Math.round(TimingBar.goldLow(low, high) * WIDTH);
        graphics.fill(goldLeft, top + 1, Math.max(left + Math.round(TimingBar.goldHigh(low, high) * WIDTH), goldLeft + 1), top + stripHeight - 1, 0xC0FFD23C);
        int bladeX = left + Math.round(blade * WIDTH);
        graphics.fill(bladeX - 1, top - 4, bladeX + 2, top + stripHeight + 4, 0xFFD8DEE6);
        return top - 12;
    }

    /** Turning: a depth gauge that fills while held, with the green band to let go in. */
    private static int renderTurn(GuiGraphics graphics, int centerX, int top) {
        int gaugeTop = top - TRACK + HEIGHT;
        int left = centerX - 6;
        float depth = struckAt >= 0.0f ? struckAt : heldPosition(held());
        graphics.fill(left - 1, gaugeTop - 1, left + 13, gaugeTop + TRACK + 1, 0xFF000000);
        graphics.fill(left, gaugeTop, left + 12, gaugeTop + TRACK, 0xFF3A2A20);
        float low = bar.value(0);
        float high = bar.value(1);
        int y = gaugeTop + TRACK;
        graphics.fill(left, y - Math.round(high * TRACK), left + 12, y - Math.round(low * TRACK), 0xFF4CC24C);
        graphics.fill(left, y - Math.round(TimingBar.goldHigh(low, high) * TRACK), left + 12, y - Math.round(TimingBar.goldLow(low, high) * TRACK), 0xFFFFD23C);
        graphics.fill(left + 2, y - Math.round(depth * TRACK), left + 10, y, 0xC0C89A64);
        graphics.fill(left - 3, y - Math.round(depth * TRACK) - 1, left + 15, y - Math.round(depth * TRACK) + 1, 0xFFFFFFFF);
        return gaugeTop - 12;
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
