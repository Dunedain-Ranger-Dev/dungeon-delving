package io.github.ranger3462.dungeondelving.entity;

import java.util.Map;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

/** The hobgoblin vanguard (tier 4): the line holder. Heavy plate, sword and board, hard to push around. */
public class HobgoblinVanguard extends HobgoblinSoldier {
    public HobgoblinVanguard(EntityType<? extends HobgoblinVanguard> type, Level level) {
        super(type, level);
        xpReward = 14;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return HobgoblinSoldier.createAttributes().add(Attributes.MAX_HEALTH, 40.0).add(Attributes.ATTACK_DAMAGE, 6.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.5).add(Attributes.MOVEMENT_SPEED, 0.25);
    }

    @Override
    protected ItemStack weapon() {
        return new ItemStack(Items.DIAMOND_SWORD);
    }

    @Override
    protected Map<EquipmentSlot, ItemStack> armor() {
        return Map.of(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET), EquipmentSlot.CHEST, new ItemStack(Items.DIAMOND_CHESTPLATE),
                EquipmentSlot.LEGS, new ItemStack(Items.IRON_LEGGINGS), EquipmentSlot.FEET, new ItemStack(Items.IRON_BOOTS));
    }
}
