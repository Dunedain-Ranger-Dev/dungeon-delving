package io.github.ranger3462.dungeondelving.vault;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;
import java.util.UUID;

import io.github.ranger3462.dungeondelving.DungeonDelving;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/**
 * Hands out vault regions to runs and tracks which are in use.
 * <p>
 * A region released by a finished run is not reused right away, because it still holds that run's blocks. On the
 * next server start, before the vault dimension loads, its region files are deleted and it becomes free again.
 */
@EventBusSubscriber(modid = DungeonDelving.MODID)
public class VaultRegions {
    private static final String[] STORAGE_FOLDERS = { "region", "entities", "poi" };
    private static VaultRegions current;

    private int nextRegion;
    private final TreeSet<Integer> free = new TreeSet<>();
    private final TreeSet<Integer> released = new TreeSet<>();
    private final Map<UUID, VaultRun> runs = new HashMap<>();
    private Path file;

    /** The region tracker for the running server. */
    public static VaultRegions get() {
        if (current == null) {
            throw new IllegalStateException("Vault regions are only available while a server is running");
        }
        return current;
    }

    // --- Regions ---

    /** Picks a clean region: the lowest wiped region if there is one, otherwise a brand new one. */
    public int allocate() {
        Integer reused = free.pollFirst();
        int region = reused != null ? reused : nextRegion++;
        save();
        return region;
    }

    /** Marks a region as finished. It is wiped and becomes reusable on the next server start. */
    public void release(int region) {
        boolean stillUsed = runs.values().stream().anyMatch(run -> run.region() == region);
        if (!stillUsed) {
            released.add(region);
            save();
        }
    }

    public Collection<Integer> freeRegions() {
        return Collections.unmodifiableSet(free);
    }

    public Collection<Integer> releasedRegions() {
        return Collections.unmodifiableSet(released);
    }

    public int regionsCreated() {
        return nextRegion;
    }

    // --- Runs ---

    public Optional<VaultRun> run(UUID player) {
        return Optional.ofNullable(runs.get(player));
    }

    public Collection<VaultRun> runs() {
        return Collections.unmodifiableCollection(runs.values());
    }

    public void startRun(VaultRun run) {
        runs.put(run.player(), run);
        save();
    }

    /** Ends a player's run and releases its region if no one else is using it. */
    public Optional<VaultRun> endRun(UUID player) {
        VaultRun run = runs.remove(player);
        if (run != null) {
            release(run.region());
            save();
        }
        return Optional.ofNullable(run);
    }

    // --- Wiping released regions ---

    /** Deletes the stored chunks of every released region in the given dimension folder, making them free again. */
    public List<Integer> wipeReleased(Path dimensionFolder) throws IOException {
        List<Integer> wiped = new ArrayList<>(released);
        for (int region : wiped) {
            int firstX = VaultDimension.firstRegionFileX(region);
            for (int dx = 0; dx < VaultDimension.regionFilesPerSide(); dx++) {
                for (int z = 0; z < VaultDimension.regionFilesPerSide(); z++) {
                    String name = "r." + (firstX + dx) + "." + z + ".mca";
                    for (String folder : STORAGE_FOLDERS) {
                        Files.deleteIfExists(dimensionFolder.resolve(folder).resolve(name));
                    }
                }
            }
        }
        released.clear();
        free.addAll(wiped);
        save();
        return wiped;
    }

    // --- Saving ---

    public CompoundTag save(CompoundTag tag) {
        tag.putInt("next_region", nextRegion);
        tag.put("free", new IntArrayTag(new ArrayList<>(free)));
        tag.put("released", new IntArrayTag(new ArrayList<>(released)));
        ListTag runList = new ListTag();
        runs.values().forEach(run -> runList.add(run.save()));
        tag.put("runs", runList);
        return tag;
    }

    public static VaultRegions load(CompoundTag tag) {
        VaultRegions regions = new VaultRegions();
        regions.nextRegion = tag.getInt("next_region");
        for (int region : tag.getIntArray("free")) {
            regions.free.add(region);
        }
        for (int region : tag.getIntArray("released")) {
            regions.released.add(region);
        }
        for (Tag runTag : tag.getList("runs", Tag.TAG_COMPOUND)) {
            VaultRun run = VaultRun.load((CompoundTag) runTag);
            regions.runs.put(run.player(), run);
        }
        return regions;
    }

    private void save() {
        if (file == null) {
            return; // Not attached to a world, e.g. in tests.
        }
        try {
            Files.createDirectories(file.getParent());
            NbtIo.writeCompressed(save(new CompoundTag()), file);
        } catch (IOException e) {
            DungeonDelving.LOGGER.error("Failed to save vault regions to {}", file, e);
        }
    }

    // --- Server lifecycle ---

    @SubscribeEvent
    public static void onServerAboutToStart(ServerAboutToStartEvent event) {
        MinecraftServer server = event.getServer();
        Path worldRoot = server.getWorldPath(LevelResource.ROOT);
        Path file = worldRoot.resolve(DungeonDelving.MODID).resolve("vault_regions.dat");

        VaultRegions regions = new VaultRegions();
        if (Files.exists(file)) {
            try {
                regions = load(NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap()));
            } catch (IOException e) {
                DungeonDelving.LOGGER.error("Failed to read vault regions from {}, starting fresh", file, e);
            }
        }
        regions.file = file;

        // The vault dimension has not loaded yet, so its region files are safe to delete.
        try {
            List<Integer> wiped = regions.wipeReleased(DimensionType.getStorageFolder(VaultDimension.VAULT, worldRoot));
            if (!wiped.isEmpty()) {
                DungeonDelving.LOGGER.info("Wiped finished vault regions {}", wiped);
            }
        } catch (IOException e) {
            DungeonDelving.LOGGER.error("Failed to wipe finished vault regions", e);
        }
        current = regions;
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        current = null;
    }
}
