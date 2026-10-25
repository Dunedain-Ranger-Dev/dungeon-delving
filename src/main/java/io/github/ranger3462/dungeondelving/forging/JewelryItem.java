package io.github.ranger3462.dungeondelving.forging;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * Goldsmithing's jewelry (DD-89), worn in a Curios slot: a ring (more health) or an amulet (more armor), better by its
 * ★. Without Curios it's just an ornament. The bonus is worked out here; {@link GoldCurios} hands it to Curios.
 */
public class JewelryItem extends StarredItem {
    public enum Kind {
        /** +1 max health a ★. */
        RING("ring", Attributes.MAX_HEALTH, 1.0),
        /** +0.5 armor a ★. */
        AMULET("necklace", Attributes.ARMOR, 0.5);

        /** The Curios slot it's worn in. */
        public final String slot;
        public final Holder<Attribute> attribute;
        public final double perStar;

        Kind(String slot, Holder<Attribute> attribute, double perStar) {
            this.slot = slot;
            this.attribute = attribute;
            this.perStar = perStar;
        }
    }

    public final Kind kind;

    public JewelryItem(Properties properties, Kind kind) {
        super(properties.stacksTo(1));
        this.kind = kind;
    }

    /** What this piece adds, for its ★. */
    public double bonus(ItemStack stack) {
        return kind.perStar * Forging.stars(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable("tooltip.dungeondelving.jewelry." + kind.name().toLowerCase(), String.format("%.1f", bonus(stack)))
                .withStyle(ChatFormatting.BLUE));
    }
}
