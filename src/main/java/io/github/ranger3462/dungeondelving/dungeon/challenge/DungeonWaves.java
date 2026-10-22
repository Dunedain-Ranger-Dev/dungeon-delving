package io.github.ranger3462.dungeondelving.dungeon.challenge;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import io.github.ranger3462.dungeondelving.DungeonDelving;
import io.github.ranger3462.dungeondelving.dungeon.DungeonDimension;
import io.github.ranger3462.dungeondelving.dungeon.DungeonMarkers;
import io.github.ranger3462.dungeondelving.dungeon.DungeonRegions;
import io.github.ranger3462.dungeondelving.dungeon.DungeonRun;
import io.github.ranger3462.dungeondelving.dungeon.RoomSpec;
import io.github.ranger3462.dungeondelving.dungeon.SpawnerMobs;
import io.github.ranger3462.dungeondelving.registry.ModRegistries;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Wave defense challenge rooms. Builders put a War Drum in the room; striking it seals the doorways and starts the
 * first wave of the dungeon's faction, spawned around the room. Each wave comes a few seconds after the last one is
 * dead; when the last wave falls, the doorways open, a reward chest appears on the drum and the room gives its key.
 * If everyone leaves the room (they can only do that by dying or leaving the dungeon), it resets: the seal comes down
 * and its wave mobs vanish, ready to try again.
 */
@EventBusSubscriber(modid = DungeonDelving.MODID)
public final class DungeonWaves {
    /** Saved on wave mobs: which region and room they belong to. */
    public static final String WAVE_TAG = DungeonDelving.MODID + ":wave";
    /** Ticks between one wave dying and the next one coming. */
    public static final int WAVE_GAP = 60;
    /** When this few of a wave are left, they glow, so none can hide. */
    public static final int GLOW_WHEN_LEFT = 3;
    private static final int CHECK_INTERVAL = 20;

    private DungeonWaves() {}

    /** Gives a room a wave defense challenge, started from the drum at this position. Nothing is tracked outside a run. */
    public static void addDrum(int region, BlockPos drum) {
        if (region < 0) {
            return;
        }
        DungeonRegions.get().progress(region).ifPresent(progress -> {
            progress.addWaveRoom(DungeonDimension.roomMinAt(drum).asLong(), drum);
            DungeonRegions.get().progressChanged();
        });
    }

    /** A player striking a War Drum: starts its room's waves, if it hasn't started. */
    public static void strikeDrum(ServerPlayer player, BlockPos drum) {
        ServerLevel level = player.serverLevel();
        int region = DungeonDimension.regionAt(drum);
        long room = DungeonDimension.roomMinAt(drum).asLong();
        Optional<DungeonProgress> progress = level.dimension() == DungeonDimension.DUNGEON ? DungeonRegions.get().progress(region) : Optional.empty();
        Optional<WaveRoom> waves = progress.flatMap(p -> p.waveRoom(room));
        if (waves.isEmpty()) {
            player.displayClientMessage(Component.translatable("message.dungeondelving.drum_quiet").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        if (waves.get().started()) {
            player.displayClientMessage(Component.translatable("message.dungeondelving.wave_running", waves.get().wave(), waves.get().waves())
                    .withStyle(ChatFormatting.GOLD), true);
            return;
        }
        BlockPos roomMin = BlockPos.of(room);
        seal(level, roomMin, waves.get());
        level.playSound(null, drum, SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 2.0f, 1.0f);
        DungeonChallenges.tellRegion(level, region, Component.translatable("message.dungeondelving.waves_begin", waves.get().waves())
                .withStyle(ChatFormatting.DARK_RED));
        nextWave(level, region, roomMin, progress.get(), waves.get());
    }

    /** Fills every open doorway of a room with Wave Seal, nudging anyone standing in one into the room. */
    public static void seal(ServerLevel level, BlockPos roomMin, WaveRoom waves) {
        List<BlockPos> doorways = doorways(roomMin);
        for (ServerPlayer player : level.players()) {
            if (doorways.contains(player.blockPosition()) || doorways.contains(player.blockPosition().above())) {
                Vec3 inward = Vec3.atCenterOf(roomMin.offset(RoomSpec.ROOM_SIZE / 2, 1, RoomSpec.ROOM_SIZE / 2)).subtract(player.position()).normalize();
                player.teleportTo(player.getX() + inward.x * 2.5, player.getY(), player.getZ() + inward.z * 2.5);
            }
        }
        for (BlockPos pos : doorways) {
            if (level.getBlockState(pos).isAir()) {
                level.setBlock(pos, ModRegistries.WAVE_SEAL.get().defaultBlockState(), Block.UPDATE_ALL);
                waves.seal().add(pos.immutable());
            }
        }
    }

    /** Takes a room's seal down. */
    public static void unseal(ServerLevel level, WaveRoom waves) {
        for (BlockPos pos : waves.seal()) {
            if (level.getBlockState(pos).is(ModRegistries.WAVE_SEAL.get())) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
        waves.seal().clear();
    }

    /** Every block of the four doorway holes in a room's walls. */
    public static List<BlockPos> doorways(BlockPos roomMin) {
        int max = RoomSpec.ROOM_SIZE - 1;
        List<BlockPos> blocks = new java.util.ArrayList<>();
        for (int across = RoomSpec.DOOR_START; across < RoomSpec.DOOR_START + RoomSpec.DOOR_WIDTH; across++) {
            for (int y = RoomSpec.DOOR_BOTTOM_Y; y < RoomSpec.DOOR_BOTTOM_Y + RoomSpec.DOOR_HEIGHT; y++) {
                blocks.add(roomMin.offset(across, y, 0));
                blocks.add(roomMin.offset(across, y, max));
                blocks.add(roomMin.offset(0, y, across));
                blocks.add(roomMin.offset(max, y, across));
            }
        }
        return blocks;
    }

    private static AABB inside(BlockPos roomMin) {
        return new AABB(roomMin.offset(1, 1, 1).getCenter(), roomMin.offset(RoomSpec.ROOM_SIZE - 2, RoomSpec.ROOM_HEIGHT - 2, RoomSpec.ROOM_SIZE - 2).getCenter())
                .inflate(0.5);
    }

    /** Starts the room's next wave, or finishes the room after the last one. */
    private static void nextWave(ServerLevel level, int region, BlockPos roomMin, DungeonProgress progress, WaveRoom waves) {
        if (!waves.advance()) {
            finish(level, region, roomMin, progress, waves);
            return;
        }
        DungeonRegions.get().progressChanged();
        int count = WaveRoom.mobsFor(progress.tier(), waves.wave());
        int spawned = 0;
        for (int i = 0; i < count; i++) {
            spawned += spawnWaveMob(level, roomMin, progress, region) ? 1 : 0;
        }
        if (waves.wave() > 1) {
            level.playSound(null, waves.drum(), SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 1.5f, 1.2f);
        }
        DungeonChallenges.tellRegion(level, region, Component.translatable("message.dungeondelving.wave", waves.wave(), waves.waves())
                .withStyle(ChatFormatting.RED));
        if (spawned == 0) {
            DungeonDelving.LOGGER.warn("Wave {} in the room at {} couldn't spawn any mobs; skipping it", waves.wave(), roomMin);
        }
    }

    /** Spawns one of the dungeon faction's mobs on open floor somewhere in the room, away from the drum. */
    private static boolean spawnWaveMob(ServerLevel level, BlockPos roomMin, DungeonProgress progress, int region) {
        RandomSource random = level.getRandom();
        var type = SpawnerMobs.pick(level, random, progress.faction(), progress.tier());
        if (type.isEmpty()) {
            return false;
        }
        for (int attempt = 0; attempt < 30; attempt++) {
            BlockPos pos = roomMin.offset(3 + random.nextInt(RoomSpec.ROOM_SIZE - 6), 1 + random.nextInt(4), 3 + random.nextInt(RoomSpec.ROOM_SIZE - 6));
            if (!level.getBlockState(pos).isAir() || !level.getBlockState(pos.above()).isAir() || !level.getBlockState(pos.below()).isSolid()) {
                continue;
            }
            if (pos.distManhattan(roomMin.offset(RoomSpec.ROOM_SIZE / 2, 1, RoomSpec.ROOM_SIZE / 2)) < 6) {
                continue;
            }
            if (type.get().value().spawn(level, pos, MobSpawnType.EVENT) instanceof Mob mob) {
                mob.setPersistenceRequired();
                CompoundTag tag = new CompoundTag();
                tag.putInt("region", region);
                tag.putLong("room", roomMin.asLong());
                mob.getPersistentData().put(WAVE_TAG, tag);
                ServerPlayer target = (ServerPlayer) level.getNearestPlayer(mob, 48);
                if (target != null && !target.isCreative() && !target.isSpectator()) {
                    mob.setTarget(target);
                }
                return true;
            }
        }
        return false;
    }

    private static void finish(ServerLevel level, int region, BlockPos roomMin, DungeonProgress progress, WaveRoom waves) {
        unseal(level, waves);
        BlockPos chest = waves.drum().above();
        if (level.getBlockState(chest).isAir()) {
            DungeonMarkers.placeChest(level, chest, DungeonMarkers.roomLoot(progress.tier()), level.getRandom());
        }
        progress.removeWaveRoom(roomMin.asLong());
        DungeonRegions.get().progressChanged();
        DungeonChallenges.roomCleared(level, region, roomMin, Vec3.atCenterOf(waves.drum().above(2)),
                Component.translatable("message.dungeondelving.waves_done"));
    }

    /** The wave mobs of one room still alive. */
    static List<Mob> waveMobs(ServerLevel level, BlockPos roomMin) {
        return level.getEntitiesOfClass(Mob.class, inside(roomMin).inflate(2), mob -> mob.isAlive()
                && mob.getPersistentData().contains(WAVE_TAG) && mob.getPersistentData().getCompound(WAVE_TAG).getLong("room") == roomMin.asLong());
    }

    /** Once a second: move running rooms on when a wave is dead, light up stragglers, and reset rooms everyone left. */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % CHECK_INTERVAL != 0) {
            return;
        }
        ServerLevel level = event.getServer().getLevel(DungeonDimension.DUNGEON);
        if (level == null) {
            return;
        }
        Set<Integer> regions = new HashSet<>();
        for (DungeonRun run : DungeonRegions.get().runs()) {
            regions.add(run.region());
        }
        for (int region : regions) {
            DungeonRegions.get().progress(region).ifPresent(progress -> {
                for (Map.Entry<Long, WaveRoom> entry : Map.copyOf(progress.waveRooms()).entrySet()) {
                    if (entry.getValue().started()) {
                        tickRoom(level, region, BlockPos.of(entry.getKey()), progress, entry.getValue());
                    }
                }
            });
        }
    }

    private static void tickRoom(ServerLevel level, int region, BlockPos roomMin, DungeonProgress progress, WaveRoom waves) {
        AABB inside = inside(roomMin);
        List<Mob> mobs = waveMobs(level, roomMin);
        boolean anyoneInside = level.players().stream().anyMatch(player -> player.isAlive() && !player.isSpectator() && inside.contains(player.position()));
        if (!anyoneInside) {
            mobs.forEach(Mob::discard);
            unseal(level, waves);
            waves.reset();
            DungeonRegions.get().progressChanged();
            DungeonChallenges.tellRegion(level, region, Component.translatable("message.dungeondelving.waves_reset").withStyle(ChatFormatting.GRAY));
            return;
        }
        if (!mobs.isEmpty()) {
            if (mobs.size() <= GLOW_WHEN_LEFT) {
                mobs.forEach(mob -> mob.addEffect(new MobEffectInstance(MobEffects.GLOWING, CHECK_INTERVAL * 2, 0, false, false)));
            }
            return;
        }
        if (waves.nextWaveAt() < 0) {
            waves.scheduleNextWave(level.getGameTime() + (waves.lastWave() ? 0 : WAVE_GAP));
            DungeonRegions.get().progressChanged();
        }
        if (level.getGameTime() >= waves.nextWaveAt()) {
            nextWave(level, region, roomMin, progress, waves);
        }
    }
}
