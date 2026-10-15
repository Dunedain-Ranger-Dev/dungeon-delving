package io.github.ranger3462.dungeondelving.dungeon.bag;

import java.util.List;

import io.github.ranger3462.dungeondelving.dungeon.DungeonRegions;
import io.github.ranger3462.dungeondelving.registry.ModRegistries;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * Given on entering a dungeon. Right-click to look inside your dungeon bag. The bag's contents live with the run, not
 * the item, and the item is taken away when the run ends.
 */
public class DungeonBagItem extends Item {
    public DungeonBagItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResultHolder.success(held);
        }
        DungeonRegions.get().bag(player.getUUID()).ifPresentOrElse(bag -> serverPlayer.openMenu(new SimpleMenuProvider(
                (id, inventory, opener) -> DungeonChestMenu.viewing(id, inventory, bag),
                Component.translatable("container.dungeondelving.dungeon_bag", bag.usedSlots(), bag.size()))), () -> {
                    // Left over from a run that already ended.
                    held.setCount(0);
                    serverPlayer.sendSystemMessage(Component.literal("The bag crumbles; its dungeon is gone.").withStyle(ChatFormatting.GRAY));
                });
        return InteractionResultHolder.consume(held);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.dungeondelving.dungeon_bag.tooltip").withStyle(ChatFormatting.GRAY));
    }

    /** Takes every dungeon bag out of a container, such as a player's inventory or ender chest. */
    public static void removeAll(Container container) {
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            if (container.getItem(slot).is(ModRegistries.DUNGEON_BAG.get())) {
                container.setItem(slot, ItemStack.EMPTY);
            }
        }
    }
}
