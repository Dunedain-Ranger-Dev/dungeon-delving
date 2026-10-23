package io.github.ranger3462.dungeondelving.client;

import io.github.ranger3462.dungeondelving.entity.ArmedHumanoid;
import io.github.ranger3462.dungeondelving.entity.CrossbowWielder;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;

/**
 * The humanoid body, with arms posed for what a goblin, hobgoblin or orc holds: drawing a bow or winding and aiming a crossbow while
 * fighting, raising a shield to block, or just holding weapons in either hand.
 */
public class ArmedHumanoidModel<T extends ArmedHumanoid> extends HumanoidModel<T> {
    public ArmedHumanoidModel(ModelPart root) {
        super(root);
    }

    @Override
    public void prepareMobModel(T mob, float limbSwing, float limbSwingAmount, float partialTick) {
        rightArmPose = ArmPose.EMPTY;
        leftArmPose = ArmPose.EMPTY;
        ItemStack held = mob.getItemInHand(InteractionHand.MAIN_HAND);
        if (held.getItem() instanceof BowItem && mob.isAggressive()) {
            rightArmPose = ArmPose.BOW_AND_ARROW;
        } else if (held.getItem() instanceof CrossbowItem && mob instanceof CrossbowWielder wielder) {
            rightArmPose = wielder.isChargingCrossbow() ? ArmPose.CROSSBOW_CHARGE : mob.isAggressive() ? ArmPose.CROSSBOW_HOLD : ArmPose.ITEM;
        } else if (!held.isEmpty()) {
            rightArmPose = ArmPose.ITEM;
        }
        if (!mob.getItemInHand(InteractionHand.OFF_HAND).isEmpty()) {
            leftArmPose = mob.isUsingItem() && mob.getUsedItemHand() == InteractionHand.OFF_HAND ? ArmPose.BLOCK : ArmPose.ITEM;
        }
        super.prepareMobModel(mob, limbSwing, limbSwingAmount, partialTick);
    }
}
