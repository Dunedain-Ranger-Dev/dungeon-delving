package io.github.ranger3462.dungeondelving.testing;

import java.util.List;

import io.github.ranger3462.dungeondelving.DungeonDelving;
import io.github.ranger3462.dungeondelving.integration.ars.ArsMana;
import io.github.ranger3462.dungeondelving.integration.irons.IronsMana;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * The Tester's Charm: a testing tool for playing in survival with creative's safety. While it's anywhere in a
 * player's inventory, they can't die (lethal hits leave them at half a heart), every hit they take is reported in chat
 * with its damage and source, they heal very fast with full hunger and mana (Iron's Spells and Ars Nouveau), and they
 * can fly like in creative. Only in the creative tab; it has no recipe.
 */
@EventBusSubscriber(modid = DungeonDelving.MODID)
public final class TestersCharm {
    /** Saved on players while the charm has given them flight, so it can be taken away again. */
    private static final String FLIGHT_TAG = DungeonDelving.MODID + ":charm_flight";
    /** Health restored every {@link #HEAL_INTERVAL} ticks: 8 health (4 hearts) a second. */
    private static final float HEAL_AMOUNT = 2.0f;
    private static final int HEAL_INTERVAL = 5;

    private TestersCharm() {}

    /** The charm item itself. */
    public static class CharmItem extends Item {
        public CharmItem(Properties properties) {
            super(properties);
        }

        @Override
        public boolean isFoil(ItemStack stack) {
            return true;
        }

        @Override
        public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
            tooltip.add(Component.translatable("item.dungeondelving.testers_charm.tooltip").withStyle(ChatFormatting.GRAY));
        }
    }

    public static boolean has(Player player) {
        return player.getInventory().hasAnyMatching(stack -> stack.getItem() instanceof CharmItem);
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        boolean charmed = has(player);
        updateFlight(player, charmed);
        if (!charmed || player.tickCount % HEAL_INTERVAL != 0) {
            return;
        }
        if (player.getHealth() < player.getMaxHealth()) {
            player.heal(HEAL_AMOUNT);
        }
        player.getFoodData().setFoodLevel(20);
        player.getFoodData().setSaturation(5.0f);
        if (ModList.get().isLoaded("irons_spellbooks")) {
            IronsMana.refill(player);
        }
        if (ModList.get().isLoaded("ars_nouveau")) {
            ArsMana.refill(player);
        }
    }

    /** Gives flight while the charm is carried, and takes it away (outside creative) once it isn't. */
    private static void updateFlight(ServerPlayer player, boolean charmed) {
        boolean given = player.getPersistentData().getBoolean(FLIGHT_TAG);
        if (charmed && !player.getAbilities().mayfly) {
            player.getAbilities().mayfly = true;
            player.getPersistentData().putBoolean(FLIGHT_TAG, true);
            player.onUpdateAbilities();
        } else if (!charmed && given) {
            player.getPersistentData().remove(FLIGHT_TAG);
            if (!player.isCreative() && !player.isSpectator()) {
                player.getAbilities().mayfly = false;
                player.getAbilities().flying = false;
                player.onUpdateAbilities();
            }
        }
    }

    /** Reports every hit, and stops lethal ones at half a heart. Runs last, so armor and other mods have had their say. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDamage(LivingDamageEvent.Pre event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !has(player)) {
            return;
        }
        float damage = event.getNewDamage();
        player.sendSystemMessage(Component.translatable("message.dungeondelving.charm_hit", String.format("%.1f", damage),
                String.format("%.1f", damage / 2.0f), describe(event.getSource())).withStyle(ChatFormatting.GRAY));
        if (damage >= player.getHealth()) {
            event.setNewDamage(Math.max(0.0f, player.getHealth() - 1.0f));
        }
    }

    /** Anything that still kills the player (e.g. /kill) is stopped too. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && has(player)) {
            event.setCanceled(true);
            player.setHealth(1.0f);
        }
    }

    /** What dealt the damage: the attacker's name if there is one, and the kind of damage. */
    private static Component describe(DamageSource source) {
        Component kind = Component.literal(source.getMsgId());
        if (source.getEntity() != null) {
            return Component.translatable("message.dungeondelving.charm_hit_by", source.getEntity().getDisplayName(), kind);
        }
        return kind;
    }
}
