package io.github.ranger3462.dungeondelving.entity;

import io.github.ranger3462.dungeondelving.registry.ModEntities;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/**
 * A spear thrown by a kobold scavenger. It flies point first (see the client's ThrownSpearRenderer) and otherwise
 * works like a {@link ThrownAxe}: it breaks on what it hits, or sticks in a block for a few seconds. It hits softer than
 * an axe, harder when thrown by a kobold in a pack.
 */
public class ThrownSpear extends ThrownAxe {
    public static final float DAMAGE = 3.0f;
    private float damage = DAMAGE;

    public ThrownSpear(EntityType<? extends ThrownSpear> type, Level level) {
        super(type, level);
    }

    public ThrownSpear(Level level, LivingEntity thrower, float damage) {
        super(ModEntities.THROWN_SPEAR.get(), level, thrower);
        this.damage = damage;
    }

    @Override
    protected Item getDefaultItem() {
        return ModEntities.GOBLIN_SPEAR.get();
    }

    @Override
    public float damage() {
        return damage;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putFloat("Damage", damage);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("Damage")) {
            damage = tag.getFloat("Damage");
        }
    }
}
