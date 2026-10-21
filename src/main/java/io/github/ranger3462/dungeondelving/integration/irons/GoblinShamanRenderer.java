package io.github.ranger3462.dungeondelving.integration.irons;

import io.github.ranger3462.dungeondelving.DungeonDelving;
import io.redspace.ironsspellbooks.entity.mobs.abstract_spell_casting_mob.AbstractSpellCastingMob;
import io.redspace.ironsspellbooks.entity.mobs.abstract_spell_casting_mob.AbstractSpellCastingMobModel;
import io.redspace.ironsspellbooks.entity.mobs.abstract_spell_casting_mob.AbstractSpellCastingMobRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/**
 * Draws goblin shamans with Iron's Spells' caster body (so its casting animations play), shrunk to goblin size, with
 * the shaman texture. Client only; registered only when Iron's Spells is installed.
 */
public class GoblinShamanRenderer extends AbstractSpellCastingMobRenderer {
    private static final ResourceLocation TEXTURE = DungeonDelving.id("textures/entity/goblin_shaman.png");

    public GoblinShamanRenderer(EntityRendererProvider.Context context) {
        super(context, new Model());
        withScale(0.75f);
    }

    /** Registers the renderer; called from the client renderer event when Iron's Spells is installed. */
    public static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(IronsGoblins.GOBLIN_SHAMAN.get(), GoblinShamanRenderer::new);
    }

    private static final class Model extends AbstractSpellCastingMobModel {
        @Override
        public ResourceLocation getTextureResource(AbstractSpellCastingMob mob) {
            return TEXTURE;
        }
    }
}
