package io.github.ranger3462.dungeondelving.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.github.ranger3462.dungeondelving.entity.ThrownAxe;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;

/**
 * Draws a thrown axe tumbling end over end along its flight, and frozen at whatever angle it hit when it lodges in a
 * block.
 */
public class ThrownAxeRenderer extends EntityRenderer<ThrownAxe> {
    /** How far it turns each tick in flight, in degrees. */
    private static final float SPIN_PER_TICK = 40.0f;
    private final ItemRenderer itemRenderer;

    public ThrownAxeRenderer(EntityRendererProvider.Context context) {
        super(context);
        itemRenderer = context.getItemRenderer();
    }

    @Override
    public void render(ThrownAxe axe, float yaw, float partialTicks, PoseStack pose, MultiBufferSource buffers, int light) {
        pose.pushPose();
        // Face the item's flat side across the flight path, so it spins like a wheel rolling along it.
        pose.mulPose(Axis.YP.rotationDegrees(Mth.lerp(partialTicks, axe.yRotO, axe.getYRot()) - 90.0f));
        pose.mulPose(Axis.ZP.rotationDegrees(-spin(axe, partialTicks)));
        pose.scale(1.1f, 1.1f, 1.1f);
        itemRenderer.renderStatic(axe.getItem(), ItemDisplayContext.FIXED, light, OverlayTexture.NO_OVERLAY, pose, buffers, axe.level(), axe.getId());
        pose.popPose();
        super.render(axe, yaw, partialTicks, pose, buffers, light);
    }

    private static float spin(ThrownAxe axe, float partialTicks) {
        if (axe.isLodged()) {
            // Frozen with the blade biting forward and down into what it hit.
            return 135.0f;
        }
        return (axe.tickCount + partialTicks) * SPIN_PER_TICK;
    }

    @Override
    @SuppressWarnings("deprecation")
    public ResourceLocation getTextureLocation(ThrownAxe axe) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
