package io.github.ranger3462.dungeondelving.integration.professions;

import java.util.Optional;
import java.util.OptionalInt;

import io.github.ranger3462.ddprofessions.character.MobLevels;
import io.github.ranger3462.ddprofessions.forging.Crafts;
import io.github.ranger3462.dungeondelving.dungeon.DungeonDimension;
import io.github.ranger3462.dungeondelving.dungeon.DungeonFactions;
import io.github.ranger3462.dungeondelving.dungeon.DungeonRegions;
import io.github.ranger3462.dungeondelving.dungeon.DungeonRun;
import io.github.ranger3462.dungeondelving.dungeon.FactionInfighting;
import io.github.ranger3462.dungeondelving.dungeon.challenge.DungeonBosses;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;

/**
 * Dungeon Delving with Professions and Skills (only loaded when it's installed): mobs in a dungeon have their tier's
 * levels, which scale skill experience. Tier 1 is levels 1 to 10, tier 2 is 11 to 20 and so on, with bosses at the top
 * of their band. Dungeon mobs drop its rarer crafting materials (DD-109): kobolds Emberroot, and casters Arcane Dust,
 * from tier 2. Its other links are data: our metals fill its tier metal tags, its borax turns up in our dungeons, and its
 * Barrowmoss grows in undead dungeons ({@code overgrowth/undead}).
 */
public final class ProfessionsLink {
    /** Levels per dungeon tier. */
    public static final int TIER_BAND = 10;
    /** The chance a kobold or caster killed by a player drops its material, by tier (index 0 is tier 1). */
    public static final float[] MATERIAL_CHANCE = { 0.0f, 0.04f, 0.06f, 0.08f };

    private ProfessionsLink() {}

    public static void register() {
        MobLevels.addSource(ProfessionsLink::dungeonLevel);
        NeoForge.EVENT_BUS.addListener(ProfessionsLink::onDrops);
    }

    /**
     * The crafting material a mob of this kind may drop in a dungeon of this tier held by this faction: Emberroot from
     * kobold dungeons, Arcane Dust from casters ({@code dungeondelving:faction_casters}), from tier 2. Empty otherwise.
     */
    public static Optional<Item> material(EntityType<?> type, int tier, String faction) {
        if (tier < 2) {
            return Optional.empty();
        }
        if (DungeonFactions.KOBOLDS.equals(DungeonFactions.current(faction))) {
            return Optional.of(Crafts.EMBERROOT.get());
        }
        return type.is(FactionInfighting.CASTERS) ? Optional.of(Crafts.ARCANE_DUST.get()) : Optional.empty();
    }

    /** A dungeon mob killed by a player sometimes drops its material. */
    private static void onDrops(LivingDropsEvent event) {
        if (!(event.getEntity() instanceof Mob mob) || !event.isRecentlyHit() || mob.level().dimension() != DungeonDimension.DUNGEON) {
            return;
        }
        DungeonRegions.get().progress(DungeonDimension.regionAt(mob.blockPosition())).ifPresent(progress -> {
            int tier = DungeonRun.clampTier(progress.tier());
            material(mob.getType(), tier, progress.faction())
                    .filter(item -> mob.getRandom().nextFloat() < MATERIAL_CHANCE[tier - 1])
                    .ifPresent(item -> event.getDrops().add(new ItemEntity(mob.level(), mob.getX(), mob.getY(), mob.getZ(), new ItemStack(item))));
        });
    }

    /** A dungeon mob's level: in its tier's band, by its stats within it. Empty outside a dungeon run. */
    public static OptionalInt dungeonLevel(Mob mob, int fromStats) {
        if (mob.level().dimension() != DungeonDimension.DUNGEON) {
            return OptionalInt.empty();
        }
        return DungeonRegions.get().progress(DungeonDimension.regionAt(mob.blockPosition())).map(progress -> {
            int floor = (progress.tier() - 1) * TIER_BAND;
            return OptionalInt.of(DungeonBosses.isBoss(mob) ? floor + TIER_BAND : floor + Mth.clamp(fromStats, 1, TIER_BAND - 1));
        }).orElse(OptionalInt.empty());
    }
}
