package io.github.ranger3462.dungeondelving.client;

import com.mojang.blaze3d.vertex.PoseStack;

import io.github.ranger3462.dungeondelving.DungeonDelving;
import io.github.ranger3462.dungeondelving.entity.SkeletonSwordsman;
import io.github.ranger3462.dungeondelving.registry.ModEntities;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.SkeletonModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.WitherSkeletonRenderer;
import net.minecraft.client.renderer.entity.ZombieRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.SkeletonClothingLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/**
 * Draws skeleton swordsmen, knights and the Skeleton Lord with the vanilla skeleton body, their armor, and the shield
 * arm raised while they block. Stray and bogged swordsmen (and the Skeleton Lord, a stray) wear their vanilla
 * cousins' textures and outer layers.
 */
public class SkeletonSwordsmanRenderer<T extends SkeletonSwordsman> extends HumanoidMobRenderer<T, SkeletonSwordsmanRenderer.Model<T>> {
    private static final ResourceLocation SKELETON = vanilla("skeleton/skeleton");
    private static final ResourceLocation STRAY = vanilla("skeleton/stray");
    private static final ResourceLocation BOGGED = vanilla("skeleton/bogged");
    private final float scale;
    private final ResourceLocation texture;

    public SkeletonSwordsmanRenderer(EntityRendererProvider.Context context, float scale) {
        this(context, scale, SKELETON);
    }

    public SkeletonSwordsmanRenderer(EntityRendererProvider.Context context, float scale, ResourceLocation texture) {
        super(context, new Model<>(context.bakeLayer(ModelLayers.SKELETON)), 0.5f);
        this.scale = scale;
        this.texture = texture;
        addLayer(new HumanoidArmorLayer<>(this, new HumanoidModel<>(context.bakeLayer(ModelLayers.SKELETON_INNER_ARMOR)),
                new HumanoidModel<>(context.bakeLayer(ModelLayers.SKELETON_OUTER_ARMOR)), context.getModelManager()));
    }

    /** A renderer with a vanilla skeleton's outer layer (a stray's tatters or a bogged's moss) on top. */
    public static <T extends SkeletonSwordsman> SkeletonSwordsmanRenderer<T> withOverlay(EntityRendererProvider.Context context, ResourceLocation texture,
            ModelLayerLocation layer, ResourceLocation overlay) {
        SkeletonSwordsmanRenderer<T> renderer = new SkeletonSwordsmanRenderer<>(context, 1.0f, texture);
        renderer.addLayer(new SkeletonClothingLayer<>(renderer, context.getModelSet(), layer, overlay));
        return renderer;
    }

    private static ResourceLocation vanilla(String path) {
        return ResourceLocation.withDefaultNamespace("textures/entity/" + path + ".png");
    }

    @Override
    public ResourceLocation getTextureLocation(T skeleton) {
        return texture;
    }

    @Override
    protected void scale(T skeleton, PoseStack poseStack, float partialTick) {
        poseStack.scale(scale, scale, scale);
    }

    /** The skeleton body, holding its weapon out and raising its shield while blocking. */
    public static class Model<T extends SkeletonSwordsman> extends SkeletonModel<T> {
        public Model(ModelPart root) {
            super(root);
        }

        @Override
        public void prepareMobModel(T skeleton, float limbSwing, float limbSwingAmount, float partialTick) {
            super.prepareMobModel(skeleton, limbSwing, limbSwingAmount, partialTick);
            rightArmPose = skeleton.getMainHandItem().isEmpty() ? ArmPose.EMPTY : ArmPose.ITEM;
            leftArmPose = skeleton.getOffhandItem().isEmpty() ? ArmPose.EMPTY : ArmPose.ITEM;
            if (skeleton.isUsingItem() && skeleton.getUsedItemHand() == InteractionHand.OFF_HAND) {
                leftArmPose = ArmPose.BLOCK;
            }
        }
    }

    @EventBusSubscriber(modid = DungeonDelving.MODID, value = Dist.CLIENT)
    public static final class Registration {
        private Registration() {}

        @SubscribeEvent
        public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
            event.registerEntityRenderer(ModEntities.SKELETON_SWORDSMAN.get(), context -> new SkeletonSwordsmanRenderer<>(context, 1.0f));
            event.registerEntityRenderer(ModEntities.SKELETON_KNIGHT.get(), context -> new SkeletonSwordsmanRenderer<>(context, 1.0f));
            event.registerEntityRenderer(ModEntities.SKELETON_LORD.get(),
                    context -> withOverlay(context, STRAY, ModelLayers.STRAY_OUTER_LAYER, vanilla("skeleton/stray_overlay")));
            event.registerEntityRenderer(ModEntities.STRAY_SWORDSMAN.get(),
                    context -> withOverlay(context, STRAY, ModelLayers.STRAY_OUTER_LAYER, vanilla("skeleton/stray_overlay")));
            event.registerEntityRenderer(ModEntities.BOGGED_SWORDSMAN.get(),
                    context -> withOverlay(context, BOGGED, ModelLayers.BOGGED_OUTER_LAYER, vanilla("skeleton/bogged_overlay")));
            registerVanilla(event);
        }

        /** Mobs drawn exactly like the vanilla mob they're built on. */
        @SuppressWarnings({ "unchecked", "rawtypes" })
        private static void registerVanilla(EntityRenderersEvent.RegisterRenderers event) {
            event.registerEntityRenderer((EntityType) ModEntities.WITHER_SKELETON_ARCHER.get(), (EntityRendererProvider) WitherSkeletonRenderer::new);
            event.registerEntityRenderer((EntityType) ModEntities.ZOMBIE_LORD.get(), (EntityRendererProvider) ZombieRenderer::new);
        }
    }
}
