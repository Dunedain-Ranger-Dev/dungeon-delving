package io.github.ranger3462.dungeondelving.skill;

import net.minecraft.world.entity.player.Player;

/**
 * Skill tree perks our own code acts on (DD-70, DD-56). Each rank is a node in a Pufferfish's Skills tree whose
 * reward is a player tag ({@link #tag}), so unlocking the node turns the perk on and a respec turns it off. The trees
 * are written by tools/skill_trees.py, which uses the same tag names.
 */
public enum Perk {
    // Forging
    STEADY_HAND("steady_hand", 3),
    SLOW_COOLING("slow_cooling", 2),
    THRIFTY("thrifty", 2),
    SURE_STRIKES("sure_strikes", 2),
    // Leatherworking
    STEADY_SCRAPER("steady_scraper", 3),
    CAREFUL_TANNER("careful_tanner", 2),
    WATER_SAVER("water_saver", 2),
    // Weaving
    STEADY_NEEDLE("steady_needle", 3),
    CAREFUL_WEAVER("careful_weaver", 2),
    // Mining
    VEIN_MINER("vein_miner", 3),
    PROSPECTOR("prospector", 1),
    // Woodcutting
    TREE_FELLER("tree_feller", 3),
    FORESTER("forester", 1),
    // Farming
    BOUNTIFUL("bountiful", 1),
    REPLANTER("replanter", 1);

    public final String key;
    public final int ranks;

    Perk(String key, int ranks) {
        this.key = key;
        this.ranks = ranks;
    }

    /** The player tag a rank's node gives (ranks count from 1). */
    public String tag(int rank) {
        return "dungeondelving.perk." + key + (ranks > 1 ? "_" + rank : "");
    }

    /** The highest rank of this perk the player has, or 0. */
    public int rank(Player player) {
        for (int rank = ranks; rank >= 1; rank--) {
            if (player.getTags().contains(tag(rank))) {
                return rank;
            }
        }
        return 0;
    }

    public boolean has(Player player) {
        return rank(player) > 0;
    }
}
