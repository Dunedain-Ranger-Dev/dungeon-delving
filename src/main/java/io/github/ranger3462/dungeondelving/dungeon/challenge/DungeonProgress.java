package io.github.ranger3462.dungeondelving.dungeon.challenge;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.Tag;

/**
 * How far a dungeon has got towards its boss: the keys needed and used on the boss door, where the door and the boss
 * are, and how many hunt targets are left in each challenge room. One per dungeon region; saved with the regions.
 */
public final class DungeonProgress {
    private final int tier;
    private final int keysNeeded;
    private final List<BlockPos> door;
    private BlockPos bossSpawn;
    private int keysUsed;
    private boolean bossSpawned;
    /** Hunt targets still alive, by the room they belong to (the room's minimum corner). */
    private final Map<Long, Integer> hunters = new HashMap<>();

    public DungeonProgress(int tier, int keysNeeded, List<BlockPos> door, BlockPos bossSpawn) {
        this.tier = tier;
        this.keysNeeded = keysNeeded;
        this.door = List.copyOf(door);
        this.bossSpawn = bossSpawn;
    }

    /** Keys the boss door needs in a dungeon of this tier: two per tier. */
    public static int keysFor(int tier) {
        return tier * 2;
    }

    public int tier() {
        return tier;
    }

    public int keysNeeded() {
        return keysNeeded;
    }

    public int keysUsed() {
        return keysUsed;
    }

    public boolean doorOpen() {
        return keysUsed >= keysNeeded;
    }

    public boolean bossSpawned() {
        return bossSpawned;
    }

    public List<BlockPos> door() {
        return door;
    }

    public BlockPos bossSpawn() {
        return bossSpawn;
    }

    /** Where the boss marker in the boss room was, once that room is built. */
    public void setBossSpawn(BlockPos pos) {
        bossSpawn = pos;
    }

    public void markBossSpawned() {
        bossSpawned = true;
    }

    /**
     * Uses one key on the boss door. Returns false if the door is already open. Check {@link #doorOpen()} afterwards
     * to see whether this key opened it.
     */
    public boolean useKey() {
        if (doorOpen()) {
            return false;
        }
        keysUsed++;
        return true;
    }

    /** Counts a hunt target spawned in a room. */
    public void addHunter(long room) {
        hunters.merge(room, 1, Integer::sum);
    }

    /** Counts a hunt target killed. Returns how many are left in its room, or empty if the room isn't tracked. */
    public Optional<Integer> hunterKilled(long room) {
        Integer left = hunters.get(room);
        if (left == null) {
            return Optional.empty();
        }
        int now = Math.max(0, left - 1);
        if (now == 0) {
            hunters.remove(room);
        } else {
            hunters.put(room, now);
        }
        return Optional.of(now);
    }

    public int huntersLeft(long room) {
        return hunters.getOrDefault(room, 0);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("tier", tier);
        tag.putInt("keys_needed", keysNeeded);
        tag.putInt("keys_used", keysUsed);
        tag.putBoolean("boss_spawned", bossSpawned);
        tag.put("door", new LongArrayTag(door.stream().mapToLong(BlockPos::asLong).toArray()));
        if (bossSpawn != null) {
            tag.putLong("boss_spawn", bossSpawn.asLong());
        }
        ListTag hunts = new ListTag();
        hunters.forEach((room, left) -> {
            CompoundTag hunt = new CompoundTag();
            hunt.putLong("room", room);
            hunt.putInt("left", left);
            hunts.add(hunt);
        });
        tag.put("hunters", hunts);
        return tag;
    }

    public static DungeonProgress load(CompoundTag tag) {
        List<BlockPos> door = new ArrayList<>();
        for (long pos : tag.getLongArray("door")) {
            door.add(BlockPos.of(pos));
        }
        DungeonProgress progress = new DungeonProgress(tag.getInt("tier"), tag.getInt("keys_needed"), door,
                tag.contains("boss_spawn") ? BlockPos.of(tag.getLong("boss_spawn")) : null);
        progress.keysUsed = tag.getInt("keys_used");
        progress.bossSpawned = tag.getBoolean("boss_spawned");
        for (Tag huntTag : tag.getList("hunters", Tag.TAG_COMPOUND)) {
            CompoundTag hunt = (CompoundTag) huntTag;
            progress.hunters.put(hunt.getLong("room"), hunt.getInt("left"));
        }
        return progress;
    }
}
