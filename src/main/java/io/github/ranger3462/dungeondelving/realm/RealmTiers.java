package io.github.ranger3462.dungeondelving.realm;

import net.minecraft.util.Mth;

/**
 * Town tiers (DD-99, docs/design/realm.md). A kingdom town grows from a hamlet (tier 1) to a city (tier 5): each turn,
 * one with wealth to spare (the tier-up's {@link #cost}, plus {@link #RESERVE} kept back for repairs) and no raid damage
 * still being rebuilt spends it and goes up a tier, gaining {@link #DEFENSE_PER_TIER} defense. Its tier makes its guards
 * stronger (see {@code TownGuard.equip}), lets more of them turn out, and raises their level.
 */
public final class RealmTiers {
    public static final int MAX_TIER = 5;
    /** Wealth a town keeps back after growing, so it can still pay for repairs. */
    public static final int RESERVE = 5;
    public static final int DEFENSE_PER_TIER = 3;
    private static final String[] NAMES = { "hamlet", "village", "town", "market town", "city" };

    private RealmTiers() {}

    /** A town's tier: 1 to {@link #MAX_TIER} (towns from before tiers count as 1). */
    public static int tier(RealmNode town) {
        return Mth.clamp(town.stat("tier"), 1, MAX_TIER);
    }

    public static String name(int tier) {
        return NAMES[Mth.clamp(tier, 1, MAX_TIER) - 1];
    }

    /** The wealth it takes to grow from {@code tier} to the next: 10 per tier (10, 20, 30, 40). */
    public static int cost(int tier) {
        return 10 * tier;
    }

    /** A town guard's level, for its town's tier: above a villager's (2), and one more per tier. */
    public static int guardLevel(int tier) {
        return 2 + tier;
    }

    /** Lets every kingdom town that can afford it grow a tier. Returns how many grew. */
    public static int grow(Realm realm) {
        int grown = 0;
        for (RealmNode town : realm.nodes()) {
            int tier = tier(town);
            boolean rebuilding = town.stat("wrecked") > town.stat("repair") || town.stat("damage") > 0;
            if (town.type != RealmNode.Type.TOWN || !town.kingdoms() || tier >= MAX_TIER || rebuilding
                    || town.stat("wealth") < cost(tier) + RESERVE) {
                continue;
            }
            realm.addStat(town, "wealth", -cost(tier));
            realm.setStat(town, "tier", tier + 1);
            realm.addStat(town, "defense", DEFENSE_PER_TIER);
            realm.news(town.name() + " has grown into a " + name(tier + 1) + " (tier " + (tier + 1) + "): its guards grow stronger.");
            grown++;
        }
        return grown;
    }
}
