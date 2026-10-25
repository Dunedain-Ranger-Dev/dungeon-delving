package io.github.ranger3462.dungeondelving.forging;

import io.github.ranger3462.dungeondelving.skill.CraftSkill;
import io.github.ranger3462.dungeondelving.skill.CraftingSkills;
import io.github.ranger3462.dungeondelving.skill.Perk;
import net.minecraft.world.entity.player.Player;

/**
 * What the smithing skills do (DD-89: Forging split into Weaponsmithing and Armorsmithing). The forge work itself
 * (striking, quenching, grinding) trains both, and its ★ is capped by the better of the two. Weapon parts and weapons
 * are capped by Weaponsmithing, armor parts and armor by Armorsmithing ({@link CraftingXp}). Everything else is a perk
 * in the two trees, bought with the points levels give: the anvil's (Steady Hand, Sure Strikes) in Weaponsmithing's,
 * the grindstone's and the metal's (Steady Grind, Slow Cooling, Thrifty) in Armorsmithing's.
 */
public final class ForgingPerks {
    /** The smithing level needed to forge each ★ (index 0 is ★1). */
    private static final int[] LEVEL_FOR_STARS = { 1, 3, 6, 10, 15, 20, 25 };

    private ForgingPerks() {}

    /** The forge work's level: the better of the player's Weaponsmithing and Armorsmithing. */
    public static int level(Player player) {
        return Math.max(CraftingSkills.level(player, CraftSkill.WEAPONSMITHING), CraftingSkills.level(player, CraftSkill.ARMORSMITHING));
    }

    /** XP for forge work: it trains both smithing skills. */
    public static void addXp(Player player, int amount) {
        CraftingSkills.addXp(player, CraftSkill.WEAPONSMITHING, amount);
        CraftingSkills.addXp(player, CraftSkill.ARMORSMITHING, amount);
    }

    /** The smithing level needed to forge up to this ★. */
    public static int levelFor(int stars) {
        return LEVEL_FOR_STARS[Math.clamp(stars, 1, Forging.MAX_STARS) - 1];
    }

    /** Extra sweet-spot width, as a share of the bar: +2% a rank of Steady Hand on the anvil, of Steady Grind at the grindstone. */
    public static float sweetSpotBonus(Player player, TimingBar.Kind kind) {
        return 0.02f * (kind == TimingBar.Kind.GRIND ? Perk.STEADY_GRIND : Perk.STEADY_HAND).rank(player);
    }

    /** How long hot metal stays workable for this smith, in ticks: a quarter longer a rank of Slow Cooling. */
    public static int coolTicks(Player player) {
        return Math.round(HotMetalItem.BASE_COOL_TICKS * (1.0f + 0.25f * Perk.SLOW_COOLING.rank(player)));
    }

    /** Iron nuggets a cracked piece returns: 5, or 6 and 8 with Thrifty. */
    public static int scrapNuggets(Player player) {
        return new int[] { 5, 6, 8 }[Perk.THRIFTY.rank(player)];
    }

    /** The share of a piece's metal that salvaging it gets back: 40%, or 55% and 70% with Thrifty. */
    public static float salvageShare(Player player) {
        return 0.4f + 0.15f * Perk.THRIFTY.rank(player);
    }

    /** Scales the chance that a strike cracks the piece (not a water quench: only oil helps there): 25% less a rank of Sure Strikes. */
    public static float crackMultiplier(Player player) {
        return 1.0f - 0.25f * Perk.SURE_STRIKES.rank(player);
    }
}
