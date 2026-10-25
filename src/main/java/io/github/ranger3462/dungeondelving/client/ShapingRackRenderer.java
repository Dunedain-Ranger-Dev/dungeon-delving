package io.github.ranger3462.dungeondelving.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.github.ranger3462.dungeondelving.DungeonDelving;
import io.github.ranger3462.dungeondelving.forging.Crafts;
import io.github.ranger3462.dungeondelving.forging.ShapingRackBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Draws the piece lying on a Shaping Rack, flat on its board. */
public class ShapingRackRenderer implements BlockEntityRenderer<ShapingRackBlockEntity> {
    public ShapingRackRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(ShapingRackBlockEntity rack, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        ItemStack piece = rack.piece();
        if (piece.isEmpty()) {
            return;
        }
        pose.pushPose();
        pose.translate(0.5, 14.2 / 16.0, 0.5);
        pose.mulPose(Axis.XP.rotationDegrees(90.0f));
        pose.scale(0.7f, 0.7f, 0.7f);
        Minecraft.getInstance().getItemRenderer().renderStatic(piece, ItemDisplayContext.FIXED, light, OverlayTexture.NO_OVERLAY, pose, buffers,
                rack.getLevel(), 0);
        pose.popPose();
    }

    @EventBusSubscriber(modid = DungeonDelving.MODID, value = Dist.CLIENT)
    public static final class Registration {
        private Registration() {}

        @SubscribeEvent
        public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
            event.registerBlockEntityRenderer(Crafts.SHAPING_RACK_ENTITY.get(), ShapingRackRenderer::new);
        }
    }
}
