package io.github.ranger3462.dungeondelving.integration.irons;

import java.util.List;

import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.entity.mobs.goals.WizardAttackGoal;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * The Goblin Shaman Chief, the tier 1 goblin boss: a shaman who has taken over a goblin clan. Not very tough, but hard
 * to pin down: he flings fire and poison, shields himself, dodges, teleports away and heals, while the arena's
 * spawners pour out his goblins.
 */
public class GoblinChief extends GoblinShaman {
    public static final double MAX_HEALTH = 70.0;

    public GoblinChief(EntityType<? extends GoblinChief> type, Level level) {
        super(type, level);
        xpReward = 40;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return GoblinShaman.createAttributes()
                .add(Attributes.MAX_HEALTH, MAX_HEALTH)
                .add(Attributes.ARMOR, 4.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.4)
                .add(Attributes.FOLLOW_RANGE, 40.0);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new WizardAttackGoal(this, 1.2, 25, 45)
                .setSpells(
                        List.of(SpellRegistry.FIREBOLT_SPELL.get(), SpellRegistry.ACID_ORB_SPELL.get(), SpellRegistry.POISON_ARROW_SPELL.get()),
                        List.of(SpellRegistry.SHIELD_SPELL.get(), SpellRegistry.EVASION_SPELL.get()),
                        List.of(SpellRegistry.TELEPORT_SPELL.get()),
                        List.of(SpellRegistry.HEAL_SPELL.get(), SpellRegistry.OAKSKIN_SPELL.get()))
                .setSpellQuality(0.25f, 0.45f));
        goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 12.0f));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, false));
    }
}
