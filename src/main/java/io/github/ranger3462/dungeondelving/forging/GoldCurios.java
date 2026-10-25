package io.github.ranger3462.dungeondelving.forging;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

/**
 * Goldsmithing's jewelry in Curios slots. Curios is optional, so nothing outside this class may refer to Curios
 * classes; call {@link #register} only when it's loaded.
 */
public final class GoldCurios {
    private GoldCurios() {}

    public static void register() {
        for (var jewelry : java.util.List.of(Crafts.GOLD_RING, Crafts.GOLD_AMULET)) {
            CuriosApi.registerCurio(jewelry.get(), new ICurioItem() {
                @Override
                public Multimap<Holder<Attribute>, AttributeModifier> getAttributeModifiers(SlotContext slotContext, ResourceLocation id, ItemStack stack) {
                    JewelryItem item = (JewelryItem) stack.getItem();
                    return ImmutableMultimap.of(item.kind.attribute, new AttributeModifier(id, item.bonus(stack), AttributeModifier.Operation.ADD_VALUE));
                }
            });
        }
    }
}
