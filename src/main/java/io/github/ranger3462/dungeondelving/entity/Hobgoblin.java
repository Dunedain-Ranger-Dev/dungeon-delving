package io.github.ranger3462.dungeondelving.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;

/**
 * A hobgoblin: a full-sized goblin, disciplined and well armed, that shows up in tier 3 and 4 goblin dungeons. Still a
 * goblin (it joins their swarms), drawn with the goblin body at full size. Each kind sets its own weapons and armor.
 */
public abstract class Hobgoblin extends Goblin {
    protected Hobgoblin(EntityType<? extends Hobgoblin> type, Level level) {
        super(type, level);
        xpReward = 10;
    }

    /** Sturdier and steadier than a goblin. Each kind raises its health and damage from here. */
    public static AttributeSupplier.Builder createAttributes() {
        return Goblin.createAttributes()
                .add(Attributes.MAX_HEALTH, 26.0)
                .add(Attributes.MOVEMENT_SPEED, 0.27)
                .add(Attributes.ATTACK_DAMAGE, 5.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.2)
                .add(Attributes.FOLLOW_RANGE, 32.0);
    }

    /** A deeper voice than a goblin's. */
    @Override
    public float getVoicePitch() {
        return super.getVoicePitch() / 1.4f * 0.85f;
    }
}
