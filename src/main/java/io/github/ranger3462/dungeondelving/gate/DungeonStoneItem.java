package io.github.ranger3462.dungeondelving.gate;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/** A dungeon stone: put it in a Gate Tuner to change the next run started from that gate. */
public class DungeonStoneItem extends Item {
    private final DungeonStone stone;

    public DungeonStoneItem(DungeonStone stone, Properties properties) {
        super(properties);
        this.stone = stone;
    }

    public DungeonStone stone() {
        return stone;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.dungeondelving." + stone.id() + "_stone.tooltip").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.dungeondelving.dungeon_stone.tooltip").withStyle(ChatFormatting.DARK_GRAY));
    }
}
