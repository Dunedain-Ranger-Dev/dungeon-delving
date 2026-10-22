package io.github.ranger3462.dungeondelving.dungeon.challenge;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.LongArrayTag;

/**
 * A wave defense room's state: which wave it's on (0 before it starts), how many it has, when the next one comes, and
 * the blocks sealing its doorways while it runs. Saved with its dungeon's {@link DungeonProgress}.
 */
public final class WaveRoom {
    private final BlockPos drum;
    private final int waves;
    private int wave;
    private long nextWaveAt = -1;
    private final List<BlockPos> seal = new ArrayList<>();

    public WaveRoom(BlockPos drum, int waves) {
        this.drum = drum;
        this.waves = waves;
    }

    /** Waves a room has in a dungeon of this tier: 3 at tiers 1 and 2, 4 at tiers 3 and 4. */
    public static int wavesFor(int tier) {
        return tier <= 2 ? 3 : 4;
    }

    /** Mobs in a wave (counted from 1) in a dungeon of this tier: one more each wave, and one more per tier. */
    public static int mobsFor(int tier, int wave) {
        return 1 + tier + wave;
    }

    public BlockPos drum() {
        return drum;
    }

    public int waves() {
        return waves;
    }

    /** The wave being fought, from 1; 0 before the room starts. */
    public int wave() {
        return wave;
    }

    public boolean started() {
        return wave > 0;
    }

    /** Moves on to the next wave. Returns false if the last one is done. */
    public boolean advance() {
        if (wave >= waves) {
            return false;
        }
        wave++;
        nextWaveAt = -1;
        return true;
    }

    public long nextWaveAt() {
        return nextWaveAt;
    }

    public void scheduleNextWave(long gameTime) {
        nextWaveAt = gameTime;
    }

    public boolean lastWave() {
        return wave >= waves;
    }

    /** The doorway blocks sealed while the room runs. */
    public List<BlockPos> seal() {
        return seal;
    }

    /** Back to how it was before it started (everyone left or fell); the seal must already be taken down. */
    public void reset() {
        wave = 0;
        nextWaveAt = -1;
        seal.clear();
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putLong("drum", drum.asLong());
        tag.putInt("waves", waves);
        tag.putInt("wave", wave);
        tag.putLong("next_wave_at", nextWaveAt);
        tag.put("seal", new LongArrayTag(seal.stream().mapToLong(BlockPos::asLong).toArray()));
        return tag;
    }

    public static WaveRoom load(CompoundTag tag) {
        WaveRoom room = new WaveRoom(BlockPos.of(tag.getLong("drum")), tag.getInt("waves"));
        room.wave = tag.getInt("wave");
        room.nextWaveAt = tag.getLong("next_wave_at");
        for (long pos : tag.getLongArray("seal")) {
            room.seal.add(BlockPos.of(pos));
        }
        return room;
    }
}
