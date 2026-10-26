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
 * margin (its {@code damage} stat, for the blueprints in DD-97). The attackers lose a little military.</li>
 * <li>Defenders win: the attackers lose military, more for a bigger margin, and the town's morale rises.</li>
 * </ul>
 * Live raids, with a player there, come later (DD-98).
 */
public final class RealmRaids {
    /** The share of the kingdom's army that turns out for a raided town: a quarter. */
    public static final int ARMY_SHARE = 4;

    private RealmRaids() {}

    /** Resolves every raid due by real time {@code upTo}. Returns how many. */
    public static int resolveDue(Realm realm, long upTo, RandomSource random) {
        List<Realm.Raid> due = new ArrayList<>(realm.raids.stream().filter(raid -> raid.resolveAt() <= upTo).toList());
        for (Realm.Raid raid : due) {
            resolve(realm, raid, 1 + random.nextInt(20), 1 + random.nextInt(20));
        }
        return due.size();
    }

    /** The defense a town puts up against a raid, before its roll. */
    public static int defense(Realm realm, RealmNode town) {
        return town.stat("defense") + realm.kingdomStat("army") / ARMY_SHARE;
    }

    /** Resolves one raid with these rolls (a d20 each), and takes it off the list. Returns whether the attackers won. */
    public static boolean resolve(Realm realm, Realm.Raid raid, int attackRoll, int defenseRoll) {
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
        int attack = attacker.stat("military") + attackRoll;
        int defense = defense(realm, target) + defenseRoll;
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
            realm.news("The " + raid.nation() + " of " + attacker.name() + " sacked " + target.name() + " (" + attack + " against " + defense
                    + "): it lost " + defenseLost + " defense, " + wealthLost + " wealth and " + moraleLost + " morale.");
            return true;
        }
        int losses = Math.min(attacker.stat("military"), 2 + (defense - attack) / 4);
        realm.addStat(attacker, "military", -losses);
        realm.addStat(target, "morale", 1);
        realm.news(target.name() + " drove off the " + raid.nation() + " of " + attacker.name() + " (" + defense + " against " + attack + "): they lost "
                + losses + " military.");
        return false;
    }
}
