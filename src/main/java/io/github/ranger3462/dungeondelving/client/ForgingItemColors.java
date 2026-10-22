package io.github.ranger3462.dungeondelving.client;

import io.github.ranger3462.dungeondelving.DungeonDelving;
import io.github.ranger3462.dungeondelving.forging.Forging;
import io.github.ranger3462.dungeondelving.forging.HotMetalItem;
import net.minecraft.client.Minecraft;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;

/**
 * Colors forged iron. Hot metal glows orange fresh from the furnace, then turns red, faded red, and back to plain iron
 * as it cools, so you can see at a glance how long you have. Quenched and unstable ingots are dark; tempered ones are a
 * cool, bright steel. Only the base layer is tinted, so overlays (sparkles, cracks) keep their own colors.
 */
@EventBusSubscriber(modid = DungeonDelving.MODID, value = Dist.CLIENT)
public final class ForgingItemColors {
    /** Heat (1 = fresh from the furnace) to tint, from hottest to cold. */
    private static final float[] HEAT = { 1.0f, 0.66f, 0.33f, 0.15f, 0.0f };
    private static final int[][] COLORS = { { 255, 170, 70 }, { 255, 95, 45 }, { 225, 60, 50 }, { 190, 125, 120 }, { 255, 255, 255 } };
    private static final int QUENCHED = 0xFF8A8A92;
    private static final int TEMPERED = 0xFFE4ECFF;

    private ForgingItemColors() {}

    @SubscribeEvent
    public static void onRegisterItemColors(RegisterColorHandlersEvent.Item event) {
        event.register((stack, tintIndex) -> tintIndex == 0 ? heatColor(stack) : -1, Forging.HEATED_INGOT.get(), Forging.STRUCK_INGOT.get());
        event.register((stack, tintIndex) -> tintIndex == 0 ? QUENCHED : -1, Forging.QUENCHED_INGOT.get(), Forging.UNSTABLE_INGOT.get());
        event.register((stack, tintIndex) -> tintIndex == 0 ? TEMPERED : -1, Forging.TEMPERED_IRON_INGOT.get());
    }

    private static int heatColor(ItemStack stack) {
        Minecraft minecraft = Minecraft.getInstance();
        float heat = minecraft.level == null ? 1.0f : HotMetalItem.temperature(stack, minecraft.level.getGameTime(), HotMetalItem.BASE_COOL_TICKS);
        for (int i = 1; i < HEAT.length; i++) {
            if (heat >= HEAT[i]) {
                float t = (heat - HEAT[i]) / (HEAT[i - 1] - HEAT[i]);
                int[] warm = COLORS[i - 1];
                int[] cool = COLORS[i];
                return FastColor.ARGB32.color(255, Math.round(Mth.lerp(t, cool[0], warm[0])), Math.round(Mth.lerp(t, cool[1], warm[1])),
                        Math.round(Mth.lerp(t, cool[2], warm[2])));
            }
        }
        return -1;
    }
}
