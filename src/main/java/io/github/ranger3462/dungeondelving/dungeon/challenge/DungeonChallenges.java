package io.github.ranger3462.dungeondelving.dungeon.challenge;

import java.util.Optional;

import io.github.ranger3462.dungeondelving.DungeonDelving;
import io.github.ranger3462.dungeondelving.dungeon.DungeonDimension;
import io.github.ranger3462.dungeondelving.dungeon.DungeonMarkers;
import io.github.ranger3462.dungeondelving.dungeon.DungeonRegions;
import io.github.ranger3462.dungeondelving.dungeon.DungeonRun;
import io.github.ranger3462.dungeondelving.dungeon.RoomSpec;
import io.github.ranger3462.dungeondelving.registry.ModRegistries;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

/**
 * The path to the boss: challenge rooms, the Dungeon Keys they drop, and the boss door. Each challenge room gives one
 * key, and its spawners give way to a reward chest, so players can finish off the room and loot it without breaking
 * spawners. There are three kinds so far:
 * <ul>
 * <li>Each hunt marker adds a hunt target to its room, and becomes a spawner. The room's spawners then turn some of
 * the mobs they spawn into hunt targets, at random, until the room has spawned all of them. Hunt targets are tougher
 * than ordinary mobs and aren't highlighted (a skill may reveal them later). Killing every hunt target in a room drops
 * its key.</li>
 * <li><b>Puzzles:</b> builders wire a vanilla redstone puzzle into a Puzzle Lock. The first time it's powered, it drops
 * the key.</li>
 * <li><b>Collection:</b> monsters killed in a room with a Relic Altar sometimes drop Relic Shards
 * ({@link #RELIC_CHANCE}). Offering the altar {@link DungeonProgress#relicsFor enough} of them gives the key.</li>
 * <li>The boss door needs {@link DungeonProgress#keysFor tier x 2} keys. Using the last one opens the door and spawns
 * the boss.</li>
 * </ul>
 */
@EventBusSubscriber(modid = DungeonDelving.MODID)
public final class DungeonChallenges {
    /** Saved on hunt targets: which region and room they belong to. */
    public static final String HUNT_TAG = DungeonDelving.MODID + ":hunt";
    /** Chance each mob a challenge room's spawners spawn is a hunt target, while the room still has some to spawn. */
    public static final double HUNTER_CHANCE = 0.3;
    /** How much tougher hunt targets are than ordinary mobs. */
    private static final double HUNTER_HEALTH = 2.0;
    private static final double HUNTER_DAMAGE = 2.0;
    /** Chance a monster killed in a collection room drops a Relic Shard, while the room's altar still needs some. */
    public static final double RELIC_CHANCE = 0.35;

    private DungeonChallenges() {}

    // --- Hunts ---

    /** Adds a hunt target to a room. Nothing is tracked if the region has no progress, e.g. in tests. */
    public static void addHunter(int region, long room) {
        if (region < 0) {
            return;
        }
        DungeonRegions.get().progress(region).ifPresent(progress -> {
            progress.addHunter(room);
            DungeonRegions.get().progressChanged();
        });
    }

    /** A dungeon spawner spawning a mob: in a room still waiting on hunt targets, it may become one. */
    @SubscribeEvent
    public static void onSpawnerSpawn(FinalizeSpawnEvent event) {
        if (event.getSpawnType() != MobSpawnType.SPAWNER || event.getLevel().getLevel().dimension() != DungeonDimension.DUNGEON) {
            return;
        }
        Mob mob = event.getEntity();
        BlockPos pos = mob.blockPosition();
        int region = DungeonDimension.regionAt(pos);
        long room = DungeonDimension.roomMinAt(pos).asLong();
        DungeonRegions.get().progress(region).ifPresent(progress -> {
            if (progress.unspawnedHunters(room) > 0 && mob.getRandom().nextDouble() < HUNTER_CHANCE && progress.hunterSpawned(room)) {
                makeHunter(mob, region, room);
                DungeonRegions.get().progressChanged();
            }
        });
    }

    /** Turns a mob into a hunt target for a room: tougher, persistent, and counted towards the room's key. */
    public static void makeHunter(Mob mob, int region, long room) {
        AttributeInstance health = mob.getAttribute(Attributes.MAX_HEALTH);
        if (health != null) {
            health.setBaseValue(health.getBaseValue() * HUNTER_HEALTH);
        }
        AttributeInstance damage = mob.getAttribute(Attributes.ATTACK_DAMAGE);
        if (damage != null) {
            damage.setBaseValue(damage.getBaseValue() + HUNTER_DAMAGE);
        }
        mob.setHealth(mob.getMaxHealth());
        mob.setPersistenceRequired();
        mob.setCustomName(Component.translatable("entity.dungeondelving.hunt_target", mob.getType().getDescription()));
        mob.setCustomNameVisible(false);
        CompoundTag hunt = new CompoundTag();
        hunt.putInt("region", region);
        hunt.putLong("room", room);
        mob.getPersistentData().put(HUNT_TAG, hunt);
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity().level() instanceof ServerLevel level)) {
            return;
        }
        if (!event.getEntity().getPersistentData().contains(HUNT_TAG)) {
            maybeDropRelic(level, event.getEntity());
            return;
        }
        CompoundTag hunt = event.getEntity().getPersistentData().getCompound(HUNT_TAG);
        int region = hunt.getInt("region");
        DungeonRegions.get().progress(region).flatMap(progress -> progress.hunterKilled(hunt.getLong("room"))).ifPresent(left -> {
            DungeonRegions.get().progressChanged();
            if (left > 0) {
                tellRegion(level, region, Component.translatable("message.dungeondelving.hunt_left", left).withStyle(ChatFormatting.GRAY));
                return;
            }
            roomCleared(level, region, BlockPos.of(hunt.getLong("room")), event.getEntity().position().add(0, 0.5, 0),
                    Component.translatable("message.dungeondelving.key_dropped"));
        });
    }

    /**
     * A challenge room is done: its spawners give way to a reward chest, and its Dungeon Key appears at {@code at},
     * glowing so it isn't lost.
     */
    public static void roomCleared(ServerLevel level, int region, BlockPos roomMin, Vec3 at, Component message) {
        DungeonRegions.get().progress(region).ifPresent(progress -> clearSpawners(level, roomMin, progress.tier(), true));
        dropGlowing(level, at, DungeonKeyItem.forRegion(region));
        level.playSound(null, BlockPos.containing(at), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1.0f, 1.2f);
        tellRegion(level, region, message.copy().withStyle(ChatFormatting.GOLD));
    }

    private static void dropGlowing(ServerLevel level, Vec3 at, ItemStack stack) {
        ItemEntity item = new ItemEntity(level, at.x, at.y, at.z, stack);
        item.setUnlimitedLifetime();
        item.setGlowingTag(true);
        level.addFreshEntity(item);
    }

    // --- Puzzles ---

    /** A Puzzle Lock was powered for the first time: its room is solved. Nothing happens outside a tracked dungeon. */
    public static void puzzleSolved(ServerLevel level, BlockPos lock) {
        level.playSound(null, lock, SoundEvents.VAULT_OPEN_SHUTTER, SoundSource.BLOCKS, 1.0f, 1.0f);
        int region = DungeonDimension.regionAt(lock);
        if (level.dimension() != DungeonDimension.DUNGEON || DungeonRegions.get().progress(region).isEmpty()) {
            return;
        }
        roomCleared(level, region, DungeonDimension.roomMinAt(lock), Vec3.atCenterOf(lock.above()),
                Component.translatable("message.dungeondelving.puzzle_solved"));
    }

    // --- Collection ---

    /** Gives a room a Relic Altar. Nothing is tracked if the region has no progress, e.g. in tests. */
    public static void addAltar(int region, long room) {
        if (region < 0) {
            return;
        }
        DungeonRegions.get().progress(region).ifPresent(progress -> {
            progress.addAltar(room);
            DungeonRegions.get().progressChanged();
        });
    }

    /** A monster killed in a room whose altar still needs relics may drop one. */
    private static void maybeDropRelic(ServerLevel level, LivingEntity entity) {
        if (level.dimension() != DungeonDimension.DUNGEON || !(entity instanceof Monster) || DungeonBosses.isBoss(entity)) {
            return;
        }
        BlockPos pos = entity.blockPosition();
        int region = DungeonDimension.regionAt(pos);
        long room = DungeonDimension.roomMinAt(pos).asLong();
        DungeonRegions.get().progress(region).ifPresent(progress -> {
            if (progress.relicsLeft(room) > 0 && entity.getRandom().nextDouble() < RELIC_CHANCE) {
                dropGlowing(level, entity.position().add(0, 0.5, 0), DungeonKeyItem.relicFor(region));
            }
        });
    }

    /** A player using a Relic Altar, holding a stack (possibly empty): offers any of this dungeon's shards it holds. */
    public static void useAltar(ServerPlayer player, BlockPos altar, ItemStack held) {
        int region = DungeonDimension.regionAt(altar);
        long room = DungeonDimension.roomMinAt(altar).asLong();
        Optional<DungeonProgress> found = player.level().dimension() == DungeonDimension.DUNGEON ? DungeonRegions.get().progress(region) : Optional.empty();
        if (found.isEmpty() || found.get().relicsLeft(room) <= 0) {
            player.displayClientMessage(Component.translatable("message.dungeondelving.altar_quiet").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        DungeonProgress progress = found.get();
        int taken = DungeonKeyItem.isRelicFrom(held, region) ? progress.offerRelics(room, held.getCount()) : 0;
        if (taken == 0) {
            player.displayClientMessage(Component.translatable("message.dungeondelving.altar_needs", progress.relicsLeft(room))
                    .withStyle(ChatFormatting.GOLD), true);
            return;
        }
        held.shrink(taken);
        DungeonRegions.get().progressChanged();
        ServerLevel level = player.serverLevel();
        level.playSound(null, altar, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 1.0f, 1.0f);
        int left = progress.relicsLeft(room);
        if (left > 0) {
            player.displayClientMessage(Component.translatable("message.dungeondelving.altar_needs", left).withStyle(ChatFormatting.GOLD), true);
            return;
        }
        roomCleared(level, region, BlockPos.of(room), Vec3.atCenterOf(altar.above()), Component.translatable("message.dungeondelving.altar_done"));
    }

    /**
     * Removes every spawner in a cleared room (whose minimum corner is {@code roomMin}), optionally leaving a reward
     * chest where the first one was. Returns how many spawners were removed.
     */
    public static int clearSpawners(ServerLevel level, BlockPos roomMin, int tier, boolean rewardChest) {
        BlockPos chest = null;
        int removed = 0;
        for (BlockPos pos : BlockPos.betweenClosed(roomMin, roomMin.offset(RoomSpec.ROOM_SIZE - 1, RoomSpec.ROOM_HEIGHT - 1, RoomSpec.ROOM_SIZE - 1))) {
            if (level.getBlockState(pos).is(Blocks.SPAWNER)) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                if (chest == null) {
                    chest = pos.immutable();
                }
                removed++;
            }
        }
        if (chest != null && rewardChest) {
            DungeonMarkers.placeChest(level, chest, DungeonMarkers.roomLoot(tier), level.getRandom());
        }
        return removed;
    }

    /** How often a boss arena's spawners spawn once the boss is out, in ticks (vanilla spawners wait 200 to 800). */
    public static final int RALLY_MIN_DELAY = 40;
    public static final int RALLY_MAX_DELAY = 100;
    /** Mobs a rallied spawner spawns at once, and how many can be near it before it stops, by tier (index 0 is tier 1). */
    public static final int[] RALLY_SPAWN_COUNT = { 1, 2, 2, 3 };
    public static final int[] RALLY_MAX_NEARBY = { 4, 6, 8, 10 };

    /**
     * Speeds up every spawner in a room (whose minimum corner is {@code roomMin}): they spawn every few seconds, a few
     * mobs at a time, from farther away. Used when a boss appears, so its arena fills with its minions.
     */
    public static int rallySpawners(ServerLevel level, BlockPos roomMin, int tier) {
        int index = DungeonRun.clampTier(tier) - DungeonRun.MIN_TIER;
        int rallied = 0;
        for (BlockPos pos : BlockPos.betweenClosed(roomMin, roomMin.offset(RoomSpec.ROOM_SIZE - 1, RoomSpec.ROOM_HEIGHT - 1, RoomSpec.ROOM_SIZE - 1))) {
            if (level.getBlockEntity(pos) instanceof SpawnerBlockEntity spawner) {
                CompoundTag tag = spawner.getSpawner().save(new CompoundTag());
                tag.putShort("Delay", (short) 20);
                tag.putShort("MinSpawnDelay", (short) RALLY_MIN_DELAY);
                tag.putShort("MaxSpawnDelay", (short) RALLY_MAX_DELAY);
                tag.putShort("SpawnCount", (short) RALLY_SPAWN_COUNT[index]);
                tag.putShort("MaxNearbyEntities", (short) RALLY_MAX_NEARBY[index]);
                tag.putShort("RequiredPlayerRange", (short) 32);
                spawner.getSpawner().load(level, pos.immutable(), tag);
                spawner.setChanged();
                rallied++;
            }
        }
        return rallied;
    }

    // --- The boss door ---

    /** A player using the boss door, holding a stack (possibly empty). */
    public static void useDoor(ServerPlayer player, ItemStack held) {
        Optional<DungeonRun> run = DungeonRegions.get().run(player.getUUID());
        Optional<DungeonProgress> found = run.flatMap(r -> DungeonRegions.get().progress(r.region()));
        if (found.isEmpty()) {
            player.displayClientMessage(Component.translatable("message.dungeondelving.door_sealed").withStyle(ChatFormatting.RED), true);
            return;
        }
        int region = run.get().region();
        DungeonProgress progress = found.get();
        if (held.is(ModRegistries.DUNGEON_KEY.get()) && !DungeonKeyItem.opens(held, region)) {
            held.setCount(0);
            player.sendSystemMessage(Component.translatable("message.dungeondelving.key_crumbles").withStyle(ChatFormatting.GRAY));
            return;
        }
        if (!DungeonKeyItem.opens(held, region) || !progress.useKey()) {
            player.displayClientMessage(Component.translatable("message.dungeondelving.door_needs", progress.keysUsed(), progress.keysNeeded())
                    .withStyle(ChatFormatting.GOLD), true);
            return;
        }
        held.shrink(1);
        DungeonRegions.get().progressChanged();
        ServerLevel level = player.serverLevel();
        level.playSound(null, player.blockPosition(), SoundEvents.IRON_DOOR_OPEN, SoundSource.BLOCKS, 1.0f, 0.6f);
        if (!progress.doorOpen()) {
            tellRegion(level, region, Component.translatable("message.dungeondelving.key_used", progress.keysUsed(), progress.keysNeeded())
                    .withStyle(ChatFormatting.GOLD));
            return;
        }
        openDoor(level, region, progress);
    }

    /** Removes the boss door and spawns the boss. */
    public static void openDoor(ServerLevel level, int region, DungeonProgress progress) {
        for (BlockPos pos : progress.door()) {
            if (level.getBlockState(pos).is(ModRegistries.BOSS_DOOR.get())) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
        if (!progress.bossSpawned() && progress.bossSpawn() != null) {
            DungeonBosses.spawn(level, progress.bossSpawn(), progress.tier(), region, progress.boss());
            progress.markBossSpawned();
            DungeonRegions.get().progressChanged();
            level.playSound(null, progress.bossSpawn(), SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 1.0f, 0.8f);
        }
        tellRegion(level, region, Component.translatable("message.dungeondelving.door_open").withStyle(ChatFormatting.DARK_RED));
    }

    /** Sends a message to everyone running the dungeon in this region. */
    public static void tellRegion(ServerLevel level, int region, Component message) {
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            if (player.level().dimension() == DungeonDimension.DUNGEON
                    && DungeonRegions.get().run(player.getUUID()).map(run -> run.region() == region).orElse(false)) {
                player.sendSystemMessage(message);
            }
        }
    }
}
