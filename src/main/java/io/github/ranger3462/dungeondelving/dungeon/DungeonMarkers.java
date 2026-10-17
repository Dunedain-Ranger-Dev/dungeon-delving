package io.github.ranger3462.dungeondelving.dungeon;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import io.github.ranger3462.dungeondelving.Config;
import io.github.ranger3462.dungeondelving.DungeonDelving;
import io.github.ranger3462.dungeondelving.registry.DungeonOre;
import io.github.ranger3462.dungeondelving.registry.ModRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * Turns the marker blocks builders place in dungeon pieces into content, freshly rolled every run:
 * <ul>
 * <li>Loot marker: a chest with the tier's {@code dungeondelving:chests/tier_<n>/room} loot, or nothing.</li>
 * <li>Mob marker: a monster spawner for a random mob from the {@code dungeondelving:dungeon_mobs} entity tag. Dungeon
 * spawners ignore light level, so lit rooms still spawn mobs.</li>
 * <li>Resource marker: the dungeon's tier ore, the common dungeon ore, an ore from the
 * {@code dungeondelving:dungeon_resources} block tag or the tier's {@code dungeondelving:dungeon_resources/tier_<n>}
 * tag, or otherwise Hallstone, so there are never gaps.</li>
 * <li>Objective marker: a reward chest with the tier's {@code dungeondelving:chests/tier_<n>/objective} loot.</li>
 * </ul>
 * Chances come from the config.
 */
public final class DungeonMarkers {
    public static final TagKey<EntityType<?>> DUNGEON_MOBS = TagKey.create(Registries.ENTITY_TYPE, DungeonDelving.id("dungeon_mobs"));
    public static final TagKey<Block> DUNGEON_RESOURCES = TagKey.create(Registries.BLOCK, DungeonDelving.id("dungeon_resources"));

    /** The dungeon's tier, and the chances used when rolling its markers. */
    public record Rolls(int tier, double lootChance, double resourceChance, double tierOreChance, double commonOreChance) {
        public static Rolls fromConfig(int tier) {
            return new Rolls(tier, Config.LOOT_MARKER_CHANCE.get(), Config.RESOURCE_MARKER_CHANCE.get(), Config.TIER_ORE_CHANCE.get(),
                    Config.COMMON_ORE_CHANCE.get());
        }
    }

    private DungeonMarkers() {}

    /** The loot table for loot-marker chests in a dungeon of this tier (1 to 4). */
    public static ResourceKey<LootTable> roomLoot(int tier) {
        return lootTable("chests/tier_" + DungeonRun.clampTier(tier) + "/room");
    }

    /** The loot table for the objective reward chest in a dungeon of this tier (1 to 4). */
    public static ResourceKey<LootTable> objectiveLoot(int tier) {
        return lootTable("chests/tier_" + DungeonRun.clampTier(tier) + "/objective");
    }

    /** Resolves every marker in a piece that was just placed with the given position and settings. */
    public static void resolve(ServerLevel level, StructureTemplate template, BlockPos pos, StructurePlaceSettings settings,
            RandomSource random, Rolls rolls) {
        for (Block marker : List.of(ModRegistries.LOOT_MARKER.get(), ModRegistries.MOB_MARKER.get(), ModRegistries.RESOURCE_MARKER.get(),
                ModRegistries.OBJECTIVE_MARKER.get())) {
            for (StructureTemplate.StructureBlockInfo info : template.filterBlocks(pos, settings, marker)) {
                resolveAt(level, info.pos(), marker, random, rolls);
            }
        }
    }

    /** Resolves one marker block at a position. */
    public static void resolveAt(ServerLevel level, BlockPos pos, Block marker, RandomSource random, Rolls rolls) {
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        if (marker == ModRegistries.LOOT_MARKER.get()) {
            if (random.nextDouble() < rolls.lootChance()) {
                placeChest(level, pos, roomLoot(rolls.tier()), random);
            }
        } else if (marker == ModRegistries.OBJECTIVE_MARKER.get()) {
            placeChest(level, pos, objectiveLoot(rolls.tier()), random);
        } else if (marker == ModRegistries.RESOURCE_MARKER.get()) {
            Block block = random.nextDouble() < rolls.resourceChance() ? pickOre(level, random, rolls).orElse(null) : null;
            level.setBlock(pos, (block != null ? block : ModRegistries.HALLSTONE.get()).defaultBlockState(), Block.UPDATE_CLIENTS);
        } else if (marker == ModRegistries.MOB_MARKER.get()) {
            placeSpawner(level, pos, random);
        }
    }

    /** The dungeon's tier ore, the common dungeon ore, or a vanilla ore, by the configured chances. */
    private static Optional<Block> pickOre(ServerLevel level, RandomSource random, Rolls rolls) {
        double roll = random.nextDouble();
        if (roll < rolls.tierOreChance()) {
            return DungeonOre.forTier(rolls.tier()).map(ore -> ore.ore.get());
        }
        if (roll < rolls.tierOreChance() + rolls.commonOreChance()) {
            return Optional.of(DungeonOre.COMMON.ore.get());
        }
        var blocks = level.registryAccess().registryOrThrow(Registries.BLOCK);
        List<Holder<Block>> choices = new ArrayList<>();
        blocks.getTag(DUNGEON_RESOURCES).ifPresent(tag -> tag.forEach(choices::add));
        blocks.getTag(tierResources(rolls.tier())).ifPresent(tag -> tag.forEach(choices::add));
        return choices.isEmpty() ? Optional.empty() : Optional.of(choices.get(random.nextInt(choices.size())).value());
    }

    /** Extra resource-marker ores for one tier, on top of {@link #DUNGEON_RESOURCES}, e.g. a magic mod's ore from tier 2. */
    public static TagKey<Block> tierResources(int tier) {
        return TagKey.create(Registries.BLOCK, DungeonDelving.id("dungeon_resources/tier_" + DungeonRun.clampTier(tier)));
    }

    private static void placeChest(ServerLevel level, BlockPos pos, ResourceKey<LootTable> table, RandomSource random) {
        Direction facing = Direction.Plane.HORIZONTAL.getRandomDirection(random);
        level.setBlock(pos, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, facing), Block.UPDATE_CLIENTS);
        if (level.getBlockEntity(pos) instanceof RandomizableContainerBlockEntity chest) {
            chest.setLootTable(table, random.nextLong());
        }
    }

    /**
     * A monster spawner for a random dungeon mob. Its spawns carry custom light rules (any block or sky light), since
     * vanilla monsters otherwise only spawn in darkness.
     */
    private static void placeSpawner(ServerLevel level, BlockPos pos, RandomSource random) {
        Optional<HolderSet.Named<EntityType<?>>> mobs = level.registryAccess().registryOrThrow(Registries.ENTITY_TYPE).getTag(DUNGEON_MOBS);
        Optional<Holder<EntityType<?>>> mob = pickFromTag(mobs, random);
        if (mob.isEmpty()) {
            return;
        }
        level.setBlock(pos, Blocks.SPAWNER.defaultBlockState(), Block.UPDATE_ALL);
        if (level.getBlockEntity(pos) instanceof SpawnerBlockEntity spawner) {
            spawner.getSpawner().load(level, pos, spawnerTag(mob.get().value()));
            spawner.setChanged();
        }
    }

    /** The spawner settings for a dungeon mob: vanilla timing, with spawning allowed at any light level. */
    static CompoundTag spawnerTag(EntityType<?> type) {
        CompoundTag entity = new CompoundTag();
        entity.putString("id", BuiltInRegistries.ENTITY_TYPE.getKey(type).toString());
        CompoundTag rules = new CompoundTag();
        rules.put("block_light_limit", lightRange());
        rules.put("sky_light_limit", lightRange());
        CompoundTag spawnData = new CompoundTag();
        spawnData.put("entity", entity);
        spawnData.put("custom_spawn_rules", rules);
        CompoundTag potential = new CompoundTag();
        potential.put("data", spawnData.copy());
        potential.putInt("weight", 1);
        ListTag potentials = new ListTag();
        potentials.add(potential);
        CompoundTag tag = new CompoundTag();
        tag.put("SpawnData", spawnData);
        tag.put("SpawnPotentials", potentials);
        tag.putShort("Delay", (short) 20);
        return tag;
    }

    private static ListTag lightRange() {
        ListTag range = new ListTag();
        range.add(IntTag.valueOf(0));
        range.add(IntTag.valueOf(15));
        return range;
    }

    private static <T> Optional<Holder<T>> pickFromTag(Optional<HolderSet.Named<T>> tag, RandomSource random) {
        return tag.flatMap(set -> set.getRandomElement(random));
    }

    private static ResourceKey<LootTable> lootTable(String path) {
        return ResourceKey.create(Registries.LOOT_TABLE, DungeonDelving.id(path));
    }
}
