package io.github.ranger3462.dungeondelving.client;

import com.mojang.blaze3d.vertex.PoseStack;

import io.github.ranger3462.dungeondelving.DungeonDelving;
import io.github.ranger3462.dungeondelving.entity.Goblin;
import io.github.ranger3462.dungeondelving.integration.irons.GoblinShamanRenderer;
import io.github.ranger3462.dungeondelving.registry.ModEntities;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/**
 * Draws goblins with the vanilla humanoid body, shrunk to three quarters size, with each goblin type's own texture
 * ({@code textures/entity/<type>.png}). A placeholder until a custom goblin model exists.
 */
public class GoblinRenderer<T extends Goblin> extends HumanoidMobRenderer<T, GoblinModel<T>> {
    private static final float SCALE = 0.75f;
    private final ResourceLocation texture;

    public GoblinRenderer(EntityRendererProvider.Context context, String type) {
        super(context, new GoblinModel<>(context.bakeLayer(ModelLayers.ZOMBIE)), 0.4f);
        this.texture = DungeonDelving.id("textures/entity/" + type + ".png");
    }

    @Override
    public ResourceLocation getTextureLocation(T goblin) {
        return texture;
    }

    @Override
    protected void scale(T goblin, PoseStack poseStack, float partialTick) {
        poseStack.scale(SCALE, SCALE, SCALE);
    }

    @EventBusSubscriber(modid = DungeonDelving.MODID, value = Dist.CLIENT)
    public static final class Registration {
        private Registration() {}

        @SubscribeEvent
        public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
            event.registerEntityRenderer(ModEntities.GOBLIN.get(), context -> new GoblinRenderer<>(context, "goblin"));
            event.registerEntityRenderer(ModEntities.GOBLIN_ARCHER.get(), context -> new GoblinRenderer<>(context, "goblin_archer"));
            if (ModList.get().isLoaded("irons_spellbooks")) {
                GoblinShamanRenderer.register(event);
            }
        }
    }
}
