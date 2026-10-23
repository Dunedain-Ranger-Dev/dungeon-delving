package io.github.ranger3462.dungeondelving.gametest;

import java.util.UUID;

import com.mojang.authlib.GameProfile;
import io.github.ranger3462.dungeondelving.DungeonDelving;
import io.github.ranger3462.dungeondelving.character.AbilityScore;
import io.github.ranger3462.dungeondelving.character.Characters;
import io.github.ranger3462.dungeondelving.character.Race;
import io.github.ranger3462.dungeondelving.character.RaceTraits;
import io.github.ranger3462.dungeondelving.registry.ModRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Pig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Checks the races (DD-82): their score bonuses, sizes and traits. */
@GameTestHolder(DungeonDelving.MODID)
@PrefixGameTestTemplate(false)
public class RaceTests {
    private static final String ANY_TEMPLATE = "dungeon/cap_placeholder";

    @GameTest(template = ANY_TEMPLATE)
    public static void racesGiveScoresAndSize(GameTestHelper helper) {
        ServerPlayer player = player(helper, "racer");
        helper.assertTrue(Characters.data(player).raceOf().isEmpty(), "a new character has no race");
        Characters.setRace(player, Race.DWARF);
        helper.assertTrue(player.getAttributeValue(AbilityScore.CONSTITUTION.attribute()) == 12 && player.getAttributeValue(AbilityScore.STRENGTH.attribute()) == 11,
                "a dwarf gets +2 CON and +1 STR");
        helper.assertTrue(player.getAttributeValue(Attributes.SCALE) < 1.0, "a dwarf is shorter");
        Characters.setRace(player, Race.ELF);
        helper.assertTrue(player.getAttributeValue(AbilityScore.CONSTITUTION.attribute()) == 10 && player.getAttributeValue(AbilityScore.DEXTERITY.attribute()) == 12,
                "changing race takes the old bonuses off and puts the new ones on");
        helper.assertTrue(player.getAttributeValue(Attributes.SCALE) == 1.0, "an elf is normal height");
        helper.assertTrue(player.getAttributeValue(Attributes.SAFE_FALL_DISTANCE) == 5.0, "an elf's falls are safe up to 5 blocks");
        Characters.setRace(player, Race.HUMAN);
        helper.assertTrue(player.getAttributeValue(Attributes.SAFE_FALL_DISTANCE) == 3.0, "the elf's trait comes off with the race");
        for (AbilityScore score : AbilityScore.values()) {
            helper.assertTrue(player.getAttributeValue(score.attribute()) == 11, "a human gets +1 " + score.abbreviation);
        }
        helper.succeed();
    }

    @GameTest(template = ANY_TEMPLATE)
    public static void halfOrcsMakeALastStand(GameTestHelper helper) {
        ServerPlayer player = player(helper, "orcblood");
        Characters.setRace(player, Race.HALF_ORC);
        var source = helper.getLevel().damageSources().generic();
        LivingDeathEvent first = NeoForge.EVENT_BUS.post(new LivingDeathEvent(player, source));
        helper.assertTrue(first.isCanceled() && player.getHealth() == 1.0f, "a half-orc's first killing blow leaves them at half a heart");
        LivingDeathEvent second = NeoForge.EVENT_BUS.post(new LivingDeathEvent(player, source));
        helper.assertFalse(second.isCanceled(), "but only once every few minutes");
        helper.succeed();
    }

    @GameTest(template = ANY_TEMPLATE)
    public static void dragonbornBreatheFire(GameTestHelper helper) {
        ServerPlayer player = player(helper, "wyrm");
        BlockPos pos = helper.absolutePos(new BlockPos(1, 2, 1)).above(100);
        player.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0.0f, 0.0f); // Facing south (+z).
        Pig ahead = EntityType.PIG.create(helper.getLevel());
        Pig behind = EntityType.PIG.create(helper.getLevel());
        ahead.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 3.5);
        behind.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() - 3.5);
        helper.getLevel().addFreshEntity(ahead);
        helper.getLevel().addFreshEntity(behind);
        try {
            Characters.setRace(player, Race.DRAGONBORN);
            int hit = RaceTraits.breathe(player);
            helper.assertTrue(hit == 1 && ahead.isOnFire() && !behind.isOnFire(), "the breath burns what's ahead, not behind; hit " + hit);
        } finally {
            ahead.discard();
            behind.discard();
        }
        helper.succeed();
    }

    @GameTest(template = ANY_TEMPLATE)
    public static void dungeonOresAreOres(GameTestHelper helper) {
        TagKey<net.minecraft.world.level.block.Block> ores = Tags.Blocks.ORES;
        helper.assertTrue(net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(DungeonDelving.id("thailite_ore")).defaultBlockState().is(ores),
                "dungeon ores count as ores (Mining XP, dwarves)");
        helper.assertTrue(new net.minecraft.world.item.ItemStack(ModRegistries.ELIXIR_OF_REBIRTH.get()).getMaxStackSize() == 1, "elixirs don't stack");
        helper.succeed();
    }

    @GameTest(template = ANY_TEMPLATE)
    public static void aClassOpensItsTreeAndBonus(GameTestHelper helper) {
        ServerPlayer player = player(helper, "classy");
        double health = player.getMaxHealth();
        Characters.setClass(player, io.github.ranger3462.dungeondelving.character.PlayerClass.FIGHTER);
        helper.assertTrue(player.getMaxHealth() == health + 2, "a fighter gets +2 max health");
        if (io.github.ranger3462.dungeondelving.character.SkillLevels.pufferfish()) {
            helper.assertTrue(io.github.ranger3462.dungeondelving.integration.skills.PufferfishSkills.isUnlocked(player, DungeonDelving.id("fighter")),
                    "the fighter tree opens");
            helper.assertFalse(io.github.ranger3462.dungeondelving.integration.skills.PufferfishSkills.isUnlocked(player, DungeonDelving.id("mage")),
                    "other class trees stay locked");
        }
        Characters.setClass(player, io.github.ranger3462.dungeondelving.character.PlayerClass.MAGE);
        helper.assertTrue(player.getMaxHealth() == health, "changing class takes the fighter bonus off");
        if (io.github.ranger3462.dungeondelving.character.SkillLevels.pufferfish()) {
            helper.assertFalse(io.github.ranger3462.dungeondelving.integration.skills.PufferfishSkills.isUnlocked(player, DungeonDelving.id("fighter")),
                    "the old class tree locks again");
            helper.assertTrue(io.github.ranger3462.dungeondelving.integration.skills.PufferfishSkills.isUnlocked(player, DungeonDelving.id("mage")),
                    "the new one opens");
        }
        helper.succeed();
    }

    private static ServerPlayer player(GameTestHelper helper, String name) {
        ServerPlayer player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), name));
        Characters.apply(player);
        return player;
    }
}
