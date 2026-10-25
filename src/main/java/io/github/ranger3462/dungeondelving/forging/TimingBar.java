package io.github.ranger3462.dungeondelving.forging;

import java.util.List;

import net.minecraft.util.Mth;

/**
 * The timing minigames behind the crafting skills. The server and the client work them out from the same numbers, so
 * what the player sees is what the server checks.
 * <ul>
 * <li>{@link Kind#STRIKE} and {@link Kind#GRIND} (smithing): a marker sweeps back and forth across a bar; click while
 * it's in the green sweet spot, ideally in its gold middle.</li>
 * <li>{@link Kind#WEAVE} (weaving, "thread the needle"): the needle's eye and the thread's tip bob up and down at
 * different speeds; click when the thread lines up with the eye.</li>
 * <li>{@link Kind#SCRAPE} (leatherworking): the scraper makes one pass across the hide; click as it crosses each patch,
 * in order. A patch passed without a click is a miss.</li>
 * <li>{@link Kind#CUT} (leatherworking, at the Shaping Rack): hold to draw the blade along the pattern, and let go on the
 * end mark. The blade only moves while held.</li>
 * <li>{@link Kind#SEW} (weaving, at the Shaping Rack): stitch on the beat, alternating needle in (use) and needle out
 * (attack).</li>
 * <li>{@link Kind#SPIN} (weaving, at the Spinning Wheel): the wheel slows by itself; tap to keep its speed in the band
 * until the yarn is spun.</li>
 * <li>{@link Kind#CARVE} (woodworking, at a fletching table): the knife drifts off the grain line; tap to nudge it back,
 * staying on the grain to the end.</li>
 * <li>{@link Kind#TURN} (woodworking, at a lathe): hold to press the chisel; the cut deepens faster and faster; let go in
 * the green. A few passes, each a little deeper.</li>
 * </ul>
 * SPIN and CARVE are worked out from the times of the player's taps ({@link #spinSpeed}, {@link #carveDrift}), so the
 * server replays exactly what the player saw.
 */
public final class TimingBar {
    /** Which minigame. */
    public enum Kind {
        STRIKE, GRIND, WEAVE, SCRAPE, CUT, SEW, SPIN, CARVE, TURN;

        /** Minigames the player plays by holding the button and letting go. */
        public boolean held() {
            return this == CUT || this == TURN;
        }

        /** Minigames worked out from the player's taps over a fixed time. */
        public boolean tapped() {
            return this == SPIN || this == CARVE;
        }
    }

    /** How a click landed: gold (the very middle: counts twice, never fails), green, or a miss. */
    public enum Hit {
        GOLD, GREEN, MISS
    }

    private TimingBar() {}

    /** Where a back-and-forth marker is, from 0 to 1, {@code ticks} after it started, for a full sweep (there and back) of {@code period} ticks. */
    public static float marker(double ticks, int period) {
        double phase = Mth.positiveModulo(ticks, period) / period;
        return (float) (phase < 0.5 ? phase * 2.0 : 2.0 - phase * 2.0);
    }

    /** Where a one-way marker (the scraper) is, from 0 to 1, {@code ticks} into a pass of {@code period} ticks. */
    public static float pass(double ticks, int period) {
        return (float) Mth.clamp(ticks / period, 0.0, 1.0);
    }

    // --- Sweet spots (smithing and scraping) ---

    /** The gold zone is this share of the green, in its middle, but never narrower than {@link #MIN_GOLD}. */
    public static final float GOLD_SHARE = 0.25f;
    public static final float MIN_GOLD = 0.012f;

    public static float goldLow(float low, float high) {
        return (low + high) / 2 - goldWidth(low, high) / 2;
    }

    public static float goldHigh(float low, float high) {
        return (low + high) / 2 + goldWidth(low, high) / 2;
    }

    private static float goldWidth(float low, float high) {
        return Math.min(high - low, Math.max(MIN_GOLD, (high - low) * GOLD_SHARE));
    }

    /** Where a click with the marker at {@code marker} landed, for the sweet spot {@code [low, high]}. */
    public static Hit judge(float marker, float low, float high) {
        if (marker >= goldLow(low, high) && marker <= goldHigh(low, high)) {
            return Hit.GOLD;
        }
        return marker >= low && marker <= high ? Hit.GREEN : Hit.MISS;
    }

    /** How far a marker position is outside the sweet spot {@code [low, high]}: 0 inside it. */
    public static float offBy(float marker, float low, float high) {
        return marker < low ? low - marker : marker > high ? marker - high : 0.0f;
    }

    // --- Threading the needle (weaving) ---

    /** The needle's eye: a back-and-forth at the bar's period. */
    public static float eye(double ticks, int period) {
        return marker(ticks, period);
    }

    /** The thread's tip: a back-and-forth at its own period, offset by {@code phase} ticks. */
    public static float thread(double ticks, float threadPeriod, float phase) {
        return marker(ticks + phase, Math.max(1, Math.round(threadPeriod)));
    }

    /** How a stitch landed: gold if the thread is in the middle of the eye, green if it's anywhere in the eye ({@code gap} tall). */
    public static Hit judgeStitch(double ticks, int period, float threadPeriod, float phase, float gap) {
        float off = Math.abs(thread(ticks, threadPeriod, phase) - eye(ticks, period));
        if (off <= gap * GOLD_SHARE / 2) {
            return Hit.GOLD;
        }
        return off <= gap / 2 ? Hit.GREEN : Hit.MISS;
    }

    // --- Holding (cutting and turning) ---

    /** Cutting: how far along the pattern the blade is, from 0 to 1, after {@code held} ticks held; {@code period} for the whole line. */
    public static float cut(double held, int period) {
        return pass(held, period);
    }

    /** Turning: how deep the chisel is, from 0 to 1, after {@code held} ticks held. It bites faster the deeper it goes. */
    public static float turn(double held, int period) {
        float depth = pass(held, period);
        return depth * depth;
    }

    // --- Sewing ---

    /** Sewing: ticks before the first stitch is due. */
    public static final int SEW_LEAD = 20;

    /** When stitch {@code index} is due, in ticks from the start, with a stitch every {@code period} ticks. */
    public static float beat(int index, int period) {
        return SEW_LEAD + index * period;
    }

    /** Which button stitch {@code index} wants: 0 (use: needle in) for even stitches, 1 (attack: needle out) for odd ones. */
    public static int stitchButton(int index) {
        return index % 2;
    }

    /** How a stitch landed: within the window's gold middle, within the window ({@code window} ticks wide), or off it or with the wrong button. */
    public static Hit judgeBeat(double ticks, int index, int period, float window, int button) {
        if (button != stitchButton(index)) {
            return Hit.MISS;
        }
        double off = Math.abs(ticks - beat(index, period));
        if (off <= window * GOLD_SHARE / 2) {
            return Hit.GOLD;
        }
        return off <= window / 2 ? Hit.GREEN : Hit.MISS;
    }

    // --- Tapping (spinning and carving) ---

    /** How long a spin or a carve lasts, in ticks. */
    public static final int TAP_DURATION = 100;
    /** Spinning: where the wheel's speed starts, how much it slows each tick, and how much a tap speeds it up. */
    public static final float SPIN_START = 0.6f;
    public static final float SPIN_DECAY = 0.011f;
    public static final float SPIN_TAP = 0.16f;
    /** Carving: how much the knife drifts each tick (on average), and how far a tap nudges it back. */
    public static final float CARVE_DRIFT = 0.009f;
    public static final float CARVE_NUDGE = 0.11f;

    /** The wheel's speed (0 to 1) at {@code ticks}, given the tap times so far: it slows by itself, and each tap speeds it up. */
    public static float spinSpeed(double ticks, List<Float> taps) {
        return simulate(ticks, taps, SPIN_START, -SPIN_DECAY, 0.0f, SPIN_TAP);
    }

    /** The knife's distance off the grain (0 to 1, the grain at 0.5) at {@code ticks}: it drifts down, each tap nudges it up. */
    public static float carveDrift(double ticks, List<Float> taps, float phase) {
        return simulate(ticks, taps, 0.5f, CARVE_DRIFT, phase, -CARVE_NUDGE);
    }

    /**
     * Steps a tapped minigame tick by tick: each tick moves the value by {@code step} (for carving, wavering around it
     * with {@code phase}), and each tap made before that tick adds {@code tap}. Kept between 0 and 1.
     */
    private static float simulate(double ticks, List<Float> taps, float start, float step, float phase, float tap) {
        float value = start;
        int next = 0;
        int whole = (int) Math.min(ticks, TAP_DURATION);
        for (int tick = 0; tick < whole; tick++) {
            while (next < taps.size() && taps.get(next) < tick + 1) {
                value += tap;
                next++;
            }
            float drift = phase == 0.0f ? step : step * (1.0f + 0.8f * (float) Math.sin(tick * 0.21 + phase));
            value = Mth.clamp(value + drift, 0.0f, 1.0f);
        }
        return value;
    }

    /** The share of a tapped minigame's ticks the value spent inside {@code [low, high]}. */
    public static float timeInBand(Kind kind, List<Float> taps, float phase, float low, float high) {
        int inside = 0;
        for (int tick = 1; tick <= TAP_DURATION; tick++) {
            float value = kind == Kind.SPIN ? spinSpeed(tick, taps) : carveDrift(tick, taps, phase);
            if (value >= low && value <= high) {
                inside++;
            }
        }
        return inside / (float) TAP_DURATION;
    }

    /** How a tapped minigame went, by its time in the band: gold from 90%, green from 65%. */
    public static Hit judgeTapped(float timeInBand) {
        return timeInBand >= 0.9f ? Hit.GOLD : timeInBand >= 0.65f ? Hit.GREEN : Hit.MISS;
    }

    // --- Quality ---

    /**
     * The ★ a minigame's work earns before any cap: 1 plus up to 6 more by its score (a gold strike scores 2, a green 1,
     * a miss 0). All gold makes ★7, all green ★4.
     */
    public static int performanceStars(int score, int strikes) {
        if (strikes <= 0) {
            return 1;
        }
        return Mth.clamp(1 + (int) Math.floor(6.0f * score / (2.0f * strikes)), 1, 7);
    }
}
