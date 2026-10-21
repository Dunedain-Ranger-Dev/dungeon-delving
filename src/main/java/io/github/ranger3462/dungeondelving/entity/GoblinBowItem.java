package io.github.ranger3462.dungeondelving.entity;

import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;

/** A badly made goblin bow: it wears out fast, and its arrows hit softer than a normal bow's. */
public class GoblinBowItem extends BowItem {
    /** How hard its arrows hit compared with a normal bow. */
    public static final double DAMAGE_FACTOR = 0.75;

    public GoblinBowItem(Properties properties) {
        super(properties);
    }

    @Override
    public AbstractArrow customArrow(AbstractArrow arrow, ItemStack projectile, ItemStack weapon) {
        arrow.setBaseDamage(arrow.getBaseDamage() * DAMAGE_FACTOR);
        return arrow;
    }
}
