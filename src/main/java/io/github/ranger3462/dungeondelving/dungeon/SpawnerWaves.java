package io.github.ranger3462.dungeondelving.dungeon;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import io.github.ranger3462.dungeondelving.DungeonDelving;
import io.github.ranger3462.dungeondelving.registry.ModRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Common rooms' spawners send one wave and break, like Vault Hunters (DD-110). A room is common when it has no
 * challenge (hunt markers, a Relic Altar, a War Drum, a Puzzle Lock) and isn't the boss room; those keep spawning,
 * since their challenges and boss fights run on their spawners. The first time a player comes in range, a wave
 * spawner's own spawning is called off and, at the end of the tick, it sends a wave of {@link #WAVE_MIN} to
 * {@link #WAVE_MAX} of its mob for the tier (pack mobs bring one more per tier above 1, as with ordinary spawners),
 * which go for the nearest player, and breaks.
 */
@EventBusSubscriber(modid = DungeonDelving.MODID)
public final class SpawnerWaves {
    /** Saved on a wave spawner's block entity: the dungeon tier its wave is for. */
    public static final String WAVE_TAG = DungeonDelving.MODID + ":wave_tier";
    /** The smallest and largest wave by tier (index 0 is tier 1). */
    public static final int[] WAVE_MIN = { 1, 2, 3, 4 };
    public static final int[] WAVE_MAX = { 3, 4, 5, 6 };
    /** How far from the spawner its wave can appear, in blocks, like a vanilla spawner. */
    private static final int SPAWN_RANGE = 4;
    /** Spawners that came into range this tick, to release at its end. */
    private static final Set<Triggered> TRIGGERED = new LinkedHashSet<>();

    private record Triggered(ResourceKey<Level> dimension, BlockPos pos) {}

    private SpawnerWaves() {}

    /** Whether a dungeon piece is a common room: no challenge and no boss, so its spawners send one wave and break. */
    public static boolean commonRoom(StructureTemplate template, BlockPos pos, StructurePlaceSettings settings) {
        for (Block block : List.of(ModRegistries.HUNT_MARKER.get(), ModRegistries.BOSS_MARKER.get(), ModRegistries.RELIC_ALTAR.get(),
                ModRegistries.WAR_DRUM.get(), ModRegistries.PUZZLE_LOCK.get())) {
            if (!template.filterBlocks(pos, settings, block).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /** Makes the spawner at a position (if there is one) a wave spawner for a dungeon of this tier. */
    public static void mark(ServerLevel level, BlockPos pos, int tier) {
        if (level.getBlockEntity(pos) instanceof SpawnerBlockEntity spawner) {
            spawner.getPersistentData().putInt(WAVE_TAG, DungeonRun.clampTier(tier));
            spawner.setChanged();
        }
    }

    public static boolean isWaveSpawner(ServerLevel level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof SpawnerBlockEntity spawner && spawner.getPersistentData().contains(WAVE_TAG);
    }

    /** How many mobs a wave of this mob brings in a dungeon of this tier. */
    public static int waveSize(int tier, EntityType<?> type, RandomSource random) {
        int index = DungeonRun.clampTier(tier) - DungeonRun.MIN_TIER;
        int pack = type.is(DungeonMarkers.PACK_MOBS) ? index : 0;
        return WAVE_MIN[index] + random.nextInt(WAVE_MAX[index] - WAVE_MIN[index] + 1) + pack;
    }

    /** A wave spawner coming into range: its own spawning is called off, and its wave comes at the end of the tick. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onSpawnerSpawn(FinalizeSpawnEvent event) {
        if (event.getSpawnType() != MobSpawnType.SPAWNER || event.getSpawner() == null) {
            return;
        }
        event.getSpawner().left().ifPresent(blockEntity -> {
            if (blockEntity instanceof SpawnerBlockEntity spawner && spawner.getPersistentData().contains(WAVE_TAG)) {
                event.setSpawnCancelled(true);
                TRIGGERED.add(new Triggered(event.getLevel().getLevel().dimension(), spawner.getBlockPos().immutable()));
            }
        });
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (TRIGGERED.isEmpty()) {
            return;
        }
        List<Triggered> due = new ArrayList<>(TRIGGERED);
        TRIGGERED.clear();
        for (Triggered triggered : due) {
            ServerLevel level = event.getServer().getLevel(triggered.dimension());
            if (level != null) {
                release(level, triggered.pos());
            }
        }
    }

    /** Breaks a wave spawner and sends its wave. Returns the mobs that came out (none if it isn't a wave spawner). */
    public static List<Mob> release(ServerLevel level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof SpawnerBlockEntity spawner) || !spawner.getPersistentData().contains(WAVE_TAG)) {
            return List.of();
        }
        int tier = spawner.getPersistentData().getInt(WAVE_TAG);
        CompoundTag entity = spawner.getSpawner().save(new CompoundTag()).getCompound("SpawnData").getCompound("entity");
        Optional<EntityType<?>> type = EntityType.by(entity);
        level.destroyBlock(pos, false);
        if (type.isEmpty()) {
            return List.of();
        }
        RandomSource random = level.getRandom();
        int size = waveSize(tier, type.get(), random);
        boolean inDungeon = level.dimension() == DungeonDimension.DUNGEON;
        List<Mob> wave = new ArrayList<>();
        for (int attempt = 0; attempt < size * 10 && wave.size() < size; attempt++) {
            BlockPos at = pos.offset(random.nextInt(2 * SPAWN_RANGE + 1) - SPAWN_RANGE, random.nextInt(3) - 1,
                    random.nextInt(2 * SPAWN_RANGE + 1) - SPAWN_RANGE);
            // Only on open floor in the spawner's own room, not through a wall into the next.
            if (inDungeon && !DungeonDimension.roomMinAt(at).equals(DungeonDimension.roomMinAt(pos))) {
                continue;
            }
            if (!level.getBlockState(at.below()).isSolid() || !level.noCollision(type.get().getSpawnAABB(at.getX() + 0.5, at.getY(), at.getZ() + 0.5))) {
                continue;
            }
            Entity spawned = type.get().spawn(level, at, MobSpawnType.SPAWNER);
            if (spawned instanceof Mob mob) {
                if (level.getNearestPlayer(mob, 24) instanceof ServerPlayer target && !target.isCreative() && !target.isSpectator()) {
                    mob.setTarget(target);
                }
                wave.add(mob);
            } else if (spawned != null) {
                spawned.discard();
            }
        }
        return wave;
    }
}
