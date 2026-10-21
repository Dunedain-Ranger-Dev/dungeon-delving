package io.github.ranger3462.dungeondelving.client;

import com.mojang.blaze3d.vertex.PoseStack;

import io.github.ranger3462.dungeondelving.DungeonDelving;
import io.github.ranger3462.dungeondelving.entity.Goblin;
import io.github.ranger3462.dungeondelving.registry.ModEntities;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/**
 * Draws goblins with the vanilla humanoid body, shrunk to three quarters size, and the goblin texture. A placeholder
 * until a custom goblin model exists.
 */
public class GoblinRenderer extends HumanoidMobRenderer<Goblin, HumanoidModel<Goblin>> {
    private static final ResourceLocation TEXTURE = DungeonDelving.id("textures/entity/goblin.png");
    private static final float SCALE = 0.75f;

    public GoblinRenderer(EntityRendererProvider.Context context) {
        super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.ZOMBIE)), 0.4f);
    }

    @Override
    public ResourceLocation getTextureLocation(Goblin goblin) {
        return TEXTURE;
    }

    @Override
    protected void scale(Goblin goblin, PoseStack poseStack, float partialTick) {
        poseStack.scale(SCALE, SCALE, SCALE);
    }

    @EventBusSubscriber(modid = DungeonDelving.MODID, value = Dist.CLIENT)
    public static final class Registration {
        private Registration() {}

        @SubscribeEvent
        public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
            event.registerEntityRenderer(ModEntities.GOBLIN.get(), GoblinRenderer::new);
        }
    }
}
