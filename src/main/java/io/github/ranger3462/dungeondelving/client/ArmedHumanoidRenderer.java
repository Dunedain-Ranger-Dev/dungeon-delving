package io.github.ranger3462.dungeondelving.client;

import com.mojang.blaze3d.vertex.PoseStack;

import io.github.ranger3462.dungeondelving.DungeonDelving;
import io.github.ranger3462.dungeondelving.entity.ArmedHumanoid;
import io.github.ranger3462.dungeondelving.integration.irons.GoblinShamanRenderer;
import io.github.ranger3462.dungeondelving.registry.ModEntities;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/**
 * Draws goblins, hobgoblins, orcs and kobolds with the vanilla humanoid body (goblins shrunk to three quarters size), with each
 * type's own texture ({@code textures/entity/<type>.png}) and any armor they wear. A placeholder until custom models
 * exist.
 */
public class ArmedHumanoidRenderer<T extends ArmedHumanoid> extends HumanoidMobRenderer<T, ArmedHumanoidModel<T>> {
    private static final float GOBLIN_SCALE = 0.75f;
    private final ResourceLocation texture;
    private final float scale;

    public ArmedHumanoidRenderer(EntityRendererProvider.Context context, String type) {
        this(context, type, GOBLIN_SCALE);
    }

    public ArmedHumanoidRenderer(EntityRendererProvider.Context context, String type, float scale) {
        super(context, new ArmedHumanoidModel<>(context.bakeLayer(ModelLayers.ZOMBIE)), 0.4f);
        addLayer(new HumanoidArmorLayer<>(this, new HumanoidModel<>(context.bakeLayer(ModelLayers.ZOMBIE_INNER_ARMOR)),
                new HumanoidModel<>(context.bakeLayer(ModelLayers.ZOMBIE_OUTER_ARMOR)), context.getModelManager()));
        this.texture = DungeonDelving.id("textures/entity/" + type + ".png");
        this.scale = scale;
    }

    @Override
    public ResourceLocation getTextureLocation(T mob) {
        return texture;
    }

    @Override
    protected void scale(T mob, PoseStack poseStack, float partialTick) {
        poseStack.scale(scale, scale, scale);
    }

    /** Orcs are drawn a little bigger than a player. */
    private static <T extends ArmedHumanoid> void registerOrc(EntityRenderersEvent.RegisterRenderers event, EntityType<T> type, String texture) {
        event.registerEntityRenderer(type, context -> new ArmedHumanoidRenderer<>(context, texture, ORC_SCALE));
    }

    private static final float ORC_SCALE = 1.08f;
    /** Kobolds are a little smaller than goblins. */
    private static final float KOBOLD_SCALE = 0.68f;

    @EventBusSubscriber(modid = DungeonDelving.MODID, value = Dist.CLIENT)
    public static final class Registration {
        private Registration() {}

        @SubscribeEvent
        public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
            event.registerEntityRenderer(ModEntities.GOBLIN.get(), context -> new ArmedHumanoidRenderer<>(context, "goblin"));
            event.registerEntityRenderer(ModEntities.GOBLIN_ARCHER.get(), context -> new ArmedHumanoidRenderer<>(context, "goblin_archer"));
            event.registerEntityRenderer(ModEntities.GOBLIN_GLADIATOR.get(), context -> new ArmedHumanoidRenderer<>(context, "goblin_gladiator"));
            event.registerEntityRenderer(ModEntities.HOBGOBLIN_SOLDIER.get(), context -> new ArmedHumanoidRenderer<>(context, "hobgoblin_soldier", 1.0f));
            event.registerEntityRenderer(ModEntities.HOBGOBLIN_HUNTER.get(), context -> new ArmedHumanoidRenderer<>(context, "hobgoblin_hunter", 1.0f));
            event.registerEntityRenderer(ModEntities.HOBGOBLIN_VANGUARD.get(), context -> new ArmedHumanoidRenderer<>(context, "hobgoblin_vanguard", 1.0f));
            event.registerEntityRenderer(ModEntities.HOBGOBLIN_SPELLBREAKER.get(),
                    context -> new ArmedHumanoidRenderer<>(context, "hobgoblin_spellbreaker", 1.0f));
            event.registerEntityRenderer(ModEntities.HOBGOBLIN_VETERAN.get(), context -> new ArmedHumanoidRenderer<>(context, "hobgoblin_veteran", 1.0f));
            event.registerEntityRenderer(ModEntities.HOBGOBLIN_GENERAL.get(), context -> new ArmedHumanoidRenderer<>(context, "hobgoblin_general", 1.0f));
            for (var orc : java.util.List.of(ModEntities.ORC_SCRAPPER, ModEntities.ORC_RAIDER, ModEntities.ORC_VETERAN, ModEntities.ORC_VAGABOND,
                    ModEntities.ORC_RAMPAGER, ModEntities.ORC_COMMANDER, ModEntities.ORC_CHIEFTAIN, ModEntities.ORC_WARCHIEF)) {
                registerOrc(event, orc.get(), orc.getId().getPath());
            }
            event.registerEntityRenderer(ModEntities.THROWN_AXE.get(), ThrownAxeRenderer::new);
            event.registerEntityRenderer(ModEntities.THROWN_SPEAR.get(), ThrownSpearRenderer::new);
            event.registerEntityRenderer(ModEntities.KOBOLD_LOOTER.get(), context -> new ArmedHumanoidRenderer<>(context, "kobold_looter", KOBOLD_SCALE));
            event.registerEntityRenderer(ModEntities.KOBOLD_SCAVENGER.get(), context -> new ArmedHumanoidRenderer<>(context, "kobold_scavenger", KOBOLD_SCALE));
            event.registerEntityRenderer(ModEntities.KOBOLD_PACKMASTER.get(), context -> new ArmedHumanoidRenderer<>(context, "kobold_packmaster", KOBOLD_SCALE));
            if (ModList.get().isLoaded("irons_spellbooks")) {
                GoblinShamanRenderer.register(event);
            }
        }
    }
}
