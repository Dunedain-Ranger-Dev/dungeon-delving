package io.github.ranger3462.dungeondelving.dungeon;

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
import io.github.ranger3462.dungeondelving.dungeon.bag.DungeonBag;
import net.minecraft.core.HolderLookup;
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
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

/**
 * Hands out dungeon regions to runs and tracks which are in use.
 * <p>
 * A region released by a finished run is not reused right away, because it still holds that run's blocks. On the
 * next server start, before the dungeon dimension loads, its region files are deleted and it becomes free again.
 */
@EventBusSubscriber(modid = DungeonDelving.MODID)
public class DungeonRegions {
    private static final String[] STORAGE_FOLDERS = { "region", "entities", "poi" };
    private static DungeonRegions current;

    private int nextRegion;
    private final TreeSet<Integer> free = new TreeSet<>();
    private final TreeSet<Integer> released = new TreeSet<>();
    private final Map<UUID, DungeonRun> runs = new HashMap<>();
    /** Ticks each active run has been going while its player was in the dungeon. */
    private final Map<UUID, Integer> elapsed = new HashMap<>();
    /** Inventories kept from players who died in a dungeon, given back when they respawn. */
    private final Map<UUID, ListTag> heldInventories = new HashMap<>();
    /** Each active run's loot bag. Changes as players loot, so it's saved with run times rather than on every change. */
    private final Map<UUID, DungeonBag> bags = new HashMap<>();
    private Path file;
    private HolderLookup.Provider registries;

    /** The region tracker for the running server. */
    public static DungeonRegions get() {
        if (current == null) {
            throw new IllegalStateException("Dungeon regions are only available while a server is running");
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

    public Optional<DungeonRun> run(UUID player) {
        return Optional.ofNullable(runs.get(player));
    }

    public Collection<DungeonRun> runs() {
        return Collections.unmodifiableCollection(runs.values());
    }

    public void startRun(DungeonRun run) {
        runs.put(run.player(), run);
        save();
    }

    public int elapsedTicks(UUID player) {
        return elapsed.getOrDefault(player, 0);
    }

    /** Adds a tick to the player's run time. Not saved immediately; see {@link #saveNow()}. */
    public int tick(UUID player) {
        return elapsed.merge(player, 1, Integer::sum);
    }

    public void holdInventory(UUID player, ListTag inventory) {
        heldInventories.put(player, inventory);
        save();
    }

    /** Takes back an inventory held since the player died in a dungeon, if there is one. */
    public Optional<ListTag> takeHeldInventory(UUID player) {
        ListTag inventory = heldInventories.remove(player);
        if (inventory != null) {
            save();
        }
        return Optional.ofNullable(inventory);
    }

    public void startBag(UUID player, DungeonBag bag) {
        bags.put(player, bag);
        save();
    }

    public Optional<DungeonBag> bag(UUID player) {
        return Optional.ofNullable(bags.get(player));
    }

    /** Removes and returns a player's bag, e.g. to empty it into their inventory or throw it away. */
    public Optional<DungeonBag> takeBag(UUID player) {
        DungeonBag bag = bags.remove(player);
        if (bag != null) {
            save();
        }
        return Optional.ofNullable(bag);
    }

    /** Ends a player's run and releases its region if no one else is using it. Any bag left over is discarded. */
    public Optional<DungeonRun> endRun(UUID player) {
        elapsed.remove(player);
        bags.remove(player);
        DungeonRun run = runs.remove(player);
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
            int firstX = DungeonDimension.firstRegionFileX(region);
            for (int dx = 0; dx < DungeonDimension.regionFilesPerSide(); dx++) {
                for (int z = 0; z < DungeonDimension.regionFilesPerSide(); z++) {
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

    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("next_region", nextRegion);
        tag.put("free", new IntArrayTag(new ArrayList<>(free)));
        tag.put("released", new IntArrayTag(new ArrayList<>(released)));
        ListTag runList = new ListTag();
        runs.values().forEach(run -> {
            CompoundTag runTag = run.save();
            runTag.putInt("elapsed_ticks", elapsedTicks(run.player()));
            runList.add(runTag);
        });
        tag.put("runs", runList);
        ListTag heldList = new ListTag();
        heldInventories.forEach((player, inventory) -> {
            CompoundTag held = new CompoundTag();
            held.putUUID("player", player);
            held.put("inventory", inventory);
            heldList.add(held);
        });
        tag.put("held_inventories", heldList);
        ListTag bagList = new ListTag();
        bags.forEach((player, bag) -> {
            CompoundTag bagTag = bag.save(registries);
            bagTag.putUUID("player", player);
            bagList.add(bagTag);
        });
        tag.put("bags", bagList);
        return tag;
    }

    public static DungeonRegions load(CompoundTag tag, HolderLookup.Provider registries) {
        DungeonRegions regions = new DungeonRegions();
        regions.nextRegion = tag.getInt("next_region");
        for (int region : tag.getIntArray("free")) {
            regions.free.add(region);
        }
        for (int region : tag.getIntArray("released")) {
            regions.released.add(region);
        }
        for (Tag runTag : tag.getList("runs", Tag.TAG_COMPOUND)) {
            DungeonRun run = DungeonRun.load((CompoundTag) runTag);
            regions.runs.put(run.player(), run);
            regions.elapsed.put(run.player(), ((CompoundTag) runTag).getInt("elapsed_ticks"));
        }
        for (Tag heldTag : tag.getList("held_inventories", Tag.TAG_COMPOUND)) {
            CompoundTag held = (CompoundTag) heldTag;
            regions.heldInventories.put(held.getUUID("player"), held.getList("inventory", Tag.TAG_COMPOUND));
        }
        for (Tag bagTag : tag.getList("bags", Tag.TAG_COMPOUND)) {
            CompoundTag bag = (CompoundTag) bagTag;
            regions.bags.put(bag.getUUID("player"), DungeonBag.load(bag, registries));
        }
        return regions;
    }

    /** Writes the current state to disk, including run times, which aren't saved on every tick. */
    public void saveNow() {
        save();
    }

    private void save() {
        if (file == null) {
            return; // Not attached to a world, e.g. in tests.
        }
        try {
            Files.createDirectories(file.getParent());
            NbtIo.writeCompressed(save(new CompoundTag(), registries), file);
        } catch (IOException e) {
            DungeonDelving.LOGGER.error("Failed to save dungeon regions to {}", file, e);
        }
    }

    // --- Server lifecycle ---

    @SubscribeEvent
    public static void onServerAboutToStart(ServerAboutToStartEvent event) {
        MinecraftServer server = event.getServer();
        Path worldRoot = server.getWorldPath(LevelResource.ROOT);
        Path file = worldRoot.resolve(DungeonDelving.MODID).resolve("dungeon_regions.dat");

        DungeonRegions regions = new DungeonRegions();
        if (Files.exists(file)) {
            try {
                regions = load(NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap()), server.registryAccess());
            } catch (IOException e) {
                DungeonDelving.LOGGER.error("Failed to read dungeon regions from {}, starting fresh", file, e);
            }
        }
        regions.file = file;
        regions.registries = server.registryAccess();

        // The dungeon dimension has not loaded yet, so its region files are safe to delete.
        try {
            List<Integer> wiped = regions.wipeReleased(DimensionType.getStorageFolder(DungeonDimension.DUNGEON, worldRoot));
            if (!wiped.isEmpty()) {
                DungeonDelving.LOGGER.info("Wiped finished dungeon regions {}", wiped);
            }
        } catch (IOException e) {
            DungeonDelving.LOGGER.error("Failed to wipe finished dungeon regions", e);
        }
        current = regions;
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        if (current != null) {
            current.save();
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        current = null;
    }
}
