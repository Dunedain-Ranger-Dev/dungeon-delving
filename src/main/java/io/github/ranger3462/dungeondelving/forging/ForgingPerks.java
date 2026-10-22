package io.github.ranger3462.dungeondelving.forging;

import io.github.ranger3462.dungeondelving.skill.CraftSkill;
import io.github.ranger3462.dungeondelving.skill.CraftingSkills;
import net.minecraft.world.entity.player.Player;

/**
 * What the Forging skill does, level by level. These are fixed perks for now; the talent tree (DD-70) will replace
 * some of them with choices.
 */
public final class ForgingPerks {
    /** The Forging level needed to forge each ★ (index 0 is ★1). */
    private static final int[] LEVEL_FOR_STARS = { 1, 3, 6, 10, 15, 20, 25 };

    private ForgingPerks() {}

    public static int level(Player player) {
        return CraftingSkills.level(player, CraftSkill.FORGING);
    }

    /** The Forging level needed to forge up to this ★. */
    public static int levelFor(int stars) {
        return LEVEL_FOR_STARS[Math.clamp(stars, 1, Forging.MAX_STARS) - 1];
    }

    /** Extra sweet-spot width, as a share of the bar: +0.25% a level. */
    public static float sweetSpotBonus(int level) {
        return 0.0025f * (level - 1);
    }

    /** How long hot metal stays workable for a smith of this level, in ticks. */
    public static int coolTicks(int level) {
        float multiplier = level >= 20 ? 1.5f : level >= 5 ? 1.25f : 1.0f;
        return Math.round(HotMetalItem.BASE_COOL_TICKS * multiplier);
    }

    /** Iron nuggets a cracked piece returns. */
    public static int scrapNuggets(int level) {
        return level >= 25 ? 8 : level >= 20 ? 7 : level >= 10 ? 6 : 5;
    }

    /** Scales the chance that a strike cracks the piece (not a water quench: only oil helps there). */
    public static float crackMultiplier(int level) {
        return level >= 30 ? 0.5f : level >= 15 ? 0.75f : 1.0f;
    }
}
