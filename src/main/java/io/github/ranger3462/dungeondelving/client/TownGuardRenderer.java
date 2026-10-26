package io.github.ranger3462.dungeondelving.client;

import io.github.ranger3462.dungeondelving.DungeonDelving;
import io.github.ranger3462.dungeondelving.entity.TownGuard;
import io.github.ranger3462.dungeondelving.registry.ModEntities;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Draws town guards with the player body, their skin (falling back to our first if a named one is missing), and their armor. */
public class TownGuardRenderer extends HumanoidMobRenderer<TownGuard, HumanoidModel<TownGuard>> {
    public TownGuardRenderer(EntityRendererProvider.Context context) {
        super(context, new Model(context.bakeLayer(ModelLayers.PLAYER)), 0.5f);
        addLayer(new HumanoidArmorLayer<>(this, new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),
                new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)), context.getModelManager()));
    }

    /** The player body, with arms posed for a crossbow, a raised shield or whatever's in hand. */
    private static final class Model extends HumanoidModel<TownGuard> {
        Model(net.minecraft.client.model.geom.ModelPart root) {
            super(root);
        }

        @Override
        public void prepareMobModel(TownGuard guard, float limbSwing, float limbSwingAmount, float partialTick) {
            rightArmPose = ArmPose.EMPTY;
            leftArmPose = ArmPose.EMPTY;
            net.minecraft.world.item.ItemStack held = guard.getMainHandItem();
            if (held.getItem() instanceof net.minecraft.world.item.CrossbowItem) {
                rightArmPose = guard.isChargingCrossbow() ? ArmPose.CROSSBOW_CHARGE : guard.isAggressive() ? ArmPose.CROSSBOW_HOLD : ArmPose.ITEM;
            } else if (!held.isEmpty()) {
                rightArmPose = ArmPose.ITEM;
            }
            if (!guard.getOffhandItem().isEmpty()) {
                leftArmPose = guard.isUsingItem() && guard.getUsedItemHand() == net.minecraft.world.InteractionHand.OFF_HAND ? ArmPose.BLOCK : ArmPose.ITEM;
            }
            super.prepareMobModel(guard, limbSwing, limbSwingAmount, partialTick);
        }
    }

    private static final ResourceLocation FALLBACK = DungeonDelving.id("textures/entity/town_guard/guard_1.png");

    @Override
    public ResourceLocation getTextureLocation(TownGuard guard) {
        ResourceLocation skin = guard.skin();
        return net.minecraft.client.Minecraft.getInstance().getResourceManager().getResource(skin).isPresent() ? skin : FALLBACK;
    }

    @EventBusSubscriber(modid = DungeonDelving.MODID, value = Dist.CLIENT)
    public static final class Registration {
        private Registration() {}

        @SubscribeEvent
        public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
            event.registerEntityRenderer(ModEntities.TOWN_GUARD.get(), TownGuardRenderer::new);
        }
    }
}
