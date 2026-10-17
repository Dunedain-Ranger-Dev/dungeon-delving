package io.github.ranger3462.dungeondelving.dungeon;

import java.util.List;
import java.util.function.Consumer;

import io.github.ranger3462.dungeondelving.DungeonDelving;
import io.github.ranger3462.dungeondelving.registry.ModRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.util.Unit;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.fml.ModList;

/**
 * Tracks the dungeon ores found during a run. Items in the {@code dungeondelving:lost_on_failure} tag that are generated
 * in the dungeon dimension (chest loot, mob drops, block drops) are marked as dungeon loot. Leaving through the exit
 * claims them; failing the run takes them away. Everything else found in a dungeon is the player's to keep.
 */
public final class DungeonLoot {
    /** Items that are lost if the run fails: by default, the Dungeon Delving ores and their raw ores, ingots and blocks. */
    public static final TagKey<Item> LOST_ON_FAILURE = TagKey.create(Registries.ITEM, DungeonDelving.id("lost_on_failure"));

    /**
     * Items that only come from dungeons: they're removed from loot generated anywhere else (see
     * {@link RemoveDungeonOnlyModifier}). Empty in the mod; the pack fills it with, e.g., magic mods' upgrade materials.
     */
    public static final TagKey<Item> DUNGEON_ONLY = TagKey.create(Registries.ITEM, DungeonDelving.id("dungeon_only"));

    private DungeonLoot() {}

    public static boolean isLoot(ItemStack stack) {
        return stack.has(ModRegistries.DUNGEON_LOOT.get());
    }

    public static void mark(ItemStack stack) {
        if (!stack.isEmpty()) {
            stack.set(ModRegistries.DUNGEON_LOOT.get(), Unit.INSTANCE);
        }
    }

    /** Marks freshly generated items in the {@link #LOST_ON_FAILURE} tag when they were generated in the dungeon dimension. */
    public static void markIfInDungeon(List<ItemStack> generated, ResourceKey<Level> dimension) {
        if (dimension == DungeonDimension.DUNGEON) {
            generated.stream().filter(stack -> stack.is(LOST_ON_FAILURE)).forEach(DungeonLoot::mark);
        }
    }

    /** Removes {@link #DUNGEON_ONLY} items from freshly generated loot, unless it was generated in the dungeon dimension. */
    public static void removeDungeonOnly(List<ItemStack> generated, ResourceKey<Level> dimension) {
        if (dimension != DungeonDimension.DUNGEON) {
            generated.removeIf(stack -> stack.is(DUNGEON_ONLY));
        }
    }

    /**
     * A successful exit: the player keeps everything, and their dungeon loot becomes ordinary items, including loot
     * stored inside backpacks and other containers they carry. Returns how many loot stacks were claimed.
     */
    public static int claim(ServerPlayer player) {
        return sweepPlayer(player, new LootSweep(stack -> {
            stack.remove(ModRegistries.DUNGEON_LOOT.get());
            return stack;
        }));
    }

    /**
     * A failed run: removes dungeon loot from everything the player carries (inventory, ender chest, Curios slots, and
     * backpacks or other containers inside those), and damages their gear by the given fraction of its max durability,
     * without fully breaking anything. Returns how many loot stacks were lost.
     */
    public static int applyFailurePenalty(ServerPlayer player, double gearDamage) {
        int lost = sweepPlayer(player, new LootSweep(stack -> ItemStack.EMPTY));
        if (gearDamage > 0) {
            forEachStack(player.getInventory(), stack -> damage(stack, gearDamage));
        }
        player.inventoryMenu.broadcastChanges();
        return lost;
    }

    /** Runs a sweep over everything the player carries. Returns how many loot stacks it found. */
    private static int sweepPlayer(ServerPlayer player, LootSweep sweep) {
        sweep.sweep(player.getInventory());
        sweep.sweep(player.getEnderChestInventory());
        if (ModList.get().isLoaded("curios")) {
            CuriosLoot.sweep(player, sweep);
        }
        sweep.overflow().forEach(stack -> player.getInventory().placeItemBackInInventory(stack));
        return sweep.found();
    }

    private static void damage(ItemStack stack, double fraction) {
        if (!stack.isDamageableItem()) {
            return;
        }
        int max = stack.getMaxDamage();
        int added = Mth.ceil(max * fraction);
        // Leave at least 1 durability so the penalty never destroys an item outright.
        stack.setDamageValue(Math.min(max - 1, stack.getDamageValue() + added));
    }

    private static void forEachStack(Container container, Consumer<ItemStack> action) {
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            if (!stack.isEmpty()) {
                action.accept(stack);
            }
        }
    }
}
