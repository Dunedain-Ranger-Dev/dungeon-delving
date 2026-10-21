package io.github.ranger3462.dungeondelving.integration.irons;

import io.github.ranger3462.dungeondelving.DungeonDelving;
import io.redspace.ironsspellbooks.entity.mobs.abstract_spell_casting_mob.AbstractSpellCastingMob;
import io.redspace.ironsspellbooks.entity.mobs.abstract_spell_casting_mob.AbstractSpellCastingMobModel;
import io.redspace.ironsspellbooks.entity.mobs.abstract_spell_casting_mob.AbstractSpellCastingMobRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/**
 * Draws goblin shamans and their chief with Iron's Spells' caster body (so its casting animations play), at goblin
 * size, with their own textures. Client only; registered only when Iron's Spells is installed.
 */
public class GoblinShamanRenderer extends AbstractSpellCastingMobRenderer {
    public GoblinShamanRenderer(EntityRendererProvider.Context context, String type, float scale) {
        super(context, new Model(DungeonDelving.id("textures/entity/" + type + ".png")));
        withScale(scale);
    }

    /** Registers the renderers; called from the client renderer event when Iron's Spells is installed. */
    public static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(IronsGoblins.GOBLIN_SHAMAN.get(), context -> new GoblinShamanRenderer(context, "goblin_shaman", 0.75f));
        event.registerEntityRenderer(IronsGoblins.GOBLIN_CHIEF.get(), context -> new GoblinShamanRenderer(context, "goblin_chief", 0.95f));
    }

    private static final class Model extends AbstractSpellCastingMobModel {
        private final ResourceLocation texture;

        Model(ResourceLocation texture) {
            this.texture = texture;
        }

        @Override
        public ResourceLocation getTextureResource(AbstractSpellCastingMob mob) {
            return texture;
        }
    }
}
