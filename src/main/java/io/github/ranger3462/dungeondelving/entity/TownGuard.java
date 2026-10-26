package io.github.ranger3462.dungeondelving.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

/**
 * A town guard (DD-98): a human soldier who turns out when the town's raided, in place of iron golems. It fights the
 * raiders (and anything hostile), never the town's side. Each wears one of the game's built-in player skins, so a
 * squad looks like a mix of people.
 * <p>
 * A town kits its guards out by its stats ({@link #equip}): better armor and weapons for a wealthier town, tougher and
 * harder-hitting guards for a better-defended one. How many turn out goes by morale (see {@code RealmLiveRaids}). Guards
 * aren't saved with the world: they're there for a fight, and gone when it's over or its ground unloads.
 */
public class TownGuard extends PathfinderMob {
    /** The built-in player skins (textures/entity/player/wide/), one per guard at random. */
    public static final String[] SKINS = { "steve", "alex", "ari", "efe", "kai", "makena", "noor", "sunny", "zuri" };
    private static final EntityDataAccessor<Integer> SKIN = SynchedEntityData.defineId(TownGuard.class, EntityDataSerializers.INT);

    public TownGuard(EntityType<? extends TownGuard> type, Level level) {
        super(type, level);
        setPersistenceRequired();
        if (!level.isClientSide) {
            entityData.set(SKIN, level.getRandom().nextInt(SKINS.length));
        }
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 20.0).add(Attributes.ATTACK_DAMAGE, 2.0).add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.FOLLOW_RANGE, 32.0).add(Attributes.ATTACK_KNOCKBACK, 0.4);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(SKIN, 0);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.15, true));
        goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0f));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Mob.class, 5, true, false, mob -> mob instanceof Enemy));
    }

    public ResourceLocation skin() {
        return ResourceLocation.withDefaultNamespace("textures/entity/player/wide/" + SKINS[Mth.clamp(entityData.get(SKIN), 0, SKINS.length - 1)] + ".png");
    }

    /** The armor tier a town's wealth buys: 0 leather (below 5), 1 chainmail (5-9), 2 iron (10-19), 3 diamond (20 and up). */
    public static int armorTier(int wealth) {
        return wealth >= 20 ? 3 : wealth >= 10 ? 2 : wealth >= 5 ? 1 : 0;
    }

    /** A guard's health for a town's defense: 20, plus 1 per point of defense, up to 60. */
    public static double health(int defense) {
        return Mth.clamp(20.0 + defense, 20.0, 60.0);
    }

    /** A guard's attack damage (before its weapon) for a town's defense: 1, plus 1 per 5 points, up to 7. */
    public static double attack(int defense) {
        return Mth.clamp(1.0 + defense / 5, 1.0, 7.0);
    }

    /** Kits a guard out for its town: armor and weapon by wealth, health and attack by defense. */
    public void equip(int wealth, int defense) {
        getAttribute(Attributes.MAX_HEALTH).setBaseValue(health(defense));
        getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(attack(defense));
        setHealth(getMaxHealth());
        Item[][] sets = {
                { Items.LEATHER_HELMET, Items.LEATHER_CHESTPLATE, Items.LEATHER_LEGGINGS, Items.LEATHER_BOOTS },
                { Items.CHAINMAIL_HELMET, Items.CHAINMAIL_CHESTPLATE, Items.CHAINMAIL_LEGGINGS, Items.CHAINMAIL_BOOTS },
                { Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS },
                { Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS } };
        Item[] weapons = { Items.STONE_SWORD, Items.IRON_SWORD, Items.IRON_SWORD, Items.DIAMOND_SWORD };
        int tier = armorTier(wealth);
        EquipmentSlot[] slots = { EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET };
        for (int i = 0; i < slots.length; i++) {
            setItemSlot(slots[i], new ItemStack(sets[tier][i]));
            setDropChance(slots[i], 0.0f);
        }
        setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(weapons[tier]));
        setDropChance(EquipmentSlot.MAINHAND, 0.0f);
        setItemSlot(EquipmentSlot.OFFHAND, tier >= 1 ? new ItemStack(Items.SHIELD) : ItemStack.EMPTY);
        setDropChance(EquipmentSlot.OFFHAND, 0.0f);
    }

    @Override
    public boolean doHurtTarget(net.minecraft.world.entity.Entity target) {
        swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        return super.doHurtTarget(target);
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public boolean canAttack(LivingEntity target) {
        return !(target instanceof TownGuard) && super.canAttack(target);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("skin", entityData.get(SKIN));
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(SKIN, tag.getInt("skin"));
    }
}
