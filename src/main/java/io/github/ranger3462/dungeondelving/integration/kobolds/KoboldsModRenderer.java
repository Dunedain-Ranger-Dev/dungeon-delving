package io.github.ranger3462.dungeondelving.integration.kobolds;

import com.mojang.blaze3d.vertex.PoseStack;

import io.github.ranger3462.dungeondelving.client.ArmedHumanoidModel;
import io.github.ranger3462.dungeondelving.entity.Kobold;
import io.github.ranger3462.dungeondelving.registry.ModEntities;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.salju.kobolds.init.KoboldsClient;

/**
 * Draws our kobolds with the Kobolds! mod's model and skins (MIT, by Jusey1 and team) when it's installed: its body,
 * horns and tail, its glowing eyes, and its armor fit. Our kobolds keep their own AI; only the look is borrowed. The
 * looter wears the plain kobold skin, the scavenger the engineer's and the Packmaster the warrior's.
 */
public class KoboldsModRenderer<T extends Kobold> extends HumanoidMobRenderer<T, ArmedHumanoidModel<T>> {
    /** Kobolds! draws its grown kobolds at this size. */
    private static final float SCALE = 0.875f;
    private static final ResourceLocation EYES = ResourceLocation.fromNamespaceAndPath("kobolds", "textures/entity/eyes/base.png");
    private final ResourceLocation texture;

    public KoboldsModRenderer(EntityRendererProvider.Context context, String skin) {
        super(context, new ArmedHumanoidModel<>(adult(context.bakeLayer(KoboldsClient.KOBOLD))), 0.36f);
        addLayer(new HumanoidArmorLayer<>(this, new HumanoidModel<>(context.bakeLayer(KoboldsClient.KOBOLD_ARMOR_INNER)),
                new HumanoidModel<>(context.bakeLayer(KoboldsClient.KOBOLD_ARMOR_OUTER)), context.getModelManager()));
        addLayer(new EyesLayer<>(this) {
            @Override
            public RenderType renderType() {
                return RenderType.eyes(EYES);
            }
        });
        this.texture = ResourceLocation.fromNamespaceAndPath("kobolds", "textures/entity/kobolds/" + skin + ".png");
    }

    /** Hides the baby horns, which the model only shows on young kobolds. */
    private static ModelPart adult(ModelPart root) {
        ModelPart head = root.getChild("head");
        for (String horn : new String[] {"baby_left_horn", "baby_right_horn"}) {
            if (head.hasChild(horn)) {
                head.getChild(horn).visible = false;
            }
        }
        return root;
    }

    @Override
    public ResourceLocation getTextureLocation(T mob) {
        return texture;
    }

    @Override
    protected void scale(T mob, PoseStack poseStack, float partialTick) {
        poseStack.scale(SCALE, SCALE, SCALE);
    }

    public static void register(EntityRenderersEvent.RegisterRenderers event) {
        register(event, ModEntities.KOBOLD_LOOTER.get(), "base");
        register(event, ModEntities.KOBOLD_SCAVENGER.get(), "engineer");
        register(event, ModEntities.KOBOLD_PACKMASTER.get(), "warrior");
    }

    private static <T extends Kobold> void register(EntityRenderersEvent.RegisterRenderers event, EntityType<T> type, String skin) {
        event.registerEntityRenderer(type, context -> new KoboldsModRenderer<>(context, skin));
    }
}
