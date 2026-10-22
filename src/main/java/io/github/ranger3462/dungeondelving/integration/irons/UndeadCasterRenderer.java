package io.github.ranger3462.dungeondelving.integration.irons;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Mob;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/**
 * Draws the wither skeleton mage and the Lich Lord with vanilla bodies and textures (a wither skeleton and a husk),
 * since Iron's caster body is shaped for player-style textures. They cast with Iron's spells all the same, just
 * without its casting animations. Client only; registered only when Iron's Spells is installed.
 */
public class UndeadCasterRenderer<T extends Mob> extends HumanoidMobRenderer<T, HumanoidModel<T>> {
    private final ResourceLocation texture;

    public UndeadCasterRenderer(EntityRendererProvider.Context context, ModelLayerLocation layer, ResourceLocation texture) {
        super(context, new HumanoidModel<>(context.bakeLayer(layer)), 0.5f);
        this.texture = texture;
    }

    @Override
    public ResourceLocation getTextureLocation(T mob) {
        return texture;
    }

    static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(IronsSkeletons.WITHER_SKELETON_MAGE.get(), context -> new UndeadCasterRenderer<>(context,
                ModelLayers.WITHER_SKELETON, ResourceLocation.withDefaultNamespace("textures/entity/skeleton/wither_skeleton.png")));
        event.registerEntityRenderer(IronsSkeletons.LICH_LORD.get(), context -> new UndeadCasterRenderer<>(context,
                ModelLayers.HUSK, ResourceLocation.withDefaultNamespace("textures/entity/zombie/husk.png")));
    }
}
