package io.github.ranger3462.dungeondelving.gametest;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.github.ranger3462.dungeondelving.DungeonDelving;
import io.github.ranger3462.dungeondelving.dungeon.DungeonFactions;
import io.github.ranger3462.dungeondelving.dungeon.DungeonRegions;
import io.github.ranger3462.dungeondelving.dungeon.challenge.DungeonBosses;
import io.github.ranger3462.dungeondelving.dungeon.challenge.DungeonProgress;
import io.github.ranger3462.dungeondelving.dungeon.layout.DungeonPieces;
import io.github.ranger3462.dungeondelving.workshop.RunicWorkshop;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Checks the boss roster: which boss guards which tier, that bosses get their own arenas, and the Goblin Shaman Chief
 * (when Iron's Spells is installed) with his staff.
 */
@GameTestHolder(DungeonDelving.MODID)
@PrefixGameTestTemplate(false)
public class BossRosterTests {
    private static final String ANY_TEMPLATE = "dungeon/cap_placeholder";
    private static final String CHIEF = "goblin_chief";

    private static boolean irons() {
        return ModList.get().isLoaded("irons_spellbooks");
    }

    @GameTest(template = ANY_TEMPLATE)
    public static void eachTierHasItsBoss(GameTestHelper helper) {
        RandomSource random = RandomSource.create(1);
        for (int i = 0; i < 20; i++) {
            helper.assertTrue(DungeonBosses.pick(1, DungeonFactions.UNDEAD, random).equals(DungeonBosses.ZOMBIE_LORD), "undead tier 1: the Zombie Lord");
            helper.assertTrue(DungeonBosses.pick(2, DungeonFactions.UNDEAD, random).equals(DungeonBosses.SKELETON_LORD), "undead tier 2: the Skeleton Lord");
            helper.assertTrue(DungeonBosses.pick(3, DungeonFactions.UNDEAD, random).equals(DungeonBosses.BARROW_KING), "undead tier 3: the Barrow King");
            helper.assertTrue(DungeonBosses.pick(4, DungeonFactions.UNDEAD, random).equals(irons() ? DungeonBosses.LICH_LORD : DungeonBosses.BARROW_KING),
                    "undead tier 4: the Lich Lord (or the Barrow King without Iron's Spells)");
        }
        helper.succeed();
    }

    @GameTest(template = ANY_TEMPLATE)
    public static void eachDungeonBelongsToOneFaction(GameTestHelper helper) {
        RandomSource random = RandomSource.create(6);
        Set<String> tierOne = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            for (int tier = 1; tier <= 4; tier++) {
                helper.assertTrue(DungeonFactions.pick(tier, random, DungeonFactions.ENABLED_DEFAULTS).equals(DungeonFactions.UNDEAD),
                        "with the default factions, every dungeon is undead");
            }
            String faction = DungeonFactions.pick(1, random, List.of(DungeonFactions.UNDEAD, DungeonFactions.GOBLINS));
            tierOne.add(faction);
            String boss = DungeonBosses.pick(1, faction, random);
            helper.assertTrue(faction.equals(DungeonFactions.GOBLINS) ? boss.equals(DungeonBosses.GOBLIN_GLADIATOR) : boss.equals(DungeonBosses.ZOMBIE_LORD),
                    "a tier 1 " + faction + " dungeon got the wrong boss: " + boss);
        }
        helper.assertTrue(tierOne.equals(Set.of(DungeonFactions.GOBLINS, DungeonFactions.UNDEAD)),
                "with goblins turned on, tier 1 should roll goblins (the Gladiator's) and undead, got " + tierOne);
        helper.assertTrue(DungeonFactions.current("skeletons").equals(DungeonFactions.UNDEAD), "saved skeleton runs load as undead");
        helper.succeed();
    }

    @GameTest(template = ANY_TEMPLATE)
    public static void bossesFightInTheirOwnArenas(GameTestHelper helper) {
        DungeonPieces pieces = DungeonPieces.load(helper.getLevel().getServer());
        RandomSource random = RandomSource.create(2);
        for (int i = 0; i < 20; i++) {
            ResourceLocation chief = pieces.pickBoss(CHIEF, DungeonBosses.bossIds(), random);
            ResourceLocation king = pieces.pickBoss(DungeonBosses.BARROW_KING, DungeonBosses.bossIds(), random);
            if (irons()) {
                helper.assertTrue(chief.getPath().contains("boss_goblin_chief"), "the chief should fight in his arena, not " + chief);
            }
            helper.assertFalse(king.getPath().contains("boss_goblin_chief"), "the Barrow King shouldn't get the chief's arena");
        }
        helper.succeed();
    }

    @GameTest(template = ANY_TEMPLATE)
    public static void runsRememberTheirBoss(GameTestHelper helper) {
        DungeonRegions regions = new DungeonRegions();
        regions.startProgress(2, new DungeonProgress(1, CHIEF, 2, List.of(), BlockPos.ZERO));
        DungeonRegions loaded = DungeonRegions.load(regions.save(new CompoundTag(), helper.getLevel().registryAccess()), helper.getLevel().registryAccess());
        helper.assertTrue(loaded.progress(2).orElseThrow().boss().equals(CHIEF), "a run's boss should be saved");
        helper.succeed();
    }

    @GameTest(template = ANY_TEMPLATE)
    public static void theChiefIsAFragileCasterWithASignatureStaff(GameTestHelper helper) {
        if (!irons()) {
            helper.succeed();
            return;
        }
        LivingEntity chief = DungeonBosses.spawn(helper.getLevel(), helper.absolutePos(new BlockPos(0, 1, 0)).above(100), 2, -1, CHIEF);
        Item staff = BuiltInRegistries.ITEM.get(DungeonDelving.id("goblin_chief_staff"));
        try {
            helper.assertTrue(DungeonBosses.isBoss(chief), "the chief should be a boss");
            helper.assertTrue(chief.getMaxHealth() == 70.0f, "the chief should have 70 health, not " + chief.getMaxHealth());
            helper.assertTrue(chief.getMainHandItem().is(staff), "the chief should hold his staff");
        } finally {
            chief.discard();
        }
        LootTable table = helper.getLevel().getServer().reloadableRegistries().getLootTable(DungeonBosses.lootTable(CHIEF, 2));
        LootParams params = new LootParams.Builder(helper.getLevel()).withParameter(LootContextParams.ORIGIN, Vec3.ZERO).create(LootContextParamSets.CHEST);
        boolean dropped = false;
        for (int i = 0; i < 60; i++) {
            List<ItemStack> drops = table.getRandomItems(params);
            helper.assertTrue(drops.stream().anyMatch(stack -> stack.is(RunicWorkshop.shard(2))), "the chief (tier 2) should always drop Runic Shard II");
            dropped |= drops.stream().anyMatch(stack -> stack.is(staff));
        }
        helper.assertTrue(dropped, "the chief should sometimes drop his staff");
        helper.assertFalse(new ItemStack(staff).getAttributeModifiers().modifiers().isEmpty(), "the staff should boost spells");
        helper.succeed();
    }
}
