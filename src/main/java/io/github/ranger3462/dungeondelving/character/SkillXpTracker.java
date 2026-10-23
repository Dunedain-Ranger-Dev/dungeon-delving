package io.github.ranger3462.dungeondelving.character;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import io.github.ranger3462.dungeondelving.DungeonDelving;
import io.github.ranger3462.dungeondelving.integration.skills.PufferfishSkills;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Watches each player's skill experience and tells their client when a skill gains some, for the XP popup by the
 * hotbar. Pufferfish's Skills awards much of it itself (mining, fishing...) with no event, so this checks every
 * skill's total a few times a second and reports what went up.
 */
@EventBusSubscriber(modid = DungeonDelving.MODID)
public final class SkillXpTracker {
    private static final int INTERVAL = 5;
    private static final Map<UUID, Map<ResourceLocation, Integer>> LAST = new HashMap<>();

    private SkillXpTracker() {}

    @SubscribeEvent
    public static void onTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % INTERVAL != 0 || !SkillLevels.pufferfish()) {
            return;
        }
        Map<ResourceLocation, Integer> last = LAST.get(player.getUUID());
        boolean first = last == null;
        if (first) {
            last = new HashMap<>();
            LAST.put(player.getUUID(), last);
        }
        for (ResourceLocation skill : SkillGroup.all()) {
            var total = PufferfishSkills.total(player, skill);
            if (total.isEmpty()) {
                continue;
            }
            Integer before = last.put(skill, total.get());
            if (!first && before != null && total.get() > before) {
                PufferfishSkills.progress(player, skill).ifPresent(progress -> PacketDistributor.sendToPlayer(player,
                        new SkillXpPayload(skill.toString(), progress.level(), progress.current(), progress.required(), total.get() - before)));
            }
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        LAST.remove(event.getEntity().getUUID());
    }
}
