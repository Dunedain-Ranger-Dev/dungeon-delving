package io.github.ranger3462.dungeondelving.gametest;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.github.ranger3462.dungeondelving.DungeonDelving;
import io.github.ranger3462.dungeondelving.dungeon.DungeonFactions;
import io.github.ranger3462.dungeondelving.dungeon.SpawnerMobs;
import io.github.ranger3462.dungeondelving.dungeon.challenge.DungeonBosses;
import io.github.ranger3462.dungeondelving.entity.SkeletonKnight;
import io.github.ranger3462.dungeondelving.entity.SkeletonLord;
import io.github.ranger3462.dungeondelving.entity.SkeletonSwordsman;
import io.github.ranger3462.dungeondelving.registry.ModEntities;
import io.github.ranger3462.dungeondelving.workshop.RunicWorkshop;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Checks the skeleton faction: swordsmen, knights, the Skeleton Lord and his guard, and where they spawn. */
@GameTestHolder(DungeonDelving.MODID)
@PrefixGameTestTemplate(false)
public class SkeletonTests {
    private static final String ANY_TEMPLATE = "dungeon/cap_placeholder";

    @GameTest(template = ANY_TEMPLATE)
    public static void swordsmenAndKnightsAreArmedAndArmored(GameTestHelper helper) {
        SkeletonSwordsman swordsman = spawn(helper, ModEntities.SKELETON_SWORDSMAN.get());
        SkeletonKnight knight = spawn(helper, ModEntities.SKELETON_KNIGHT.get());
        try {
            helper.assertTrue(swordsman.getMainHandItem().is(Items.IRON_SWORD) && swordsman.getOffhandItem().is(Items.SHIELD),
                    "swordsmen should carry a sword and a shield");
            helper.assertTrue(swordsman.getMaxHealth() == 24.0f, "swordsmen should have 24 health, not " + swordsman.getMaxHealth());
            helper.assertTrue(knight.getMaxHealth() == 34.0f && knight.getItemBySlot(EquipmentSlot.CHEST).is(Items.IRON_CHESTPLATE),
                    "knights should be tougher and armored");
            helper.assertTrue(swordsman.getType().is(EntityTypeTags.SKELETONS), "swordsmen count as skeletons (e.g. for Smite)");
            swordsman.startUsingItem(InteractionHand.OFF_HAND);
            helper.assertTrue(swordsman.isUsingItem(), "swordsmen should be able to raise their shields");
        } finally {
            swordsman.discard();
            knight.discard();
        }
        helper.succeed();
    }

    @GameTest(template = ANY_TEMPLATE)
    public static void theSkeletonLordComesWithHisHonorGuard(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(0, 1, 0)).above(100);
        LivingEntity lord = DungeonBosses.spawn(helper.getLevel(), pos, 1, -1, DungeonBosses.SKELETON_LORD);
        List<SkeletonKnight> guard = helper.getLevel().getEntitiesOfClass(SkeletonKnight.class, new AABB(pos).inflate(6));
        try {
            helper.assertTrue(lord instanceof SkeletonLord && DungeonBosses.isBoss(lord), "the Skeleton Lord should spawn as a boss");
            helper.assertTrue(lord.getMaxHealth() == 120.0f && lord.getMainHandItem().is(Items.MACE), "the Lord should be big, tough and carry a mace");
            helper.assertTrue(guard.size() == SkeletonLord.GUARDS, "the Lord should have " + SkeletonLord.GUARDS + " knights, has " + guard.size());
        } finally {
            lord.discard();
            guard.forEach(LivingEntity::discard);
        }
        LootTable table = helper.getLevel().getServer().reloadableRegistries().getLootTable(DungeonBosses.lootTable(DungeonBosses.SKELETON_LORD, 1));
        LootParams params = new LootParams.Builder(helper.getLevel()).withParameter(LootContextParams.ORIGIN, Vec3.ZERO).create(LootContextParamSets.CHEST);
        boolean bulwark = false;
        for (int i = 0; i < 60; i++) {
            List<ItemStack> drops = table.getRandomItems(params);
            helper.assertTrue(drops.stream().anyMatch(stack -> stack.is(RunicWorkshop.shard(1))), "the Lord should always drop Runic Shard I");
            bulwark |= drops.stream().anyMatch(stack -> stack.is(Items.SHIELD) && stack.has(DataComponents.ITEM_NAME)
                    && !stack.getAttributeModifiers().modifiers().isEmpty());
        }
        helper.assertTrue(bulwark, "the Lord should sometimes drop his Bulwark");
        helper.succeed();
    }

    @GameTest(template = ANY_TEMPLATE)
    public static void skeletonDungeonsSpawnSkeletons(GameTestHelper helper) {
        RandomSource random = RandomSource.create(4);
        Set<EntityType<?>> tierOne = new HashSet<>();
        Set<EntityType<?>> tierTwo = new HashSet<>();
        Set<EntityType<?>> tierThree = new HashSet<>();
        for (int i = 0; i < 400; i++) {
            tierOne.add(SpawnerMobs.pick(helper.getLevel(), random, DungeonFactions.SKELETON_DEFAULTS, 1).orElseThrow().value());
            tierTwo.add(SpawnerMobs.pick(helper.getLevel(), random, DungeonFactions.SKELETON_DEFAULTS, 2).orElseThrow().value());
            tierThree.add(SpawnerMobs.pick(helper.getLevel(), random, DungeonFactions.SKELETON_DEFAULTS, 3).orElseThrow().value());
        }
        helper.assertTrue(tierOne.equals(Set.of(ModEntities.SKELETON_SWORDSMAN.get(), EntityType.SKELETON)),
                "tier 1 skeleton dungeons should spawn swordsmen and skeleton archers, got " + tierOne);
        helper.assertTrue(tierTwo.equals(Set.of(ModEntities.SKELETON_SWORDSMAN.get(), EntityType.SKELETON, EntityType.STRAY, EntityType.BOGGED)),
                "tier 2 skeleton dungeons should add strays and bogged, got " + tierTwo);
        helper.assertTrue(tierThree.contains(ModEntities.SKELETON_KNIGHT.get()) && tierThree.contains(EntityType.WITHER_SKELETON),
                "tier 3 skeleton dungeons should add knights and wither skeletons, got " + tierThree);
        helper.succeed();
    }

    /** A raised shield really blocks: a hit from the front does nothing, a hit from behind still lands. */
    @GameTest(template = ANY_TEMPLATE, timeoutTicks = 60)
    public static void aRaisedShieldBlocksHitsFromTheFront(GameTestHelper helper) {
        SkeletonSwordsman swordsman = spawn(helper, ModEntities.SKELETON_SWORDSMAN.get());
        swordsman.setNoAi(true);
        swordsman.setYRot(0.0f); // Facing south (+z).
        swordsman.setYHeadRot(0.0f);
        swordsman.setYBodyRot(0.0f);
        swordsman.startUsingItem(InteractionHand.OFF_HAND);
        Zombie front = EntityType.ZOMBIE.create(helper.getLevel());
        front.moveTo(swordsman.getX(), swordsman.getY(), swordsman.getZ() + 2);
        Zombie behind = EntityType.ZOMBIE.create(helper.getLevel());
        behind.moveTo(swordsman.getX(), swordsman.getY(), swordsman.getZ() - 2);
        // Shields only block once they've been up for 5 ticks.
        helper.runAfterDelay(10, () -> {
            try {
                helper.assertTrue(swordsman.isBlocking(), "the swordsman should be blocking after raising its shield");
                float health = swordsman.getHealth();
                swordsman.hurt(helper.getLevel().damageSources().mobAttack(front), 6.0f);
                helper.assertTrue(swordsman.getHealth() == health, "a hit from the front should be blocked");
                swordsman.invulnerableTime = 0;
                swordsman.hurt(helper.getLevel().damageSources().mobAttack(behind), 6.0f);
                helper.assertTrue(swordsman.getHealth() < health, "a hit from behind should still land");
            } finally {
                swordsman.discard();
                front.discard();
                behind.discard();
            }
            helper.succeed();
        });
    }

    private static <T extends SkeletonSwordsman> T spawn(GameTestHelper helper, EntityType<T> type) {
        T mob = type.create(helper.getLevel());
        BlockPos pos = helper.absolutePos(new BlockPos(0, 1, 0)).above(100);
        mob.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        EventHooks.finalizeMobSpawn(mob, helper.getLevel(), helper.getLevel().getCurrentDifficultyAt(pos), MobSpawnType.EVENT, null);
        helper.getLevel().addFreshEntity(mob);
        return mob;
    }
}
