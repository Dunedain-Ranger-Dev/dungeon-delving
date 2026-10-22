package io.github.ranger3462.dungeondelving.dungeon.challenge;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.IntPredicate;

import io.github.ranger3462.dungeondelving.DungeonDelving;
import io.github.ranger3462.dungeondelving.dungeon.DungeonDimension;
import io.github.ranger3462.dungeondelving.dungeon.DungeonFactions;
import io.github.ranger3462.dungeondelving.dungeon.DungeonRegions;
import io.github.ranger3462.dungeondelving.dungeon.DungeonRun;
import io.github.ranger3462.dungeondelving.entity.SkeletonLord;
import io.github.ranger3462.dungeondelving.entity.ZombieLord;
import io.github.ranger3462.dungeondelving.registry.ModEntities;
import io.github.ranger3462.dungeondelving.registry.ModRegistries;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.WitherSkeleton;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Dungeon bosses. Each run's boss is picked from the boss types that guard its tier (see {@link #register}), and
 * fights in its own arena when there is one ({@code boss_<boss>_*} pieces). Each boss drops its own loot table,
 * {@code dungeondelving:bosses/<boss>/tier_<n>}, in place of its normal drops, and shows a boss bar to players nearby.
 * When it dies, its arena's spawners shut off and an exit appears just inside the boss room's door.
 * <p>
 * The undead's bosses: the Zombie Lord (tier 1), the Skeleton Lord (tier 2), the Barrow King (a big, buffed wither
 * skeleton, tier 3) and, with Iron's Spells, the Lich Lord (tier 4; the Barrow King guards tier 4 without it). More
 * bosses come from integrations (e.g. the Goblin Shaman Chief with Iron's Spells).
 */
@EventBusSubscriber(modid = DungeonDelving.MODID)
public final class DungeonBosses {
    /** Saved on bosses: which boss it is, its tier and region. */
    public static final String BOSS_TAG = DungeonDelving.MODID + ":boss";
    public static final String BARROW_KING = "barrow_king";
    public static final String SKELETON_LORD = "skeleton_lord";
    public static final String ZOMBIE_LORD = "zombie_lord";
    /** Registered by the Iron's Spells integration; without it, the Barrow King guards tier 4 as well. */
    public static final String LICH_LORD = "lich_lord";
    /** How close players must be to see a boss's health bar. */
    private static final double BAR_RANGE = 48.0;
    /** How far inside the boss room's door the exit appears. */
    private static final int EXIT_INSET = 2;

    private static final Map<UUID, ServerBossEvent> BARS = new HashMap<>();

    private DungeonBosses() {}

    /** The loot table a boss drops in a dungeon of this tier. */
    public static ResourceKey<LootTable> lootTable(String boss, int tier) {
        return ResourceKey.create(Registries.LOOT_TABLE, DungeonDelving.id("bosses/" + boss + "/tier_" + DungeonRun.clampTier(tier)));
    }

    /** A kind of boss: its faction, which tiers it can guard, and how to make one (without adding it to the world). */
    public record BossType(String id, String faction, IntPredicate tiers, Spawner spawner) {}

    @FunctionalInterface
    public interface Spawner {
        Mob create(ServerLevel level, BlockPos pos, int tier);
    }

    /** Mobs that spawn alongside a boss, e.g. its guards. */
    @FunctionalInterface
    public interface Retinue {
        void spawn(ServerLevel level, BlockPos pos, int tier);
    }

    private static final Map<String, BossType> TYPES = new LinkedHashMap<>();
    private static final Map<String, List<Retinue>> RETINUES = new HashMap<>();
    /** Skeleton knights guarding the Barrow King. */
    public static final int BARROW_KING_GUARDS = 3;

    static {
        register(new BossType(ZOMBIE_LORD, DungeonFactions.UNDEAD, tier -> tier == 1, ZombieLord::create));
        register(new BossType(SKELETON_LORD, DungeonFactions.UNDEAD, tier -> tier == 2, SkeletonLord::create));
        register(new BossType(BARROW_KING, DungeonFactions.UNDEAD, tier -> tier == 3 || (tier == 4 && !TYPES.containsKey(LICH_LORD)),
                DungeonBosses::createBarrowKing));
        addRetinue(SKELETON_LORD, (level, pos, tier) -> ring(level, pos, ModEntities.SKELETON_KNIGHT.get(), SkeletonLord.GUARDS, 3.0, 0.0));
        addRetinue(BARROW_KING, (level, pos, tier) -> ring(level, pos, ModEntities.SKELETON_KNIGHT.get(), BARROW_KING_GUARDS, 3.0, 0.0));
    }

    /** Adds a boss type, e.g. from an optional integration. */
    public static void register(BossType type) {
        TYPES.put(type.id(), type);
    }

    /** Adds mobs that spawn with a boss, e.g. from an optional integration. */
    public static void addRetinue(String boss, Retinue retinue) {
        RETINUES.computeIfAbsent(boss, id -> new ArrayList<>()).add(retinue);
    }

    /**
     * Spawns {@code count} mobs of a type in a ring around a position, persistent and facing the way in. {@code turn}
     * rotates the ring (as a fraction of a full turn), so two rings don't overlap.
     */
    public static void ring(ServerLevel level, BlockPos pos, EntityType<? extends Mob> type, int count, double radius, double turn) {
        for (int i = 0; i < count; i++) {
            double angle = Math.PI * 2 * (i + turn) / count;
            Mob mob = type.create(level);
            if (mob == null) {
                continue;
            }
            mob.moveTo(pos.getX() + 0.5 + Math.cos(angle) * radius, pos.getY(), pos.getZ() + 0.5 + Math.sin(angle) * radius, 180.0f, 0.0f);
            EventHooks.finalizeMobSpawn(mob, level, level.getCurrentDifficultyAt(pos), MobSpawnType.EVENT, null);
            mob.setPersistenceRequired();
            level.addFreshEntity(mob);
        }
    }

    public static Set<String> bossIds() {
        return Collections.unmodifiableSet(TYPES.keySet());
    }

    /** Picks the boss for a dungeon of this tier from any faction: any boss that guards the tier, or the Barrow King. */
    public static String pick(int tier, RandomSource random) {
        return pick(tier, null, random);
    }

    /** Picks the boss for a dungeon of this tier held by this faction (any faction if null), or the Barrow King if none fits. */
    public static String pick(int tier, String faction, RandomSource random) {
        List<BossType> candidates = TYPES.values().stream()
                .filter(type -> type.tiers().test(tier) && (faction == null || type.faction().equals(faction))).toList();
        return candidates.isEmpty() ? BARROW_KING : candidates.get(random.nextInt(candidates.size())).id();
    }

    /** Whether a faction has a boss for this tier. */
    public static boolean guards(String faction, int tier) {
        return TYPES.values().stream().anyMatch(type -> type.faction().equals(faction) && type.tiers().test(tier));
    }

    /** Spawns the Barrow King for a dungeon of this tier. */
    public static LivingEntity spawn(ServerLevel level, BlockPos pos, int tier, int region) {
        return spawn(level, pos, tier, region, BARROW_KING);
    }

    /**
     * Spawns a boss for a dungeon of this tier: tagged as a boss (for its loot table and boss bar), persistent, and
     * rallying its arena's spawners.
     */
    public static LivingEntity spawn(ServerLevel level, BlockPos pos, int tier, int region, String boss) {
        BossType type = TYPES.getOrDefault(boss, TYPES.get(BARROW_KING));
        Mob mob = type.spawner().create(level, pos, tier);
        mob.setPersistenceRequired();
        CompoundTag tag = new CompoundTag();
        tag.putString("id", type.id());
        tag.putInt("tier", tier);
        tag.putInt("region", region);
        mob.getPersistentData().put(BOSS_TAG, tag);
        level.addFreshEntity(mob);
        RETINUES.getOrDefault(type.id(), List.of()).forEach(retinue -> retinue.spawn(level, pos, tier));
        DungeonChallenges.rallySpawners(level, DungeonDimension.roomMinAt(pos), tier);
        return mob;
    }

    /** The Barrow King: a big, buffed wither skeleton in a gold crown, with his court (see {@link #addRetinue}). */
    private static Mob createBarrowKing(ServerLevel level, BlockPos pos, int tier) {
        WitherSkeleton king = EntityType.WITHER_SKELETON.create(level);
        king.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0.0f, 0.0f);
        EventHooks.finalizeMobSpawn(king, level, level.getCurrentDifficultyAt(pos), MobSpawnType.EVENT, null);
        setBase(king, Attributes.MAX_HEALTH, 50.0 + 50.0 * tier);
        setBase(king, Attributes.ATTACK_DAMAGE, 6.0 + 2.0 * tier);
        setBase(king, Attributes.ARMOR, 4.0 + 2.0 * tier);
        setBase(king, Attributes.KNOCKBACK_RESISTANCE, 0.6);
        setBase(king, Attributes.SCALE, 1.4);
        setBase(king, Attributes.FOLLOW_RANGE, 48.0);
        king.setHealth(king.getMaxHealth());
        king.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(tier >= 3 ? Items.NETHERITE_SWORD : Items.DIAMOND_SWORD));
        king.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.GOLDEN_HELMET));
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            king.setDropChance(slot, 0.0f);
        }
        king.setCustomName(Component.translatable("entity.dungeondelving.barrow_king").withStyle(ChatFormatting.GOLD));
        king.setCustomNameVisible(true);
        return king;
    }

    /** Sets an attribute's base value, if the mob has it. */
    public static void setBase(LivingEntity entity, Holder<Attribute> attribute, double value) {
        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance != null) {
            instance.setBaseValue(value);
        }
    }

    public static boolean isBoss(Entity entity) {
        return entity.getPersistentData().contains(BOSS_TAG);
    }

    /** Replaces a boss's drops with its own loot table. */
    @SubscribeEvent
    public static void onDrops(LivingDropsEvent event) {
        LivingEntity boss = event.getEntity();
        if (!(boss.level() instanceof ServerLevel level) || !isBoss(boss)) {
            return;
        }
        CompoundTag tag = boss.getPersistentData().getCompound(BOSS_TAG);
        LootTable table = level.getServer().reloadableRegistries().getLootTable(lootTable(tag.getString("id"), tag.getInt("tier")));
        LootParams params = new LootParams.Builder(level)
                .withParameter(LootContextParams.THIS_ENTITY, boss)
                .withParameter(LootContextParams.ORIGIN, boss.position())
                .withParameter(LootContextParams.DAMAGE_SOURCE, event.getSource())
                .withOptionalParameter(LootContextParams.ATTACKING_ENTITY, event.getSource().getEntity())
                .withOptionalParameter(LootContextParams.DIRECT_ATTACKING_ENTITY, event.getSource().getDirectEntity())
                .withOptionalParameter(LootContextParams.LAST_DAMAGE_PLAYER,
                        event.getSource().getEntity() instanceof ServerPlayer player ? player : null)
                .create(LootContextParamSets.ENTITY);
        event.getDrops().clear();
        for (ItemStack stack : table.getRandomItems(params)) {
            ItemEntity drop = new ItemEntity(level, boss.getX(), boss.getY() + 0.5, boss.getZ(), stack);
            drop.setUnlimitedLifetime();
            event.getDrops().add(drop);
        }
        DungeonChallenges.tellRegion(level, tag.getInt("region"),
                Component.translatable("message.dungeondelving.boss_defeated", boss.getDisplayName()).withStyle(ChatFormatting.GOLD));
        DungeonChallenges.clearSpawners(level, DungeonDimension.roomMinAt(boss.blockPosition()), tag.getInt("tier"), false);
        DungeonRegions.get().progress(tag.getInt("region")).flatMap(progress -> exitPos(progress.door()))
                .ifPresent(pos -> level.setBlock(pos, ModRegistries.DUNGEON_EXIT.get().defaultBlockState(), Block.UPDATE_ALL));
    }

    /**
     * Where the exit appears once the boss is dead: on the floor just inside the boss room's door, so players don't
     * have to walk back to the start. Empty if there's no door.
     */
    public static Optional<BlockPos> exitPos(List<BlockPos> door) {
        if (door.isEmpty()) {
            return Optional.empty();
        }
        int bottom = door.stream().mapToInt(BlockPos::getY).min().getAsInt();
        List<BlockPos> sill = door.stream().filter(pos -> pos.getY() == bottom).toList();
        BlockPos middle = sill.get(sill.size() / 2);
        BlockPos center = DungeonDimension.roomCenterAt(middle);
        int dx = center.getX() - middle.getX();
        int dz = center.getZ() - middle.getZ();
        Direction inward = Math.abs(dx) > Math.abs(dz) ? (dx > 0 ? Direction.EAST : Direction.WEST) : (dz > 0 ? Direction.SOUTH : Direction.NORTH);
        return Optional.of(middle.relative(inward, EXIT_INSET));
    }

    // --- Boss bars ---

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide() && event.getEntity() instanceof LivingEntity boss && isBoss(boss)) {
            BARS.computeIfAbsent(boss.getUUID(),
                    id -> new ServerBossEvent(boss.getDisplayName(), BossEvent.BossBarColor.YELLOW, BossEvent.BossBarOverlay.NOTCHED_10));
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (BARS.isEmpty() || event.getServer().getTickCount() % 5 != 0) {
            return;
        }
        ServerLevel dungeon = event.getServer().getLevel(DungeonDimension.DUNGEON);
        Iterator<Map.Entry<UUID, ServerBossEvent>> bars = BARS.entrySet().iterator();
        while (bars.hasNext()) {
            Map.Entry<UUID, ServerBossEvent> entry = bars.next();
            ServerBossEvent bar = entry.getValue();
            if (dungeon == null || !(dungeon.getEntity(entry.getKey()) instanceof LivingEntity boss) || !boss.isAlive()) {
                bar.removeAllPlayers();
                bars.remove();
                continue;
            }
            bar.setProgress(boss.getHealth() / boss.getMaxHealth());
            for (ServerPlayer player : dungeon.players()) {
                if (player.distanceToSqr(boss) <= BAR_RANGE * BAR_RANGE) {
                    bar.addPlayer(player);
                } else {
                    bar.removePlayer(player);
                }
            }
        }
    }
}
