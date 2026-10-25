package io.github.ranger3462.dungeondelving.forging;

import io.github.ranger3462.dungeondelving.skill.CraftSkill;
import io.github.ranger3462.dungeondelving.skill.Perk;
import net.minecraft.world.entity.player.Player;

/**
 * What the Leatherworking, Weaving and Woodworking perks do (see {@link ForgingPerks} for Forging). Each is a node in that skill's
 * tree (DD-70): a level gives a point to spend, rather than a bonus by itself.
 */
public final class CraftPerks {
    private CraftPerks() {}

    /** Extra room for error, as a share of the bar: +2% a rank of Steady Scraper, Steady Needle or Steady Knife. */
    public static float sweetSpotBonus(Player player, CraftSkill skill) {
        Perk perk = switch (skill) {
            case WEAVING -> Perk.STEADY_NEEDLE;
            case WOODWORKING -> Perk.STEADY_KNIFE;
            default -> Perk.STEADY_SCRAPER;
        };
        return 0.02f * perk.rank(player);
    }

    /** Scales the chance a miss ruins the piece: 25% less a rank of Careful Tanner, Careful Weaver or Careful Carver. */
    public static float failMultiplier(Player player, CraftSkill skill) {
        Perk perk = switch (skill) {
            case WEAVING -> Perk.CAREFUL_WEAVER;
            case WOODWORKING -> Perk.CAREFUL_CARVER;
            default -> Perk.CAREFUL_TANNER;
        };
        return 1.0f - 0.25f * perk.rank(player);
    }

    /** The chance boiling leather doesn't use up a level of water: 25% a rank of Water Saver. */
    public static float boilSavesWater(Player player) {
        return 0.25f * Perk.WATER_SAVER.rank(player);
    }
}
