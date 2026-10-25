package io.github.ranger3462.dungeondelving.forging;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * A wood finish (Woodworking): oil or resin rubbed into a wooden piece as it's assembled. Like flux in a forged part, it
 * caps the piece's ★ ({@link StarredRecipes} treats it as flux).
 */
public class FinishItem extends FluxItem {
    public FinishItem(Properties properties, int maxStars) {
        super(properties, maxStars);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.dungeondelving.finish", StarredItem.starsText(maxStars)).withStyle(ChatFormatting.GRAY));
    }
}
