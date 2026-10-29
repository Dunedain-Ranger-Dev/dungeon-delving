package io.github.ranger3462.dungeondelving.gametest;

import io.github.ranger3462.ddprofessions.ProfessionsAndSkills;
import io.github.ranger3462.ddprofessions.skill.ActivitySkills;
import io.github.ranger3462.dungeondelving.DungeonDelving;
import io.github.ranger3462.dungeondelving.dungeon.DungeonMarkers;
import io.github.ranger3462.dungeondelving.integration.professions.ProfessionsLink;
import io.github.ranger3462.dungeondelving.registry.DungeonOre;
import io.github.ranger3462.dungeondelving.registry.ModEntities;
import io.github.ranger3462.dungeondelving.workshop.RunicWorkshop;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Dungeon Delving with Professions and Skills (DD-107): our ores count as ores for its skills and races, our metals fill
 * its tier metal tags, its borax turns up in our dungeons, our bosses drop its tomes, our spear trains its Polearms,
 * and only dungeon mobs get a dungeon level. Each check needs Professions and Skills, so passes without it.
 */
@GameTestHolder(DungeonDelving.MODID)
@PrefixGameTestTemplate(false)
public class ProfessionsLinkTests {
    private static final String ANY_TEMPLATE = "dungeon/cap_placeholder";

    private static boolean professions() {
        return ModList.get().isLoaded(ProfessionsAndSkills.MODID);
    }

    @GameTest(template = ANY_TEMPLATE)
    public static void dungeonOresAreOres(GameTestHelper helper) {
        helper.assertTrue(DungeonOre.TIER_4.ore.get().defaultBlockState().is(Tags.Blocks.ORES), "dungeon ores count as ores (Mining XP, dwarves)");
        helper.succeed();
    }

    @GameTest(template = ANY_TEMPLATE)
    public static void ourMetalsAndBoraxFillTheirTags(GameTestHelper helper) {
        if (!professions()) {
            helper.succeed();
            return;
        }
        var items = helper.getLevel().registryAccess().registryOrThrow(Registries.ITEM);
        DungeonOre[] tiers = { DungeonOre.TIER_1, DungeonOre.TIER_2, DungeonOre.TIER_3, DungeonOre.TIER_4 };
        for (int tier = 1; tier <= 4; tier++) {
            TagKey<net.minecraft.world.item.Item> metals = TagKey.create(Registries.ITEM, ProfessionsAndSkills.id("tier_metals/tier_" + tier));
            helper.assertTrue(new ItemStack(tiers[tier - 1].ingot.get()).is(metals), "a tier " + tier + " ingot is a tier " + tier + " metal");
        }
        helper.assertTrue(new ItemStack(RunicWorkshop.SHARDS.get(2).get()).is(TagKey.create(Registries.ITEM, ProfessionsAndSkills.id("runic_shards"))),
                "Runic Shard III makes runic flux");
        var borax = BuiltInRegistries.BLOCK.get(ProfessionsAndSkills.id("borax_deposit"));
        helper.assertTrue(helper.getLevel().registryAccess().registryOrThrow(Registries.BLOCK).getTag(DungeonMarkers.tierResources(2)).orElseThrow()
                .contains(borax.builtInRegistryHolder()), "borax turns up in tier 2 dungeons");
        helper.assertTrue(items.containsKey(ProfessionsAndSkills.id("borax")), "and Professions and Skills' items are there");
        helper.succeed();
    }

    @GameTest(template = ANY_TEMPLATE)
    public static void bossesDropTomesAndSpearsTrainPolearms(GameTestHelper helper) {
        if (!professions()) {
            helper.succeed();
            return;
        }
        var key = ResourceKey.create(Registries.LOOT_TABLE, DungeonDelving.id("bosses/barrow_king/tier_1"));
        var table = helper.getLevel().getServer().reloadableRegistries().getLootTable(key);
        helper.assertTrue(table.getPool(ProfessionsAndSkills.MODID + ":tome_of_insight") != null, "the boss table has the tome pool");
        if (ModList.get().isLoaded("bettercombat")) { // Its weapon attributes make the spear a spear.
            helper.assertTrue(ActivitySkills.styleFor(new ItemStack(ModEntities.GOBLIN_SPEAR.get())).filter(ActivitySkills.POLEARMS::equals).isPresent(),
                    "the goblin spear trains Polearms");
        }
        helper.succeed();
    }

    @GameTest(template = ANY_TEMPLATE)
    public static void onlyDungeonMobsGetADungeonLevel(GameTestHelper helper) {
        if (!professions()) {
            helper.succeed();
            return;
        }
        Zombie zombie = EntityType.ZOMBIE.create(helper.getLevel());
        helper.assertTrue(ProfessionsLink.dungeonLevel(zombie, 3).isEmpty(), "a zombie outside a dungeon keeps its own level");
        zombie.discard();
        helper.succeed();
    }
}
