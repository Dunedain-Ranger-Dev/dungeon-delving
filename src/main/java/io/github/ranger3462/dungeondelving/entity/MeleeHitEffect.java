package io.github.ranger3462.dungeondelving.entity;

import io.github.ranger3462.dungeondelving.DungeonDelving;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

/**
 * A mob whose melee hits do something extra (e.g. a stray swordsman's chill). Applied from the damage event rather
 * than {@code doHurtTarget}, because Better Mob Combat lands mob swings through its own attack logic and skips
 * {@code doHurtTarget}; the damage event fires however the hit lands.
 */
public interface MeleeHitEffect {
    /** Called after this mob's melee hit has damaged a target. */
    void onMeleeHit(LivingEntity target);

    @EventBusSubscriber(modid = DungeonDelving.MODID)
    final class Events {
        private Events() {}

        @SubscribeEvent
        public static void onDamage(LivingDamageEvent.Post event) {
            var source = event.getSource();
            // A melee hit: the attacker itself is the direct cause, not a projectile it fired.
            if (event.getNewDamage() > 0 && source.getEntity() instanceof MeleeHitEffect attacker && source.getDirectEntity() == source.getEntity()
                    && !event.getEntity().level().isClientSide) {
                attacker.onMeleeHit(event.getEntity());
            }
        }
    }
}
