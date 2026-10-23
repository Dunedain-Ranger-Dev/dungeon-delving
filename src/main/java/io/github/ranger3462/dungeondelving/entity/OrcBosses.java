package io.github.ranger3462.dungeondelving.entity;

import java.util.List;
import java.util.Map;

import io.github.ranger3462.dungeondelving.DungeonDelving;
import io.github.ranger3462.dungeondelving.registry.ModEntities;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.EventHooks;

/**
 * The orc faction's bosses that don't need Iron's Spells: the Orc Commander (tier 1), the Orc Chieftain (tier 2) and the
 * Orc Warchief (tier 4). The Orc Lorekeeper (tier 3) comes with Iron's Spells.
 */
public final class OrcBosses {
    /** Every orc, for the Warchief's war cry. */
    public static final TagKey<EntityType<?>> ORCS = TagKey.create(Registries.ENTITY_TYPE, DungeonDelving.id("orcs"));

    private OrcBosses() {}

    private static <T extends Mob> T make(EntityType<T> type, ServerLevel level, BlockPos pos, String name) {
        T boss = type.create(level);
        boss.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 180.0f, 0.0f);
        EventHooks.finalizeMobSpawn(boss, level, level.getCurrentDifficultyAt(pos), MobSpawnType.EVENT, null);
        boss.setCustomName(Component.translatable("entity.dungeondelving." + name).withStyle(ChatFormatting.GOLD));
        boss.setCustomNameVisible(true);
        return boss;
    }

    public static Mob commander(ServerLevel level, BlockPos pos, int tier) {
        return make(ModEntities.ORC_COMMANDER.get(), level, pos, "orc_commander");
    }

    public static Mob chieftain(ServerLevel level, BlockPos pos, int tier) {
        return make(ModEntities.ORC_CHIEFTAIN.get(), level, pos, "orc_chieftain");
    }

    public static Mob warchief(ServerLevel level, BlockPos pos, int tier) {
        return make(ModEntities.ORC_WARCHIEF.get(), level, pos, "orc_warchief");
    }

    /** The Orc Commander, the tier 1 orc boss: a drilled fighter with an axe and a shield he raises to block. */
    public static class Commander extends Orc {
        public static final double MAX_HEALTH = 100.0;

        public Commander(EntityType<? extends Commander> type, Level level) {
            super(type, level);
            xpReward = 40;
        }

        public static AttributeSupplier.Builder createAttributes() {
            return Orc.createAttributes().add(Attributes.MAX_HEALTH, MAX_HEALTH).add(Attributes.ATTACK_DAMAGE, 7.0).add(Attributes.ARMOR, 4.0)
                    .add(Attributes.KNOCKBACK_RESISTANCE, 0.6).add(Attributes.SCALE, 1.15).add(Attributes.FOLLOW_RANGE, 40.0);
        }

        @Override
        protected void registerGoals() {
            super.registerGoals();
            goalSelector.addGoal(3, new ShieldBlockGoal(this));
        }

        @Override
        protected ItemStack weapon() {
            return new ItemStack(Items.IRON_AXE);
        }

        @Override
        protected ItemStack offhand() {
            return new ItemStack(Items.SHIELD);
        }

        @Override
        protected Map<EquipmentSlot, ItemStack> armor() {
            return Map.of(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET), EquipmentSlot.CHEST, new ItemStack(Items.CHAINMAIL_CHESTPLATE));
        }
    }

    /** The Orc Chieftain, the tier 2 orc boss: a huge orc with a two-handed diamond axe and a lot of health. */
    public static class Chieftain extends Orc {
        public static final double MAX_HEALTH = 150.0;

        public Chieftain(EntityType<? extends Chieftain> type, Level level) {
            super(type, level);
            xpReward = 50;
        }

        public static AttributeSupplier.Builder createAttributes() {
            return Orc.createAttributes().add(Attributes.MAX_HEALTH, MAX_HEALTH).add(Attributes.ATTACK_DAMAGE, 10.0).add(Attributes.ARMOR, 6.0)
                    .add(Attributes.KNOCKBACK_RESISTANCE, 0.8).add(Attributes.SCALE, 1.3).add(Attributes.FOLLOW_RANGE, 40.0);
        }

        @Override
        protected ItemStack weapon() {
            return new ItemStack(Items.DIAMOND_AXE);
        }

        @Override
        protected Map<EquipmentSlot, ItemStack> armor() {
            return Map.of(EquipmentSlot.HEAD, new ItemStack(Items.GOLDEN_HELMET), EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
        }
    }

    /**
     * The Orc Warchief, the tier 4 orc boss: a towering melee fighter with some war magic. Every so often he lets out a
     * war cry ({@link #warCry}) that empowers the orcs around him and hardens himself, and he slams the ground when you
     * crowd him ({@link #slam}), throwing everyone near him back.
     */
    public static class Warchief extends Orc {
        public static final double MAX_HEALTH = 260.0;
        public static final int WAR_CRY_COOLDOWN = 300;
        public static final int SLAM_COOLDOWN = 120;
        public static final double WAR_CRY_RANGE = 16.0;
        public static final double SLAM_RANGE = 4.0;
        private int nextWarCry = 60;
        private int nextSlam = 40;

        public Warchief(EntityType<? extends Warchief> type, Level level) {
            super(type, level);
            xpReward = 80;
        }

        public static AttributeSupplier.Builder createAttributes() {
            return Orc.createAttributes().add(Attributes.MAX_HEALTH, MAX_HEALTH).add(Attributes.ATTACK_DAMAGE, 12.0).add(Attributes.ARMOR, 10.0)
                    .add(Attributes.KNOCKBACK_RESISTANCE, 0.9).add(Attributes.SCALE, 1.4).add(Attributes.MOVEMENT_SPEED, 0.27)
                    .add(Attributes.FOLLOW_RANGE, 48.0);
        }

        @Override
        protected ItemStack weapon() {
            return new ItemStack(Items.NETHERITE_AXE);
        }

        @Override
        protected Map<EquipmentSlot, ItemStack> armor() {
            return Map.of(EquipmentSlot.HEAD, new ItemStack(Items.NETHERITE_HELMET), EquipmentSlot.CHEST, new ItemStack(Items.DIAMOND_CHESTPLATE));
        }

        @Override
        protected void customServerAiStep() {
            super.customServerAiStep();
            LivingEntity target = getTarget();
            if (target == null || !(level() instanceof ServerLevel level)) {
                return;
            }
            if (tickCount >= nextWarCry) {
                nextWarCry = tickCount + WAR_CRY_COOLDOWN;
                warCry(level);
            }
            if (tickCount >= nextSlam && distanceTo(target) < SLAM_RANGE) {
                nextSlam = tickCount + SLAM_COOLDOWN;
                slam(level);
            }
        }

        /** Empowers the orcs around him (Strength) and hardens himself (Resistance). Returns how many orcs it reached. */
        public int warCry(ServerLevel level) {
            List<Mob> orcs = level.getEntitiesOfClass(Mob.class, getBoundingBox().inflate(WAR_CRY_RANGE), mob -> mob != this && mob.getType().is(ORCS));
            orcs.forEach(orc -> orc.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 200, 0)));
            addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 200, 0));
            playSound(SoundEvents.RAVAGER_ROAR, 2.0f, 0.7f);
            level.sendParticles(ParticleTypes.ANGRY_VILLAGER, getX(), getY() + getBbHeight(), getZ(), 8, 0.6, 0.3, 0.6, 0.0);
            return orcs.size();
        }

        /** Slams the ground: hurts and throws back everyone near him who isn't an orc. Returns how many it hit. */
        public int slam(ServerLevel level) {
            List<LivingEntity> hit = level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(SLAM_RANGE),
                    entity -> entity != this && !entity.getType().is(ORCS) && entity.isAlive());
            for (LivingEntity entity : hit) {
                entity.hurt(damageSources().mobAttack(this), 6.0f);
                Vec3 away = entity.position().subtract(position()).normalize();
                entity.push(away.x * 1.2, 0.5, away.z * 1.2);
                entity.hurtMarked = true;
            }
            playSound(SoundEvents.GENERIC_EXPLODE.value(), 1.0f, 0.7f);
            level.sendParticles(ParticleTypes.EXPLOSION, getX(), getY() + 0.2, getZ(), 3, 1.0, 0.1, 1.0, 0.0);
            return hit.size();
        }
    }
}
