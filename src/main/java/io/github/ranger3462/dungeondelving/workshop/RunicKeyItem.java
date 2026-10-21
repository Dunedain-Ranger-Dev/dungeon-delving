package io.github.ranger3462.dungeondelving.workshop;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * A Runic Key: opens a dungeon of its tier at the Dungeon Gate, and is used up doing it. Made at the Runic Workshop;
 * each tier's key is made from the key below it.
 */
public class RunicKeyItem extends Item {
    private final int tier;

    public RunicKeyItem(int tier, Properties properties) {
        super(properties);
        this.tier = tier;
    }

    public int tier() {
        return tier;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.dungeondelving.runic_key.tooltip", tier).withStyle(ChatFormatting.GRAY));
    }
}
