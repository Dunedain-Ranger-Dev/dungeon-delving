package io.github.ranger3462.dungeondelving.integration.irons;

import java.util.List;

import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/**
 * The goblin pyro (tiers 2-4): a goblin with a fire habit and no interest in healing anyone. Pure fire magic:
 * Firebolts, Fire Arrows and Scorch.
 */
public class GoblinPyro extends GoblinShaman {
    public GoblinPyro(EntityType<? extends GoblinPyro> type, Level level) {
        super(type, level);
    }

    @Override
    protected List<AbstractSpell> attackSpells() {
        return List.of(SpellRegistry.FIREBOLT_SPELL.get(), SpellRegistry.FIRE_ARROW_SPELL.get(), SpellRegistry.SCORCH_SPELL.get());
    }

    @Override
    protected boolean healsAllies() {
        return false;
    }

    @Override
    public boolean fireImmune() {
        return true;
    }
}
