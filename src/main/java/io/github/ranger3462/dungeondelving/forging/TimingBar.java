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

    /** How far a marker position is outside the sweet spot {@code [low, high]}: 0 inside it. */
    public static float offBy(float marker, float low, float high) {
        return marker < low ? low - marker : marker > high ? marker - high : 0.0f;
    }
}
