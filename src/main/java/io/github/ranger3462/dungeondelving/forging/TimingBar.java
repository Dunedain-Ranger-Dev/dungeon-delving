package io.github.ranger3462.dungeondelving.forging;

import net.minecraft.util.Mth;

/**
 * The timing minigames behind the crafting skills. The server and the client work them out from the same numbers, so
 * what the player sees is what the server checks.
 * <ul>
 * <li>{@link Kind#STRIKE} and {@link Kind#GRIND} (smithing): a marker sweeps back and forth across a bar; click while
 * it's in the green sweet spot, ideally in its gold middle.</li>
 * <li>{@link Kind#WEAVE} (weaving, "thread the needle"): the needle's eye and the thread's tip bob up and down at
 * different speeds; click when the thread lines up with the eye.</li>
 * <li>{@link Kind#CUT} (leatherworking, at the Shaping Rack): hold to draw the blade along the pattern, and let go on the
 * end mark. The blade only moves while held.</li>
 * <li>{@link Kind#TURN} (woodworking, at a lathe): hold to press the chisel; the cut deepens faster and faster; let go in
 * the green. A few passes, each a little deeper.</li>
 * </ul>
 */
public final class TimingBar {
    /** Which minigame. */
    public enum Kind {
        STRIKE, GRIND, WEAVE, CUT, TURN;

        /** Minigames the player plays by holding the button and letting go. */
        public boolean held() {
            return this == CUT || this == TURN;
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

    /** Where a one-way marker (the blade) is, from 0 to 1, {@code ticks} into a pass of {@code period} ticks. */
    public static float pass(double ticks, int period) {
        return (float) Mth.clamp(ticks / period, 0.0, 1.0);
    }

    // --- Sweet spots ---

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
}
