package io.github.ranger3462.dungeondelving.entity;

import io.github.ranger3462.dungeondelving.DungeonDelving;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

/**
 * Kobolds (DD-54): small, scaly scavengers of the first tier, weak alone and dangerous in a crowd. Their trick is
 * <b>pack tactics</b>: with {@link #PACK} or more other kobolds within {@link #PACK_RANGE} blocks, they hit
 * {@link #PACK_BONUS} times as hard (thrown spears too, see {@link KoboldScavenger}). Split them up, or take the
 * stragglers first.
 */
public abstract class Kobold extends ArmedHumanoid {
    public static final TagKey<EntityType<?>> KOBOLDS = TagKey.create(Registries.ENTITY_TYPE, DungeonDelving.id("kobolds"));
    public static final double SPEED = 0.32;
    /** Other kobolds it takes nearby to fight as a pack. */
    public static final int PACK = 2;
    public static final double PACK_RANGE = 6.0;
    public static final double PACK_BONUS = 1.5;
    private static final ResourceLocation PACK_MODIFIER = DungeonDelving.id("kobold_pack");

    protected Kobold(EntityType<? extends Kobold> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 12.0)
                .add(Attributes.MOVEMENT_SPEED, SPEED)
                .add(Attributes.ATTACK_DAMAGE, 2.5)
                .add(Attributes.FOLLOW_RANGE, 24.0);
    }

    /** How many other kobolds are close enough to fight alongside it. */
    public int packmates() {
        return level().getEntitiesOfClass(Kobold.class, getBoundingBox().inflate(PACK_RANGE), other -> other != this && other.isAlive()).size();
    }

    /** Whether it's fighting as a pack, and so hitting harder. */
    public boolean inPack() {
        return packmates() >= PACK;
    }

    /** What its blows (and spears) are multiplied by: {@link #PACK_BONUS} in a pack, else 1. */
    public double packMultiplier() {
        return inPack() ? PACK_BONUS : 1.0;
    }

    /** Checks its pack once a second, and gives or takes its pack bonus to melee damage. */
    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (tickCount % 20 == 0) {
            updatePack();
        }
    }

    public void updatePack() {
        AttributeInstance damage = getAttribute(Attributes.ATTACK_DAMAGE);
        if (damage == null) {
            return;
        }
        damage.removeModifier(PACK_MODIFIER);
        if (inPack()) {
            damage.addTransientModifier(new AttributeModifier(PACK_MODIFIER, PACK_BONUS - 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.FOX_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.FOX_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.FOX_DEATH;
    }

    /** A yappy, high-pitched voice. */
    @Override
    public float getVoicePitch() {
        return super.getVoicePitch() * 1.3f;
    }

    /** The kobold looter: rushes in swinging a stolen pickaxe. */
    public static class Looter extends Kobold {
        public Looter(EntityType<? extends Looter> type, Level level) {
            super(type, level);
        }

        public static AttributeSupplier.Builder createAttributes() {
            return Kobold.createAttributes().add(Attributes.ATTACK_DAMAGE, 3.0);
        }

        @Override
        protected ItemStack weapon() {
            return new ItemStack(Items.STONE_PICKAXE);
        }
    }
}
