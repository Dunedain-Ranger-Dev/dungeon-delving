package io.github.ranger3462.dungeondelving.client;

import com.mojang.blaze3d.vertex.PoseStack;

import io.github.ranger3462.dungeondelving.DungeonDelving;
import io.github.ranger3462.dungeondelving.entity.SkeletonSwordsman;
import io.github.ranger3462.dungeondelving.registry.ModEntities;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.SkeletonModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/**
 * Draws skeleton swordsmen, knights and the Skeleton Lord with the vanilla skeleton body and texture, their armor, and
 * the shield arm raised while they block.
 */
public class SkeletonSwordsmanRenderer<T extends SkeletonSwordsman> extends HumanoidMobRenderer<T, SkeletonSwordsmanRenderer.Model<T>> {
    private static final ResourceLocation TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/skeleton/skeleton.png");
    private final float scale;

    public SkeletonSwordsmanRenderer(EntityRendererProvider.Context context, float scale) {
        super(context, new Model<>(context.bakeLayer(ModelLayers.SKELETON)), 0.5f);
        this.scale = scale;
        addLayer(new HumanoidArmorLayer<>(this, new HumanoidModel<>(context.bakeLayer(ModelLayers.SKELETON_INNER_ARMOR)),
                new HumanoidModel<>(context.bakeLayer(ModelLayers.SKELETON_OUTER_ARMOR)), context.getModelManager()));
    }

    @Override
    public ResourceLocation getTextureLocation(T skeleton) {
        return TEXTURE;
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
            event.registerEntityRenderer(ModEntities.SKELETON_LORD.get(), context -> new SkeletonSwordsmanRenderer<>(context, 1.0f));
        }
    }
}
