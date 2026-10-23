package io.github.ranger3462.dungeondelving.entity;

import java.util.Map;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

/**
 * The hobgoblin spellbreaker (tier 4): a fast, lightly armored duelist with an axe and a sword, trained to hunt mages.
 * It goes after anyone who has cast a spell lately ({@link Spellcasters}) before anyone else.
 */
public class HobgoblinSpellbreaker extends Hobgoblin {
    public HobgoblinSpellbreaker(EntityType<? extends HobgoblinSpellbreaker> type, Level level) {
        super(type, level);
        xpReward = 14;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Hobgoblin.createAttributes().add(Attributes.MAX_HEALTH, 30.0).add(Attributes.ATTACK_DAMAGE, 7.0)
                .add(Attributes.MOVEMENT_SPEED, 0.32).add(Attributes.FOLLOW_RANGE, 40.0);
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        // Casters first, ahead of whoever is nearest.
        targetSelector.addGoal(0, new NearestAttackableTargetGoal<>(this, Player.class, 10, false, false,
                target -> target instanceof Player player && Spellcasters.isCaster(player)));
    }

    @Override
    protected void addAttackGoal() {
        goalSelector.addGoal(2, new net.minecraft.world.entity.ai.goal.MeleeAttackGoal(this, 1.25, true));
    }

    @Override
    protected ItemStack weapon() {
        return new ItemStack(Items.IRON_AXE);
    }

    @Override
    protected ItemStack offhand() {
        return new ItemStack(Items.IRON_SWORD);
    }

    @Override
    protected Map<EquipmentSlot, ItemStack> armor() {
        return Map.of(EquipmentSlot.CHEST, new ItemStack(Items.LEATHER_CHESTPLATE), EquipmentSlot.LEGS, new ItemStack(Items.LEATHER_LEGGINGS));
    }
}
