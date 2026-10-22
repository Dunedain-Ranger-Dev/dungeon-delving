package io.github.ranger3462.dungeondelving.gametest;

import java.util.UUID;

import com.mojang.authlib.GameProfile;

import dev.shadowsoffire.apotheosis.affix.AffixHelper;
import dev.shadowsoffire.apotheosis.socket.SocketHelper;
import io.github.ranger3462.dungeondelving.DungeonDelving;
import io.github.ranger3462.dungeondelving.forging.ForgedGear;
import io.github.ranger3462.dungeondelving.forging.Forging;
import io.github.ranger3462.dungeondelving.integration.apotheosis.ApotheosisForging;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Checks what forged gear's ★ means to Apotheosis (when it's installed): sockets, affix multipliers and ★7's rolled rarity. */
@GameTestHolder(DungeonDelving.MODID)
@PrefixGameTestTemplate(false)
public class ApotheosisForgingTests {
    private static final String ANY_TEMPLATE = "dungeon/cap_placeholder";

    @GameTest(template = ANY_TEMPLATE)
    public static void highStarsAddSockets(GameTestHelper helper) {
        if (!ModList.get().isLoaded("apotheosis")) {
            helper.succeed();
            return;
        }
        Item helmet = Forging.ARMOR.get(ForgedGear.Weight.HEAVY).get(ArmorItem.Type.HELMET).get();
        helper.assertTrue(SocketHelper.getSockets(starred(helmet, 3)) == 0, "★3 gear has no extra sockets");
        helper.assertTrue(SocketHelper.getSockets(starred(helmet, 4)) == 1, "★4 gear has an extra socket");
        helper.assertTrue(SocketHelper.getSockets(starred(helmet, 7)) == 2, "★5 and up have two");
        helper.succeed();
    }

    /** ★7 gear is crafted with affixes; the same affixes count for more on ★7 than they would on ★5. */
    @GameTest(template = ANY_TEMPLATE)
    public static void starSevenRollsAndMultipliesAffixes(GameTestHelper helper) {
        if (!ModList.get().isLoaded("apotheosis")) {
            helper.succeed();
            return;
        }
        ServerPlayer player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "apoth_tester"));
        ItemStack seven = ItemStack.EMPTY;
        ItemStack five = ItemStack.EMPTY;
        double fiveBonus = 0;
        // A roll could (rarely) be all special effects with no attribute bonuses to scale; roll again if so.
        for (int roll = 0; roll < 10 && fiveBonus == 0; roll++) {
            seven = starred(Forging.FORGED_SWORD.get(), 7);
            ApotheosisForging.roll(seven, player);
            five = seven.copy();
            five.set(Forging.STARS.get(), 5);
            fiveBonus = bonus(five);
        }
        helper.assertTrue(AffixHelper.hasAffixes(seven), "a ★7 sword gets rolled affixes");
        double sevenBonus = bonus(seven);
        helper.assertTrue(fiveBonus > 0 && Math.abs(sevenBonus / fiveBonus - ApotheosisForging.MULTIPLIER_SEVEN) < 0.01,
                "★7 should scale Apotheosis's bonuses by " + ApotheosisForging.MULTIPLIER_SEVEN + ": ★5 " + fiveBonus + ", ★7 " + sevenBonus);
        helper.assertTrue(ApotheosisForging.multiplier(6) == ApotheosisForging.MULTIPLIER_SIX && ApotheosisForging.multiplier(5) == 1.0f,
                "only ★6 and ★7 multiply");
        helper.succeed();
    }

    private static ItemStack starred(Item item, int stars) {
        ItemStack stack = new ItemStack(item);
        stack.set(Forging.STARS.get(), stars);
        ForgedGear.applyStats(stack);
        return stack;
    }

    /** The total of a sword's attribute modifiers other than its own base damage and speed. */
    private static double bonus(ItemStack stack) {
        double[] total = { 0 };
        stack.forEachModifier(EquipmentSlot.MAINHAND, (attribute, modifier) -> {
            if (!modifier.id().equals(Item.BASE_ATTACK_DAMAGE_ID) && !modifier.id().equals(Item.BASE_ATTACK_SPEED_ID)) {
                total[0] += Math.abs(modifier.amount());
            }
        });
        return total[0];
    }
}
