package io.github.ranger3462.dungeondelving.forging;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * Smithing flux, worked into every forged part. Better flux is needed for better parts: a part made with this flux is at
 * most {@link #maxStars} ★, however good its ingots (see {@link StarredRecipes}).
 */
public class FluxItem extends Item {
    public final int maxStars;

    public FluxItem(Properties properties, int maxStars) {
        super(properties);
        this.maxStars = maxStars;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.dungeondelving.flux", StarredItem.starsText(maxStars)).withStyle(ChatFormatting.GRAY));
    }
}
