package io.github.ranger3462.dungeondelving.gametest;

import java.util.UUID;

import com.mojang.authlib.GameProfile;

import dev.shadowsoffire.apotheosis.affix.AffixHelper;
import dev.shadowsoffire.apotheosis.tiers.WorldTier;
import io.github.ranger3462.dungeondelving.DungeonDelving;
import io.github.ranger3462.dungeondelving.forging.ForgedGear;
import io.github.ranger3462.dungeondelving.forging.Forging;
import io.github.ranger3462.dungeondelving.integration.apotheosis.ApotheosisDungeons;
import io.github.ranger3462.dungeondelving.integration.apotheosis.ApotheosisForging;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Checks that Apotheosis stays in dungeons: world tiers follow dungeon tiers, and no affix gear turns up outside them. */
@GameTestHolder(DungeonDelving.MODID)
@PrefixGameTestTemplate(false)
public class ApotheosisDungeonTests {
    private static final String ANY_TEMPLATE = "dungeon/cap_placeholder";

    private static boolean apotheosis() {
        return ModList.get().isLoaded("apotheosis");
    }

    @GameTest(template = ANY_TEMPLATE)
    public static void worldTiersFollowDungeonTiers(GameTestHelper helper) {
        if (!apotheosis()) {
            helper.succeed();
            return;
        }
        helper.assertTrue(ApotheosisDungeons.tierFor(1) == WorldTier.FRONTIER && ApotheosisDungeons.tierFor(2) == WorldTier.ASCENT
                && ApotheosisDungeons.tierFor(3) == WorldTier.SUMMIT && ApotheosisDungeons.tierFor(4) == WorldTier.PINNACLE,
                "dungeon tiers 1-4 are Frontier, Ascent, Summit and Pinnacle");
        ServerPlayer player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "tier_tester"));
        WorldTier.setTier(player, WorldTier.SUMMIT);
        ApotheosisDungeons.syncTier(player);
        helper.assertTrue(WorldTier.getTier(player) == WorldTier.HAVEN, "outside a dungeon, a player is put back in Haven");
        helper.succeed();
    }

    @GameTest(template = ANY_TEMPLATE)
    public static void mobsOutsideDungeonsDontKeepAffixGear(GameTestHelper helper) {
        if (!apotheosis()) {
            helper.succeed();
            return;
        }
        ServerPlayer player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "affix_tester"));
        ItemStack sword = new ItemStack(Forging.FORGED_SWORD.get());
        sword.set(Forging.STARS.get(), Forging.MAX_STARS);
        ForgedGear.applyStats(sword);
        ApotheosisForging.roll(sword, player);
        helper.assertTrue(ApotheosisDungeons.isApotheosisLoot(sword), "a rolled sword is affix gear");
        Zombie zombie = EntityType.ZOMBIE.create(helper.getLevel());
        zombie.setItemSlot(EquipmentSlot.MAINHAND, sword);
        zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(net.minecraft.world.item.Items.IRON_HELMET));
        ApotheosisDungeons.stripAffixGear(zombie);
        helper.assertTrue(zombie.getMainHandItem().isEmpty(), "affix gear comes off a mob outside a dungeon");
        helper.assertTrue(!zombie.getItemBySlot(EquipmentSlot.HEAD).isEmpty(), "plain gear stays on");
        zombie.discard();
        helper.succeed();
    }

    /** With the pack's data, Apotheosis no longer injects affix gear or gems into chests outside dungeons. */
    @GameTest(template = ANY_TEMPLATE)
    public static void overworldChestsHaveNoAffixGear(GameTestHelper helper) {
        if (!apotheosis()) {
            helper.succeed();
            return;
        }
        ServerLevel level = helper.getLevel();
        LootTable table = level.getServer().reloadableRegistries()
                .getLootTable(ResourceKey.create(Registries.LOOT_TABLE, ResourceLocation.withDefaultNamespace("chests/simple_dungeon")));
        ServerPlayer player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "chest_tester"));
        WorldTier.setTier(player, WorldTier.PINNACLE); // Even at the top tier, which outside dungeons would normally be the most generous.
        LootParams params = new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN, helper.absoluteVec(net.minecraft.world.phys.Vec3.ZERO))
                .withParameter(LootContextParams.THIS_ENTITY, player).create(LootContextParamSets.CHEST);
        int found = 0;
        for (int i = 0; i < 200; i++) {
            for (ItemStack stack : table.getRandomItems(params)) {
                if (ApotheosisDungeons.isApotheosisLoot(stack) || AffixHelper.hasAffixes(stack)) {
                    found++;
                }
            }
        }
        helper.assertTrue(found == 0, "200 overworld dungeon chests should hold no affix gear or gems, found " + found);
        helper.succeed();
    }
}
