package io.github.ranger3462.dungeondelving.entity;

import io.github.ranger3462.dungeondelving.registry.ModEntities;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * The goblin warrior: the everyday goblin. Small, fast and fragile, with no armor; it rushes straight at the player, and
 * goblins are dangerous in numbers. Faster than a zombie, and three hits from an iron sword kill it. Goblins swarm when
 * one is hit (see {@link GoblinSwarm}).
 */
public class Goblin extends ArmedHumanoid {
    public static final double MAX_HEALTH = 14.0;
    public static final double SPEED = 0.30;
    public static final double DAMAGE = 3.0;
    /** Chance the goblin drops its weapon when killed. */
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
    protected ItemStack weapon() {
        return new ItemStack(ModEntities.GOBLIN_SPEAR.get());
    }

    @Override
    protected float weaponDropChance() {
        return SPEAR_DROP_CHANCE;
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
