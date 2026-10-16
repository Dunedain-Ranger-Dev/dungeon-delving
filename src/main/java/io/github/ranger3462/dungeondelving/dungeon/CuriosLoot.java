package io.github.ranger3462.dungeondelving.dungeon;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.event.DropRulesEvent;
import top.theillusivec4.curios.api.type.capability.ICurio;

/**
 * Curios support, used only when Curios is installed (it's an optional dependency, so nothing else may refer to
 * these Curios classes directly).
 * <ul>
 * <li>Dungeon loot sweeps also cover Curios slots, e.g. a backpack worn on the back.</li>
 * <li>Dying in a dungeon keeps Curios items equipped, like the rest of the held inventory, instead of dropping them
 * in a dungeon that's about to be wiped.</li>
 * </ul>
 */
public final class CuriosLoot {
    private CuriosLoot() {}

    /** Called from the mod constructor when Curios is loaded. */
    public static void register() {
        NeoForge.EVENT_BUS.addListener(CuriosLoot::onDropRules);
    }

    static void sweep(ServerPlayer player, LootSweep sweep) {
        CuriosApi.getCuriosInventory(player).ifPresent(curios -> sweep.sweep(curios.getEquippedCurios()));
    }

    private static void onDropRules(DropRulesEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && player.level().dimension() == DungeonDimension.DUNGEON) {
            event.addOverride(stack -> true, ICurio.DropRule.ALWAYS_KEEP);
        }
    }
}
