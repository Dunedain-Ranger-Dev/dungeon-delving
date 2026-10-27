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
 * {@link #TIER_LOSS_MARGIN} or more, a town also falls back a tier, down to ruins (tier 0).</li>
 * <li>Defenders win: the attackers lose military, more for a bigger margin, and the town's morale rises.</li>
 * </ul>
 * A raid can also be to take a place ({@link #capturable}): a stronghold or site the nation doesn't hold, or a town
 * that's been knocked down to ruins. Won, the attackers take it and leave a garrison of up to {@link #GARRISON} there
 * (a stronghold's military, a town's defense); lost, they lose military as usual. A stronghold's or site's defense is
 * its fortification, plus the army's share if it's the kingdom's.
 * <p>
 * The kingdom raids too: its army marches from one of its towns on a town a monster nation holds (the raid's nation
 * is {@link RealmNode#KINGDOM}), attacking with half the army. Those are always rolled.
 * <p>
 * With a player at the target when dusk comes, it's fought instead ({@link RealmLiveRaids}), and the damage each side
 * does decides it the same way.
 */
public final class RealmRaids {
    /** The share of the kingdom's army that turns out for a raided town: a quarter. */
    public static final int ARMY_SHARE = 4;
    /** A sacking by this much or more knocks a town back a tier (DD-99). */
    public static final int TIER_LOSS_MARGIN = 10;
    /** The most military a nation leaves to hold a node it captures. */
    public static final int GARRISON = 5;

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
            boolean fightable = server != null && town.isPresent() && !RealmNode.KINGDOM.equals(raid.nation());
            net.minecraft.server.level.ServerLevel level = fightable ? server.getLevel(town.get().dimension()) : null;
            if (level != null && level.isLoaded(town.get().pos()) && RealmLiveRaids.playerAt(level, town.get().pos(), RealmLiveRaids.PRESENCE)) {
                RealmLiveRaids.start(realm, raid, level);
            } else {
                resolve(realm, raid, 1 + random.nextInt(20), 1 + random.nextInt(20));
            }
        }
        return due.size();
    }

    /**
     * Declares a raid at dusk {@code resolveAt} (real time). On a town a monster nation holds: by the kingdom, from the
     * strongest kingdom town linked to it. On any other town, or a stronghold or site to take it: by the strongest
     * monster stronghold linked to it, or for a lost stronghold with none, the nearest pushed-back capital's champion.
     * Empty if nothing can attack it, or a raid's already on it.
     */
    public static Optional<Realm.Raid> declare(Realm realm, String targetId, long resolveAt) {
        Optional<RealmNode> target = realm.node(targetId).filter(node -> node.type == RealmNode.Type.TOWN || ((node.type == RealmNode.Type.STRONGHOLD || node.type == RealmNode.Type.SITE) && !node.monsters()));
        if (target.isEmpty() || realm.raids.stream().anyMatch(raid -> raid.town().equals(targetId))) {
            return Optional.empty();
        }
        if (target.get().type == RealmNode.Type.TOWN && target.get().monsters()) {
            Optional<RealmNode> from = realm.neighbors(targetId).stream().filter(node -> node.type == RealmNode.Type.TOWN && node.kingdoms())
                    .max(java.util.Comparator.comparingInt(node -> node.stat("defense")));
            if (from.isEmpty()) {
                return Optional.empty();
            }
            Realm.Raid raid = new Realm.Raid(RealmNode.KINGDOM, from.get().id, targetId, realm.turn, resolveAt);
            realm.raids.add(raid);
            realm.news(who(raid, from.get()) + " will " + RealmTurns.raidVerb(target.get(), RealmNode.KINGDOM) + " at dusk!");
            return Optional.of(raid);
        }
        Optional<RealmNode> attacker = realm.neighbors(targetId).stream().filter(node -> node.type == RealmNode.Type.STRONGHOLD && node.monsters())
                .max(java.util.Comparator.comparingInt(node -> node.stat("military")));
        if (attacker.isEmpty() && target.get().type == RealmNode.Type.STRONGHOLD) {
            attacker = realm.nodes().stream().filter(node -> node.type == RealmNode.Type.CAPITAL && RealmCapitals.pushedBack(realm, node.owner())
                    && node.dimension().equals(target.get().dimension())).min(java.util.Comparator.comparingDouble(node -> node.pos().distSqr(target.get().pos())));
        }
        if (attacker.isEmpty()) {
            return Optional.empty();
        }
        Realm.Raid raid = new Realm.Raid(attacker.get().owner(), attacker.get().id, targetId, realm.turn, resolveAt);
        realm.raids.add(raid);
        realm.news(who(raid, attacker.get()) + " will " + RealmTurns.raidVerb(target.get(), raid.nation()) + " at dusk!");
        return Optional.of(raid);
    }

    /** Who's attacking, for the news: "The goblins of warren", or "The kingdom's army from riverhold". */
    static String who(Realm.Raid raid, RealmNode attacker) {
        return RealmNode.KINGDOM.equals(raid.nation()) ? "The kingdom's army from " + attacker.name() : "The " + raid.nation() + " of " + attacker.name();
    }

    /**
     * Whether a raid by {@code by} (the kingdom, or a monster nation) can take a node: a stronghold or site no monster
     * nation holds (monsters only; the kingdom takes those by clearing them), or a town in ruins (tier 0) held by the
     * other side, or no one. A town above ruins can only be sacked.
     */
    public static boolean capturable(RealmNode node, String by) {
        boolean monsters = !RealmNode.KINGDOM.equals(by);
        return switch (node.type) {
            case STRONGHOLD, SITE -> monsters && !node.monsters();
            case TOWN -> RealmTiers.tier(node) == 0 && (monsters ? !node.monsters() : !node.kingdoms());
            default -> false;
        };
    }

    /** A node's own defense: a town's defense, or anything else's fortification. */
    public static int ownDefense(RealmNode node) {
        return node.stat(node.type == RealmNode.Type.TOWN ? "defense" : "fortification");
    }

    /** The defense a raid's target puts up, before its roll: its own, plus the army's share if it's the kingdom's. */
    public static int defense(Realm realm, RealmNode target) {
        return ownDefense(target) + (target.kingdoms() ? realm.kingdomStat("army") / ARMY_SHARE : 0);
    }

    /** Resolves one raid with these rolls (a d20 each), and takes it off the list. Returns whether the attackers won. */
    public static boolean resolve(Realm realm, Realm.Raid raid, int attackRoll, int defenseRoll) {
        Optional<RealmNode> town = realm.node(raid.town());
        Optional<RealmNode> stronghold = realm.node(raid.stronghold());
        int attack = stronghold.map(node -> attack(realm, raid, node)).orElse(0) + attackRoll;
        int defense = town.map(node -> defense(realm, node)).orElse(0) + defenseRoll;
        return decide(realm, raid, attack, defense, false);
    }

    /** A raid's attack, before its roll: the stronghold's military (+ the champion's bonus from a capital), or half the kingdom's army. */
    public static int attack(Realm realm, Realm.Raid raid, RealmNode attacker) {
        if (RealmNode.KINGDOM.equals(raid.nation())) {
            return realm.kingdomStat("army") / 2;
        }
        return attacker.stat("military") + (attacker.type == RealmNode.Type.CAPITAL ? RealmCapitals.CHAMPION_BONUS : 0);
    }

    /** Takes up to {@code amount} military from a raid's attackers (the kingdom's army, for its raids); returns how much. */
    private static int lose(Realm realm, Realm.Raid raid, RealmNode attacker, int amount) {
        boolean kingdom = RealmNode.KINGDOM.equals(raid.nation());
        int lost = Math.min(kingdom ? realm.kingdomStat("army") : attacker.stat("military"), amount);
        if (kingdom) {
            realm.addKingdomStat("army", -lost);
        } else {
            realm.addStat(attacker, "military", -lost);
        }
        return lost;
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
        if (attack > defense && capturable(target, raid.nation())) {
            int garrison = lose(realm, raid, attacker, GARRISON);
            realm.news(who(raid, attacker) + " stormed " + target.name() + how + " (" + attack + " against " + defense + ").");
            realm.setOwner(target, raid.nation());
            if (target.type == RealmNode.Type.TOWN) {
                realm.setStat(target, "upgrading", 0);
                realm.addStat(target, "defense", garrison);
            } else {
                realm.setStat(target, "military", garrison);
            }
            if (attacker.type == RealmNode.Type.CAPITAL) {
                RealmCapitals.championRaidDecided(realm, attacker, true);
            }
            return true;
        }
        if (attack > defense) {
            int margin = attack - defense;
            int defenseLost = Math.min(target.stat("defense"), Math.max(1, margin / 4));
            int wealthLost = Math.min(target.stat("wealth"), 2 + margin / 4);
            int moraleLost = Math.min(target.stat("morale"), 2);
            realm.addStat(target, "defense", -defenseLost);
            realm.addStat(target, "wealth", -wealthLost);
            realm.addStat(target, "morale", -moraleLost);
            realm.addStat(target, "damage", margin);
            lose(realm, raid, attacker, 1);
            realm.news(who(raid, attacker) + " sacked " + target.name() + how + " (" + attack + " against " + defense
                    + "): it lost " + defenseLost + " defense, " + wealthLost + " wealth and " + moraleLost + " morale.");
            if (attacker.type == RealmNode.Type.CAPITAL) {
                RealmCapitals.championRaidDecided(realm, attacker, true);
            }
            int tier = RealmTiers.tier(target);
            if (margin >= TIER_LOSS_MARGIN && tier > 0) {
                realm.setStat(target, "tier", tier - 1);
                realm.setStat(target, "upgrading", 0);
                realm.news(tier == 1 ? target.name() + " was so badly sacked it fell to ruins: whoever beats its defenders next can take it."
                        : target.name() + " was so badly sacked it fell back to a " + RealmTiers.name(tier - 1) + " (tier " + (tier - 1) + ").");
            }
            return true;
        }
        int losses = lose(realm, raid, attacker, 2 + (defense - attack) / 4);
        if (target.type == RealmNode.Type.TOWN) {
            realm.addStat(target, "morale", 1);
        }
        realm.news(target.name() + " drove off " + who(raid, attacker).replaceFirst("^The", "the") + how + " (" + defense + " against " + attack + "): they lost "
                + losses + " military.");
        return false;
    }
}
