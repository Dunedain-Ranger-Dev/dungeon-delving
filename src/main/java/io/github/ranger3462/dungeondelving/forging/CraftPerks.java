package io.github.ranger3462.dungeondelving.forging;

/**
 * What the Leatherworking and Weaving skills do, level by level (see {@link ForgingPerks} for Forging). Fixed perks for
 * now; the talent tree (DD-70) will turn some of them into choices, and is where bonus results come from.
 */
public final class CraftPerks {
    private CraftPerks() {}

    /** Extra room for error (the needle's eye, or each patch), as a share of the bar: +0.25% a level. */
    public static float sweetSpotBonus(int level) {
        return 0.0025f * (level - 1);
    }

    /** Scales the chance a miss snaps the thread or tears the hide: 25% less from level 15, 50% less at 30. */
    public static float failMultiplier(int level) {
        return level >= 30 ? 0.5f : level >= 15 ? 0.75f : 1.0f;
    }

    /** The chance boiling leather doesn't use up a level of water: 25% from Leatherworking 10, 50% from 20. */
    public static float boilSavesWater(int level) {
        return level >= 20 ? 0.5f : level >= 10 ? 0.25f : 0.0f;
    }
}
