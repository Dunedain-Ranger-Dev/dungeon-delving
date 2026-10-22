package io.github.ranger3462.dungeondelving.forging;

/**
 * What the Leatherworking and Weaving skills do, level by level (see {@link ForgingPerks} for Forging). Fixed perks for
 * now; the talent tree (DD-70) will turn some of them into choices.
 */
public final class CraftPerks {
    private CraftPerks() {}

    /** Extra loom sweet-spot width, as a share of the bar: +0.25% a Weaving level. */
    public static float weavingSweetSpotBonus(int level) {
        return 0.0025f * (level - 1);
    }

    /** The chance boiling leather doesn't use up a level of water: 25% from Leatherworking 10, 50% from 20. */
    public static float boilSavesWater(int level) {
        return level >= 20 ? 0.5f : level >= 10 ? 0.25f : 0.0f;
    }
}
