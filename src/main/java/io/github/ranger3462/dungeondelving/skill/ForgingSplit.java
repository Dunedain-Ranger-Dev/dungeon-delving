package io.github.ranger3462.dungeondelving.skill;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.github.ranger3462.dungeondelving.DungeonDelving;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * Moves players from the old Forging skill to Weaponsmithing and Armorsmithing (DD-89). On login, a player with Forging
 * XP gets it in both new skills (if more than they have there), and loses the old Forging tree's perks, so its points
 * can be spent again in the new trees.
 */
@EventBusSubscriber(modid = DungeonDelving.MODID)
public final class ForgingSplit {
    /** The old Forging tree's perks: their nodes now live in the two new trees, bought afresh. */
    private static final List<Perk> OLD_PERKS = List.of(Perk.STEADY_HAND, Perk.SURE_STRIKES, Perk.SLOW_COOLING, Perk.THRIFTY);

    private ForgingSplit() {}

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        migrate(event.getEntity());
    }

    /** Moves a player's Forging XP to the two smithing skills, if they have any. */
    public static void migrate(Player player) {
        Map<String, Integer> all = player.getData(CraftingSkills.XP);
        Integer forging = all.get(CraftSkill.OLD_FORGING);
        if (forging == null) {
            return;
        }
        Map<String, Integer> without = new HashMap<>(all);
        without.remove(CraftSkill.OLD_FORGING);
        player.setData(CraftingSkills.XP, without);
        for (CraftSkill skill : List.of(CraftSkill.WEAPONSMITHING, CraftSkill.ARMORSMITHING)) {
            CraftingSkills.setXp(player, skill, Math.max(forging, CraftingSkills.xp(player, skill)));
        }
        for (Perk perk : OLD_PERKS) {
            for (int rank = 1; rank <= perk.ranks; rank++) {
                player.removeTag(perk.tag(rank));
            }
        }
    }
}
