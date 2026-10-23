package io.github.ranger3462.dungeondelving.entity;

import java.util.Map;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

/**
 * An orc (DD-74): bigger, tougher and slower than a goblin, fighting in warbands. Each kind of orc is a small subclass
 * here, setting its stats, weapons and armor; the ranged ones and the bosses have their own files.
 */
public abstract class Orc extends ArmedHumanoid {
    protected Orc(EntityType<? extends Orc> type, Level level) {
        super(type, level);
        xpReward = 8;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 24.0)
                .add(Attributes.MOVEMENT_SPEED, 0.26)
                .add(Attributes.ATTACK_DAMAGE, 4.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.25)
                .add(Attributes.FOLLOW_RANGE, 32.0);
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.PIGLIN_BRUTE_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.PIGLIN_BRUTE_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.PIGLIN_BRUTE_DEATH;
    }

    /** Deeper than a piglin brute. */
    @Override
    public float getVoicePitch() {
        return super.getVoicePitch() * 0.75f;
    }

    /** The orc scrapper (tier 1): a brawler in scavenged leather with a stone axe. */
    public static class Scrapper extends Orc {
        public Scrapper(EntityType<? extends Scrapper> type, Level level) {
            super(type, level);
        }

        public static AttributeSupplier.Builder createAttributes() {
            return Orc.createAttributes().add(Attributes.MAX_HEALTH, 20.0);
        }

        @Override
        protected ItemStack weapon() {
            return new ItemStack(Items.STONE_AXE);
        }

        @Override
        protected Map<EquipmentSlot, ItemStack> armor() {
            return Map.of(EquipmentSlot.CHEST, new ItemStack(Items.LEATHER_CHESTPLATE));
        }
    }

    /** The orc veteran (tiers 2-3): an iron axe, a chainmail shirt and helm, and the scars to show for it. */
    public static class Veteran extends Orc {
        public Veteran(EntityType<? extends Veteran> type, Level level) {
            super(type, level);
            xpReward = 10;
        }

        public static AttributeSupplier.Builder createAttributes() {
            return Orc.createAttributes().add(Attributes.MAX_HEALTH, 32.0).add(Attributes.ATTACK_DAMAGE, 6.0);
        }

        @Override
        protected ItemStack weapon() {
            return new ItemStack(Items.IRON_AXE);
        }

        @Override
        protected Map<EquipmentSlot, ItemStack> armor() {
            return Map.of(EquipmentSlot.HEAD, new ItemStack(Items.CHAINMAIL_HELMET), EquipmentSlot.CHEST, new ItemStack(Items.CHAINMAIL_CHESTPLATE));
        }
    }

    /**
     * The orc rampager (tier 4): a hulking berserker with a diamond axe. Below half health it flies into a rage
     * (Strength and Speed) and fights to the death.
     */
    public static class Rampager extends Orc {
        private boolean raging;

        public Rampager(EntityType<? extends Rampager> type, Level level) {
            super(type, level);
            xpReward = 16;
        }

        public static AttributeSupplier.Builder createAttributes() {
            return Orc.createAttributes().add(Attributes.MAX_HEALTH, 50.0).add(Attributes.ATTACK_DAMAGE, 8.0).add(Attributes.MOVEMENT_SPEED, 0.29)
                    .add(Attributes.KNOCKBACK_RESISTANCE, 0.5).add(Attributes.SCALE, 1.15);
        }

        @Override
        protected ItemStack weapon() {
            return new ItemStack(Items.DIAMOND_AXE);
        }

        @Override
        protected Map<EquipmentSlot, ItemStack> armor() {
            return Map.of(EquipmentSlot.LEGS, new ItemStack(Items.IRON_LEGGINGS));
        }

        public boolean isRaging() {
            return raging;
        }

        @Override
        protected void customServerAiStep() {
            super.customServerAiStep();
            if (!raging && getHealth() < getMaxHealth() / 2) {
                raging = true;
                addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, Integer.MAX_VALUE, 1));
                addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, Integer.MAX_VALUE, 0));
                playSound(SoundEvents.RAVAGER_ROAR, 1.5f, 1.1f);
            }
        }
    }
}
