package io.github.ranger3462.dungeondelving.forging;

import io.github.ranger3462.dungeondelving.DungeonDelving;
import io.github.ranger3462.dungeondelving.skill.CraftSkill;
import io.github.ranger3462.dungeondelving.skill.CraftingSkills;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * Crafting skill XP for crafting forged parts and gear, leather, cloth and wooden pieces (on top of the XP from the stations
 * themselves).
 * Gear trains the skill it's mostly made with: plate and weapons Forging, a hauberk (on its leather backing)
 * Leatherworking, and brigandine (on its woven gambeson) Weaving.
 */
@EventBusSubscriber(modid = DungeonDelving.MODID)
public final class CraftingXp {
    /** Forging XP for a forged piece of gear, per ★; and for a forged part, per ★. */
    public static final int GEAR_XP_PER_STAR = 15;
    public static final int PART_XP_PER_STAR = 2;
    /** XP for each leather or cloth material crafted. */
    public static final int MATERIAL_XP = 3;

    private CraftingXp() {}

    /** The skill crafting a piece of forged gear trains. */
    public static CraftSkill skillFor(ItemStack gear) {
        if (gear.getItem() instanceof ForgedGear.Armor armor) {
            return switch (armor.weight) {
                case LIGHT -> CraftSkill.WEAVING;
                case MEDIUM -> CraftSkill.LEATHERWORKING;
                case HEAVY -> CraftSkill.FORGING;
            };
        }
        return CraftSkill.FORGING;
    }

    @SubscribeEvent
    public static void onCrafted(PlayerEvent.ItemCraftedEvent event) {
        ItemStack crafted = event.getCrafting();
        int stars = Forging.stars(crafted);
        if (crafted.getItem() instanceof ForgedGear.Armor || crafted.getItem() instanceof ForgedGear.Sword || crafted.getItem() instanceof ForgedGear.Axe) {
            CraftingSkills.addXp(event.getEntity(), skillFor(crafted), GEAR_XP_PER_STAR * stars);
        } else if (crafted.is(Forging.ARMOR_PLATE.get()) || crafted.is(Forging.RIVETS.get()) || crafted.is(Forging.CHAIN_LINKS.get())) {
            CraftingSkills.addXp(event.getEntity(), CraftSkill.FORGING, PART_XP_PER_STAR * stars);
        } else if (crafted.is(Forging.HARDENED_LEATHER.get()) || crafted.is(Forging.LEATHER_BASE.get()) || crafted.is(Crafts.STRAPS.get())) {
            CraftingSkills.addXp(event.getEntity(), CraftSkill.LEATHERWORKING, MATERIAL_XP);
        } else if (crafted.is(Forging.GAMBESON_BASE.get()) || crafted.is(Crafts.BOWSTRING.get()) || crafted.is(Crafts.STAFF_WRAP.get())) {
            CraftingSkills.addXp(event.getEntity(), CraftSkill.WEAVING, MATERIAL_XP);
        } else if (crafted.has(Forging.STARS.get()) && (crafted.is(net.minecraft.world.item.Items.BOW) || crafted.is(net.minecraft.world.item.Items.CROSSBOW))) {
            // A bow or crossbow from parts (the plain recipes give no stars).
            CraftingSkills.addXp(event.getEntity(), CraftSkill.WOODWORKING, GEAR_XP_PER_STAR * stars);
        } else if (crafted.is(Crafts.STOCK.get())) {
            CraftingSkills.addXp(event.getEntity(), CraftSkill.WOODWORKING, MATERIAL_XP);
        }
    }
}
