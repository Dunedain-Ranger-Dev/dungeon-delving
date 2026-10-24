package io.github.ranger3462.dungeondelving.forging;

import io.github.ranger3462.dungeondelving.skill.CraftSkill;
import io.github.ranger3462.dungeondelving.skill.Perk;
import net.minecraft.world.entity.player.Player;

/**
 * What the Leatherworking and Weaving perks do (see {@link ForgingPerks} for Forging). Each is a node in that skill's
 * tree (DD-70): a level gives a point to spend, rather than a bonus by itself.
 */
public final class CraftPerks {
    private CraftPerks() {}

    /** Extra room for error (the needle's eye, or each patch), as a share of the bar: +2% a rank of Steady Scraper or Steady Needle. */
    public static float sweetSpotBonus(Player player, CraftSkill skill) {
        return 0.02f * (skill == CraftSkill.WEAVING ? Perk.STEADY_NEEDLE : Perk.STEADY_SCRAPER).rank(player);
    }

    /** Scales the chance a miss snaps the thread or tears the hide: 25% less a rank of Careful Tanner or Careful Weaver. */
    public static float failMultiplier(Player player, CraftSkill skill) {
        return 1.0f - 0.25f * (skill == CraftSkill.WEAVING ? Perk.CAREFUL_WEAVER : Perk.CAREFUL_TANNER).rank(player);
    }

    /** The chance boiling leather doesn't use up a level of water: 25% a rank of Water Saver. */
    public static float boilSavesWater(Player player) {
        return 0.25f * Perk.WATER_SAVER.rank(player);
    }
}
