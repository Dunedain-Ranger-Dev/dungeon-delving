package io.github.ranger3462.dungeondelving.forging;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * Hot metal (a heated or struck ingot). It starts cooling the first time it ticks outside a furnace, and is only
 * workable while it's still warm (see {@link #temperature}). Cold hot metal goes back in the furnace with no loss.
 */
public class HotMetalItem extends StarredItem {
    /** How long hot metal stays workable, in ticks, before the Forging skill slows it down. */
    public static final int BASE_COOL_TICKS = 600;

    public HotMetalItem(Properties properties) {
        super(properties);
    }

    /** How hot a stack is, from 1 (fresh from the furnace) to 0 (cold), if it cools over {@code coolTicks}. */
    public static float temperature(ItemStack stack, long gameTime, int coolTicks) {
        Long heatedAt = stack.get(Forging.HEATED_AT.get());
        if (heatedAt == null) {
            return 1.0f;
        }
        return Mth.clamp(1.0f - (gameTime - heatedAt) / (float) coolTicks, 0.0f, 1.0f);
    }

    public static boolean isHot(ItemStack stack, long gameTime, int coolTicks) {
        return stack.getItem() instanceof HotMetalItem && temperature(stack, gameTime, coolTicks) > 0.0f;
    }

    /** Starts the cooling clock, if it hasn't started yet. */
    public static void startCooling(ItemStack stack, Level level) {
        if (!level.isClientSide() && !stack.has(Forging.HEATED_AT.get())) {
            stack.set(Forging.HEATED_AT.get(), level.getGameTime());
        }
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        startCooling(stack, level);
    }

    @Override
    public boolean onEntityItemUpdate(ItemStack stack, ItemEntity entity) {
        startCooling(stack, entity.level());
        return false;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.dungeondelving.aiming_for", starsText(Forging.stars(stack))).withStyle(ChatFormatting.GRAY));
        Level level = context.level();
        float heat = level == null ? 1.0f : temperature(stack, level.getGameTime(), BASE_COOL_TICKS);
        tooltip.add(heatText(heat));
    }

    /** Glowing hot, hot, cooling or cold. Roughly, since the Forging skill changes how fast metal cools. */
    public static Component heatText(float heat) {
        if (heat > 0.66f) {
            return Component.translatable("tooltip.dungeondelving.heat.glowing").withStyle(ChatFormatting.GOLD);
        } else if (heat > 0.33f) {
            return Component.translatable("tooltip.dungeondelving.heat.hot").withStyle(ChatFormatting.RED);
        } else if (heat > 0.0f) {
            return Component.translatable("tooltip.dungeondelving.heat.cooling").withStyle(ChatFormatting.DARK_RED);
        }
        return Component.translatable("tooltip.dungeondelving.heat.cold").withStyle(ChatFormatting.DARK_GRAY);
    }
}
