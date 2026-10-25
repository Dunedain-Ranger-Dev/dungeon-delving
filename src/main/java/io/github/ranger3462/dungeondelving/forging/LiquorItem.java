package io.github.ranger3462.dungeondelving.forging;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * A craft's vat liquid: tanning liquor (Leatherworking), sizing (Weaving) or a finish (Woodworking), brewed in a
 * cooking pot and poured into a cauldron to make that craft's vat; or a refining salt (Goldsmithing), put into a
 * crucible's fuel slot. What soaks in it comes out at its {@link #stars}:
 * the liquid is to these crafts what the heated ingot's tier is to Forging, and the better ones take longer.
 */
public class LiquorItem extends Item {
    public final int stars;
    public final Profession profession;

    public LiquorItem(Properties properties, int stars, Profession profession) {
        super(properties);
        this.stars = stars;
        this.profession = profession;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        // A vat's time is for its whole batch; a crucible's for each ingot.
        long ticks = profession == Profession.GOLDSMITHING ? CrucibleBlockEntity.ticksFor(stars, false) : SoakingVatBlock.soakTicks(stars);
        tooltip.add(Component.translatable("tooltip.dungeondelving.liquor." + profession.id(), StarredItem.starsText(stars), SoakingVatBlock.minutes(ticks))
                .withStyle(ChatFormatting.GRAY));
    }
}
