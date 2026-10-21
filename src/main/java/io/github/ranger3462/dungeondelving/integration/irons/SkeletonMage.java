package io.github.ranger3462.dungeondelving.integration.irons;

import java.util.List;

import io.github.ranger3462.dungeondelving.dungeon.challenge.DungeonBosses;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.entity.mobs.SupportMob;
import io.redspace.ironsspellbooks.entity.mobs.abstract_spell_casting_mob.AbstractSpellCastingMob;
import io.redspace.ironsspellbooks.entity.mobs.goals.FindSupportableTargetGoal;
import io.redspace.ironsspellbooks.entity.mobs.goals.WizardAttackGoal;
import io.redspace.ironsspellbooks.entity.mobs.goals.WizardSupportGoal;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.GoalSelector;
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
 * The skeleton mage, the Barrow King's court caster: it hangs back, heals the King (and other skeletons) when they're
 * hurt, wraps them in Fortify, and otherwise throws the odd Wither Skull. Casts real Iron's Spells spells, so it only
 * exists when Iron's Spells is installed.
 */
public class SkeletonMage extends AbstractSpellCastingMob implements Enemy, SupportMob {
    public static final double MAX_HEALTH = 20.0;
    /** Heals an ally once it's below this share of its health. */
    private static final float HEAL_BELOW = 0.8f;

    private final GoalSelector supportTargetSelector;
    private LivingEntity supportTarget;

    public SkeletonMage(EntityType<? extends SkeletonMage> type, Level level) {
        super(type, level);
        xpReward = 10;
        supportTargetSelector = new GoalSelector(level.getProfilerSupplier());
        supportTargetSelector.addGoal(0, new FindSupportableTargetGoal<>(this, LivingEntity.class, true,
                ally -> ally != this && isAlliedTo(ally) && ally.getHealth() < ally.getMaxHealth() * HEAL_BELOW));
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, MAX_HEALTH)
                .add(Attributes.MOVEMENT_SPEED, 0.25)
                .add(Attributes.ATTACK_DAMAGE, 2.0)
                .add(Attributes.FOLLOW_RANGE, 32.0);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new WizardSupportGoal<>(this, 1.2, 40, 80)
                .setSpells(List.of(SpellRegistry.BLESSING_OF_LIFE_SPELL.get(), SpellRegistry.BLESSING_OF_LIFE_SPELL.get(),
                        SpellRegistry.HEALING_CIRCLE_SPELL.get()), List.of(SpellRegistry.FORTIFY_SPELL.get()))
                .setSpellQuality(0.2f, 0.4f));
        goalSelector.addGoal(3, new WizardAttackGoal(this, 1.0, 60, 100)
                .setSpells(List.of(SpellRegistry.WITHER_SKULL_SPELL.get()), List.of(), List.of(), List.of(SpellRegistry.HEAL_SPELL.get()))
                .setSpellQuality(0.1f, 0.25f));
        goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 0.8));
        goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0f));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (tickCount % 4 == 0 && tickCount > 1) {
            supportTargetSelector.tick();
        }
    }

    /** Skeletons and dungeon bosses are its allies: they're who it heals and buffs. */
    @Override
    public boolean isAlliedTo(Entity entity) {
        return super.isAlliedTo(entity) || entity.getType().is(EntityTypeTags.SKELETONS) || DungeonBosses.isBoss(entity);
    }

    @Override
    public LivingEntity getSupportTarget() {
        return supportTarget;
    }

    @Override
    public void setSupportTarget(LivingEntity target) {
        supportTarget = target;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.SKELETON_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.SKELETON_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.SKELETON_DEATH;
    }

    @Override
    protected boolean shouldDespawnInPeaceful() {
        return true;
    }
}
