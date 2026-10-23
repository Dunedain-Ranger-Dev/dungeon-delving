package io.github.ranger3462.dungeondelving.client;

import io.github.ranger3462.dungeondelving.DungeonDelving;
import io.github.ranger3462.dungeondelving.registry.ModEntities;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * Item model properties our items' models switch on. Vanilla only gives its own bow the "pull" and "pulling"
 * properties, so without these the Goblin Bow's drawing frames never show, in a goblin archer's hands or a player's.
 */
@EventBusSubscriber(modid = DungeonDelving.MODID, value = Dist.CLIENT)
public final class ItemModelProperties {
    private ItemModelProperties() {}

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            // As vanilla's bow: how far it's drawn (0 to 1 over a second), and whether it's being drawn at all.
            ItemProperties.register(ModEntities.GOBLIN_BOW.get(), ResourceLocation.withDefaultNamespace("pull"), (stack, level, entity, seed) ->
                    entity == null || entity.getUseItem() != stack ? 0.0f : (stack.getUseDuration(entity) - entity.getUseItemRemainingTicks()) / 20.0f);
            ItemProperties.register(ModEntities.GOBLIN_BOW.get(), ResourceLocation.withDefaultNamespace("pulling"), (stack, level, entity, seed) ->
                    entity != null && entity.isUsingItem() && entity.getUseItem() == stack ? 1.0f : 0.0f);
        });
    }
}
