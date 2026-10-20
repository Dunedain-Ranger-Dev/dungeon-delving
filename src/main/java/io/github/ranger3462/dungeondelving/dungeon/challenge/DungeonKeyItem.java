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
 */
public class DungeonKeyItem extends Item {
    public DungeonKeyItem(Properties properties) {
        super(properties);
    }

    /** A key for a dungeon region. */
    public static ItemStack forRegion(int region) {
        ItemStack key = new ItemStack(ModRegistries.DUNGEON_KEY.get());
        key.set(ModRegistries.KEY_REGION.get(), region);
        return key;
    }

    /** Whether a stack is a key that works in this region. */
    public static boolean opens(ItemStack stack, int region) {
        return stack.is(ModRegistries.DUNGEON_KEY.get()) && Integer.valueOf(region).equals(stack.get(ModRegistries.KEY_REGION.get()));
    }

    /** Takes every Dungeon Key away from a player, including keys stored in backpacks. Returns how many stacks. */
    public static int removeAll(ServerPlayer player) {
        return DungeonLoot.sweepPlayer(player, new LootSweep(stack -> stack.is(ModRegistries.DUNGEON_KEY.get()), stack -> ItemStack.EMPTY));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.dungeondelving.dungeon_key.tooltip").withStyle(ChatFormatting.GRAY));
    }
}
