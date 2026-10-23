package io.github.ranger3462.dungeondelving.entity;

import io.github.ranger3462.dungeondelving.registry.ModEntities;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

/** A hand axe thrown by an orc raider. It hits hard, then breaks; nothing is left to pick up. */
public class ThrownAxe extends ThrowableItemProjectile {
    public static final float DAMAGE = 5.0f;

    public ThrownAxe(EntityType<? extends ThrownAxe> type, Level level) {
        super(type, level);
    }

    public ThrownAxe(Level level, LivingEntity thrower) {
        super(ModEntities.THROWN_AXE.get(), thrower, level);
    }

    @Override
    protected Item getDefaultItem() {
        return Items.IRON_AXE;
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);
        strike(result.getEntity());
    }

    /** Hurts what the axe hit. */
    public void strike(Entity target) {
        target.hurt(damageSources().thrown(this, getOwner()), DAMAGE);
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (!level().isClientSide) {
            playSound(SoundEvents.ITEM_BREAK, 0.6f, 1.2f);
            discard();
        }
    }
}
