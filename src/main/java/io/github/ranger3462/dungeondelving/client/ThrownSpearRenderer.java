package io.github.ranger3462.dungeondelving.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.github.ranger3462.dungeondelving.entity.ThrownSpear;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;

/** Draws a thrown spear point first along its flight, like an arrow, and as it stuck when it lodges in a block. */
public class ThrownSpearRenderer extends EntityRenderer<ThrownSpear> {
    private final ItemRenderer itemRenderer;

    public ThrownSpearRenderer(EntityRendererProvider.Context context) {
        super(context);
        itemRenderer = context.getItemRenderer();
    }

    @Override
    public void render(ThrownSpear spear, float yaw, float partialTicks, PoseStack pose, MultiBufferSource buffers, int light) {
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(Mth.lerp(partialTicks, spear.yRotO, spear.getYRot()) - 90.0f));
        pose.mulPose(Axis.ZP.rotationDegrees(Mth.lerp(partialTicks, spear.xRotO, spear.getXRot())));
        // The spear's sprite runs corner to corner, point up and right: turn it to lie along the flight, point forward.
        pose.mulPose(Axis.ZP.rotationDegrees(-45.0f));
        pose.scale(1.3f, 1.3f, 1.3f);
        itemRenderer.renderStatic(spear.getItem(), ItemDisplayContext.FIXED, light, OverlayTexture.NO_OVERLAY, pose, buffers, spear.level(), spear.getId());
        pose.popPose();
        super.render(spear, yaw, partialTicks, pose, buffers, light);
    }

    @Override
    @SuppressWarnings("deprecation")
    public ResourceLocation getTextureLocation(ThrownSpear spear) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
