package io.github.ranger3462.dungeondelving.client;

import java.util.ArrayList;
import java.util.List;

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
 * <li>Scraping: a hide with dark patches, and a scraper making one pass across it.</li>
 * <li>Cutting: a strip of leather with the pattern line and its end mark; the blade moves while the button is held.</li>
 * <li>Sewing: stitches coming in along a seam, each marked with the button it wants (right: in, left: out).</li>
 * <li>Spinning: the wheel's speed against its band, and how long is left.</li>
 * <li>Carving: the grain line with its band, and the knife drifting off it.</li>
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
    /** Scraping and sewing: how many patches or stitches this client has struck (or let pass) so far. */
    private static int scraped;
    /** Cutting and turning: when the button went down (bar time), or -1 while it's up. */
    private static float holdStart = -1.0f;
    /** Spinning and carving: the taps so far, and whether the end has been sent. */
    private static final List<Float> TAPS = new ArrayList<>();
    private static boolean doneSent;

    private TimingBarOverlay() {}

    /** Shows or hides a minigame. Called on the client when the server sends it. A new bar starts running now. */
    public static void handle(TimingBarPayload payload) {
        boolean sameBar = payload.needed() > 0 && payload.bar() == bar.bar() && payload.kind() == bar.kind();
        bar = payload;
        if (!sameBar) {
            barArrived = clientTicks();
            struckAt = -1.0f;
            scraped = 0;
            holdStart = -1.0f;
            TAPS.clear();
            doneSent = false;
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

    /** Whether the use and attack buttons were down at the end of the last tick: a click only counts if it wasn't. */
    private static boolean useWasDown;
    private static boolean attackWasDown;

    /**
     * While a minigame is up, clicks strike instead of using the item (or, while sewing, attacking), and only a fresh
     * click counts: holding the button down (which normally repeats every few ticks) doesn't strike again.
     */
    @SubscribeEvent
    public static void onUse(InputEvent.InteractionKeyMappingTriggered event) {
        if (!active()) {
            return;
        }
        TimingBar.Kind kind = bar.barKind();
        if (event.isAttack() && kind == TimingBar.Kind.SEW) {
            event.setCanceled(true);
            event.setSwingHand(false);
            if (!attackWasDown) {
                attackWasDown = true;
                stitch(1);
            }
            return;
        }
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
            case SCRAPE -> {
                if (scraped < bar.needed()) {
                    strike(scraped++, elapsed(), 0);
                }
            }
            case SEW -> stitch(0);
            case CUT, TURN -> {
                if (struckAt < 0.0f && holdStart < 0.0f) {
                    holdStart = elapsed();
                }
            }
            case SPIN, CARVE -> {
                float now = elapsed();
                if (now < TimingBar.TAP_DURATION) {
                    TAPS.add(now);
                    strike(TAPS.size() - 1, now, 0);
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

    private static void stitch(int button) {
        if (scraped < bar.needed()) {
            strike(scraped++, elapsed(), button);
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
        attackWasDown = minecraft.options.keyAttack.isDown();
        if (!active()) {
            return;
        }
        TimingBar.Kind kind = bar.barKind();
        // A patch the scraper has gone past without a click is a miss: tell the server, so the pass moves on.
        if (kind == TimingBar.Kind.SCRAPE && scraped < bar.needed() && TimingBar.pass(elapsed(), bar.period()) > bar.value(scraped * 2 + 1)) {
            PacketDistributor.sendToServer(new StrikePayload(bar.bar(), scraped++, elapsed(), 0));
        }
        // A stitch whose window has gone by is a miss.
        if (kind == TimingBar.Kind.SEW && scraped < bar.needed() && elapsed() > TimingBar.beat(scraped, bar.period()) + bar.value(0) / 2) {
            PacketDistributor.sendToServer(new StrikePayload(bar.bar(), scraped++, elapsed(), 2));
        }
        // Letting go of a held minigame strikes; so does holding on far past the end.
        if (kind.held() && holdStart >= 0.0f && (!useDown || held() > bar.period() * 1.3f)) {
            float held = held();
            struckAt = heldPosition(held);
            holdStart = -1.0f;
            strike(scraped, held, 0);
        }
        // A spin or carve whose time is up.
        if (kind.tapped() && !doneSent && elapsed() >= TimingBar.TAP_DURATION) {
            doneSent = true;
            PacketDistributor.sendToServer(new StrikePayload(bar.bar(), StrikePayload.DONE, elapsed(), 0));
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
            case CUT -> renderCut(graphics, centerX, top);
            case TURN -> renderTurn(graphics, centerX, top);
            case SEW -> renderSew(graphics, centerX, top);
            case SPIN, CARVE -> renderTapped(graphics, centerX, top);
            default -> renderBar(graphics, centerX, top);
        };
        Component label = Component.translatable("gui.dungeondelving.timing." + bar.barKind().name().toLowerCase());
        graphics.drawCenteredString(minecraft.font, label, centerX, labelY, 0xFFFFFF);
        // A pip per strike needed (not for scraping or sewing, where each patch or stitch is its own mark).
        if (bar.barKind() != TimingBar.Kind.SCRAPE && bar.barKind() != TimingBar.Kind.SEW && !bar.barKind().tapped()) {
            for (int i = 0; i < bar.needed(); i++) {
                int x = centerX + (i - bar.needed() / 2) * 8 - 2;
                graphics.fill(x, top + HEIGHT + 4, x + 5, top + HEIGHT + 9, i < bar.done() ? 0xFFFFC040 : 0xFF555555);
            }
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

    /** Sewing: stitches coming in from the right to the needle, each with the button it wants. */
    private static int renderSew(GuiGraphics graphics, int centerX, int top) {
        Minecraft minecraft = Minecraft.getInstance();
        int left = centerX - WIDTH / 2;
        int seamHeight = HEIGHT + 8;
        float ticks = elapsed();
        float window = bar.value(0);
        int needleX = left + 24;
        graphics.fill(left - 1, top - 1, left + WIDTH + 1, top + seamHeight + 1, 0xFF000000);
        graphics.fill(left, top, left + WIDTH, top + seamHeight, 0xFFDCCFB4);
        // The window around the needle.
        int half = Math.round(window / 2 * 2.5f);
        graphics.fill(needleX - half, top, needleX + half, top + seamHeight, 0x904CC24C);
        graphics.fill(needleX, top - 3, needleX + 1, top + seamHeight + 3, 0xFFB8C0CC);
        for (int i = scraped; i < bar.needed(); i++) {
            float until = TimingBar.beat(i, bar.period()) - ticks;
            int x = needleX + Math.round(until * 2.5f);
            if (x > left + WIDTH - 6) {
                break;
            }
            boolean in = TimingBar.stitchButton(i) == 0;
            graphics.fill(x - 3, top + 3, x + 3, top + seamHeight - 3, in ? 0xFF7A4A2A : 0xFF2A4A7A);
            graphics.drawCenteredString(minecraft.font, in ? "R" : "L", x, top + 4, 0xFFFFFF);
        }
        return top - 12;
    }

    /** Spinning and carving: the value's track with its band, the value now, and how much time is left. */
    private static int renderTapped(GuiGraphics graphics, int centerX, int top) {
        int left = centerX - WIDTH / 2;
        float ticks = Math.min(elapsed(), TimingBar.TAP_DURATION);
        boolean spin = bar.barKind() == TimingBar.Kind.SPIN;
        float value = spin ? TimingBar.spinSpeed(ticks, TAPS) : TimingBar.carveDrift(ticks, TAPS, bar.value(2));
        float low = bar.value(0);
        float high = bar.value(1);
        graphics.fill(left - 1, top - 1, left + WIDTH + 1, top + HEIGHT + 1, 0xFF000000);
        graphics.fill(left, top, left + WIDTH, top + HEIGHT, spin ? 0xFF2A2A3A : 0xFF6A4A2A);
        graphics.fill(left + Math.round(low * WIDTH), top, left + Math.round(high * WIDTH), top + HEIGHT, 0xFF4CC24C);
        if (!spin) {
            int grain = left + WIDTH / 2;
            graphics.fill(grain, top, grain + 1, top + HEIGHT, 0xFF3A2410);
        }
        int markerX = left + Math.round(value * WIDTH);
        boolean inside = value >= low && value <= high;
        graphics.fill(markerX - 1, top - 3, markerX + 2, top + HEIGHT + 3, inside ? 0xFFFFFFFF : 0xFFFF6040);
        // Time left.
        float left_ = 1.0f - ticks / TimingBar.TAP_DURATION;
        graphics.fill(left, top + HEIGHT + 4, left + Math.round(left_ * WIDTH), top + HEIGHT + 6, 0xFFFFC040);
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
