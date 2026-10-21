package io.github.ranger3462.dungeondelving.client;

import io.github.ranger3462.dungeondelving.entity.Goblin;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;

/** The humanoid body, with arms posed for what a goblin holds: drawing a bow while fighting, or holding a weapon. */
public class GoblinModel<T extends Goblin> extends HumanoidModel<T> {
    public GoblinModel(ModelPart root) {
        super(root);
    }

    @Override
    public void prepareMobModel(T goblin, float limbSwing, float limbSwingAmount, float partialTick) {
        rightArmPose = ArmPose.EMPTY;
        leftArmPose = ArmPose.EMPTY;
        ItemStack held = goblin.getItemInHand(InteractionHand.MAIN_HAND);
        if (held.getItem() instanceof BowItem && goblin.isAggressive()) {
            rightArmPose = ArmPose.BOW_AND_ARROW;
        } else if (!held.isEmpty()) {
            rightArmPose = ArmPose.ITEM;
        }
        super.prepareMobModel(goblin, limbSwing, limbSwingAmount, partialTick);
    }
}
