package io.github.ranger3462.dungeondelving.entity;

import java.util.Map;

import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
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
 * A humanoid dungeon fighter that spawns with its own gear: goblins, hobgoblins and orcs. It hunts players, and each
 * kind picks how it fights ({@link #addAttackGoal}) and what it carries ({@link #weapon}, {@link #offhand},
 * {@link #armor}). Drawn with the humanoid body by {@code ArmedHumanoidRenderer}.
 */
public abstract class ArmedHumanoid extends Monster {
    protected ArmedHumanoid(EntityType<? extends ArmedHumanoid> type, Level level) {
        super(type, level);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        addAttackGoal();
        goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 1.0));
        goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0f));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    /** How it fights: by default, it rushes in with its weapon. */
    protected void addAttackGoal() {
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.1, true));
    }

    /** The weapon it spawns holding. */
    protected ItemStack weapon() {
        return ItemStack.EMPTY;
    }

    /** The chance it drops its weapon when killed. */
    protected float weaponDropChance() {
        return 0.0f;
    }

    /** What it holds in its off hand: a shield, a second weapon, or nothing. */
    protected ItemStack offhand() {
        return ItemStack.EMPTY;
    }

    /** The armor it wears, by slot. None by default. */
    protected Map<EquipmentSlot, ItemStack> armor() {
        return Map.of();
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType spawnType, SpawnGroupData data) {
        SpawnGroupData result = super.finalizeSpawn(level, difficulty, spawnType, data);
        setItemSlot(EquipmentSlot.MAINHAND, weapon());
        setDropChance(EquipmentSlot.MAINHAND, weaponDropChance());
        setItemSlot(EquipmentSlot.OFFHAND, offhand());
        setDropChance(EquipmentSlot.OFFHAND, 0.0f);
        armor().forEach((slot, item) -> {
            setItemSlot(slot, item);
            setDropChance(slot, 0.0f);
        });
        return result;
    }
}
