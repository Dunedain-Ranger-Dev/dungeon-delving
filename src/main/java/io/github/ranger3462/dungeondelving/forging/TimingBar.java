package io.github.ranger3462.dungeondelving.forging;

import net.minecraft.util.Mth;

/**
 * The timing bar used for striking, grinding and weaving: a marker sweeps back and forth across the bar, and each
 * strike should land while it's inside the sweet spot. The server and the client draw the same bar from the same
 * numbers, so what the player sees is what the server checks.
 */
public final class TimingBar {
    /** What the bar is for. */
    public enum Kind {
        STRIKE, GRIND, WEAVE
    }

    private TimingBar() {}

    /** Where the marker is, from 0 to 1, {@code ticks} after the bar started, for a full sweep (there and back) of {@code period} ticks. */
    public static float marker(double ticks, int period) {
        double phase = Mth.positiveModulo(ticks, period) / period;
        return (float) (phase < 0.5 ? phase * 2.0 : 2.0 - phase * 2.0);
    }

    /** How a strike landed: in the gold zone (counts twice, never cracks), in the green, or outside it. */
    public enum Hit {
        GOLD, GREEN, MISS
    }

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

    /** Where a strike with the marker at {@code marker} landed, for the sweet spot {@code [low, high]}. */
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
}
