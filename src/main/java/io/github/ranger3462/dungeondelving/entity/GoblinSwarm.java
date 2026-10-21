package io.github.ranger3462.dungeondelving.entity;

import io.github.ranger3462.dungeondelving.DungeonDelving;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

/**
 * Goblins look out for each other: hurting any goblin (any mob in the {@code dungeondelving:goblins} entity tag) sends
 * every goblin within {@link #RANGE} blocks after the attacker. Goblins are hostile anyway; this makes them swarm.
 */
@EventBusSubscriber(modid = DungeonDelving.MODID)
public final class GoblinSwarm {
    public static final TagKey<EntityType<?>> GOBLINS = TagKey.create(Registries.ENTITY_TYPE, DungeonDelving.id("goblins"));
    /** How far a hurt goblin's call reaches. */
    public static final double RANGE = 40.0;

    private GoblinSwarm() {}

    public static boolean isGoblin(LivingEntity entity) {
        return entity.getType().is(GOBLINS);
    }

    @SubscribeEvent
    public static void onDamage(LivingDamageEvent.Post event) {
        LivingEntity hurt = event.getEntity();
        if (!hurt.level().isClientSide && isGoblin(hurt) && event.getSource().getEntity() instanceof LivingEntity attacker && !isGoblin(attacker)) {
            callForHelp(hurt, attacker);
        }
    }

    /** Sends every goblin within {@link #RANGE} blocks of {@code goblin} after the attacker. */
    public static void callForHelp(LivingEntity goblin, LivingEntity attacker) {
        if (attacker instanceof Player player && (player.isCreative() || player.isSpectator())) {
            return;
        }
        for (Mob other : goblin.level().getEntitiesOfClass(Mob.class, goblin.getBoundingBox().inflate(RANGE), GoblinSwarm::isGoblin)) {
            if (other != goblin && other.isAlive() && other.getTarget() != attacker && other.distanceTo(goblin) <= RANGE) {
                other.setTarget(attacker);
            }
        }
    }
}
