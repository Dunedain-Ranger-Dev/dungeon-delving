package io.github.ranger3462.dungeondelving.forging;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** An item that carries a forging ★ (see {@link Forging#STARS}), shown after its name. */
public class StarredItem extends Item {
    public StarredItem(Properties properties) {
        super(properties);
    }

    @Override
    public Component getName(ItemStack stack) {
        MutableComponent name = super.getName(stack).copy();
        return stack.has(Forging.STARS.get()) ? name.append(" ").append(starsText(Forging.stars(stack))) : name;
    }

    /** A starred item with a line of tooltip. */
    public static class WithTooltip extends StarredItem {
        private final String tooltip;

        public WithTooltip(Properties properties, String tooltip) {
            super(properties);
            this.tooltip = tooltip;
        }

        public static java.util.function.Function<Properties, StarredItem> of(String tooltip) {
            return properties -> new WithTooltip(properties, tooltip);
        }

        @Override
        public void appendHoverText(ItemStack stack, TooltipContext context, java.util.List<Component> lines, net.minecraft.world.item.TooltipFlag flag) {
            lines.add(Component.translatable(tooltip).withStyle(ChatFormatting.GRAY));
        }
    }

    /** "★★★" in gold, for a ★ count. */
    public static Component starsText(int stars) {
        return Component.literal("★".repeat(Math.max(0, stars))).withStyle(ChatFormatting.GOLD);
    }
}
