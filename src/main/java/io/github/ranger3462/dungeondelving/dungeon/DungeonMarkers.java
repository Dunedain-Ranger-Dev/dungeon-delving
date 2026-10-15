package io.github.ranger3462.dungeondelving.dungeon;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import io.github.ranger3462.dungeondelving.Config;
import io.github.ranger3462.dungeondelving.DungeonDelving;
import io.github.ranger3462.dungeondelving.registry.ModRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * Turns the marker blocks builders place in dungeon pieces into content, freshly rolled every run:
 * <ul>
 * <li>Loot marker: a chest with {@code dungeondelving:chests/dungeon_room} loot, or nothing.</li>
 * <li>Mob marker: one or more mobs from the {@code dungeondelving:dungeon_mobs} entity tag.</li>
 * <li>Resource marker: an ore from the {@code dungeondelving:dungeon_resources} block tag, or nothing.</li>
 * <li>Objective marker: a reward chest with {@code dungeondelving:chests/dungeon_objective} loot.</li>
 * </ul>
 * Chances and mob counts come from the config.
 */
public final class DungeonMarkers {
    public static final ResourceKey<LootTable> ROOM_LOOT = lootTable("chests/dungeon_room");
    public static final ResourceKey<LootTable> OBJECTIVE_LOOT = lootTable("chests/dungeon_objective");
    public static final TagKey<EntityType<?>> DUNGEON_MOBS = TagKey.create(Registries.ENTITY_TYPE, DungeonDelving.id("dungeon_mobs"));
    public static final TagKey<Block> DUNGEON_RESOURCES = TagKey.create(Registries.BLOCK, DungeonDelving.id("dungeon_resources"));

    /** Chances and limits used when rolling markers. */
    public record Rolls(double lootChance, double resourceChance, int maxMobs) {
        public static Rolls fromConfig() {
            return new Rolls(Config.LOOT_MARKER_CHANCE.get(), Config.RESOURCE_MARKER_CHANCE.get(), Config.MAX_MOBS_PER_MARKER.get());
        }
    }

    private DungeonMarkers() {}

    /** Resolves every marker in a piece that was just placed with the given position and settings. */
    public static List<Entity> resolve(ServerLevel level, StructureTemplate template, BlockPos pos, StructurePlaceSettings settings,
            RandomSource random, Rolls rolls) {
        List<Entity> spawned = new ArrayList<>();
        for (Block marker : List.of(ModRegistries.LOOT_MARKER.get(), ModRegistries.MOB_MARKER.get(), ModRegistries.RESOURCE_MARKER.get(),
                ModRegistries.OBJECTIVE_MARKER.get())) {
            for (StructureTemplate.StructureBlockInfo info : template.filterBlocks(pos, settings, marker)) {
                spawned.addAll(resolveAt(level, info.pos(), marker, random, rolls));
            }
        }
        return spawned;
    }

    /** Resolves one marker block at a position. Returns any mobs it spawned. */
    public static List<Entity> resolveAt(ServerLevel level, BlockPos pos, Block marker, RandomSource random, Rolls rolls) {
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        if (marker == ModRegistries.LOOT_MARKER.get()) {
            if (random.nextDouble() < rolls.lootChance()) {
                placeChest(level, pos, ROOM_LOOT, random);
            }
        } else if (marker == ModRegistries.OBJECTIVE_MARKER.get()) {
            placeChest(level, pos, OBJECTIVE_LOOT, random);
        } else if (marker == ModRegistries.RESOURCE_MARKER.get()) {
            if (random.nextDouble() < rolls.resourceChance()) {
                pickFromTag(level.registryAccess().registryOrThrow(Registries.BLOCK).getTag(DUNGEON_RESOURCES), random)
                        .ifPresent(ore -> level.setBlock(pos, ore.value().defaultBlockState(), Block.UPDATE_CLIENTS));
            }
        } else if (marker == ModRegistries.MOB_MARKER.get()) {
            return spawnMobs(level, pos, random, rolls.maxMobs());
        }
        return List.of();
    }

    private static void placeChest(ServerLevel level, BlockPos pos, ResourceKey<LootTable> table, RandomSource random) {
        Direction facing = Direction.Plane.HORIZONTAL.getRandomDirection(random);
        level.setBlock(pos, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, facing), Block.UPDATE_CLIENTS);
        if (level.getBlockEntity(pos) instanceof RandomizableContainerBlockEntity chest) {
            chest.setLootTable(table, random.nextLong());
        }
    }

    private static List<Entity> spawnMobs(ServerLevel level, BlockPos pos, RandomSource random, int maxMobs) {
        Optional<HolderSet.Named<EntityType<?>>> mobs = level.registryAccess().registryOrThrow(Registries.ENTITY_TYPE).getTag(DUNGEON_MOBS);
        List<Entity> spawned = new ArrayList<>();
        int count = 1 + random.nextInt(maxMobs);
        for (int i = 0; i < count; i++) {
            pickFromTag(mobs, random).ifPresent(type -> {
                Entity entity = type.value().spawn(level, pos, MobSpawnType.STRUCTURE);
                if (entity instanceof Mob mob) {
                    mob.setPersistenceRequired();
                }
                if (entity != null) {
                    spawned.add(entity);
                }
            });
        }
        return spawned;
    }

    private static <T> Optional<Holder<T>> pickFromTag(Optional<HolderSet.Named<T>> tag, RandomSource random) {
        return tag.flatMap(set -> set.getRandomElement(random));
    }

    private static ResourceKey<LootTable> lootTable(String path) {
        return ResourceKey.create(Registries.LOOT_TABLE, DungeonDelving.id(path));
    }
}
