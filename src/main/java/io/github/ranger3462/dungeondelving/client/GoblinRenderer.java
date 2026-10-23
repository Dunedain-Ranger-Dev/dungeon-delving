package io.github.ranger3462.dungeondelving.client;

import com.mojang.blaze3d.vertex.PoseStack;

import io.github.ranger3462.dungeondelving.DungeonDelving;
import io.github.ranger3462.dungeondelving.entity.Goblin;
import io.github.ranger3462.dungeondelving.integration.irons.GoblinShamanRenderer;
import io.github.ranger3462.dungeondelving.registry.ModEntities;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/**
 * Draws goblins with the vanilla humanoid body, shrunk to three quarters size (hobgoblins at full size), with each
 * goblin type's own texture ({@code textures/entity/<type>.png}) and any armor they wear. A placeholder until a custom
 * goblin model exists.
 */
public class GoblinRenderer<T extends Goblin> extends HumanoidMobRenderer<T, GoblinModel<T>> {
    private static final float GOBLIN_SCALE = 0.75f;
    private final ResourceLocation texture;
    private final float scale;

    public GoblinRenderer(EntityRendererProvider.Context context, String type) {
        this(context, type, GOBLIN_SCALE);
    }

    public GoblinRenderer(EntityRendererProvider.Context context, String type, float scale) {
        super(context, new GoblinModel<>(context.bakeLayer(ModelLayers.ZOMBIE)), 0.4f);
        addLayer(new HumanoidArmorLayer<>(this, new HumanoidModel<>(context.bakeLayer(ModelLayers.ZOMBIE_INNER_ARMOR)),
                new HumanoidModel<>(context.bakeLayer(ModelLayers.ZOMBIE_OUTER_ARMOR)), context.getModelManager()));
        this.texture = DungeonDelving.id("textures/entity/" + type + ".png");
        this.scale = scale;
    }

    @Override
    public ResourceLocation getTextureLocation(T goblin) {
        return texture;
    }

    @Override
    protected void scale(T goblin, PoseStack poseStack, float partialTick) {
        poseStack.scale(scale, scale, scale);
    }

    @EventBusSubscriber(modid = DungeonDelving.MODID, value = Dist.CLIENT)
    public static final class Registration {
        private Registration() {}

        @SubscribeEvent
        public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
            event.registerEntityRenderer(ModEntities.GOBLIN.get(), context -> new GoblinRenderer<>(context, "goblin"));
            event.registerEntityRenderer(ModEntities.GOBLIN_ARCHER.get(), context -> new GoblinRenderer<>(context, "goblin_archer"));
            event.registerEntityRenderer(ModEntities.GOBLIN_GLADIATOR.get(), context -> new GoblinRenderer<>(context, "goblin_gladiator"));
            event.registerEntityRenderer(ModEntities.HOBGOBLIN_SOLDIER.get(), context -> new GoblinRenderer<>(context, "hobgoblin_soldier", 1.0f));
            event.registerEntityRenderer(ModEntities.HOBGOBLIN_HUNTER.get(), context -> new GoblinRenderer<>(context, "hobgoblin_hunter", 1.0f));
            event.registerEntityRenderer(ModEntities.HOBGOBLIN_VANGUARD.get(), context -> new GoblinRenderer<>(context, "hobgoblin_vanguard", 1.0f));
            event.registerEntityRenderer(ModEntities.HOBGOBLIN_SPELLBREAKER.get(),
                    context -> new GoblinRenderer<>(context, "hobgoblin_spellbreaker", 1.0f));
            event.registerEntityRenderer(ModEntities.HOBGOBLIN_VETERAN.get(), context -> new GoblinRenderer<>(context, "hobgoblin_veteran", 1.0f));
            event.registerEntityRenderer(ModEntities.HOBGOBLIN_GENERAL.get(), context -> new GoblinRenderer<>(context, "hobgoblin_general", 1.0f));
            if (ModList.get().isLoaded("irons_spellbooks")) {
                GoblinShamanRenderer.register(event);
            }
        }
    }
}
