package io.github.ranger3462.dungeondelving.realm;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import io.github.ranger3462.dungeondelving.DungeonDelving;
import io.github.ranger3462.dungeondelving.dungeon.SpawnerMobs;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Live raids (DD-98, docs/design/realm.md). When a raid's dusk comes and a player is at the town, it isn't rolled: it's
 * fought. The attacking nation's mobs come in {@link #WAVES} waves from all round the town (its dungeon faction's mobs,
 * of a tier by the stronghold's military), against the town's guards (iron golems, for now, more for a better defense)
 * and whoever's there.
 * <p>
 * The damage each side does decides it: what the raiders deal to guards, villagers and players, plus what's left of
 * them when time runs out, against what they take. Beat every wave and the town holds; run out the clock behind, and it's
 * sacked, as a rolled raid would be (with block damage by the margin). A player can fight for either side: hurting the
 * guards counts for the raiders (and is noted: standing will cost them, later). If every player leaves, the raid is
 * rolled after all.
 */
@EventBusSubscriber(modid = DungeonDelving.MODID)
public final class RealmLiveRaids {
    public static final int WAVES = 3;
    /** How near a player must be (blocks) for a raid to be fought, not rolled; and how far they can go before it's abandoned. */
    public static final int PRESENCE = 48;
    public static final int ABANDON = PRESENCE + 32;
    /** Where the raiders come from: this far out from the town's middle. */
    public static final int RING = 24;
    /** How long a fight can last (ticks), and how long without a player before it's rolled instead. */
    public static final int TIME_LIMIT = 20 * 60 * 5;
    public static final int ALONE_LIMIT = 20 * 30;
    /** Scores are scaled down by this before they're weighed like a rolled raid's numbers. */
    public static final int SCORE_SCALE = 5;
    private static final String RAIDER = DungeonDelving.MODID + ":raider";

    private static final Map<String, LiveRaid> ACTIVE = new HashMap<>();

    /** One raid being fought. */
    public static final class LiveRaid {
        final Realm.Raid raid;
        final ServerLevel level;
        final BlockPos center;
        final int tier;
        final int waveSize;
        int wave;
        final Set<UUID> raiders = new HashSet<>();
        final Set<UUID> guards = new HashSet<>();
        final Set<UUID> turncoats = new HashSet<>();
        float attackScore;
        float defenseScore;
        int age;
        int alone;

        LiveRaid(Realm.Raid raid, ServerLevel level, BlockPos center, int tier, int waveSize) {
            this.raid = raid;
            this.level = level;
            this.center = center;
            this.tier = tier;
            this.waveSize = waveSize;
        }

        public int wave() {
            return wave;
        }

        public float attackScore() {
            return attackScore;
        }

        public float defenseScore() {
            return defenseScore;
        }

        public Set<UUID> raiders() {
            return raiders;
        }
    }

    private RealmLiveRaids() {}

    public static boolean isLive(Realm.Raid raid) {
        LiveRaid live = ACTIVE.get(raid.town());
        return live != null && live.raid.equals(raid);
    }

    public static Optional<LiveRaid> live(String town) {
        return Optional.ofNullable(ACTIVE.get(town));
    }

    /** Whether a player is near enough to a town to fight its raid. */
    public static boolean playerAt(ServerLevel level, BlockPos center, int range) {
        return level.players().stream().anyMatch(player -> !player.isSpectator() && player.blockPosition().closerThan(center, range));
    }

    /** A stronghold's raiding tier, by its military: 1 below 15, up to 4 from 45. */
    public static int tier(int military) {
        return Mth.clamp(1 + military / 15, 1, 4);
    }

    /** Starts fighting a raid at its town: guards turn out and the first wave comes. */
    public static LiveRaid start(Realm realm, Realm.Raid raid, ServerLevel level) {
        RealmNode town = realm.node(raid.town()).orElseThrow();
        int military = realm.node(raid.stronghold()).map(node -> node.stat("military")).orElse(10);
        LiveRaid live = new LiveRaid(raid, level, town.pos(), tier(military), Mth.clamp(3 + military / 8, 3, 12));
        ACTIVE.put(raid.town(), live);
        int guards = Mth.clamp(RealmRaids.defense(realm, town) / 8, 1, 4);
        for (int i = 0; i < guards; i++) {
            IronGolem golem = EntityType.IRON_GOLEM.spawn(level, surface(level, town.pos(), 4, level.getRandom()), MobSpawnType.EVENT);
            if (golem != null) {
                golem.setPersistenceRequired();
                live.guards.add(golem.getUUID());
            }
        }
        realm.news(town.name() + " is under attack by the " + raid.nation() + "! Its guards turn out.");
        nextWave(realm, live);
        return live;
    }

    private static void nextWave(Realm realm, LiveRaid live) {
        live.wave++;
        RandomSource random = live.level.getRandom();
        for (int i = 0; i < live.waveSize; i++) {
            Optional<net.minecraft.core.Holder<EntityType<?>>> type = SpawnerMobs.pick(live.level, random, live.raid.nation(), live.tier);
            if (type.isEmpty()) {
                continue;
            }
            float angle = random.nextFloat() * Mth.TWO_PI;
            BlockPos at = surface(live.level, live.center.offset((int) (Mth.cos(angle) * RING), 0, (int) (Mth.sin(angle) * RING)), 2, random);
            if (type.get().value().spawn(live.level, at, MobSpawnType.EVENT) instanceof Mob mob) {
                mob.setPersistenceRequired();
                mob.getPersistentData().putString(RAIDER, live.raid.town());
                live.raiders.add(mob.getUUID());
            }
        }
        realm.news("Wave " + live.wave + " of " + WAVES + " comes for " + realm.node(live.raid.town()).map(RealmNode::name).orElse(live.raid.town()) + ".");
    }

    /** A standing spot on the ground near {@code around}. */
    private static BlockPos surface(ServerLevel level, BlockPos around, int spread, RandomSource random) {
        int x = around.getX() + random.nextInt(spread * 2 + 1) - spread;
        int z = around.getZ() + random.nextInt(spread * 2 + 1) - spread;
        return new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (!ACTIVE.isEmpty() && event.getServer().getTickCount() % 20 == 10) {
            tick(event.getServer(), Realm.get(event.getServer()), 20);
        }
    }

    /** Moves every fight on by {@code ticks}: steers idle raiders, sends the next wave, ends fights that are won, lost or left. */
    public static void tick(MinecraftServer server, Realm realm, int ticks) {
        for (Iterator<LiveRaid> it = ACTIVE.values().iterator(); it.hasNext();) {
            LiveRaid live = it.next();
            live.age += ticks;
            live.alone = playerAt(live.level, live.center, ABANDON) ? 0 : live.alone + ticks;
            int standing = 0;
            for (UUID id : live.raiders) {
                if (live.level.getEntity(id) instanceof Mob mob && mob.isAlive()) {
                    standing++;
                    if (mob.getTarget() == null && !mob.blockPosition().closerThan(live.center, 4)) {
                        mob.getNavigation().moveTo(live.center.getX() + 0.5, live.center.getY(), live.center.getZ() + 0.5, 1.0);
                    }
                }
            }
            if (live.alone >= ALONE_LIMIT) {
                clear(live);
                it.remove();
                realm.record("Everyone left " + live.raid.town() + "'s fight: it was decided by the odds.");
                RealmRaids.resolve(realm, live.raid, 1 + live.level.getRandom().nextInt(20), 1 + live.level.getRandom().nextInt(20));
            } else if (standing == 0 && live.wave < WAVES) {
                nextWave(realm, live);
            } else if (standing == 0 || live.age >= TIME_LIMIT) {
                it.remove();
                finish(realm, live, standing);
            }
        }
        RealmTurns.tellNews(server, realm);
    }

    /** A fight is over: weigh the scores like a rolled raid's numbers, and clear the field. */
    static void finish(Realm realm, LiveRaid live, int raidersLeft) {
        float attack = live.attackScore + raidersLeft * 10;
        clear(live);
        int scaledAttack = Math.round(attack / SCORE_SCALE);
        int scaledDefense = Math.round(live.defenseScore / SCORE_SCALE);
        if (raidersLeft == 0) {
            scaledAttack = Math.min(scaledAttack, scaledDefense); // Every wave beaten: the town holds.
        }
        RealmRaids.decide(realm, live.raid, scaledAttack, scaledDefense, true);
        if (!live.turncoats.isEmpty()) {
            realm.news(live.turncoats.size() + (live.turncoats.size() == 1 ? " adventurer" : " adventurers") + " fought for the " + live.raid.nation()
                    + ". The kingdom won't forget.");
        }
    }

    private static void clear(LiveRaid live) {
        for (UUID id : live.raiders) {
            Entity entity = live.level.getEntity(id);
            if (entity != null) {
                entity.discard();
            }
        }
        for (UUID id : live.guards) {
            Entity entity = live.level.getEntity(id);
            if (entity != null) {
                entity.discard();
            }
        }
    }

    /** Counts damage for a fight: raiders hurt (the town's side), and raiders or turncoats hurting guards, villagers and players. */
    @SubscribeEvent
    public static void onDamage(LivingDamageEvent.Post event) {
        if (ACTIVE.isEmpty()) {
            return;
        }
        LivingEntity victim = event.getEntity();
        Entity attacker = event.getSource().getEntity();
        float damage = event.getNewDamage();
        for (LiveRaid live : ACTIVE.values()) {
            if (live.raiders.contains(victim.getUUID())) {
                live.defenseScore += damage;
            } else if (attacker != null && live.raiders.contains(attacker.getUUID()) && defender(live, victim)) {
                live.attackScore += damage;
            } else if (attacker instanceof ServerPlayer player && live.guards.contains(victim.getUUID())) {
                live.attackScore += damage;
                live.turncoats.add(player.getUUID());
            }
        }
    }

    private static boolean defender(LiveRaid live, LivingEntity entity) {
        return live.guards.contains(entity.getUUID()) || entity instanceof ServerPlayer || entity instanceof AbstractVillager;
    }

    /** Counts damage as if it were dealt in a fight, for tests. */
    public static void score(LiveRaid live, float attack, float defense) {
        live.attackScore += attack;
        live.defenseScore += defense;
    }

    /** Ends every fight at once, for tests. */
    public static void clearAll() {
        ACTIVE.values().forEach(RealmLiveRaids::clear);
        ACTIVE.clear();
    }

    /** The live raids' raiders, for tests. */
    public static List<Mob> raiders(LiveRaid live) {
        return live.raiders.stream().map(live.level::getEntity).filter(entity -> entity instanceof Mob).map(entity -> (Mob) entity).toList();
    }
}
