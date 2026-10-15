package io.github.ranger3462.dungeondelving.client;

import io.github.ranger3462.dungeondelving.DungeonDelving;
import io.github.ranger3462.dungeondelving.dungeon.DungeonLoot;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/** Labels dungeon loot in item tooltips, so players know which items they'd lose. */
@EventBusSubscriber(modid = DungeonDelving.MODID, value = Dist.CLIENT)
public final class LootTooltip {
    private LootTooltip() {}

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        if (DungeonLoot.isLoot(event.getItemStack())) {
            event.getToolTip().add(Component.translatable("tooltip.dungeondelving.dungeon_loot").withStyle(ChatFormatting.LIGHT_PURPLE));
        }
    }
}
