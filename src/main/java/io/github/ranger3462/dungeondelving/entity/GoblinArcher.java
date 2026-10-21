package io.github.ranger3462.dungeondelving.entity;

import io.github.ranger3462.dungeondelving.registry.ModEntities;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.RangedBowAttackGoal;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.level.Level;

/**
 * The goblin archer: keeps its distance and shoots badly made arrows from a Goblin Bow. Fragile like other goblins,
 * and joins their swarms.
 */
public class GoblinArcher extends Goblin implements RangedAttackMob {
    public static final double MAX_HEALTH = 10.0;
    public static final double SPEED = 0.27;
    /** How far off an archer's shots go; higher is worse. Skeletons use 14 minus 4 per difficulty level. */
    private static final float BASE_INACCURACY = 20.0f;

    public GoblinArcher(EntityType<? extends GoblinArcher> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Goblin.createAttributes().add(Attributes.MAX_HEALTH, MAX_HEALTH).add(Attributes.MOVEMENT_SPEED, SPEED);
    }

    @Override
    protected void addAttackGoal() {
        goalSelector.addGoal(2, new RangedBowAttackGoal<>(this, 1.0, 30, 14.0f));
    }

    @Override
    protected ItemStack weapon() {
        return new ItemStack(ModEntities.GOBLIN_BOW.get());
    }

    @Override
    public void performRangedAttack(LivingEntity target, float velocity) {
        ItemStack weapon = getItemInHand(ProjectileUtil.getWeaponHoldingHand(this, item -> item instanceof BowItem));
        ItemStack ammo = getProjectile(weapon);
        AbstractArrow arrow = ProjectileUtil.getMobArrow(this, ammo, velocity, weapon);
        if (weapon.getItem() instanceof ProjectileWeaponItem bow) {
            arrow = bow.customArrow(arrow, ammo, weapon);
        }
        double dx = target.getX() - getX();
        double dy = target.getY(1.0 / 3.0) - arrow.getY();
        double dz = target.getZ() - getZ();
        double flat = Math.sqrt(dx * dx + dz * dz);
        float inaccuracy = BASE_INACCURACY - level().getDifficulty().getId() * 4;
        arrow.shoot(dx, dy + flat * 0.2, dz, 1.4f, inaccuracy);
        playSound(SoundEvents.SKELETON_SHOOT, 1.0f, 1.0f / (getRandom().nextFloat() * 0.4f + 0.8f));
        level().addFreshEntity(arrow);
    }

    @Override
    public boolean canFireProjectileWeapon(ProjectileWeaponItem weapon) {
        return weapon instanceof BowItem;
    }
}
