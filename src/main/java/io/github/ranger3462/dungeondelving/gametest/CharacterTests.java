package io.github.ranger3462.dungeondelving.gametest;

import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;
import io.github.ranger3462.dungeondelving.DungeonDelving;
import io.github.ranger3462.dungeondelving.character.AbilityScore;
import io.github.ranger3462.dungeondelving.character.Characters;
import io.github.ranger3462.dungeondelving.character.SkillLevels;
import io.github.ranger3462.dungeondelving.registry.ModRegistries;
import io.github.ranger3462.dungeondelving.skill.CraftSkill;
import io.github.ranger3462.dungeondelving.skill.CraftingSkills;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Checks the character core (DD-80): the character level from skill levels, ability points, and the scores' effects. */
@GameTestHolder(DungeonDelving.MODID)
@PrefixGameTestTemplate(false)
public class CharacterTests {
    private static final String ANY_TEMPLATE = "dungeon/cap_placeholder";

    @GameTest(template = ANY_TEMPLATE)
    public static void theCharacterLevelAveragesTheBestSkills(GameTestHelper helper) {
        helper.assertTrue(SkillLevels.characterLevel(List.of(), 6) == 1, "a new character is level 1");
        helper.assertTrue(SkillLevels.characterLevel(List.of(10, 8, 6, 4, 2, 0, 0, 30), 6) == 1 + (30 + 10 + 8 + 6 + 4 + 2) / 6,
                "the level should average the best six skills");
        // A pure crafter levels the same as a pure fighter with the same skill levels.
        helper.assertTrue(SkillLevels.characterLevel(List.of(12, 12, 0, 0), 2) == SkillLevels.characterLevel(List.of(0, 0, 12, 12), 2),
                "which skills they are doesn't matter");
        helper.succeed();
    }

    @GameTest(template = ANY_TEMPLATE)
    public static void pointsGoIntoScoresAndScoresDoThings(GameTestHelper helper) {
        ServerPlayer player = player(helper, "score_tester");
        helper.assertTrue(player.getAttributeValue(AbilityScore.STRENGTH.attribute()) == AbilityScore.BASE, "scores start at 10");
        helper.assertTrue(Characters.available(player) == 0, "a level 1 character has no points yet");
        helper.assertFalse(Characters.allocate(player, AbilityScore.STRENGTH), "no points, nothing to allocate");

        Characters.addBonusPoints(player, 6);
        double health = player.getMaxHealth();
        double damage = player.getAttributeValue(Attributes.ATTACK_DAMAGE);
        helper.assertTrue(Characters.allocate(player, AbilityScore.STRENGTH) && Characters.allocate(player, AbilityScore.STRENGTH),
                "points should go into Strength");
        for (int i = 0; i < 4; i++) {
            Characters.allocate(player, AbilityScore.CONSTITUTION);
        }
        helper.assertTrue(player.getAttributeValue(AbilityScore.STRENGTH.attribute()) == 12, "2 points: Strength 12");
        helper.assertTrue(player.getAttributeValue(Attributes.ATTACK_DAMAGE) > damage, "Strength should raise melee damage");
        helper.assertTrue(player.getMaxHealth() == health + 4, "4 points of Constitution: +4 max health, got " + (player.getMaxHealth() - health));
        helper.assertTrue(Characters.available(player) == 0, "all 6 points spent");

        Characters.respec(player);
        helper.assertTrue(player.getAttributeValue(AbilityScore.STRENGTH.attribute()) == AbilityScore.BASE && player.getMaxHealth() == health,
                "a respec takes every point back");
        helper.assertTrue(Characters.available(player) == 6, "and they can be spent again");
        helper.succeed();
    }

    @GameTest(template = ANY_TEMPLATE)
    public static void tomesGiveBonusPoints(GameTestHelper helper) {
        ServerPlayer player = player(helper, "tome_tester");
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModRegistries.TOME_OF_INSIGHT.get(), 2));
        player.getMainHandItem().use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        helper.assertTrue(Characters.available(player) == 1, "reading a tome gives a point");
        helper.assertTrue(player.getMainHandItem().getCount() == 1, "the tome is used up");
        helper.succeed();
    }

    @GameTest(template = ANY_TEMPLATE)
    public static void craftingSkillsCountTowardTheCharacterLevel(GameTestHelper helper) {
        ServerPlayer player = player(helper, "crafter");
        CraftingSkills.setLevel(player, CraftSkill.ARMORSMITHING, 13);
        List<Integer> levels = SkillLevels.levels(player);
        helper.assertTrue(levels.contains(12), "Armorsmithing 13 should count as skill level 12 (counting from 0), got " + levels);
        if (SkillLevels.pufferfish()) {
            var forging = net.puffish.skillsmod.api.SkillsAPI.getCategory(ResourceLocation.fromNamespaceAndPath(DungeonDelving.MODID, "armorsmithing"))
                    .flatMap(category -> category.getExperience()).orElseThrow();
            helper.assertTrue(forging.getLevel(player) == 12, "the Armorsmithing tree should follow the Armorsmithing skill, got " + forging.getLevel(player));
        }
        helper.assertTrue(Characters.level(player) == 1 + 12 / 6, "one skill at 12 makes character level 3");
        helper.assertTrue(Characters.available(player) == 2, "two levels past 1: two points");
        helper.succeed();
    }

    private static ServerPlayer player(GameTestHelper helper, String name) {
        ServerPlayer player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), name));
        Characters.apply(player);
        return player;
    }
}
