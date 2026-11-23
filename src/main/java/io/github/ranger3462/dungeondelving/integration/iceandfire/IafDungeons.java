package io.github.ranger3462.dungeondelving.integration.iceandfire;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.iafenvoy.iceandfire.data.TrollType;
import com.iafenvoy.iceandfire.entity.DragonBaseEntity;
import com.iafenvoy.iceandfire.entity.TrollEntity;
import com.iafenvoy.iceandfire.registry.IafTrollTypes;

import io.github.ranger3462.dungeondelving.DungeonDelving;
import io.github.ranger3462.dungeondelving.dungeon.DungeonDimension;
import io.github.ranger3462.dungeondelving.dungeon.DungeonFactions;
import io.github.ranger3462.dungeondelving.dungeon.DungeonRegions;
import io.github.ranger3462.dungeondelving.dungeon.challenge.DungeonBosses;
import io.github.ranger3462.dungeondelving.dungeon.challenge.DungeonProgress;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Ice and Fire in the dungeons (DD-58), only loaded when it's installed, so nothing outside this package may refer to
 * these classes directly:
 * <ul>
 * <li>Its dragons spawn in dungeons as hatchlings, small enough for the rooms, among the tier 3 and 4 kobolds.</li>
 * <li>The kobolds' tier 3 and 4 boss is the Wyrmling Nest: a young brood guardian, with hatchlings coming in waves
 * until it falls.</li>
 * <li>Its trolls take their faction's kind (forest, frost or mountain), each led by a troll king.</li>
 * <li>The mythical beasts' boss is its Hydra.</li>
 * </ul>
 */
public final class IafDungeons {
    public static final String WYRMLING_NEST = "wyrmling_nest";
    public static final String FOREST_TROLL_KING = "forest_troll_king";
    public static final String FROST_TROLL_KING = "frost_troll_king";
    public static final String MOUNTAIN_TROLL_KING = "mountain_troll_king";
    public static final String HYDRA = "hydra";
    public static final List<String> DRAGONS = List.of("fire_dragon", "ice_dragon", "lightning_dragon");
    /** How old (in Ice and Fire days) dungeon hatchlings are: young enough to fit the rooms. */
    public static final int HATCHLING_MIN_DAYS = 8;
    public static final int HATCHLING_MAX_DAYS = 18;
    /** The Wyrmling Nest's guardian's age at tiers 3 and 4: older than its brood, still small enough for a boss room. */
    public static final int GUARDIAN_DAYS_T3 = 35;
    public static final int GUARDIAN_DAYS_T4 = 45;
    /** The nest sends this many hatchlings every {@link #WAVE_TICKS}, while fewer than {@link #MAX_BROOD} of them are alive. */
    public static final int WAVE_SIZE = 2;
    public static final int WAVE_TICKS = 20 * 20;
    public static final int MAX_BROOD = 6;
    /** Saved on mobs this integration has already set up, so a reload doesn't redo it. */
    private static final String SET_UP = DungeonDelving.MODID + ":iaf_set_up";

    /** A nest being fought: its guardian, where it is, and the hatchlings it has sent. */
    private record Nest(ResourceKey<Level> dimension, BlockPos pos, int tier, List<UUID> brood) {}

    private static final Map<UUID, Nest> NESTS = new HashMap<>();

    private IafDungeons() {}

    /** Called from the mod constructor when Ice and Fire is loaded. */
    public static void register() {
        DungeonBosses.register(new DungeonBosses.BossType(WYRMLING_NEST, DungeonFactions.KOBOLDS, tier -> tier >= 3, IafDungeons::createNestGuardian));
        DungeonBosses.addRetinue(WYRMLING_NEST, (level, pos, tier) -> brood(level, pos, tier, 3, null));
        DungeonBosses.register(new DungeonBosses.BossType(FOREST_TROLL_KING, DungeonFactions.FOREST_TROLLS, tier -> tier == 2,
                (level, pos, tier) -> createTrollKing(level, pos, tier, IafTrollTypes.FOREST, FOREST_TROLL_KING)));
        DungeonBosses.register(new DungeonBosses.BossType(FROST_TROLL_KING, DungeonFactions.FROST_TROLLS, tier -> tier == 3,
                (level, pos, tier) -> createTrollKing(level, pos, tier, IafTrollTypes.FROST, FROST_TROLL_KING)));
        DungeonBosses.register(new DungeonBosses.BossType(MOUNTAIN_TROLL_KING, DungeonFactions.MOUNTAIN_TROLLS, tier -> tier == 3,
                (level, pos, tier) -> createTrollKing(level, pos, tier, IafTrollTypes.MOUNTAIN, MOUNTAIN_TROLL_KING)));
        DungeonBosses.register(new DungeonBosses.BossType(HYDRA, DungeonFactions.MYTHICAL_BEASTS, tier -> tier == 3, IafDungeons::createHydra));
        NeoForge.EVENT_BUS.addListener(IafDungeons::onJoin);
        NeoForge.EVENT_BUS.addListener(IafDungeons::onServerTick);
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent event) -> NESTS.clear());
    }

    private static EntityType<?> type(String path) {
        return BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.fromNamespaceAndPath("iceandfire", path));
    }

    private static <T extends Mob> T make(EntityType<T> type, ServerLevel level, BlockPos pos) {
        T mob = type.create(level);
        mob.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 180.0f, 0.0f);
        EventHooks.finalizeMobSpawn(mob, level, level.getCurrentDifficultyAt(pos), MobSpawnType.EVENT, null);
        mob.getPersistentData().putBoolean(SET_UP, true);
        return mob;
    }

    private static void name(Mob boss, String name) {
        boss.setCustomName(Component.translatable("entity.dungeondelving." + name).withStyle(ChatFormatting.GOLD));
        boss.setCustomNameVisible(true);
    }

    /** A dragon of a random kind, this many days old, going for the nearest player. */
    @SuppressWarnings("unchecked")
    public static DragonBaseEntity dragon(ServerLevel level, BlockPos pos, int days) {
        EntityType<DragonBaseEntity> type = (EntityType<DragonBaseEntity>) type(DRAGONS.get(level.getRandom().nextInt(DRAGONS.size())));
        DragonBaseEntity dragon = make(type, level, pos);
        dragon.setAgeInDays(days);
        hunt(level, dragon);
        return dragon;
    }

    /** Hatchlings around a nest; adds them to the nest's brood if there is one. */
    private static void brood(ServerLevel level, BlockPos pos, int tier, int count, Nest nest) {
        RandomSource random = level.getRandom();
        for (int i = 0; i < count; i++) {
            double angle = Math.PI * 2 * (i + random.nextDouble()) / count;
            BlockPos at = BlockPos.containing(pos.getX() + 0.5 + Math.cos(angle) * 4, pos.getY(), pos.getZ() + 0.5 + Math.sin(angle) * 4);
            DragonBaseEntity hatchling = dragon(level, at, HATCHLING_MIN_DAYS + 5 + random.nextInt(HATCHLING_MAX_DAYS - HATCHLING_MIN_DAYS));
            hatchling.setPersistenceRequired();
            level.addFreshEntity(hatchling);
            if (nest != null) {
                nest.brood().add(hatchling.getUUID());
            }
        }
    }

    /** The Wyrmling Nest's guardian: a young dragon, older than its brood, with a boss's health. */
    private static Mob createNestGuardian(ServerLevel level, BlockPos pos, int tier) {
        DragonBaseEntity guardian = dragon(level, pos, tier >= 4 ? GUARDIAN_DAYS_T4 : GUARDIAN_DAYS_T3);
        DungeonBosses.setBase(guardian, Attributes.MAX_HEALTH, 120.0 + 80.0 * (tier - 3));
        DungeonBosses.setBase(guardian, Attributes.FOLLOW_RANGE, 48.0);
        guardian.setHealth(guardian.getMaxHealth());
        name(guardian, WYRMLING_NEST);
        NESTS.put(guardian.getUUID(), new Nest(level.dimension(), pos.immutable(), tier, new ArrayList<>()));
        return guardian;
    }

    /** A troll king: a bigger, tougher troll of its faction's kind. */
    @SuppressWarnings("unchecked")
    private static Mob createTrollKing(ServerLevel level, BlockPos pos, int tier, TrollType kind, String name) {
        TrollEntity king = make((EntityType<TrollEntity>) type("troll"), level, pos);
        king.setTrollType(kind);
        DungeonBosses.setBase(king, Attributes.MAX_HEALTH, 80.0 + 60.0 * tier);
        DungeonBosses.setBase(king, Attributes.ATTACK_DAMAGE, 8.0 + 3.0 * tier);
        DungeonBosses.setBase(king, Attributes.ARMOR, 6.0 + 2.0 * tier);
        DungeonBosses.setBase(king, Attributes.KNOCKBACK_RESISTANCE, 0.8);
        DungeonBosses.setBase(king, Attributes.SCALE, 1.3);
        DungeonBosses.setBase(king, Attributes.FOLLOW_RANGE, 48.0);
        king.setHealth(king.getMaxHealth());
        name(king, name);
        hunt(level, king);
        return king;
    }

    /** The mythical beasts' boss: Ice and Fire's Hydra, with a boss's health. */
    @SuppressWarnings("unchecked")
    private static Mob createHydra(ServerLevel level, BlockPos pos, int tier) {
        Mob hydra = make((EntityType<Mob>) type("hydra"), level, pos);
        DungeonBosses.setBase(hydra, Attributes.MAX_HEALTH, 250.0);
        DungeonBosses.setBase(hydra, Attributes.FOLLOW_RANGE, 48.0);
        hydra.setHealth(hydra.getMaxHealth());
        name(hydra, HYDRA);
        hunt(level, hydra);
        return hydra;
    }

    /** Sets a mob on the nearest player who can be hunted. */
    private static void hunt(ServerLevel level, Mob mob) {
        if (level.getNearestPlayer(mob, 32) instanceof ServerPlayer target && !target.isCreative() && !target.isSpectator()) {
            mob.setTarget(target);
        }
    }

    /** Dungeon dragons that came from spawners grow down to hatchlings; dungeon trolls take their faction's kind. */
    private static void onJoin(EntityJoinLevelEvent event) {
        Entity entity = event.getEntity();
        if (!(event.getLevel() instanceof ServerLevel level) || level.dimension() != DungeonDimension.DUNGEON
                || entity.getPersistentData().getBoolean(SET_UP) || DungeonBosses.isBoss(entity)) {
            return;
        }
        if (entity instanceof DragonBaseEntity dragon && !dragon.isTame()) {
            dragon.setAgeInDays(HATCHLING_MIN_DAYS + level.getRandom().nextInt(HATCHLING_MAX_DAYS - HATCHLING_MIN_DAYS + 1));
            dragon.getPersistentData().putBoolean(SET_UP, true);
        } else if (entity instanceof TrollEntity troll) {
            TrollType kind = trollKind(level, troll.blockPosition());
            if (kind != null) {
                troll.setTrollType(kind);
            }
            troll.getPersistentData().putBoolean(SET_UP, true);
        }
    }

    /** The troll kind of the dungeon at a position, from its faction (null outside a troll dungeon). */
    public static TrollType trollKind(ServerLevel level, BlockPos pos) {
        String faction = DungeonRegions.get().progress(DungeonDimension.regionAt(pos)).map(DungeonProgress::faction).orElse(null);
        return trollKind(faction);
    }

    public static TrollType trollKind(String faction) {
        if (DungeonFactions.FOREST_TROLLS.equals(faction)) {
            return IafTrollTypes.FOREST;
        }
        if (DungeonFactions.FROST_TROLLS.equals(faction)) {
            return IafTrollTypes.FROST;
        }
        if (DungeonFactions.MOUNTAIN_TROLLS.equals(faction)) {
            return IafTrollTypes.MOUNTAIN;
        }
        return null;
    }

    /** Sends the next wave from each nest whose guardian still stands; forgets nests whose guardian is gone. */
    private static void onServerTick(ServerTickEvent.Post event) {
        if (NESTS.isEmpty() || event.getServer().getTickCount() % WAVE_TICKS != 0) {
            return;
        }
        Iterator<Map.Entry<UUID, Nest>> nests = NESTS.entrySet().iterator();
        while (nests.hasNext()) {
            Map.Entry<UUID, Nest> entry = nests.next();
            Nest nest = entry.getValue();
            ServerLevel level = event.getServer().getLevel(nest.dimension());
            Entity guardian = level == null ? null : level.getEntity(entry.getKey());
            if (guardian == null || !guardian.isAlive()) {
                nests.remove();
                continue;
            }
            nest.brood().removeIf(id -> level.getEntity(id) == null || !level.getEntity(id).isAlive());
            int room = MAX_BROOD - nest.brood().size();
            if (room > 0) {
                brood(level, nest.pos(), nest.tier(), Math.min(WAVE_SIZE, room), nest);
            }
        }
    }

    /** A dragon's age in Ice and Fire days, or -1 if it isn't one of its dragons (for tests). */
    public static int dragonAge(Entity entity) {
        return entity instanceof DragonBaseEntity dragon ? dragon.getAgeInDays() : -1;
    }

    /** A troll's kind, e.g. "forest", or null if it isn't one of its trolls (for tests). */
    public static String trollKindName(Entity entity) {
        return entity instanceof TrollEntity troll ? troll.getTrollType().getName() : null;
    }

    /** How many hatchlings the nest whose guardian this is has out (for tests); -1 if it isn't a nest guardian. */
    public static int broodSize(UUID guardian) {
        Nest nest = NESTS.get(guardian);
        return nest == null ? -1 : nest.brood().size();
    }

    /** Sends a nest's next wave now (for tests). */
    public static void sendWave(ServerLevel level, UUID guardian) {
        Nest nest = NESTS.get(guardian);
        if (nest != null) {
            brood(level, nest.pos(), nest.tier(), Math.min(WAVE_SIZE, MAX_BROOD - nest.brood().size()), nest);
        }
    }
}
