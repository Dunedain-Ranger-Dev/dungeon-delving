package io.github.ranger3462.dungeondelving.gametest;

import java.util.Optional;
import java.util.UUID;

import com.mojang.authlib.GameProfile;
import io.github.ranger3462.dungeondelving.DungeonDelving;
import io.github.ranger3462.dungeondelving.character.SkillLevels;
import io.github.ranger3462.dungeondelving.forging.Forging;
import io.github.ranger3462.dungeondelving.forging.ForgedGear;
import io.github.ranger3462.dungeondelving.integration.skills.PufferfishSkills;
import io.github.ranger3462.dungeondelving.registry.ModEntities;
import io.github.ranger3462.dungeondelving.skill.ActivitySkills;
import io.github.ranger3462.dungeondelving.skill.ActivitySkills.ArmorWeight;
import io.github.ranger3462.dungeondelving.skill.MagicSchool;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Checks the activity skills (DD-81): which tree a weapon, armor or spell school trains, and that hits give experience. */
@GameTestHolder(DungeonDelving.MODID)
@PrefixGameTestTemplate(false)
public class ActivitySkillTests {
    private static final String ANY_TEMPLATE = "dungeon/cap_placeholder";

    @GameTest(template = ANY_TEMPLATE)
    public static void weaponsTrainTheirStyle(GameTestHelper helper) {
        expectStyle(helper, new ItemStack(Items.DIAMOND_SWORD), ActivitySkills.ONE_HANDED);
        expectStyle(helper, new ItemStack(Items.TRIDENT), ActivitySkills.POLEARMS);
        expectStyle(helper, new ItemStack(ModEntities.GOBLIN_SPEAR.get()), ActivitySkills.POLEARMS);
        expectStyle(helper, ItemStack.EMPTY, ActivitySkills.UNARMED);
        helper.assertTrue(ActivitySkills.styleFor(new ItemStack(Items.SHIELD)).isEmpty(), "a shield trains no combat style");
        helper.assertTrue(ActivitySkills.styleFor(new ItemStack(Items.COBBLESTONE)).isEmpty(), "a block trains no combat style");
        helper.assertTrue(ActivitySkills.styleForCategory("claymore").equals(Optional.of(ActivitySkills.TWO_HANDED))
                && ActivitySkills.styleForCategory("dagger").equals(Optional.of(ActivitySkills.DAGGERS))
                && ActivitySkills.styleForCategory("fist").equals(Optional.of(ActivitySkills.UNARMED))
                && ActivitySkills.styleForCategory("wand").isEmpty(), "Better Combat types should map to the right styles");
        helper.succeed();
    }

    @GameTest(template = ANY_TEMPLATE)
    public static void armorSortsByWeight(GameTestHelper helper) {
        helper.assertTrue(ActivitySkills.weightOf(new ItemStack(Items.LEATHER_CHESTPLATE)).equals(Optional.of(ArmorWeight.LIGHT)), "leather is light");
        helper.assertTrue(ActivitySkills.weightOf(new ItemStack(Items.CHAINMAIL_CHESTPLATE)).equals(Optional.of(ArmorWeight.MEDIUM)), "chainmail is medium");
        helper.assertTrue(ActivitySkills.weightOf(new ItemStack(Items.NETHERITE_HELMET)).equals(Optional.of(ArmorWeight.HEAVY)), "netherite is heavy");
        helper.assertTrue(ActivitySkills.weightOf(new ItemStack(Forging.ARMOR.get(ForgedGear.Weight.MEDIUM).get(ArmorItem.Type.BOOTS).get()))
                .equals(Optional.of(ArmorWeight.MEDIUM)), "forged armor keeps its weight");
        helper.assertTrue(ActivitySkills.weightOf(new ItemStack(Items.IRON_SWORD)).isEmpty(), "a sword isn't armor");
        BuiltInRegistries.ITEM.getOptional(ResourceLocation.fromNamespaceAndPath("wizards", "wizard_robe_chest")).ifPresent(robe ->
                helper.assertTrue(ActivitySkills.weightOf(new ItemStack(robe)).equals(Optional.of(ArmorWeight.MAGE)), "a wizard's robe is mage armor"));

        Pig wearer = EntityType.PIG.create(helper.getLevel());
        try {
            wearer.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
            helper.assertTrue(ActivitySkills.wornWeight(wearer).isEmpty(), "one piece isn't enough to count");
            wearer.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.LEATHER_CHESTPLATE));
            wearer.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.IRON_LEGGINGS));
            wearer.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.IRON_BOOTS));
            helper.assertTrue(ActivitySkills.wornWeight(wearer).equals(Optional.of(ArmorWeight.HEAVY)), "two and two: the heavier wins");
        } finally {
            wearer.discard();
        }
        helper.succeed();
    }

    @GameTest(template = ANY_TEMPLATE)
    public static void spellSchoolsMapAcrossBothMods(GameTestHelper helper) {
        helper.assertTrue(MagicSchool.byId("spell_power:frost").equals(Optional.of(MagicSchool.FROST))
                && MagicSchool.byId("irons_spellbooks:ice").equals(Optional.of(MagicSchool.FROST)), "Spell Engine frost and Iron's ice are one school");
        helper.assertTrue(MagicSchool.byId("irons_spellbooks:eldritch").equals(Optional.of(MagicSchool.NECROMANCY)), "eldritch is necromancy");
        helper.assertTrue(MagicSchool.byId("spell_power:physical_melee").isEmpty() && MagicSchool.byId("spell_power:igni").isEmpty(),
                "martial skills and Witcher signs aren't magic schools");
        helper.succeed();
    }

    @GameTest(template = ANY_TEMPLATE)
    public static void hittingTrainsTheWeaponAndDualWielding(GameTestHelper helper) {
        if (!SkillLevels.pufferfish()) {
            helper.succeed();
            return;
        }
        ServerPlayer player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "swordsman"));
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.IRON_SWORD));
        Pig pig = EntityType.PIG.create(helper.getLevel());
        BlockPos pos = helper.absolutePos(new BlockPos(0, 1, 0)).above(100);
        pig.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        helper.getLevel().addFreshEntity(pig);
        try {
            int oneHanded = total(player, ActivitySkills.ONE_HANDED);
            int dual = total(player, ActivitySkills.DUAL_WIELDING);
            pig.hurt(helper.getLevel().damageSources().playerAttack(player), 4.0f);
            helper.assertTrue(total(player, ActivitySkills.ONE_HANDED) == oneHanded + 4, "a 4-damage sword hit should give 4 One-Handed XP");
            helper.assertTrue(total(player, ActivitySkills.DUAL_WIELDING) == dual + 2, "with a sword in each hand it trains Dual Wielding too");
        } finally {
            pig.discard();
        }
        helper.succeed();
    }

    /** One bad node (an attribute a mod doesn't register) makes Pufferfish's Skills drop every one of our trees; catch that. */
    @GameTest(template = ANY_TEMPLATE)
    public static void everyTreeLoads(GameTestHelper helper) {
        if (!SkillLevels.pufferfish()) {
            helper.succeed();
            return;
        }
        ServerPlayer player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "tree_checker"));
        for (String id : SkillLevels.DEFAULT_SKILLS) {
            ResourceLocation category = ResourceLocation.parse(id);
            if (category.getNamespace().equals(DungeonDelving.MODID)) {
                helper.assertTrue(PufferfishSkills.level(player, category).isPresent(), "the " + id + " tree should load");
            }
        }
        helper.succeed();
    }

    private static int total(ServerPlayer player, String skill) {
        return PufferfishSkills.total(player, DungeonDelving.id(skill)).orElseThrow();
    }

    private static void expectStyle(GameTestHelper helper, ItemStack stack, String style) {
        helper.assertTrue(ActivitySkills.styleFor(stack).equals(Optional.of(style)), stack + " should train " + style + ", got " + ActivitySkills.styleFor(stack));
    }
}
