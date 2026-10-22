package io.github.ranger3462.dungeondelving.dungeon.challenge;

import java.util.List;

import io.github.ranger3462.dungeondelving.dungeon.DungeonLoot;
import io.github.ranger3462.dungeondelving.dungeon.LootSweep;
import io.github.ranger3462.dungeondelving.registry.ModRegistries;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * A Dungeon Key: dropped when a challenge room is cleared, and used on the boss door. Keys belong to the dungeon they
 * came from, only work there, and are taken away when the run ends, so they can't be saved up for later runs.
 * <p>
 * Relic Shards (for collection rooms' altars) are run-bound items of this class too, so the same rules apply to them.
 */
public class DungeonKeyItem extends Item {
    public DungeonKeyItem(Properties properties) {
        super(properties);
    }

    /** A key for a dungeon region. */
    public static ItemStack forRegion(int region) {
        return forRegion(ModRegistries.DUNGEON_KEY.get(), region);
    }

    /** A Relic Shard for a dungeon region. */
    public static ItemStack relicFor(int region) {
        return forRegion(ModRegistries.RELIC_SHARD.get(), region);
    }

    private static ItemStack forRegion(Item item, int region) {
        ItemStack stack = new ItemStack(item);
        stack.set(ModRegistries.KEY_REGION.get(), region);
        return stack;
    }

    /** Whether a stack is a Relic Shard from this region. */
    public static boolean isRelicFrom(ItemStack stack, int region) {
        return stack.is(ModRegistries.RELIC_SHARD.get()) && Integer.valueOf(region).equals(stack.get(ModRegistries.KEY_REGION.get()));
    }

    /** Whether a stack is a key that works in this region. */
    public static boolean opens(ItemStack stack, int region) {
        return stack.is(ModRegistries.DUNGEON_KEY.get()) && Integer.valueOf(region).equals(stack.get(ModRegistries.KEY_REGION.get()));
    }

    /** Takes every Dungeon Key and Relic Shard away from a player, including ones stored in backpacks. Returns how many stacks. */
    public static int removeAll(ServerPlayer player) {
        return DungeonLoot.sweepPlayer(player, new LootSweep(stack -> stack.getItem() instanceof DungeonKeyItem, stack -> ItemStack.EMPTY));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(getDescriptionId() + ".tooltip").withStyle(ChatFormatting.GRAY));
    }
}
