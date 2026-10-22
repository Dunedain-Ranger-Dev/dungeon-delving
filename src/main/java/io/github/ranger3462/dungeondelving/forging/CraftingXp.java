package io.github.ranger3462.dungeondelving.forging;

import io.github.ranger3462.dungeondelving.DungeonDelving;
import io.github.ranger3462.dungeondelving.skill.CraftSkill;
import io.github.ranger3462.dungeondelving.skill.CraftingSkills;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/** Crafting skill XP for crafting forged parts and gear, leather and cloth (on top of the XP from the stations themselves). */
@EventBusSubscriber(modid = DungeonDelving.MODID)
public final class CraftingXp {
    /** Forging XP for a forged piece of gear, per ★; and for a forged part, per ★. */
    public static final int GEAR_XP_PER_STAR = 15;
    public static final int PART_XP_PER_STAR = 2;
    /** XP for each leather or cloth material crafted. */
    public static final int MATERIAL_XP = 3;

    private CraftingXp() {}

    @SubscribeEvent
    public static void onCrafted(PlayerEvent.ItemCraftedEvent event) {
        ItemStack crafted = event.getCrafting();
        int stars = Forging.stars(crafted);
        if (crafted.getItem() instanceof ForgedGear.Armor || crafted.getItem() instanceof ForgedGear.Sword || crafted.getItem() instanceof ForgedGear.Axe) {
            CraftingSkills.addXp(event.getEntity(), CraftSkill.FORGING, GEAR_XP_PER_STAR * stars);
        } else if (crafted.is(Forging.ARMOR_PLATE.get()) || crafted.is(Forging.RIVETS.get()) || crafted.is(Forging.CHAIN_LINKS.get())) {
            CraftingSkills.addXp(event.getEntity(), CraftSkill.FORGING, PART_XP_PER_STAR * stars);
        } else if (crafted.is(Forging.REFINED_LEATHER.get()) || crafted.is(Forging.HARDENED_LEATHER.get()) || crafted.is(Forging.LEATHER_BASE.get())) {
            CraftingSkills.addXp(event.getEntity(), CraftSkill.LEATHERWORKING, MATERIAL_XP);
        } else if (crafted.is(Forging.PADDED_CLOTH.get()) || crafted.is(Forging.GAMBESON_BASE.get())) {
            CraftingSkills.addXp(event.getEntity(), CraftSkill.WEAVING, MATERIAL_XP);
        }
    }
}
