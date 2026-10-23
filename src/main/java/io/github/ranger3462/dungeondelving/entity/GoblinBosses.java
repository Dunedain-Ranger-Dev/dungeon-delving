package io.github.ranger3462.dungeondelving.entity;

import java.util.Map;

import io.github.ranger3462.dungeondelving.registry.ModEntities;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.EventHooks;

/**
 * The goblin faction's bosses that don't need Iron's Spells: the Goblin Gladiator (tier 1), the Hobgoblin Veteran (tier
 * 3) and the Hobgoblin General (tier 4). The Goblin Shaman Chief (tier 2) comes with Iron's Spells.
 */
public final class GoblinBosses {
    private GoblinBosses() {}

    /** Makes a boss for its arena, named; {@code DungeonBosses} adds it to the world. */
    private static <T extends Mob> T make(EntityType<T> type, ServerLevel level, BlockPos pos, String name) {
        T boss = type.create(level);
        boss.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 180.0f, 0.0f);
        EventHooks.finalizeMobSpawn(boss, level, level.getCurrentDifficultyAt(pos), MobSpawnType.EVENT, null);
        boss.setCustomName(Component.translatable("entity.dungeondelving." + name).withStyle(ChatFormatting.GOLD));
        boss.setCustomNameVisible(true);
        return boss;
    }

    public static Mob gladiator(ServerLevel level, BlockPos pos, int tier) {
        return make(ModEntities.GOBLIN_GLADIATOR.get(), level, pos, "goblin_gladiator");
    }

    public static Mob veteran(ServerLevel level, BlockPos pos, int tier) {
        return make(ModEntities.HOBGOBLIN_VETERAN.get(), level, pos, "hobgoblin_veteran");
    }

    public static Mob general(ServerLevel level, BlockPos pos, int tier) {
        return make(ModEntities.HOBGOBLIN_GENERAL.get(), level, pos, "hobgoblin_general");
    }

    /** The Goblin Gladiator, the tier 1 goblin boss: a big pit-fighter goblin with an axe and a sword. */
    public static class Gladiator extends Goblin {
        public static final double MAX_HEALTH = 90.0;

        public Gladiator(EntityType<? extends Gladiator> type, Level level) {
            super(type, level);
            xpReward = 40;
        }

        public static AttributeSupplier.Builder createAttributes() {
            return Goblin.createAttributes().add(Attributes.MAX_HEALTH, MAX_HEALTH).add(Attributes.ATTACK_DAMAGE, 7.0)
                    .add(Attributes.ARMOR, 4.0).add(Attributes.KNOCKBACK_RESISTANCE, 0.6).add(Attributes.SCALE, 1.6)
                    .add(Attributes.FOLLOW_RANGE, 40.0);
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
            return Map.of(EquipmentSlot.HEAD, new ItemStack(Items.CHAINMAIL_HELMET));
        }
    }

    /** The Hobgoblin Veteran, the tier 3 goblin boss: a vanguard who has survived everything. */
    public static class Veteran extends HobgoblinVanguard {
        public static final double MAX_HEALTH = 160.0;

        public Veteran(EntityType<? extends Veteran> type, Level level) {
            super(type, level);
            xpReward = 60;
        }

        public static AttributeSupplier.Builder createAttributes() {
            return HobgoblinVanguard.createAttributes().add(Attributes.MAX_HEALTH, MAX_HEALTH).add(Attributes.ATTACK_DAMAGE, 9.0)
                    .add(Attributes.KNOCKBACK_RESISTANCE, 0.8).add(Attributes.SCALE, 1.2).add(Attributes.FOLLOW_RANGE, 40.0);
        }

        @Override
        protected ItemStack weapon() {
            return new ItemStack(Items.NETHERITE_SWORD);
        }
    }

    /**
     * The Hobgoblin General, the tier 4 goblin boss: shoots a crossbow at range, and draws a sword when you close in
     * ({@link #MELEE_RANGE}), going back to the crossbow when you back off.
     */
    public static class General extends HobgoblinHunter {
        public static final double MAX_HEALTH = 200.0;
        /** Closer than this, the General draws his sword; farther than {@link #RANGED_RANGE}, he goes back to the crossbow. */
        public static final double MELEE_RANGE = 5.0;
        public static final double RANGED_RANGE = 8.0;

        public General(EntityType<? extends General> type, Level level) {
            super(type, level);
            xpReward = 80;
        }

        public static AttributeSupplier.Builder createAttributes() {
            return HobgoblinHunter.createAttributes().add(Attributes.MAX_HEALTH, MAX_HEALTH).add(Attributes.ATTACK_DAMAGE, 10.0)
                    .add(Attributes.ARMOR, 8.0).add(Attributes.KNOCKBACK_RESISTANCE, 0.7).add(Attributes.SCALE, 1.25)
                    .add(Attributes.MOVEMENT_SPEED, 0.29).add(Attributes.FOLLOW_RANGE, 48.0);
        }

        @Override
        protected void addAttackGoal() {
            super.addAttackGoal();
            // Only with the sword out; the crossbow goal only runs with the crossbow out.
            goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.2, true) {
                @Override
                public boolean canUse() {
                    return isSwordDrawn() && super.canUse();
                }

                @Override
                public boolean canContinueToUse() {
                    return isSwordDrawn() && super.canContinueToUse();
                }
            });
        }

        @Override
        protected Map<EquipmentSlot, ItemStack> armor() {
            return Map.of(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET), EquipmentSlot.CHEST, new ItemStack(Items.NETHERITE_CHESTPLATE),
                    EquipmentSlot.LEGS, new ItemStack(Items.IRON_LEGGINGS));
        }

        public boolean isSwordDrawn() {
            return getMainHandItem().getItem() instanceof SwordItem;
        }

        @Override
        protected void customServerAiStep() {
            super.customServerAiStep();
            if (tickCount % 10 == 0 && getTarget() != null) {
                switchWeapons(distanceTo(getTarget()));
            }
        }

        /** Draws the sword or the crossbow for a target this far away. */
        public void switchWeapons(double distance) {
            if (distance < MELEE_RANGE && !isSwordDrawn()) {
                setChargingCrossbow(false);
                stopUsingItem();
                setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.DIAMOND_SWORD));
                setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.IRON_AXE));
            } else if (distance > RANGED_RANGE && isSwordDrawn()) {
                // The crossbow takes both hands.
                setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.CROSSBOW));
                setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
            }
            setDropChance(EquipmentSlot.MAINHAND, 0.0f);
            setDropChance(EquipmentSlot.OFFHAND, 0.0f);
        }
    }
}
