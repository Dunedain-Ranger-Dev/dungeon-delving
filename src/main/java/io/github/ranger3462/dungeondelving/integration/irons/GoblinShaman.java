package io.github.ranger3462.dungeondelving.integration.irons;

import java.util.List;

import io.github.ranger3462.dungeondelving.entity.GoblinSwarm;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.entity.mobs.abstract_spell_casting_mob.AbstractSpellCastingMob;
import io.redspace.ironsspellbooks.entity.mobs.goals.WizardAttackGoal;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * The goblin shaman (tiers 2-4): a fragile hedge-caster of poison and healing. It keeps its distance, flinging Poison
 * Arrows and Poison Splash, heals itself, and mends hurt goblins nearby. Casts real Iron's Spells spells, so it only
 * exists when Iron's Spells is installed. The goblin pyro and the Goblin Shaman Chief build on it.
 */
public class GoblinShaman extends AbstractSpellCastingMob implements Enemy {
    public static final double MAX_HEALTH = 12.0;
    public static final double SPEED = 0.27;

    public GoblinShaman(EntityType<? extends GoblinShaman> type, Level level) {
        super(type, level);
        xpReward = 8;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, MAX_HEALTH)
                .add(Attributes.MOVEMENT_SPEED, SPEED)
                .add(Attributes.ATTACK_DAMAGE, 2.0)
                .add(Attributes.FOLLOW_RANGE, 24.0);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        if (healsAllies()) {
            goalSelector.addGoal(1, new SupportCastGoal(this, ally -> ally == this || GoblinSwarm.isGoblin(ally), 16.0, 6.0, 60, 100,
                    new SupportCastGoal.Mend(0, 120), List.of()).setSpellQuality(0.15f, 0.3f));
        }
        goalSelector.addGoal(2, new WizardAttackGoal(this, 1.15, 40, 70)
                .setSpells(attackSpells(), List.of(), List.of(), List.of())
                .setSpellQuality(0.1f, 0.3f));
        goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 1.0));
        goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0f));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    /** The spells it attacks with. */
    protected List<AbstractSpell> attackSpells() {
        return List.of(SpellRegistry.POISON_ARROW_SPELL.get(), SpellRegistry.POISON_SPLASH_SPELL.get());
    }

    /** Whether it mends hurt goblins around it (and itself) with Mending. */
    protected boolean healsAllies() {
        return true;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.PIGLIN_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.PIGLIN_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.PIGLIN_DEATH;
    }

    @Override
    public float getVoicePitch() {
        return super.getVoicePitch() * 1.4f;
    }

    @Override
    protected boolean shouldDespawnInPeaceful() {
        return true;
    }
}
