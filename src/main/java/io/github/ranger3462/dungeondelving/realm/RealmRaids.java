package io.github.ranger3462.dungeondelving.realm;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import net.minecraft.util.RandomSource;

/**
 * Raids resolved by a roll (DD-96, docs/design/realm.md). A raid declared at a turn's dawn resolves at its dusk, half a
 * turn later: the attacking stronghold's military plus a d20, against the town's defense, plus a share of the kingdom's
 * army, plus a d20.
 * <ul>
 * <li>Attackers win: the town loses defense, wealth and morale, more for a bigger margin, and is owed block damage by the
 * margin (its {@code damage} stat, for the blueprints in DD-97). The attackers lose a little military. Sacked by
 * {@link #TIER_LOSS_MARGIN} or more, a town above tier 1 also falls back a tier.</li>
 * <li>Defenders win: the attackers lose military, more for a bigger margin, and the town's morale rises.</li>
 * </ul>
 * With a player at the town when dusk comes, it's fought instead ({@link RealmLiveRaids}), and the damage each side does
 * decides it the same way.
 */
public final class RealmRaids {
    /** The share of the kingdom's army that turns out for a raided town: a quarter. */
    public static final int ARMY_SHARE = 4;
    /** A sacking by this much or more knocks a town back a tier (DD-99). */
    public static final int TIER_LOSS_MARGIN = 10;

    private RealmRaids() {}

    /** Rolls every raid due by real time {@code upTo} (not the ones being fought). Returns how many. */
    public static int resolveDue(Realm realm, long upTo, RandomSource random) {
        return resolveDue(null, realm, upTo, random);
    }

    /**
     * Resolves every raid due by real time {@code upTo}: fought live where a player is at the town (with a server to
     * fight it on), rolled everywhere else. Returns how many were rolled or started.
     */
    public static int resolveDue(net.minecraft.server.MinecraftServer server, Realm realm, long upTo, RandomSource random) {
        List<Realm.Raid> due = new ArrayList<>(realm.raids.stream().filter(raid -> raid.resolveAt() <= upTo && !RealmLiveRaids.isLive(raid)).toList());
        for (Realm.Raid raid : due) {
            Optional<RealmNode> town = realm.node(raid.town());
            net.minecraft.server.level.ServerLevel level = server == null || town.isEmpty() ? null : server.getLevel(town.get().dimension());
            if (level != null && level.isLoaded(town.get().pos()) && RealmLiveRaids.playerAt(level, town.get().pos(), RealmLiveRaids.PRESENCE)) {
                RealmLiveRaids.start(realm, raid, level);
            } else {
                resolve(realm, raid, 1 + random.nextInt(20), 1 + random.nextInt(20));
            }
        }
        return due.size();
    }

    /**
     * Declares a raid on a town by the strongest monster stronghold linked to it, at dusk {@code resolveAt} (real time).
     * Empty if no stronghold borders the town, or one's already raiding it.
     */
    public static Optional<Realm.Raid> declare(Realm realm, String townId, long resolveAt) {
        Optional<RealmNode> town = realm.node(townId).filter(node -> node.type == RealmNode.Type.TOWN);
        if (town.isEmpty() || realm.raids.stream().anyMatch(raid -> raid.town().equals(townId))) {
            return Optional.empty();
        }
        Optional<RealmNode> stronghold = realm.neighbors(townId).stream().filter(node -> node.type == RealmNode.Type.STRONGHOLD && node.monsters())
                .max(java.util.Comparator.comparingInt(node -> node.stat("military")));
        if (stronghold.isEmpty()) {
            return Optional.empty();
        }
        Realm.Raid raid = new Realm.Raid(stronghold.get().owner(), stronghold.get().id, townId, realm.turn, resolveAt);
        realm.raids.add(raid);
        realm.news("The " + raid.nation() + " of " + stronghold.get().name() + " will raid " + town.get().name() + " at dusk!");
        return Optional.of(raid);
    }

    /** The defense a town puts up against a raid, before its roll. */
    public static int defense(Realm realm, RealmNode town) {
        return town.stat("defense") + realm.kingdomStat("army") / ARMY_SHARE;
    }

    /** Resolves one raid with these rolls (a d20 each), and takes it off the list. Returns whether the attackers won. */
    public static boolean resolve(Realm realm, Realm.Raid raid, int attackRoll, int defenseRoll) {
        Optional<RealmNode> town = realm.node(raid.town());
        Optional<RealmNode> stronghold = realm.node(raid.stronghold());
        int attack = stronghold.map(node -> node.stat("military")).orElse(0) + attackRoll;
        int defense = town.map(node -> defense(realm, node)).orElse(0) + defenseRoll;
        return decide(realm, raid, attack, defense, false);
    }

    /**
     * Settles a raid on its attack and defense (a roll's totals, or a fight's scores), and takes it off the list:
     * attackers ahead sack the town, by the margin; otherwise they're driven off. Returns whether the attackers won.
     */
    public static boolean decide(Realm realm, Realm.Raid raid, int attack, int defense, boolean fought) {
        realm.raids.remove(raid);
        Optional<RealmNode> town = realm.node(raid.town());
        Optional<RealmNode> stronghold = realm.node(raid.stronghold());
        if (town.isEmpty() || stronghold.isEmpty()) {
            realm.record("The " + raid.nation() + "' raid on " + raid.town() + " came to nothing.");
            realm.setDirty();
            return false;
        }
        RealmNode target = town.get();
        RealmNode attacker = stronghold.get();
        String how = fought ? " in a pitched fight" : "";
        if (attack > defense) {
            int margin = attack - defense;
            int defenseLost = Math.min(target.stat("defense"), Math.max(1, margin / 4));
            int wealthLost = Math.min(target.stat("wealth"), 2 + margin / 4);
            int moraleLost = Math.min(target.stat("morale"), 2);
            realm.addStat(target, "defense", -defenseLost);
            realm.addStat(target, "wealth", -wealthLost);
            realm.addStat(target, "morale", -moraleLost);
            realm.addStat(target, "damage", margin);
            realm.addStat(attacker, "military", -Math.min(attacker.stat("military"), 1));
            realm.news("The " + raid.nation() + " of " + attacker.name() + " sacked " + target.name() + how + " (" + attack + " against " + defense
                    + "): it lost " + defenseLost + " defense, " + wealthLost + " wealth and " + moraleLost + " morale.");
            int tier = RealmTiers.tier(target);
            if (margin >= TIER_LOSS_MARGIN && tier > 1) {
                realm.setStat(target, "tier", tier - 1);
                realm.news(target.name() + " was so badly sacked it fell back to a " + RealmTiers.name(tier - 1) + " (tier " + (tier - 1) + ").");
            }
            return true;
        }
        int losses = Math.min(attacker.stat("military"), 2 + (defense - attack) / 4);
        realm.addStat(attacker, "military", -losses);
        realm.addStat(target, "morale", 1);
        realm.news(target.name() + " drove off the " + raid.nation() + " of " + attacker.name() + how + " (" + defense + " against " + attack + "): they lost "
                + losses + " military.");
        return false;
    }
}
