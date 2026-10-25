package io.github.ranger3462.dungeondelving.forging;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * A bottle of tanning liquor, brewed in a cooking pot from tree bark (and, for the better ones, a dungeon herb). Pour it
 * into a cauldron to make a Tanning Vat. Leather tanned in it is at most {@link #maxStars} ★: the liquor is to
 * Leatherworking what flux is to Forging.
 */
public class LiquorItem extends Item {
    public final int maxStars;

    public LiquorItem(Properties properties, int maxStars) {
        super(properties);
        this.maxStars = maxStars;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.dungeondelving.liquor", StarredItem.starsText(maxStars)).withStyle(ChatFormatting.GRAY));
    }
}
