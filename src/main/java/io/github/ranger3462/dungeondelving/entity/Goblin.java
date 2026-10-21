package io.github.ranger3462.dungeondelving.entity;

import io.github.ranger3462.dungeondelving.registry.ModEntities;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;

/**
 * The spear goblin: the everyday dungeon enemy. Small, fast and fragile; it rushes straight at the player. Faster than
 * a zombie, and three hits from an iron sword kill it. Goblins are hostile, and they swarm: hitting one sends every
 * goblin within {@link #SWARM_RANGE} blocks after the attacker.
 */
public class Goblin extends Monster {
    public static final double MAX_HEALTH = 14.0;
    public static final double SPEED = 0.30;
    public static final double DAMAGE = 3.0;
    /** How far a hurt goblin's call reaches. */
    public static final double SWARM_RANGE = 20.0;
    /** Chance the goblin drops its spear when killed. */
    public static final float SPEAR_DROP_CHANCE = 0.05f;

    public Goblin(EntityType<? extends Goblin> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, MAX_HEALTH)
                .add(Attributes.MOVEMENT_SPEED, SPEED)
                .add(Attributes.ATTACK_DAMAGE, DAMAGE)
                .add(Attributes.FOLLOW_RANGE, 24.0);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.1, true));
        goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 1.0));
        goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0f));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType spawnType, SpawnGroupData data) {
        SpawnGroupData result = super.finalizeSpawn(level, difficulty, spawnType, data);
        setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(ModEntities.GOBLIN_SPEAR.get()));
        setDropChance(EquipmentSlot.MAINHAND, SPEAR_DROP_CHANCE);
        return result;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean hurt = super.hurt(source, amount);
        if (hurt && !level().isClientSide && source.getEntity() instanceof LivingEntity attacker && !(attacker instanceof Goblin)) {
            callForHelp(attacker);
        }
        return hurt;
    }

    /** Sends every goblin within {@link #SWARM_RANGE} blocks after the attacker. */
    public void callForHelp(LivingEntity attacker) {
        if (attacker instanceof Player player && (player.isCreative() || player.isSpectator())) {
            return;
        }
        for (Goblin goblin : level().getEntitiesOfClass(Goblin.class, getBoundingBox().inflate(SWARM_RANGE))) {
            if (goblin != this && goblin.isAlive() && goblin.getTarget() != attacker && goblin.distanceTo(this) <= SWARM_RANGE) {
                goblin.setTarget(attacker);
            }
        }
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

    /** Higher than a piglin, so goblins sound small and shrill. */
    @Override
    public float getVoicePitch() {
        return super.getVoicePitch() * 1.4f;
    }
}
