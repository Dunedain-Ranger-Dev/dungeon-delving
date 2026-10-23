package io.github.ranger3462.dungeondelving.entity;

import java.util.Map;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

/** The hobgoblin soldier (tiers 3-4): sword and a shield it raises to block, in medium armor (iron helm and chainmail). */
public class HobgoblinSoldier extends Hobgoblin {
    public HobgoblinSoldier(EntityType<? extends HobgoblinSoldier> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Hobgoblin.createAttributes().add(Attributes.MAX_HEALTH, 28.0).add(Attributes.ATTACK_DAMAGE, 5.0);
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        goalSelector.addGoal(3, new ShieldBlockGoal(this));
    }

    @Override
    protected ItemStack weapon() {
        return new ItemStack(Items.IRON_SWORD);
    }

    @Override
    protected ItemStack offhand() {
        return new ItemStack(Items.SHIELD);
    }

    @Override
    protected Map<EquipmentSlot, ItemStack> armor() {
        return Map.of(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET), EquipmentSlot.CHEST, new ItemStack(Items.CHAINMAIL_CHESTPLATE),
                EquipmentSlot.LEGS, new ItemStack(Items.CHAINMAIL_LEGGINGS));
    }
}
