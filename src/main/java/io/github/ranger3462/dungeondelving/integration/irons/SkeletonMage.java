package io.github.ranger3462.dungeondelving.integration.irons;

import java.util.List;

import io.github.ranger3462.dungeondelving.dungeon.challenge.DungeonBosses;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.entity.mobs.abstract_spell_casting_mob.AbstractSpellCastingMob;
import io.redspace.ironsspellbooks.entity.mobs.goals.WizardAttackGoal;
import io.redspace.ironsspellbooks.registries.MobEffectRegistry;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
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
 * The skeleton mage, the Barrow King's court caster: it heals the King (and other skeletons) when they're hurt, keeps
 * Fortify and Haste on them in a fight, and only throws the odd Wither Skull between casts. Casts real Iron's Spells spells, so it only
 * exists when Iron's Spells is installed.
 */
public class SkeletonMage extends AbstractSpellCastingMob implements Enemy {
    public static final double MAX_HEALTH = 20.0;

    public SkeletonMage(EntityType<? extends SkeletonMage> type, Level level) {
        super(type, level);
        xpReward = 10;
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
        // Healing and buffing come first; it only attacks between casts.
        goalSelector.addGoal(1, new SupportCastGoal(this, ally -> ally == this || isAlliedTo(ally), 20.0, 6.0, 30, 60,
                List.of(SpellRegistry.BLESSING_OF_LIFE_SPELL.get()),
                List.of(new SupportCastGoal.Buff(SpellRegistry.FORTIFY_SPELL.get(), MobEffectRegistry.FORTIFY),
                        new SupportCastGoal.Buff(SpellRegistry.HASTE_SPELL.get(), MobEffectRegistry.HASTENED)))
                .setSpellQuality(0.2f, 0.4f));
        goalSelector.addGoal(3, new WizardAttackGoal(this, 1.0, 100, 160)
                .setSpells(List.of(SpellRegistry.WITHER_SKULL_SPELL.get()), List.of(), List.of(), List.of(SpellRegistry.HEAL_SPELL.get()))
                .setSpellQuality(0.1f, 0.25f));
        goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 0.8));
        goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0f));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    /** Skeletons and dungeon bosses are its allies: they're who it heals and buffs. */
    @Override
    public boolean isAlliedTo(Entity entity) {
        return super.isAlliedTo(entity) || entity.getType().is(EntityTypeTags.SKELETONS) || DungeonBosses.isBoss(entity);
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
