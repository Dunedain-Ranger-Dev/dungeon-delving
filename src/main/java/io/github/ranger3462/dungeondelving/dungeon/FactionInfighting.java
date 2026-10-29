package io.github.ranger3462.dungeondelving.dungeon;

import java.util.List;

import io.github.ranger3462.dungeondelving.Config;
import io.github.ranger3462.dungeondelving.DungeonDelving;
import io.github.ranger3462.dungeondelving.entity.GoblinSwarm;
import io.github.ranger3462.dungeondelving.entity.OrcBosses;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import io.github.ranger3462.dungeondelving.entity.Kobold;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;

/**
 * When a dungeon mob is hit by one of its own faction (an orc's stray axe, a skeleton's arrow), it may turn on it. That
 * makes for a fun brawl in low-tier dungeons (up to {@code factionInfightingMaxTier}), but above that tier a faction
 * holds together. Casters ({@link #CASTERS}) are never turned on by their own side, at any tier, so a shaman healing
 * the warband doesn't get killed by it.
 */
@EventBusSubscriber(modid = DungeonDelving.MODID)
public final class FactionInfighting {
    /** A faction's spellcasters and support mobs, never targeted by their own faction. */
    public static final TagKey<EntityType<?>> CASTERS = TagKey.create(Registries.ENTITY_TYPE, DungeonDelving.id("faction_casters"));
    /** The factions, by the tag their mobs share. */
    private static final List<TagKey<EntityType<?>>> FACTIONS = List.of(GoblinSwarm.GOBLINS, OrcBosses.ORCS, Kobold.KOBOLDS,
            EntityTypeTags.UNDEAD);

    private FactionInfighting() {}

    /** Whether two mobs belong to the same faction. */
    public static boolean sameFaction(LivingEntity one, LivingEntity other) {
        return FACTIONS.stream().anyMatch(faction -> one.getType().is(faction) && other.getType().is(faction));
    }

    /** Whether a mob may turn on another of its faction in a dungeon of this tier. */
    public static boolean mayTurnOn(LivingEntity target, int tier) {
        return !target.getType().is(CASTERS) && tier <= Config.FACTION_INFIGHTING_MAX_TIER.get();
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onTarget(LivingChangeTargetEvent event) {
        LivingEntity target = event.getNewAboutToBeSetTarget();
        if (!(event.getEntity() instanceof Mob mob) || target == null || mob.level().isClientSide()
                || mob.level().dimension() != DungeonDimension.DUNGEON || !sameFaction(mob, target)) {
            return;
        }
        int tier = DungeonRegions.get().progress(DungeonDimension.regionAt(mob.blockPosition())).map(progress -> progress.tier())
                .orElse(DungeonRun.MAX_TIER);
        if (!mayTurnOn(target, tier)) {
            event.setCanceled(true);
        }
    }
}
